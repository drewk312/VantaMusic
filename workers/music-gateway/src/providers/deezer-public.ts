import type { Env, ProviderId, StreamResult } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { inferBitrateKbps, inferContainerFromUrl } from "../lib/stream-quality";
import { fetchJson } from "./shared";

/**
 * Deezer public FLAC gateway.
 *
 * Mirrors Monochrome's `getDeezerStreamUrl` (js/api.js:1886) which uses
 *   GET {base}/stream/?isrc={isrc}&format={FLAC|MP3_320|MP3_128}
 * with a HEAD probe. Monochrome's default is https://dzr.tabs-vs-spaces.wtf.
 *
 * Deezer does NOT have Atmos — this provider is strictly for CD/Hi-Res FLAC
 * fallback when Tidal/Amazon Atmos is unavailable. We keep it outside the Atmos
 * strict path so `isAtmosQuality → null` doesn't block it.
 */

const DEFAULT_DZR_BASE = "https://dzr.tabs-vs-spaces.wtf";

function dzrBases(env: Env): string[] {
  const configured = env.DEEZER_PUBLIC_BASE_URL?.trim().replace(/\/+$/, "");
  if (configured) return [configured, DEFAULT_DZR_BASE];
  return [DEFAULT_DZR_BASE];
}

function deezerFormat(quality: string): string {
  const v = quality.trim().toLowerCase();
  if (v === "16" || v === "lossless" || v === "cd") return "FLAC";
  if (v === "hi_res" || v === "24" || v === "hi_res_lossless") return "FLAC";
  // Monochrome maps everything else to FLAC when possible
  return "FLAC";
}

function toDeezerResult(url: string, quality: string, provider: ProviderId): StreamResult {
  const format = inferContainerFromUrl(url) ?? "flac";
  const bitrateKbps = inferBitrateKbps(quality, format);
  return {
    url,
    streamUrl: url,
    format,
    quality,
    mimeType: format.includes("/") ? format : `audio/${format}`,
    bitrateKbps,
    provider,
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: false,
    isHiRes: quality === "24" || quality.toLowerCase().includes("hi_res"),
  };
}

export async function streamViaDeezerPublic(
  env: Env,
  isrc: string,
  quality: string
): Promise<StreamResult | null> {
  const trimmedIsrc = isrc.trim().toUpperCase();
  if (!isDeezerIsrc(trimmedIsrc)) return null;

  const format = deezerFormat(quality);
  const bases = dzrBases(env);

  for (const base of bases) {
    const url = `${base}/stream/?isrc=${encodeURIComponent(trimmedIsrc)}&format=${encodeURIComponent(format)}`;
    // Probe with HEAD like Monochrome does — 200/405/501 all indicate the stream exists
    try {
      const head = await fetch(url, { method: "HEAD", cf: { cacheTtl: 0 } } as RequestInit);
      if (head.ok || head.status === 405 || head.status === 501) {
        const safe = normalizePublicHttpsUrl(url);
        if (safe) return toDeezerResult(safe, quality, "deezer");
      }
      // Some mirrors disable HEAD — try GET with Range
      if (head.status === 403 || head.status === 404) {
        const probe = await fetch(url, {
          method: "GET",
          headers: { Range: "bytes=0-0" },
          cf: { cacheTtl: 0 },
        } as RequestInit);
        if (probe.ok || probe.status === 206) {
          const safe = normalizePublicHttpsUrl(url);
          if (safe) return toDeezerResult(safe, quality, "deezer");
        }
      }
    } catch {
      // try next base
    }
  }
  return null;
}

export function isDeezerIsrc(value: string | undefined): boolean {
  return Boolean(value && /^[A-Z]{2}[A-Z0-9]{3}\d{7}$/i.test(value.trim()));
}

export interface DeezerTrackMeta {
  isrc?: string;
  title?: string;
  artist?: string;
  duration?: number;
}

export async function lookupDeezerTrack(trackId: string): Promise<DeezerTrackMeta | null> {
  if (!/^\d{4,12}$/.test(trackId.trim())) return null;
  const payload = await fetchJson(`https://api.deezer.com/track/${encodeURIComponent(trackId.trim())}`);
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const artist = record.artist && typeof record.artist === "object"
    ? (record.artist as { name?: unknown }).name
    : undefined;
  const isrc = typeof record.isrc === "string" ? record.isrc.trim() : undefined;
  return {
    isrc: isDeezerIsrc(isrc) ? isrc!.toUpperCase() : undefined,
    title: typeof record.title === "string" ? record.title : undefined,
    artist: typeof artist === "string" ? artist : undefined,
    duration: typeof record.duration === "number" ? record.duration : undefined,
  };
}

/** Resolve a real ISRC, then the public Deezer FLAC mirror. Numeric IDs are not ISRCs. */
export async function streamViaDeezerByTrackId(
  env: Env,
  trackId: string,
  isrc: string | undefined,
  quality: string
): Promise<StreamResult | null> {
  const known = isDeezerIsrc(isrc) ? isrc!.trim().toUpperCase() : undefined;
  const resolved = known ?? (await lookupDeezerTrack(trackId))?.isrc;
  if (!resolved) return null;
  return streamViaDeezerPublic(env, resolved, quality);
}
