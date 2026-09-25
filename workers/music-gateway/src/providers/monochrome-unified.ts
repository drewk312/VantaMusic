import type { Env, ProviderId, StreamResult } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import {
  hasDolbyAtmosSignal,
  inferBitrateKbps,
  inferContainerFromUrl,
  isHiResSignal,
  isAtmosQuality,
} from "../lib/stream-quality";

/**
 * Monochrome Unified Playback integration.
 *
 * Mirrors Monochrome's `getUnifiedPlaybackStreamUrl` / `fetchUnifiedPlaybackEnvelope`
 * (js/api.js:2574-2860, js/storage.js:3165). The frontend calls:
 *   GET {base}/api/v2/track/?track=&artist=&album=&isrc=&duration=&quality=&intent=stream
 *   Headers: Authorization: Bearer {token}, X-Turnstile-JWT: {jwt}
 *   ← { playback: [{ url, source, quality, codec, kind, delivery, sample_rate_hz, bit_depth, ... , encryption:{key_id}}], ... }
 *
 * We tie BOTH backends into VANTA:
 *  - Primary Atmos: SpotiFLAC community relays (already wired via streamViaSignedCommunity)
 *  - Secondary: Monochrome's music-api.geeked.wtf (Amazon/Tidal) — uses its servers, same as Monochrome does.
 *
 * Turnstile is required by the unified API (428 without JWT). The JWT is cached in env
 * MONOCHROME_TURNSTILE_JWT — mint it once via Monochrome's Turnstile flow (sitekey
 * 0x4AAAAAADgxqF6QVMm0GLHH) and store as a Worker secret. Without it this provider
 * returns null and the SpotiFLAC path is tried.
 */

const DEFAULT_BASE = "https://music-api.geeked.wtf";
const DEFAULT_TOKEN = "amp_29b2lIr4mze4tK-P8QDOxfMZ9anCgJ9_uGTUks3nIyo";

function monochromeEnv(env: Env): { base: string; token: string; jwt: string | null } | null {
  const base = (env.MONOCHROME_API_BASE_URL ?? DEFAULT_BASE).trim().replace(/\/+$/, "") || DEFAULT_BASE;
  const token = (env.MONOCHROME_API_TOKEN ?? DEFAULT_TOKEN).trim() || DEFAULT_TOKEN;
  const jwt = env.MONOCHROME_TURNSTILE_JWT?.trim() || null;
  if (!base || !token) return null;
  return { base, token, jwt };
}

function normalizeQuality(quality: string): string {
  const v = quality.trim().toUpperCase();
  // Monochrome's QUALITY_TOKENS: keep Atmos strict
  if (v === "ATMOS" || v === "DOLBY_ATMOS" || v.includes("ATMOS") || v === "EAC3_JOC" || v === "EAC3") {
    if (v.includes("AC4")) {
      return v.includes("LOW") ? "DOLBY_ATMOS_AC4_LOW" : "DOLBY_ATMOS_AC4_HIGH";
    }
    return v.includes("LOW") ? "DOLBY_ATMOS_EAC3_LOW" : "DOLBY_ATMOS_EAC3_HIGH";
  }
  if (v === "24" || v === "HI_RES" || v === "HI_RES_LOSSLESS") return "HI_RES_LOSSLESS";
  if (v === "16" || v === "LOSSLESS") return "LOSSLESS";
  return v;
}

function inferMonochromeQualityToken(quality: string): string {
  return normalizeQuality(quality);
}

export async function streamViaMonochromeUnified(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string,
  meta?: { title?: string; artist?: string; album?: string; isrc?: string; durationSec?: number }
): Promise<StreamResult | null> {
  if (!trackId.trim()) return null;
  // Monochrome only brokers Amazon/Tidal/Monochrome (which is internal FLAC)
  if (provider !== "tidal" && provider !== "amazon" && provider !== "deezer" && provider !== "qobuz") {
    return null;
  }

  const cfg = monochromeEnv(env);
  if (!cfg) return null;

  // Without a Turnstile JWT the unified API returns 428. Fail fast to let SpotiFLAC try.
  // We keep the request anyway if MONOCHROME_ALLOW_UNAUTH is set (for testing).
  const allowUnauth = env.MONOCHROME_ALLOW_UNAUTH === "true";
  if (!cfg.jwt && !allowUnauth) return null;

  // Need minimal track identity. If we don't have it, we can't query unified API.
  // Fall back to letting other providers handle it.
  const title = meta?.title?.trim();
  const artist = meta?.artist?.trim();
  const isrc = meta?.isrc?.trim();
  const tidalId = provider === "tidal" && /^\d+$/.test(trackId.trim()) ? trackId.trim() : null;
  if (!title && !isrc && !tidalId) return null;

  const params = new URLSearchParams();
  if (title) params.set("track", title);
  else params.set("track", trackId);
  if (artist) params.set("artist", artist);
  if (meta?.album) params.set("album", meta.album);
  if (isrc) params.set("isrc", isrc);
  if (meta?.durationSec) params.set("duration", String(Math.round(meta.durationSec)));
  params.set("quality", inferMonochromeQualityToken(quality));
  params.set("intent", "stream");

  // Monochrome also sends track id when available for Tidal
  if (tidalId) {
    params.set("tidal_id", tidalId);
  }

  const url = `${cfg.base}/api/v2/track/?${params.toString()}`;
  const headers: Record<string, string> = {
    Accept: "application/json",
    Authorization: `Bearer ${cfg.token}`,
  };
  if (cfg.jwt) headers["X-Turnstile-JWT"] = cfg.jwt;

  let res: Response;
  try {
    res = await fetch(url, { headers, signal: AbortSignal.timeout(4_000), cf: { cacheTtl: 0 } } as RequestInit);
  } catch {
    return null;
  }

  if (res.status === 428 || res.status === 401 || res.status === 403) {
    // Turnstile expired or token rotated — signal to caller without throwing
    console.log("VANTA_MONOCHROME_AUTH", JSON.stringify({ status: res.status, provider, quality }));
    return null;
  }
  if (res.status === 429) return null;
  if (!res.ok) return null;

  let envelope: Record<string, unknown>;
  try {
    envelope = (await res.json()) as Record<string, unknown>;
  } catch {
    return null;
  }

  const playback = (envelope as { playback?: unknown }).playback;
  if (!Array.isArray(playback) || playback.length === 0) return null;

  // Select first direct/dash/hls audio resource (mirrors Monochrome getUnifiedPlaybackResource)
  const resources = playback.filter(
    (r) =>
      r !== null &&
      typeof r === "object" &&
      typeof (r as Record<string, unknown>).url === "string" &&
      ((r as Record<string, unknown>).kind === "audio" || (r as Record<string, unknown>).kind === "manifest") &&
      ((r as Record<string, unknown>).delivery === "direct" ||
        (r as Record<string, unknown>).delivery === "dash" ||
        (r as Record<string, unknown>).delivery === "hls")
  ) as Record<string, unknown>[];

  for (const resource of resources) {
    const sourceUrl = typeof resource.url === "string" ? normalizePublicHttpsUrl(resource.url) : null;
    if (!sourceUrl) continue;

    // This adapter has no DRM/license contract. Never hand ciphertext to the
    // player or advertise the nonexistent /api/decrypt-stream endpoint.
    if (resource.encryption || resource.decryptionKey || resource.decryption_key || resource.drm) continue;
    const result = toMonochromeResult(sourceUrl, resource, provider);
    if (!result || (isAtmosQuality(quality) && !result.isDolbyAtmos)) continue;
    return result;
  }
  return null;
}

function toMonochromeResult(
  url: string,
  resource: Record<string, unknown>,
  provider: ProviderId
): StreamResult | null {
  const safeUrl = normalizePublicHttpsUrl(url);
  if (!safeUrl) return null;

  const deliveredQuality = typeof resource.quality === "string" ? resource.quality : "";
  const codec =
    typeof resource.codec === "string"
      ? resource.codec
      : (resource.source === "amazon" && /^(UHD|HI_RES)/i.test(String(resource.quality ?? ""))
          ? "flac"
          : undefined);
  const format =
    codec ?? inferContainerFromUrl(safeUrl) ??
      (typeof resource.container === "string" ? resource.container : undefined);
  const mimeType = resource.delivery === "dash" ? "application/dash+xml"
    : resource.delivery === "hls" ? "application/vnd.apple.mpegurl"
    : format === "m4a" || format === "mp4" ? "audio/mp4"
    : format?.includes("/") ? format : format ? `audio/${format}` : undefined;

  const bitrateKbps =
    typeof resource.bandwidth === "number"
      ? Math.round(resource.bandwidth / 1000)
      : typeof (resource as Record<string, unknown>).bitrate_kbps === "number"
        ? ((resource as Record<string, unknown>).bitrate_kbps as number)
        : inferBitrateKbps(String(deliveredQuality), format);

  const sampleRateHz =
    typeof resource.sample_rate_hz === "number"
      ? resource.sample_rate_hz
      : typeof (resource as Record<string, unknown>).sampleRateHz === "number"
        ? ((resource as Record<string, unknown>).sampleRateHz as number)
        : undefined;

  const bitDepth =
    typeof resource.bit_depth === "number"
      ? resource.bit_depth
      : typeof (resource as Record<string, unknown>).bitDepth === "number"
        ? ((resource as Record<string, unknown>).bitDepth as number)
        : undefined;

  const isAtmos = hasDolbyAtmosSignal(String(deliveredQuality), format);

  return {
    url: safeUrl,
    streamUrl: safeUrl,
    format,
    quality: String(deliveredQuality),
    mimeType,
    bitrateKbps,
    bitDepth,
    sampleRateHz,
    channelCount: typeof resource.channels === "number" ? resource.channels : undefined,
    provider,
    isDolbyAtmos: isAtmos,
    isSpatialAudio: isAtmos,
    isSurround: isAtmos,
    isHiRes: isHiResSignal(String(deliveredQuality), format, sampleRateHz ? sampleRateHz / 1000 : null, bitDepth ?? null),
  };
}
