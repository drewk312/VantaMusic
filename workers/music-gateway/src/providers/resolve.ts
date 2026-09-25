import type { Env, ResolveResult } from "../types";
import { fetchJson } from "./shared";
import { getQobuzTrackMeta, lookupQobuzTrackByIsrc } from "./qobuz-api";

interface OdesliResponse {
  linksByPlatform?: Record<string, { url?: string }>;
  entitiesByUniqueId?: Record<string, { isrc?: string }>;
}

interface ZarzResolveResponse {
  success?: boolean;
  isrc?: string;
  songUrls?: Record<string, string | null>;
}

interface IdhsLink {
  type?: string;
  url?: string;
  notAvailable?: boolean;
}

interface IdhsResponse {
  links?: IdhsLink[];
}

export async function resolveTrackLinks(targetUrl: string, env: Env): Promise<ResolveResult> {
  const merged: ResolveResult = { external_links: {} };

  const odesli = await resolveViaOdesli(targetUrl).catch(() => null);
  mergeResolve(merged, odesli);

  if (!hasPlatformIds(merged)) {
    const idhs = await resolveViaIdhs(targetUrl).catch(() => null);
    mergeResolve(merged, idhs);
  }

  if (!merged.spotify_id && isSpotifyUrl(targetUrl)) {
    const zarz = await resolveViaZarz(targetUrl, env).catch(() => null);
    mergeResolve(merged, zarz);
  }

  return merged;
}

export async function resolveTrack(opts: {
  url?: string;
  trackId?: string;
  provider?: string;
  env?: Env;
}): Promise<ResolveResult> {
  const env = opts.env ?? ({} as Env);

  if (opts.url) {
    return resolveTrackLinks(opts.url, env);
  }

  if (opts.provider === "deezer" && opts.trackId) {
    const fromDeezer = await resolveDeezerTrack(opts.trackId, env);
    if (fromDeezer) return fromDeezer;
  }

  if (opts.provider === "qobuz" && opts.trackId) {
    const fromQobuz = await resolveQobuzTrack(opts.trackId, env);
    if (fromQobuz) return fromQobuz;
  }

  if (opts.provider === "tidal" && opts.trackId) {
    const fromTidal = await resolveTidalTrack(opts.trackId, env).catch(() => null);
    if (fromTidal) return fromTidal;
    return resolveTrackLinks(`https://listen.tidal.com/track/${opts.trackId}`, env);
  }

  if (opts.provider === "amazon" && opts.trackId) {
    return resolveTrackLinks(`https://music.amazon.com/tracks/${opts.trackId}`, env);
  }

  if (opts.provider === "apple" && opts.trackId) {
    return resolveTrackLinks(`https://music.apple.com/us/song/${opts.trackId}`, env);
  }

  if (opts.provider === "spotify" && opts.trackId) {
    return resolveTrackLinks(`https://open.spotify.com/track/${encodeURIComponent(opts.trackId)}`, env);
  }
  // Never reinterpret a SoundCloud numeric ID as another catalog's track ID.
  if (opts.provider === "soundcloud") return { external_links: {} };

  if (opts.trackId) {
    const fromDeezer = await resolveDeezerTrack(opts.trackId, env).catch(() => null);
    if (fromDeezer?.isrc) return fromDeezer;
    const fromQobuz = await resolveQobuzTrack(opts.trackId, env).catch(() => null);
    if (fromQobuz?.isrc) return fromQobuz;
    return fromDeezer ?? fromQobuz ?? { external_links: {} };
  }

  throw new Error("resolve_requires_url_or_track_id");
}

async function resolveViaOdesli(targetUrl: string): Promise<ResolveResult> {
  try {
    const response = await fetch(
      `https://api.odesli.co/matches?url=${encodeURIComponent(targetUrl)}`,
      {
        headers: { Accept: "application/json" },
        signal: AbortSignal.timeout(1_000),
      }
    );
    if (!response.ok) {
      return { external_links: {} };
    }

    const data = (await response.json()) as any;
    const result: ResolveResult = { external_links: {} };
    if (data.type && !["song", "track"].includes(data.type)) return result;

    for (const entity of Object.values(data.metadataByUniqueId ?? data.entitiesByUniqueId ?? {})) {
      if ((entity as any)?.isrc) {
        result.isrc = (entity as any).isrc;
        break;
      }
    }

    applyOdesliLinks(result, data.links ?? data.linksByPlatform ?? {});
    return result;
  } catch {
    return { external_links: {} };
  }
}

async function resolveViaIdhs(targetUrl: string): Promise<ResolveResult> {
  const response = await fetch("https://idonthavespotify.sjdonado.com/api/search?v=1", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": "VANTA-MusicGateway/2.0",
    },
    body: JSON.stringify({
      link: targetUrl,
      adapters: ["tidal", "deezer", "qobuz", "amazonMusic", "appleMusic", "spotify"],
    }),
    signal: AbortSignal.timeout(3_000),
  });
  if (!response.ok) throw new Error(`idhs_failed:${response.status}`);

  const data = (await response.json()) as IdhsResponse;
  const result: ResolveResult = { external_links: {} };

  for (const link of data.links ?? []) {
    if (!link.url || link.notAvailable) continue;
    const type = (link.type ?? "").toLowerCase();
    switch (type) {
      case "tidal":
        result.external_links!.tidal = link.url;
        result.tidal_id = extractId(link.url, /\/track\/(\d+)/);
        break;
      case "deezer":
        result.external_links!.deezer = link.url;
        result.deezer_id = extractId(link.url, /\/track\/(\d+)/);
        break;
      case "qobuz":
        result.external_links!.qobuz = link.url;
        result.qobuz_id = extractId(link.url, /\/track\/(\d+)/);
        break;
      case "applemusic":
        result.external_links!.apple = link.url;
        result.apple_id = extractId(link.url, /(?:\?i=|\/id)(\d+)/);
        break;
      case "spotify":
        result.external_links!.spotify = link.url;
        result.spotify_id = extractId(link.url, /\/track\/([a-zA-Z0-9]+)/);
        break;
      default:
        break;
    }
  }

  return result;
}

async function resolveViaZarz(targetUrl: string, env: Env): Promise<ResolveResult> {
  const endpoint = env.ZARZ_RESOLVE_URL?.trim() || "https://api.zarz.moe/v1/resolve";
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": "VANTA-MusicGateway/2.0",
    },
    body: JSON.stringify({ url: targetUrl }),
  });
  if (!response.ok) throw new Error(`zarz_failed:${response.status}`);

  const data = (await response.json()) as ZarzResolveResponse;
  const result: ResolveResult = { external_links: {}, isrc: data.isrc };

  for (const [platform, url] of Object.entries(data.songUrls ?? {})) {
    if (!url) continue;
    const key = platform.toLowerCase();
    if (key === "tidal") {
      result.external_links!.tidal = url;
      result.tidal_id = extractId(url, /\/track\/(\d+)/);
    } else if (key === "qobuz") {
      result.external_links!.qobuz = url;
      result.qobuz_id = extractId(url, /\/track\/(\d+)/);
    } else if (key === "deezer") {
      result.external_links!.deezer = url;
      result.deezer_id = extractId(url, /\/track\/(\d+)/);
    } else if (key === "amazonmusic") {
      result.external_links!.amazon = url;
      result.amazon_id = extractAmazonId(url);
    } else if (key === "applemusic") {
      result.external_links!.apple = url;
      result.apple_id = extractId(url, /(?:\?i=|\/id)(\d+)/);
    } else if (key === "spotify") {
      result.external_links!.spotify = url;
      result.spotify_id = extractId(url, /\/track\/([a-zA-Z0-9]+)/);
    }
  }

  return result;
}

async function resolveDeezerTrack(deezerTrackId: string, env: Env): Promise<ResolveResult | null> {
  const payload = await fetchJson(`https://api.deezer.com/track/${encodeURIComponent(deezerTrackId)}`);
  const track = payload as {
    isrc?: string;
    link?: string;
    title?: string;
    artist?: { name?: string };
    album?: { title?: string };
    duration?: number;
  } | null;
  if (!track) return null;

  const merged: ResolveResult = {
    deezer_id: deezerTrackId,
    isrc: track.isrc,
    title: track.title,
    artist: track.artist?.name,
    durationSec: typeof track.duration === "number" ? track.duration : undefined,
    external_links: track.link ? { deezer: track.link } : {},
  };
  const [_, __, tidal] = await Promise.all([
    mergeResolveFromIsrc(merged),
    resolveViaOdesli(track.link || `https://www.deezer.com/track/${deezerTrackId}`).then((mapped) => {
      if (!mapped.isrc || !track.isrc || mapped.isrc.toUpperCase() === track.isrc.toUpperCase()) mergeResolve(merged, mapped);
    }).catch(() => undefined),
    resolveTidalTrackByMetadata(env, {
      title: track.title,
      artist: track.artist?.name,
      album: track.album?.title,
      isrc: track.isrc,
    }).catch(() => null),
  ]);
  if (tidal) mergeResolve(merged, tidal);
  return merged;
}

async function resolveQobuzTrack(qobuzTrackId: string, env: Env): Promise<ResolveResult | null> {
  const meta = await getQobuzTrackMeta(qobuzTrackId).catch(() => null);
  if (!meta) return null;

  const merged: ResolveResult = {
    qobuz_id: meta.id,
    isrc: meta.isrc,
    title: meta.title,
    artist: meta.artist,
    durationSec: meta.durationSec,
    external_links: { qobuz: `https://open.qobuz.com/track/${meta.id}` },
  };
  const [_, __, tidal] = await Promise.all([
    mergeResolveFromIsrc(merged),
    resolveViaOdesli(`https://open.qobuz.com/track/${meta.id}`).then((mapped) => {
      if (!mapped.isrc || !meta.isrc || mapped.isrc.toUpperCase() === meta.isrc.toUpperCase()) mergeResolve(merged, mapped);
    }).catch(() => undefined),
    resolveTidalTrackByMetadata(env, {
      title: meta.title,
      artist: meta.artist,
      album: meta.album,
      isrc: meta.isrc,
    }).catch(() => null),
  ]);
  if (tidal) mergeResolve(merged, tidal);
  return merged;
}

async function fetchTidalApi(env: Env, path: string): Promise<unknown | null> {
  const token = env.TIDAL_API_KEY?.trim() || "CzET4vdadNUFQ5JU";
  const isJwt = token.startsWith("eyJr") || token.includes(".");
  const headers: Record<string, string> = isJwt
    ? { Authorization: `Bearer ${token}`, Accept: "application/json" }
    : { "x-tidal-token": token, Accept: "application/json" };

  const url = `https://api.tidal.com/v1/${path.replace(/^\//, "")}`;
  try {
    const res = await fetch(url, { headers, signal: AbortSignal.timeout(4000) });
    if (res.ok) return await res.json();
    if (res.status === 401 && token !== "CzET4vdadNUFQ5JU") {
      const retryRes = await fetch(url, {
        headers: { "x-tidal-token": "CzET4vdadNUFQ5JU", Accept: "application/json" },
        signal: AbortSignal.timeout(4000),
      });
      if (retryRes.ok) return await retryRes.json();
    }
  } catch {
    return null;
  }
  return null;
}

async function resolveTidalTrack(tidalTrackId: string, env: Env): Promise<ResolveResult | null> {
  const payload = await fetchTidalApi(env, `tracks/${encodeURIComponent(tidalTrackId)}?countryCode=US`);
  const track = payload as {
    id?: number;
    title?: string;
    isrc?: string;
    audioModes?: string[];
    artist?: { name?: string };
    album?: { title?: string };
    duration?: number;
  } | null;
  if (!track || !track.id) return null;

  const isAtmos = Array.isArray(track.audioModes) && track.audioModes.includes("DOLBY_ATMOS");
  const merged: ResolveResult = {
    tidal_id: String(track.id),
    tidal_atmos_id: isAtmos ? String(track.id) : undefined,
    isrc: track.isrc,
    title: track.title,
    artist: track.artist?.name,
    durationSec: typeof track.duration === "number" ? track.duration : undefined,
    external_links: { tidal: `https://listen.tidal.com/track/${track.id}` },
  };

  await mergeResolveFromIsrc(merged);

  if (!merged.tidal_atmos_id) {
    const tidalAtmos = await resolveTidalTrackByMetadata(env, {
      title: track.title,
      artist: track.artist?.name,
      album: track.album?.title,
      isrc: track.isrc,
    }).catch(() => null);
    if (tidalAtmos?.tidal_atmos_id) {
      merged.tidal_atmos_id = tidalAtmos.tidal_atmos_id;
    }
  }

  return merged;
}

/**
 * Odesli/song.link is now auth-gated (401 PUBLIC_API_ACCESS_DEPRECATED), so the
 * old cross-catalog mapping is dead. Qobuz and Deezer both expose free ISRC
 * metadata, and Tidal's own catalog search accepts a Bearer token (same accounts
 * that can play Atmos). Search Tidal by `title artist` and pick the hit whose
 * ISRC matches exactly — that guarantees the SAME recording, never a wrong song.
 */
export async function resolveTidalTrackByMetadata(
  env: Env,
  meta: { title?: string; artist?: string; album?: string; isrc?: string }
): Promise<ResolveResult | null> {
  const primaryArtist = (meta.artist ?? "").split(/[,;&]|feat\.|ft\./i)[0].trim();
  const query = [meta.title, primaryArtist || meta.artist].filter(Boolean).join(" ").trim();
  if (!query) return null;

  const payload = await fetchTidalApi(env, `search/tracks?query=${encodeURIComponent(query)}&limit=30&countryCode=US`);
  const items = (payload as { items?: Array<Record<string, unknown>> } | null)?.items ?? [];

  const norm = (value: string): string =>
    value.toLowerCase().replace(/[^a-z0-9]+/g, " ").replace(/\s+/g, " ").trim();

  const wantedTitle = norm(meta.title ?? "");
  const wantedArtist = norm(primaryArtist || meta.artist || "");

  // Look for any Dolby Atmos edition of this track on Tidal.
  // Record labels often issue Atmos masters under a distinct ISRC or track ID.
  const atmosHit = items.find((item) => {
    const modes = Array.isArray(item.audioModes) ? item.audioModes : [];
    if (!modes.includes("DOLBY_ATMOS")) return false;
    const itemTitle = norm(String(item.title ?? ""));
    const artistObj = item.artist as { name?: string } | undefined;
    const itemArtist = norm(artistObj?.name ?? "");
    const titleMatch = wantedTitle && (itemTitle === wantedTitle || itemTitle.startsWith(wantedTitle) || wantedTitle.startsWith(itemTitle));
    const artistMatch = !wantedArtist || !itemArtist || itemArtist.includes(wantedArtist) || wantedArtist.includes(itemArtist);
    return titleMatch && artistMatch;
  });
  const atmosId = atmosHit?.id != null ? String(atmosHit.id) : undefined;

  // When the source supplies an ISRC, the Tidal hit MUST carry the same ISRC.
  // A same-title/wrong-ISRC hit is a different recording and must never play.
  if (meta.isrc) {
    const wanted = meta.isrc.trim().toUpperCase();
    const exact = items.find((item) => String(item.isrc ?? "").trim().toUpperCase() === wanted);
    if (exact?.id != null) {
      const id = String(exact.id);
      if (/^\d{4,}$/.test(id)) {
        return {
          tidal_id: id,
          tidal_atmos_id: atmosId,
          isrc: meta.isrc.trim(),
          external_links: { tidal: `https://listen.tidal.com/track/${id}` },
        };
      }
    }
    if (atmosId) {
      return {
        tidal_id: atmosId,
        tidal_atmos_id: atmosId,
        isrc: meta.isrc.trim(),
        external_links: { tidal: `https://listen.tidal.com/track/${atmosId}` },
      };
    }
    return null;
  }

  // No ISRC: only accept a hit whose title matches the query title exactly
  // (normalized). Same-title/different-ISRC is a risk-free-enough nearest guess
  // for streaming; mismatched titles are a hard reject.
  if (!wantedTitle) return null;
  const exactTitle = items.find((item) => norm(String((item as { title?: string }).title ?? "")) === wantedTitle);
  const matchedId = exactTitle?.id != null ? String(exactTitle.id) : atmosId;
  if (!matchedId || !/^\d{4,}$/.test(matchedId)) return null;

  return {
    tidal_id: matchedId,
    tidal_atmos_id: atmosId,
    isrc: undefined,
    external_links: { tidal: `https://listen.tidal.com/track/${matchedId}` },
  };
}

async function mergeResolveFromIsrc(target: ResolveResult): Promise<void> {
  const isrc = target.isrc?.trim();
  if (!isrc) return;

  if (!target.deezer_id) {
    const track = await fetchJson(`https://api.deezer.com/track/isrc:${encodeURIComponent(isrc)}`) as { id?: number; isrc?: string } | null;
    if (track?.id && track.isrc?.trim().toUpperCase() === isrc.toUpperCase()) {
      target.deezer_id = String(track.id);
      target.external_links = target.external_links ?? {};
      target.external_links.deezer = `https://www.deezer.com/track/${track.id}`;
    }
  }

  if (!target.qobuz_id) {
    const qobuzId = await lookupQobuzTrackByIsrc(isrc).catch(() => null);
    if (qobuzId) {
      target.qobuz_id = qobuzId;
      target.external_links = target.external_links ?? {};
      target.external_links.qobuz = `https://open.qobuz.com/track/${qobuzId}`;
    }
  }
}

function applyOdesliLinks(result: ResolveResult, links: Record<string, { url?: string }>) {
  if (links.spotify?.url) {
    result.external_links!.spotify = links.spotify.url;
    result.spotify_id = extractId(links.spotify.url, /\/track\/([a-zA-Z0-9]+)/);
  }
  if (links.tidal?.url) {
    result.external_links!.tidal = links.tidal.url;
    result.tidal_id = extractId(links.tidal.url, /\/track\/(\d+)/);
  }
  if (links.qobuz?.url) {
    result.external_links!.qobuz = links.qobuz.url;
    result.qobuz_id = extractId(links.qobuz.url, /\/track\/(\d+)/);
  }
  if (links.deezer?.url) {
    result.external_links!.deezer = links.deezer.url;
    result.deezer_id = extractId(links.deezer.url, /\/track\/(\d+)/);
  }
  if (links.amazonMusic?.url) {
    result.external_links!.amazon = links.amazonMusic.url;
    result.amazon_id = extractAmazonId(links.amazonMusic.url);
  }
  if (links.appleMusic?.url) {
    result.external_links!.apple = links.appleMusic.url;
    result.apple_id = extractId(links.appleMusic.url, /(?:\?i=|\/id)(\d+)/);
  }
}

function mergeResolve(target: ResolveResult, incoming: ResolveResult | null | undefined) {
  if (!incoming) return;
  target.isrc = target.isrc ?? incoming.isrc;
  target.tidal_id = target.tidal_id ?? incoming.tidal_id;
  target.tidal_atmos_id = target.tidal_atmos_id ?? incoming.tidal_atmos_id;
  target.qobuz_id = target.qobuz_id ?? incoming.qobuz_id;
  target.deezer_id = target.deezer_id ?? incoming.deezer_id;
  target.amazon_id = target.amazon_id ?? incoming.amazon_id;
  target.amazon_atmos_id = target.amazon_atmos_id ?? incoming.amazon_atmos_id;
  target.spotify_id = target.spotify_id ?? incoming.spotify_id;
  target.apple_id = target.apple_id ?? incoming.apple_id;
  target.title = target.title ?? incoming.title;
  target.artist = target.artist ?? incoming.artist;
  target.durationSec = target.durationSec ?? incoming.durationSec;
  target.external_links = { ...(target.external_links ?? {}), ...(incoming.external_links ?? {}) };
}

function hasPlatformIds(result: ResolveResult): boolean {
  return Boolean(result.tidal_id || result.qobuz_id || result.deezer_id || result.amazon_id);
}

function isSpotifyUrl(url: string): boolean {
  const lower = url.toLowerCase();
  return lower.includes("spotify.com/") || lower.startsWith("spotify:");
}

function extractId(url: string, pattern: RegExp): string | undefined {
  return url.match(pattern)?.[1];
}

function extractAmazonId(url: string): string | undefined {
  return (
    url.match(/[?&]trackAsin=([A-Z0-9]+)/i)?.[1] ??
    url.match(/\/tracks\/([A-Z0-9]+)/i)?.[1]
  );
}
