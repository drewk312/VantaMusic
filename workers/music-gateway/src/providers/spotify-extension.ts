import type { GatewayTrack } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";

// Public web-client bootstrap constants from the supplied spotify-web 1.10.2.
const VERSION = 61;
const SECRET = [44,55,47,42,70,40,34,114,76,74,50,111,120,97,75,76,94,102,43,69,49,120,118,80,64,78];
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
type WebSession = { access: string; client: string; version: string; expires: number };
async function text(url: string, init: RequestInit = {}): Promise<string | null> {
  try {
    const r = await fetch(url, { ...init, redirect: "manual", signal: AbortSignal.timeout(4000) });
    if (!r.ok || !r.body) { await r.body?.cancel(); return null; }
    let result = "", bytes = 0; const reader = r.body.getReader(), decoder = new TextDecoder();
    try { while (true) { const next = await reader.read(); if (next.done) break; bytes += next.value.length;
      if (bytes > 2_000_000) { await reader.cancel(); return null; } result += decoder.decode(next.value, { stream: true });
    } } finally { reader.releaseLock(); }
    return result + decoder.decode();
  } catch { return null; }
}
async function json(url: string, init: RequestInit = {}): Promise<any> {
  const value = await text(url, init); try { return value ? JSON.parse(value) : null; } catch { return null; }
}
export async function spotifyWebTotp(timestamp = Date.now()): Promise<string> {
  const secret = SECRET.map((v, i) => v ^ ((i % 33) + 9)).join("");
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const counter = new Uint8Array(8); new DataView(counter.buffer).setBigUint64(0, BigInt(Math.floor(timestamp / 30000)));
  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", key, counter)); const offset = digest[digest.length - 1] & 15;
  return String((new DataView(digest.buffer).getUint32(offset) & 0x7fffffff) % 1000000).padStart(6, "0");
}
async function session(): Promise<WebSession | null> {
  const cache = typeof caches === "undefined" ? null : caches.default;
  const cacheKey = new Request("https://open.spotify.com/vanta-extension-public-session-v1");
  const cached = await cache?.match(cacheKey).catch(() => undefined);
  if (cached) { const data = await cached.json() as WebSession; if (data.expires > Date.now() + 60000) return data; }
  const totp = await spotifyWebTotp();
  const [token, home] = await Promise.all([
    json(`https://open.spotify.com/api/token?reason=init&productType=web-player&totp=${totp}&totpVer=${VERSION}&totpServer=${totp}`, { headers: { "User-Agent": UA } }),
    text("https://open.spotify.com", { headers: { "User-Agent": UA } }),
  ]);
  if (!token?.accessToken || !token.clientId || !home) return null;
  const encoded = home.match(/<script id="appServerConfig" type="text\/plain">([^<]+)<\/script>/)?.[1];
  let version: string;
  try { version = JSON.parse(atob(encoded ?? "")).clientVersion; } catch { return null; }
  if (!version) return null;
  const client = await json("https://clienttoken.spotify.com/v1/clienttoken", { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json", "User-Agent": UA },
    body: JSON.stringify({ client_data: { client_version: version, client_id: token.clientId, js_sdk_data: {
      device_brand: "unknown", device_model: "unknown", os: "windows", os_version: "NT 10.0", device_id: crypto.randomUUID().replace(/-/g, ""), device_type: "computer",
    } } }),
  });
  if (client?.response_type !== "RESPONSE_GRANTED_TOKEN_RESPONSE" || !client.granted_token?.token) return null;
  const data: WebSession = { access: token.accessToken, client: client.granted_token.token, version,
    expires: Math.min(Number(token.accessTokenExpirationTimestampMs), Date.now() + Number(client.granted_token.expires_after_seconds) * 1000) };
  if (!Number.isFinite(data.expires)) return null;
  await cache?.put(cacheKey, Response.json(data, { headers: { "Cache-Control": `max-age=${Math.max(1, Math.floor((data.expires - Date.now()) / 1000) - 60)}` } })).catch(() => undefined);
  return data;
}

export function spotifyExtensionTracks(payload: any): GatewayTrack[] {
  const search = payload?.data?.searchV2;
  return (search?.tracksV2?.items ?? search?.tracks?.items ?? []).flatMap((item: any) => {
    const track = item.item?.data ?? item.track ?? item.data;
    const id = track?.id ?? track?.uri?.split(":").at(-1);
    const artists = (track?.artists?.items ?? []).map((a: any) => (a.data ?? a).profile?.name).filter(Boolean);
    if (!id || !/^[a-zA-Z0-9]{22}$/.test(id) || !track.name || !artists.length) return [];
    const album = track.albumOfTrack ?? track.album;
    const images = [...(album?.coverArt?.sources ?? [])].sort((a: any, b: any) => (b.width ?? 0) - (a.width ?? 0));
    return [{ id, spotify_id: id, provider: "spotify", title: track.name, artist: artists.join(", "), album: album?.name,
      artworkURL: normalizePublicHttpsUrl(images[0]?.url) ?? undefined,
      duration: Math.round((track.duration?.totalMilliseconds ?? track.trackDuration?.totalMilliseconds ?? 0) / 1000),
      audioQuality: "Catalog metadata", isHiRes: false, isDolbyAtmos: false }];
  });
}

export async function searchSpotifyExtension(query: string, limit = 15): Promise<GatewayTrack[]> {
  const auth = await session(); if (!auth) return [];
  const data = await json("https://api-partner.spotify.com/pathfinder/v2/query", { method: "POST",
    headers: { Authorization: `Bearer ${auth.access}`, "Client-Token": auth.client, "Spotify-App-Version": auth.version, "Content-Type": "application/json", "User-Agent": UA },
    body: JSON.stringify({ variables: { searchTerm: query, offset: 0, limit, numberOfTopResults: 5, includeAudiobooks: true, includeArtistHasConcertsField: false, includePreReleases: true, includeAuthors: false },
      operationName: "searchDesktop", extensions: { persistedQuery: { version: 1, sha256Hash: "fcad5a3e0d5af727fb76966f06971c19cfa2275e6ff7671196753e008611873c" } } }),
  });
  return spotifyExtensionTracks(data);
}

function mapWebApiTrack(item: Record<string, unknown> | null | undefined): GatewayTrack | null {
  if (!item || typeof item !== "object") return null;
  const id = String(item.id ?? "");
  const name = String(item.name ?? "");
  const artists = ((item.artists as unknown[]) ?? []) as Record<string, unknown>[];
  const artist = artists
    .map((entry) => String(entry.name ?? "").trim())
    .filter((value) => value.length > 0)
    .join(", ");
  if (!id || !/^[a-zA-Z0-9]{22}$/.test(id) || !name || !artist) return null;
  const album = item.album as Record<string, unknown> | undefined;
  const images = ((album?.images as unknown[]) ?? []) as Record<string, string>[];
  const durationMs = typeof item.duration_ms === "number" ? item.duration_ms : 0;
  const isrc = ((item.external_ids as Record<string, string> | undefined)?.isrc) ?? undefined;
  return {
    id,
    spotify_id: id,
    provider: "spotify",
    title: name,
    artist,
    album: typeof album?.name === "string" ? album.name : undefined,
    artworkURL: normalizePublicHttpsUrl(images[0]?.url) ?? undefined,
    duration: Math.round(durationMs / 1000) || undefined,
    isrc,
    audioQuality: "Catalog metadata",
    isHiRes: false,
    isDolbyAtmos: false,
  };
}

function cleanPlaylistId(playlistId: string): string {
  return playlistId
    .replace(/^spotify:playlist:/i, "")
    .replace(/^https?:\/\/open\.spotify\.com\/playlist\//i, "")
    .split("?")[0]
    .trim();
}

function mapEmbedTrack(row: Record<string, unknown>): GatewayTrack | null {
  const uri = String(row.uri ?? "");
  const id = uri.startsWith("spotify:track:")
    ? uri.slice("spotify:track:".length)
    : String(row.id ?? "");
  const title = String(row.title ?? row.name ?? "").trim();
  const artist = String(row.subtitle ?? "").trim()
    .replace(/\u00a0/g, " ")
    .replace(/\s+/g, " ");
  if (!id || !/^[a-zA-Z0-9]{22}$/.test(id) || !title || !artist) return null;
  const durationMs = typeof row.duration === "number" ? row.duration : 0;
  return {
    id,
    spotify_id: id,
    provider: "spotify",
    title,
    artist,
    duration: Math.round(durationMs / 1000) || undefined,
    audioQuality: "Catalog metadata",
    isHiRes: false,
    isDolbyAtmos: false,
  };
}

/**
 * Public playlist via Spotify's embed page (__NEXT_DATA__).
 * No app registration, no CAPTCHA, no api.spotify.com rate limit.
 * Embed typically returns up to ~50 tracks — enough for most shared playlists.
 */
export async function importSpotifyPlaylistEmbed(
  playlistId: string,
  maxTracks = 300,
): Promise<{ name?: string; artworkURL?: string; tracks: GatewayTrack[] }> {
  const cleanId = cleanPlaylistId(playlistId);
  if (!cleanId || !/^[a-zA-Z0-9]+$/.test(cleanId)) return { tracks: [] };

  try {
    const res = await fetch(`https://open.spotify.com/embed/playlist/${encodeURIComponent(cleanId)}`, {
      headers: { "User-Agent": UA, Accept: "text/html" },
      redirect: "follow",
      signal: AbortSignal.timeout(12_000),
      cf: { cacheTtl: 300 } as never,
    });
    if (!res.ok) return { tracks: [] };
    const html = await res.text();
    const raw = html.match(/<script id="__NEXT_DATA__" type="application\/json">([^<]+)<\/script>/)?.[1];
    if (!raw) return { tracks: [] };
    const root = JSON.parse(raw) as {
      props?: { pageProps?: { state?: { data?: { entity?: Record<string, unknown> } } } };
    };
    const entity = root.props?.pageProps?.state?.data?.entity;
    if (!entity || typeof entity !== "object") return { tracks: [] };

    const name = typeof entity.name === "string" && entity.name.trim() ? entity.name.trim() : undefined;
    const cover = entity.coverArt as { sources?: Array<{ url?: string }> } | undefined;
    const artworkURL = normalizePublicHttpsUrl(cover?.sources?.[0]?.url) ?? undefined;
    const list = (entity.trackList as unknown[]) ?? [];
    const cap = Math.min(Math.max(maxTracks, 1), 2000);
    const tracks: GatewayTrack[] = [];
    for (const row of list) {
      if (!row || typeof row !== "object") continue;
      const mapped = mapEmbedTrack(row as Record<string, unknown>);
      if (mapped) tracks.push(mapped);
      if (tracks.length >= cap) break;
    }
    return { name, artworkURL, tracks };
  } catch {
    return { tracks: [] };
  }
}

/**
 * Public playlist tracks. Prefers embed scrape (no auth). Falls back to
 * anonymous web-player token + Web API when embed is empty.
 */
export async function importSpotifyPlaylistExtension(
  playlistId: string,
  maxTracks = 1000,
): Promise<{ name?: string; artworkURL?: string; tracks: GatewayTrack[] }> {
  const embed = await importSpotifyPlaylistEmbed(playlistId, maxTracks);
  // If the playlist has fewer than 100 tracks, the embed returned the entire playlist.
  if (embed.tracks.length > 0 && (embed.tracks.length < 100 || maxTracks <= embed.tracks.length)) {
    return embed;
  }

  const cleanId = cleanPlaylistId(playlistId);
  if (!cleanId || !/^[a-zA-Z0-9]+$/.test(cleanId)) return embed.tracks.length > 0 ? embed : { tracks: [] };

  const auth = await session();
  if (!auth) return embed.tracks.length > 0 ? embed : { tracks: [] };

  const headers = {
    Authorization: `Bearer ${auth.access}`,
    Accept: "application/json",
    "User-Agent": UA,
  };

  let name: string | undefined = embed.name;
  let artworkURL: string | undefined = embed.artworkURL;
  try {
    const meta = await json(
      `https://api.spotify.com/v1/playlists/${encodeURIComponent(cleanId)}?fields=name,images`,
      { headers },
    );
    if (meta && typeof meta.name === "string" && meta.name.trim()) name = meta.name.trim();
    const images = (meta?.images as Record<string, string>[] | undefined) ?? [];
    artworkURL = normalizePublicHttpsUrl(images[0]?.url) ?? artworkURL;
  } catch {
    // metadata is optional; tracks still matter
  }

  const tracks: GatewayTrack[] = [...embed.tracks];
  let offset = tracks.length;
  const pageSize = 100;
  const cap = Math.min(Math.max(maxTracks, 1), 2000);

  while (tracks.length < cap) {
    const limit = Math.min(pageSize, cap - tracks.length);
    const page = await json(
      `https://api.spotify.com/v1/playlists/${encodeURIComponent(cleanId)}/tracks?limit=${limit}&offset=${offset}&market=US&fields=items(track(id,name,artists,album(name,images),duration_ms,external_ids)),next`,
      { headers },
    );
    if (!page) break;
    const items = (page.items as Array<{ track?: Record<string, unknown> | null }> | undefined) ?? [];
    if (items.length === 0) break;
    for (const row of items) {
      const mapped = mapWebApiTrack(row.track ?? undefined);
      if (mapped) tracks.push(mapped);
      if (tracks.length >= cap) break;
    }
    if (!page.next) break;
    offset += items.length;
    if (offset > 5000) break;
  }

  return { name, artworkURL, tracks };
}
