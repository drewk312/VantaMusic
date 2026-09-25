import type { Env, GatewayTrack } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";

/**
 * Apple Music provider — ties in Apple's servers for search/metadata + Atmos detection.
 *
 * Uses public mint https://am-mint.binimum.org/token (no auth, no Turnstile) to get a
 * dev token, then calls https://api.music.apple.com/v1/catalog/{storefront}/search
 * (same as Monochrome's js/apple-music-api.js). This gives us:
 *  - Catalog search enrichment (like Monochrome does for ranking)
 *  - audioTraits: ["atmos","lossless","hi-res-lossless"] for Atmos badge detection
 *  - extendedAssetUrls.enhancedHls: HLS master with ec-3 Atmos rendition (needs FairPlay for playback)
 *  - editorialVideo.motionDetailSquare: animated square video covers (HLS)
 *
 * Playback of full Atmos HLS requires an Apple Music subscription (FairPlay) server-side,
 * so we use this provider for SEARCH enrichment + Atmos signal, not for direct streaming.
 * The actual Atmos stream comes from SpotiFLAC/Monochrome (which hold the paid sessions).
 */

const TOKEN_URL = "https://am-mint.binimum.org/token";
const APPLE_BASE = "https://api.music.apple.com";
const AMP_BASE = "https://amp-api.music.apple.com";

interface MintResponse {
  dev_token?: string;
  storefront_id?: string;
  token?: string;
  storefront?: string;
}

let cachedToken: { token: string; storefront: string; expiresAt: number } | null = null;

export function normalizeAppleStorefront(raw: string | null | undefined): string | null {
  const s = raw?.trim().toLowerCase() ?? "";
  return /^[a-z]{2}$/.test(s) ? s : null;
}

export async function getAppleDevToken(): Promise<{ token: string; storefront: string } | null> {
  const now = Date.now();
  if (cachedToken && cachedToken.expiresAt > now + 60_000) {
    return { token: cachedToken.token, storefront: cachedToken.storefront };
  }

  try {
    const res = await fetch(TOKEN_URL, {
      headers: { Accept: "application/json" },
      cf: { cacheTtl: 3600 } as never,
    });
    if (!res.ok) return null;
    const data = (await res.json()) as MintResponse;
    const token = data.dev_token ?? data.token;
    const storefront = normalizeAppleStorefront(data.storefront_id ?? data.storefront) ?? "us";
    if (!token) return null;

    // Decode JWT exp if possible
    let expiresAt = now + 3600_000;
    try {
      const payload = token.split(".")[1];
      const json = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
      if (json.exp) expiresAt = json.exp * 1000;
    } catch {
      // fallback
    }

    cachedToken = { token, storefront, expiresAt };
    return { token, storefront };
  } catch {
    return null;
  }
}

function buildAppleHeaders(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    Origin: "https://monochrome.tf",
    Accept: "application/json",
  };
}

/**
 * Search Apple Music catalog and return GatewayTracks.
 * Used to enrich VANTA search with Apple's ranking + Atmos traits.
 */
export async function searchAppleMusic(query: string, limit = 10): Promise<GatewayTrack[]> {
  const mint = await getAppleDevToken();
  if (!mint) return [];

  const params = new URLSearchParams({
    term: query,
    types: "songs,albums,artists",
    limit: String(Math.min(limit, 10)),
    "extend[albums]": "editorialVideo",
    l: "en-US",
    with: "lyricHighlights",
  });

  const url = `${APPLE_BASE}/v1/catalog/${mint.storefront}/search?${params}`;
  let res: Response;
  try {
    res = await fetch(url, { headers: buildAppleHeaders(mint.token), cf: { cacheTtl: 300 } as never });
  } catch {
    return [];
  }
  if (!res.ok) return [];
  let data: Record<string, unknown>;
  try {
    data = (await res.json()) as Record<string, unknown>;
  } catch {
    return [];
  }

  const results = (data as { results?: Record<string, { data?: unknown[] }> }).results;
  if (!results) return [];

  const tracks: GatewayTrack[] = [];
  const songs = results.songs?.data ?? [];
  for (const item of songs as Record<string, unknown>[]) {
    const track = appleSongToGatewayTrack(item, mint.storefront);
    if (track) tracks.push(track);
  }

  // Also add albums as gateway tracks for browse
  const albums = results.albums?.data ?? [];
  for (const item of albums.slice(0, 3) as Record<string, unknown>[]) {
    const albumTracks = appleAlbumToGatewayTracks(item, mint.storefront);
    tracks.push(...albumTracks);
  }

  return tracks;
}

export function appleSongToGatewayTrack(item: Record<string, unknown>, _storefront: string): GatewayTrack | null {
  const attrs = item.attributes as Record<string, unknown> | undefined;
  if (!attrs) return null;
  const id = String(item.id ?? "");
  const title = String(attrs.name ?? attrs.title ?? "");
  const artist = String((attrs.artistName as string) ?? (attrs.artist as string) ?? "");
  if (!title || !artist || !id) return null;

  const audioTraits = (attrs.audioTraits as string[] | undefined) ?? [];
  const hasAtmos = audioTraits.some((t) => t.toLowerCase().includes("atmos"));
  const isHiRes = audioTraits.some((t) => t.toLowerCase().includes("hi-res"));
  const artwork = attrs.artwork as Record<string, string> | undefined;
  const artworkURL = artwork?.url ? artwork.url.replace("{w}", "500").replace("{h}", "500").replace("{f}", "jpg") : undefined;
  const isrc = (attrs.isrc as string | undefined) ?? undefined;
  const durationMs = typeof attrs.durationInMillis === "number" ? attrs.durationInMillis : undefined;

  const editorialVideo = (attrs.editorialVideo as Record<string, unknown> | undefined)?.motionDetailSquare as
    | Record<string, unknown>
    | undefined;
  const videoUrl = typeof editorialVideo?.video === "string" ? (editorialVideo.video as string) : undefined;

  return {
    id: `apple:${id}`,
    title,
    artist,
    album: typeof attrs.albumName === "string" ? attrs.albumName : undefined,
    artworkURL: videoUrl ? normalizePublicHttpsUrl(videoUrl) ?? artworkURL : artworkURL,
    duration: durationMs ? Math.round(durationMs / 1000) : undefined,
    isrc,
    provider: "apple",
    isDolbyAtmos: hasAtmos,
    isSpatialAudio: hasAtmos,
    isSurround: hasAtmos,
    isHiRes,
    // Use audioTraits as evidence — verified only if we checked extendedAssetUrls
    spatialEvidence: hasAtmos ? "metadata" : "stereo",
    atmosMixAvailable: hasAtmos,
    format: hasAtmos ? "atmos" : isHiRes ? "hi-res-lossless" : "aac",
    apple_id: id,
  };
}

function appleAlbumToGatewayTracks(item: Record<string, unknown>, storefront: string): GatewayTrack[] {
  const attrs = item.attributes as Record<string, unknown> | undefined;
  const rels = item.relationships as Record<string, unknown> | undefined;
  const tracksRel = rels?.tracks as Record<string, unknown> | undefined;
  const tracksData = (tracksRel?.data as unknown[]) ?? [];
  if (tracksData.length === 0) {
    // Return album as a track-like entry for browse
    const id = String(item.id ?? "");
    const title = String(attrs?.name ?? "");
    const artist = String((attrs?.artistName as string) ?? "");
    if (!title || !artist) return [];
    return [
      {
        id: `apple:album:${id}`,
        title,
        artist,
        album: title,
        artworkURL: artworkUrlFromAttrs(attrs),
        provider: "apple",
        apple_id: id,
      },
    ];
  }
  return tracksData
    .map((t) => appleSongToGatewayTrack(t as Record<string, unknown>, storefront))
    .filter(Boolean) as GatewayTrack[];
}

function artworkUrlFromAttrs(attrs?: Record<string, unknown>): string | undefined {
  const art = attrs?.artwork as Record<string, string> | undefined;
  if (!art?.url) return undefined;
  return art.url.replace("{w}", "500").replace("{h}", "500").replace("{f}", "jpg");
}

/**
 * Fetch extendedAssetUrls for a track to get HLS with Atmos rendition.
 * Returns the HLS URL if available, otherwise null.
 */
export async function fetchAppleHls(appleId: string): Promise<string | null> {
  const mint = await getAppleDevToken();
  if (!mint) return null;
  const id = appleId.replace(/^apple:/, "").replace(/^album:/, "");
  const url = `${AMP_BASE}/v1/catalog/${mint.storefront}/songs/${encodeURIComponent(id)}?extend=extendedAssetUrls`;
  try {
    const res = await fetch(url, { headers: buildAppleHeaders(mint.token), cf: { cacheTtl: 3600 } as never });
    if (!res.ok) return null;
    const data = (await res.json()) as Record<string, unknown>;
    const items = (data as { data?: unknown[] }).data;
    const attrs = (items?.[0] as Record<string, unknown> | undefined)?.attributes as Record<string, unknown> | undefined;
    const urls = attrs?.extendedAssetUrls as Record<string, string> | undefined;
    const hls = urls?.enhancedHls ?? urls?.hlsUrl;
    return hls ? normalizePublicHttpsUrl(hls) : null;
  } catch {
    return null;
  }
}
