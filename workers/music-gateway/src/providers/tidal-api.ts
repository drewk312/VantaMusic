import type { Env, StreamResult } from "../types";
import { resolveTidalAccessToken } from "./oauth-refresh";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { fetchJson, fetchText } from "./shared";

const UA = "VANTA-MusicGateway/2.0";

/**
 * Native Tidal Atmos/E-AC-3 JOC resolution via a self-hosted Tidal API
 * (hifi-api compatible). When TIDAL_API_URL is configured, Atmos streams
 * resolve through the `trackManifests` endpoint, which returns a DASH
 * manifest carrying E-AC-3 JOC (Dolby Atmos) media.
 *
 * The Android client hands the manifest URL to Media3, which plays it with
 * the surround-passthrough AudioAttributes flag already wired in.
 */

interface TidalManifestResponse {
  data?: {
    data?: {
      attributes?: {
        uri?: string;
        formats?: string[];
        trackPresentation?: string;
        drmData?: string | null;
      };
    };
  };
}

function isAtmosRequest(quality: string): boolean {
  const q = quality.trim().toLowerCase();
  return q === "atmos" || q === "dolby_atmos" || q === "eac3" || q === "eac3_joc";
}

function apiBase(env: Env): string | null {
  const raw = env.TIDAL_API_URL?.trim();
  if (!raw) return null;
  try {
    const parsed = new URL(raw);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return null;
    return raw.replace(/\/+$/, "");
  } catch {
    return null;
  }
}

export async function streamTidalNative(
  env: Env,
  trackId: string,
  quality: string,
  request?: Request
): Promise<StreamResult | null> {
  if (!trackId.trim()) return null;
  const token = await resolveTidalAccessToken(env, request);
  const base = apiBase(env);
  if (!token && !base) return null;

  if (isAtmosRequest(quality)) {
    return streamTidalAtmos(env, base, trackId, token);
  }

  const isRealTidal = !base || base.includes("api.tidal.com") || base.includes("openapi.tidal.com");

  // The classic hifi-api `/track/?id=&quality=` shape only exists on self-hosted
  // hifi instances. Real `api.tidal.com` has no such route, so only probe it for
  // custom bases to avoid burning budget on an offline-hifi 404/timeout.
  if (base && !isRealTidal) {
    const url = `${base}/track/?id=${encodeURIComponent(trackId)}&quality=${quality === "16" ? "LOSSLESS" : "HI_RES_LOSSLESS"}`;
    const payload = await fetchJson(url, { headers: tidalBearerHeaders(token) });
    const streamUrl = extractManifestUrl(payload);
    if (streamUrl) {
      return {
        url: streamUrl,
        streamUrl,
        format: "flac",
        quality: quality === "16" ? "16-bit / 44.1 kHz FLAC" : "Hi-Res FLAC",
        mimeType: "audio/flac",
        provider: "tidal",
        isDolbyAtmos: false,
        isSpatialAudio: false,
        isSurround: false,
        isHiRes: quality !== "16",
      };
    }
  }

  // Real Tidal (openapi.tidal.com): the account token that unlocks Atmos usually
  // does NOT unlock FLAC manifests (403 CLIENT_NOT_ENTITLED; LOSSLESS/HI_RES are
  // not valid format tokens either). Try FLAC anyway, then upgrade to the Atmos
  // presentation when granted — same recording, best playable quality.
  const openapi = await streamTidalFlacOpenapi(env, trackId, quality, token);
  if (openapi) {
    console.log(
      "VANTA_TIDAL_FLAC",
      JSON.stringify({ trackId, quality, format: openapi.format, isHiRes: openapi.isHiRes })
    );
    return openapi;
  }

  // Atmos is requested explicitly above. Do not substitute it for FLAC:
  // callers falling back after a decoder failure must get stereo media.
  return null;
}

/**
 * Tidal lossless/hi-res FLAC via the openapi trackManifests endpoint and the
 * configured account token (works for HiFi/HiFi Plus accounts that can resolve
 * Atmos — the same token unlocks LOSSLESS and HI_RES manifests).
 */
export async function streamTidalFlacOpenapi(
  env: Env,
  trackId: string,
  quality: string,
  accessToken?: string | null
): Promise<StreamResult | null> {
  const id = trackId.trim();
  if (!id) return null;
  const key = accessToken?.trim() || env.TIDAL_API_KEY?.trim();
  if (!key) return null;

  // openapi.tidal.com accepts `FLAC` as the lossless/hi-res format token; the
  // legacy `LOSSLESS`/`HI_RES` tokens are invalid values (400). Try FLAC and log
  // the first response so an entitlement error is visible instead of dead-ending.
  const formatList = ["FLAC"];
  for (const formats of formatList) {
    const url = `https://openapi.tidal.com/v2/trackManifests/${encodeURIComponent(id)}?formats=${formats}&adaptive=true&manifestType=MPEG_DASH&uriScheme=HTTPS&usage=PLAYBACK&countryCode=US`;
    let response: Response | null = null;
    try {
      response = await fetch(url, {
        headers: { Authorization: `Bearer ${key}`, Accept: "application/vnd.api+json" },
        signal: AbortSignal.timeout(4_000),
      });
    } catch (err) {
      console.warn("VANTA_TIDAL_FLAC", JSON.stringify({ trackId: id, formats, fetchError: err instanceof Error ? err.message : String(err) }));
      return null;
    }
    if (!response.ok) {
      const body = (await response.text().catch(() => "")).slice(0, 120);
      console.warn("VANTA_TIDAL_FLAC", JSON.stringify({ trackId: id, formats, status: response.status, body }));
      return null;
    }
    let payload: unknown;
    try {
      payload = await response.json();
    } catch {
      continue;
    }
    const stream = tidalFlacManifestToStream(payload, id, quality);
    if (stream && !(await isTidalPreviewManifest(stream.url, id))) return stream;
  }
  console.warn("VANTA_TIDAL_FLAC", JSON.stringify({ trackId: id, quality, resolved: false }));
  return null;
}

/**
 * Verify a Tidal DASH manifest is the full-length track and not a ~30s preview.
 * Tidal previews are served from im-cf.manifest.tidal.com with a
 * mediaPresentationDuration near PT30S. When the manifest cannot be fetched the
 * result is left untouched (trackPresentation already guards the common case).
 */
export async function isTidalPreviewManifest(url: string, trackId = "unknown"): Promise<boolean> {
  try {
    const parsed = new URL(url);
    if (!/\.mpd$/.test(parsed.pathname) && !parsed.hostname.includes("manifest.tidal.com")) {
      return false;
    }
  } catch {
    return false;
  }
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(2_500) });
    if (!response.ok) return false;
    const body = await response.text();
    const duration = durationFromMpd(body);
    if (duration != null) {
      const isPreview = duration <= 45;
      if (isPreview) {
        console.warn(
          "VANTA_TIDAL_PREVIEW_REJECTED",
          JSON.stringify({ trackId, url, reason: `mpd duration ${duration}s` })
        );
      }
      return isPreview;
    }
  } catch {
    return false;
  }
  return false;
}

export function durationFromMpd(body: string): number | null {
  const match = body.match(/mediaPresentationDuration="PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?"/);
  if (!match) return null;
  const hours = match[1] ? Number(match[1]) : 0;
  const minutes = match[2] ? Number(match[2]) : 0;
  const seconds = match[3] ? Number(match[3]) : 0;
  const total = hours * 3600 + minutes * 60 + seconds;
  return Number.isFinite(total) && total > 0 ? total : null;
}

export function tidalFlacManifestToStream(
  payload: unknown,
  trackId = "unknown",
  quality = "24",
  nowSeconds = Math.floor(Date.now() / 1000)
): StreamResult | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const data = record.data as
    | { attributes?: { uri?: string | null; formats?: string[]; drmData?: string | null; trackPresentation?: string | null } }
    | undefined;
  const attributes = data?.attributes;
  if (!attributes?.uri) return null;

  const trackPresentation = attributes.trackPresentation?.trim().toUpperCase();
  if (trackPresentation && /PREVIEW|SNIPPET|EXCERPT/.test(trackPresentation)) {
    console.warn(
      "VANTA_TIDAL_PREVIEW_REJECTED",
      JSON.stringify({ trackId, quality, trackPresentation, reason: "not a full-length track" })
    );
    return null;
  }

  const directUrl = normalizePublicHttpsUrl(attributes.uri);
  if (!directUrl) return null;

  const formats = (attributes.formats ?? []).map((f) => f.toUpperCase());
  const hiRes = formats.includes("HI_RES") || formats.includes("MQA");
  const protectedManifest = Boolean(attributes.drmData?.trim());

  if (formats.length && !formats.some((f) => f === "LOSSLESS" || f === "HI_RES" || f === "MQA" || f === "FLAC")) {
    console.warn("VANTA_TIDAL_FLAC", JSON.stringify({ trackId, reason: "no_lossless_format", formats }));
    return null;
  }

  return {
    url: directUrl,
    streamUrl: directUrl,
    format: "flac",
    quality: hiRes ? "24-bit / 96 kHz FLAC" : "16-bit / 44.1 kHz FLAC",
    mimeType: "application/dash+xml",
    provider: "tidal",
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: false,
    isHiRes: hiRes,
    expiresAt: protectedManifest ? nowSeconds + 8 * 60 : undefined,
    drm: protectedManifest
      ? {
          scheme: "widevine",
          licenseProxy: "tidal",
          forceDefaultLicenseUri: true,
        }
      : undefined,
  };
}

async function streamTidalAtmos(
  env: Env,
  base: string | null,
  trackId: string,
  accessToken?: string | null
): Promise<StreamResult | null> {
  const token = accessToken?.trim() || env.TIDAL_API_KEY?.trim() || null;
  const headers = tidalBearerHeaders(token);
  // Try hifi-api style first (most self-hosted)
  if (base) {
    const hifiUrl = `${base}/trackManifests/?id=${encodeURIComponent(trackId)}&formats=EAC3_JOC&adaptive=true&manifestType=MPEG_DASH&uriScheme=HTTPS&usage=PLAYBACK`;
    const payload = await fetchJson(hifiUrl, { headers });
    const stream = tidalManifestToStream(payload, trackId);
    if (stream) return stream;
  }

  if (token) {
    const openApiUrl = `https://openapi.tidal.com/v2/trackManifests/${encodeURIComponent(trackId)}?formats=EAC3_JOC&adaptive=true&manifestType=MPEG_DASH&uriScheme=HTTPS&usage=PLAYBACK&countryCode=US`;
    const payload = await fetchJson(openApiUrl, { headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.api+json" } });
    // openapi returns {data:{attributes:{uri,formats}}} not {data:{data:{attributes}}}
    const openPayload = payload as { data?: { attributes?: { uri?: string; formats?: string[]; drmData?: string | null } } };
    if (openPayload?.data?.attributes) {
      const stream = tidalManifestToStream({ data: { data: { attributes: openPayload.data.attributes } } }, trackId);
      if (stream) return stream;
    }
  }
  return null;
}

export function tidalManifestToStream(
  payload: unknown,
  trackId = "unknown",
  nowSeconds = Math.floor(Date.now() / 1000)
): StreamResult | null {
  if (!payload || typeof payload !== "object") return null;
  const manifest = payload as TidalManifestResponse;
  const attributes = manifest.data?.data?.attributes;
  if (!attributes) return null;

  const trackPresentation = attributes.trackPresentation?.trim().toUpperCase();
  if (trackPresentation && /PREVIEW|SNIPPET|EXCERPT/.test(trackPresentation)) {
    console.warn(
      "VANTA_TIDAL_PREVIEW_REJECTED",
      JSON.stringify({ trackId, trackPresentation, reason: "not a full-length track" })
    );
    return null;
  }

  const formats = attributes.formats ?? [];
  if (!formats.some((f) => f.toUpperCase() === "EAC3_JOC")) {
    console.warn("VANTA_TIDAL_ATMOS", JSON.stringify({ trackId, reason: "eac3_joc_unavailable", formats }));
    return null;
  }

  // Media3 needs a fetchable manifest URL. Inline data URIs are intentionally
  // rejected because they cannot safely pass through the public URL boundary.
  const directUrl = normalizePublicHttpsUrl(attributes.uri ?? "");
  if (!directUrl) return null;
  const protectedManifest = Boolean(attributes.drmData?.trim());
  return {
    url: directUrl,
    streamUrl: directUrl,
    format: "eac3-joc",
    quality: "Dolby Atmos",
    mimeType: "application/dash+xml",
    provider: "tidal",
    isDolbyAtmos: true,
    isSpatialAudio: true,
    isSurround: true,
    isHiRes: false,
    expiresAt: protectedManifest ? nowSeconds + 8 * 60 : undefined,
    drm: protectedManifest
      ? {
          scheme: "widevine",
          licenseProxy: "tidal",
          forceDefaultLicenseUri: true,
        }
      : undefined,
  };
}

function extractManifestUrl(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const data = record.data as Record<string, unknown> | undefined;
  const manifest = (data?.manifest as string) ?? "";
  if (!manifest) return null;

  const url = normalizePublicHttpsUrl(manifest);
  if (url) return url;

  // base64 JSON "BTS" manifest: { urls: [...] }
  try {
    const decoded = atob(manifest);
    const parsed = JSON.parse(decoded) as { urls?: string[] };
    const first = parsed.urls?.[0];
    return first ? normalizePublicHttpsUrl(first) : null;
  } catch {
    return null;
  }
}

/** Probe whether the native Tidal API is reachable (used by /status). */
export async function tidalNativeHealth(env: Env): Promise<{ reachable: boolean; atmos: boolean }> {
  const base = apiBase(env);
  if (!base) return { reachable: false, atmos: false };
  const root = await fetchText(`${base}/`, { headers: tidalApiHeaders(env) });
  const reachable = root != null;
  return { reachable, atmos: reachable };
}

function tidalApiHeaders(env: Env): Record<string, string> {
  return tidalBearerHeaders(env.TIDAL_API_KEY?.trim() || null);
}

function tidalBearerHeaders(token: string | null): Record<string, string> {
  if (!token) return {};
  return {
    Authorization: `Bearer ${token}`,
    "X-Api-Key": token,
  };
}

/**
 * Music-video companion for TV: search Tidal videos, then resolve an HLS/DASH
 * playback URL. Picture only — Android merges this with FLAC/Atmos audio.
 */
export async function resolveTidalMusicVideo(
  env: Env,
  query: string,
  request?: Request
): Promise<StreamResult | null> {
  const q = query.trim();
  if (!q) return null;
  const token = (await resolveTidalAccessToken(env, request))?.trim() || env.TIDAL_API_KEY?.trim() || null;
  if (!token) {
    console.warn("VANTA_TIDAL_VIDEO", JSON.stringify({ query: q, reason: "no_token" }));
    return null;
  }

  const videoId = await searchTidalVideoId(q, token);
  if (!videoId) {
    console.warn("VANTA_TIDAL_VIDEO", JSON.stringify({ query: q, reason: "no_search_hit" }));
    return null;
  }

  const stream = await streamTidalVideoById(videoId, token);
  if (stream) {
    console.log("VANTA_TIDAL_VIDEO", JSON.stringify({ query: q, videoId, url: stream.url.slice(0, 80) }));
  }
  return stream;
}

async function searchTidalVideoId(query: string, token: string): Promise<string | null> {
  const encoded = encodeURIComponent(query);
  // Legacy catalog search (broad video inventory).
  const legacyUrl =
    `https://api.tidal.com/v1/search/?query=${encoded}&types=VIDEOS&limit=5&offset=0&countryCode=US`;
  try {
    const response = await fetch(legacyUrl, {
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Tidal-Token": token,
        Accept: "application/json",
        "User-Agent": UA,
      },
      signal: AbortSignal.timeout(4_000),
    });
    if (response.ok) {
      const payload = (await response.json()) as {
        videos?: { items?: Array<{ id?: number | string; title?: string }> };
      };
      const first = payload.videos?.items?.find((item) => item.id != null);
      if (first?.id != null) return String(first.id);
    }
  } catch (err) {
    console.warn(
      "VANTA_TIDAL_VIDEO",
      JSON.stringify({ step: "legacy_search", error: err instanceof Error ? err.message : String(err) })
    );
  }

  // OpenAPI searchResults → videos relationship.
  const openUrl =
    `https://openapi.tidal.com/v2/searchResults/${encoded}/relationships/videos?countryCode=US&include=videos`;
  try {
    const response = await fetch(openUrl, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.api+json" },
      signal: AbortSignal.timeout(4_000),
    });
    if (!response.ok) return null;
    const payload = (await response.json()) as {
      data?: Array<{ id?: string }> | { id?: string };
      included?: Array<{ id?: string; type?: string }>;
    };
    if (Array.isArray(payload.data) && payload.data[0]?.id) return payload.data[0].id;
    const included = payload.included?.find((row) => row.type === "videos" && row.id);
    return included?.id ?? null;
  } catch {
    return null;
  }
}

async function streamTidalVideoById(videoId: string, token: string): Promise<StreamResult | null> {
  // Prefer openapi videoManifests.
  const openUrl =
    `https://openapi.tidal.com/v2/videoManifests/${encodeURIComponent(videoId)}?uriScheme=HTTPS&usage=PLAYBACK&countryCode=US`;
  try {
    const response = await fetch(openUrl, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.api+json" },
      signal: AbortSignal.timeout(5_000),
    });
    if (response.ok) {
      const payload = (await response.json()) as {
        data?: { attributes?: { uri?: string | null; formats?: string[] } };
      };
      const uri = payload.data?.attributes?.uri?.trim();
      const direct = uri ? normalizePublicHttpsUrl(uri) : null;
      if (direct) {
        const mime = direct.includes(".mpd")
          ? "application/dash+xml"
          : direct.includes(".m3u8")
            ? "application/x-mpegURL"
            : "video/mp4";
        return {
          url: direct,
          streamUrl: direct,
          format: "video",
          quality: "Tidal Music Video",
          mimeType: mime,
          provider: "tidal",
          isDolbyAtmos: false,
          isSpatialAudio: false,
          isSurround: false,
          isHiRes: false,
        };
      }
    }
  } catch (err) {
    console.warn(
      "VANTA_TIDAL_VIDEO",
      JSON.stringify({ step: "openapi_manifest", videoId, error: err instanceof Error ? err.message : String(err) })
    );
  }

  // Legacy playbackinfopostpaywall → base64 emu/bts manifest with HLS URL.
  const legacyUrl =
    `https://api.tidal.com/v1/videos/${encodeURIComponent(videoId)}/playbackinfopostpaywall` +
    `?countryCode=US&videoquality=HIGH&playbackmode=STREAM&assetpresentation=FULL`;
  try {
    const response = await fetch(legacyUrl, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
        "User-Agent": UA,
      },
      signal: AbortSignal.timeout(5_000),
    });
    if (!response.ok) return null;
    const payload = (await response.json()) as {
      manifest?: string;
      manifestMimeType?: string;
    };
    const encoded = payload.manifest?.trim();
    if (!encoded) return null;
    let decoded: string;
    try {
      decoded = atob(encoded);
    } catch {
      return null;
    }
    let hlsUrl: string | null = null;
    try {
      const parsed = JSON.parse(decoded) as { urls?: string[]; mimeType?: string };
      hlsUrl = parsed.urls?.find((u) => typeof u === "string" && u.startsWith("http")) ?? null;
    } catch {
      // Some manifests are raw M3U8 text.
      if (decoded.includes("#EXTM3U")) {
        // Not a URL — cannot play without hosting; skip.
        return null;
      }
    }
    const direct = hlsUrl ? normalizePublicHttpsUrl(hlsUrl) : null;
    if (!direct) return null;
    return {
      url: direct,
      streamUrl: direct,
      format: "video",
      quality: "Tidal Music Video",
      mimeType: "application/x-mpegURL",
      provider: "tidal",
      isDolbyAtmos: false,
      isSpatialAudio: false,
      isSurround: false,
      isHiRes: false,
    };
  } catch {
    return null;
  }
}
