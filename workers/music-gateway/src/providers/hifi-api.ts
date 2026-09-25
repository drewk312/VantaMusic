import {
  hasDolbyAtmosSignal,
  hasSpatialAudioSignal,
  hasSurroundSignal,
  inferBitrateKbps,
  inferContainerFromUrl,
  isHiResSignal,
} from "../lib/stream-quality";
import { tidalManifestToStream } from "./tidal-api";
import type { Env, GatewayTrack, ProviderId, StreamResult } from "../types";
import { fetchJson, fetchText } from "./shared";

const UA = "VANTA-MusicGateway/2.0";

/**
 * Self-hosted SpotiFLAC-style Tidal "hifi-api" instance.
 *
 * This mirrors exactly how the classic SpotiFLAC 7.2.2 binary resolves FLAC
 * from a user-supplied Tidal instance (binimum/hifi-api and its
 * sachinsenal0x64/hifi predecessor):
 *
 *   GET {base}/api/search?artist_name=Adele&track_name=Hello   -> track id
 *   GET {base}/api/download-music?track_id={id}&quality=27     -> FLAC stream URL
 *   GET {base}/track/?id={id}&quality=HI_RES_LOSSLESS          -> DASH manifest
 *
 * Quality is Tidal's numeric tier: 9 = MP3/AAC, 16 = lossless FLAC
 * 16-bit/44.1 kHz, 27 = hi-res FLAC (up to 24-bit/192 kHz). A self-hosted
 * instance typically only exposes 9/16/27.
 */

const QUALITY_TO_FMT: Record<string, string> = {
  "16": "16",
  "24": "27",
  hi_res: "27",
  hi_res_lossless: "27",
  lossless: "16",
  high: "9",
  low: "9",
};

function instanceBase(env: Env): string | null {
  const raw = env.HIFI_API_URL?.trim();
  if (!raw) return null;
  try {
    const parsed = new URL(raw);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return null;
    return raw.replace(/\/+$/, "");
  } catch {
    return null;
  }
}

function instanceHeaders(env: Env): Record<string, string> {
  const key = env.HIFI_API_KEY?.trim();
  if (!key) return {};
  return {
    Authorization: `Bearer ${key}`,
    "X-Api-Key": key,
  };
}

function fmtForQuality(quality: string): string {
  const normalized = quality.trim().toLowerCase();
  return QUALITY_TO_FMT[normalized] ?? (normalized === "16" ? "16" : "27");
}

export function mapQualityToTidalFmt(quality: string): string {
  return fmtForQuality(quality);
}

function isHiResQuality(quality: string): boolean {
  const fmt = fmtForQuality(quality);
  return fmt === "27";
}

function isAtmosQuality(quality: string): boolean {
  const q = quality.trim().toLowerCase();
  return q === "atmos" || q === "dolby_atmos" || q === "eac3" || q === "eac3_joc";
}

export function parseStreamResponse(payload: unknown, provider: ProviderId, quality: string): StreamResult | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const url = firstString(record, [
    "url",
    "streamUrl",
    "stream_url",
    "downloadUrl",
    "download_url",
    "download_url_flac",
    "location",
    "link",
  ]);
  if (!url) return null;

  const safeUrl = safeHttps(url);
  if (!safeUrl) return null;

  const format = firstString(record, ["format", "codec", "mimeType"]) ?? inferContainerFromUrl(safeUrl) ?? "flac";
  const qualityLabel =
    firstString(record, ["quality", "qualityLabel", "audioQuality"]) ??
    (isHiResQuality(quality) ? "Hi-Res FLAC" : "16-bit / 44.1 kHz FLAC");
  const bitrate = firstNumber(record, ["bitrateKbps", "bitrate_kbps", "bitrate", "bit_rate"]) ?? inferBitrateKbps(qualityLabel, format);
  const atmos = hasDolbyAtmosSignal(qualityLabel, format);
  const spatial = hasSpatialAudioSignal(qualityLabel, format) || atmos;
  const surround = hasSurroundSignal(qualityLabel, format) || atmos;

  return {
    url: safeUrl,
    streamUrl: safeUrl,
    format,
    quality: qualityLabel,
    mimeType: format.includes("/") ? format : `audio/${format}`,
    bitrateKbps: bitrate,
    provider,
    isDolbyAtmos: atmos,
    isSpatialAudio: spatial,
    isSurround: surround,
    isHiRes: isHiResSignal(qualityLabel, format),
  };
}

function safeHttps(url: string): string | null {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== "https:") return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

function firstString(record: Record<string, unknown>, keys: string[]): string | null {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return null;
}

function firstNumber(record: Record<string, unknown>, keys: string[]): number | null {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && /^\d+(\.\d+)?$/.test(value)) return Number(value);
  }
  return null;
}

/** Extract the Tidal track id from a search response. */
function extractTrackIdFromSearch(payload: unknown): string | null {  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const candidates: Array<{ root?: unknown; key: string }> = [
    { root: record, key: "id" },
    { root: record, key: "track_id" },
    { root: record, key: "trackId" },
    { root: (record.data as Record<string, unknown> | undefined) ?? {}, key: "id" },
    { root: (record.data as Record<string, unknown> | undefined) ?? {}, key: "track_id" },
  ];
  for (const { root, key } of candidates) {
    const value = (root as Record<string, unknown>)[key];
    if (value != null) return String(value);
  }
  for (const value of Object.values(record)) {
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item && typeof item === "object") {
          const id = (item as Record<string, unknown>).id;
          if (id != null) return String(id);
        }
      }
    }
  }
  return null;
}

/**
 * Resolve a Tidal stream from a self-hosted hifi-api instance.
 *
 * Try the modern `/track/?id=&quality=` DASH-manifest shape first, then the
 * classic `/api/download-music?track_id=&quality=` download shape (the one the
 * SpotiFLAC binary uses), then `/api/dl` as a last resort.
 */
export async function streamTidalHifiApi(
  env: Env,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  if (!trackId.trim()) return null;
  const base = instanceBase(env);
  if (!base) return null;

  const headers = instanceHeaders(env);
  const id = encodeURIComponent(trackId.trim());
  const fmt = fmtForQuality(quality);

  // Verified Dolby Atmos (E-AC-3 JOC) via trackManifests when Atmos is
  // requested and the self-hosted instance has a Tidal HiFi Plus session.
  if (isAtmosQuality(quality)) {
    const atmosUrl = `${base}/trackManifests/?id=${id}&formats=EAC3_JOC&adaptive=true&manifestType=MPEG_DASH&uriScheme=HTTPS&usage=PLAYBACK`;
    const atmosPayload = await fetchJson(atmosUrl, { headers });
    const atmos = tidalManifestToStream(atmosPayload, trackId.trim());
    if (atmos) {
      console.log(
        "VANTA_HIFI_API_ATMOS",
        JSON.stringify({ trackId: trackId.trim(), path: "trackManifests", format: atmos.format })
      );
      return atmos;
    }
    // If no verified Atmos is available, the caller's acceptStreamForRequestedQuality
    // will reject any FLAC result for an Atmos request, so return null here.
    return null;
  }

  // 1. Modern hifi-api trackManifests/track shape.
  const trackUrl = `${base}/track/?id=${id}&quality=${fmt === "27" ? "HI_RES_LOSSLESS" : "LOSSLESS"}`;
  const trackPayload = await fetchJson(trackUrl, { headers });
  const trackResult = parseStreamResponse(trackPayload, "tidal", quality);
  if (trackResult) return trackResult;

  // 2. Classic SpotiFLAC download-music shape.
  for (const path of [
    `/api/download-music?track_id=${id}&quality=${fmt}`,
    `/api/download?track_id=${id}&quality=${fmt}`,
    `/api/dl?track_id=${id}&quality=${fmt}`,
  ]) {
    const payload = await fetchJson(`${base}${path}`, { headers });
    const result = parseStreamResponse(payload, "tidal", quality);
    if (result) {
      console.log(
        "VANTA_HIFI_API_RESOLVE",
        JSON.stringify({ trackId: trackId.trim(), path, quality: fmt, format: result.format, isHiRes: result.isHiRes })
      );
      return result;
    }
  }

  // 3. JSON-post to /api/dl (encrypted-community style fallback).
  const dlPayload = await fetchJson(`${base}/api/dl`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ id: trackId.trim(), track_id: trackId.trim(), quality: fmt, service: "tidal", provider: "tidal" }),
  });
  const dlResult = parseStreamResponse(dlPayload, "tidal", quality);
  if (dlResult) return dlResult;

  return null;
}

/** Search a self-hosted hifi-api instance by artist + title. */
export async function searchTidalHifiApi(
  query: string,
  env: Env,
  limit = 25
): Promise<GatewayTrack[]> {
  const base = instanceBase(env);
  if (!base) return [];

  const headers = instanceHeaders(env);
  const parts = query.split(/\s+-\s+|\s+by\s+|\s+--\s+/i);
  const trackName = (parts[0] ?? query).trim();
  const artistName = (parts[1] ?? "").trim();

  const searches = [`${base}/api/search?track_name=${encodeURIComponent(trackName)}&limit=${limit}`];
  if (artistName) {
    searches.push(`${base}/api/search?artist_name=${encodeURIComponent(artistName)}&track_name=${encodeURIComponent(trackName)}&limit=${limit}`);
    searches.push(`${base}/api/search?q=${encodeURIComponent(query)}&limit=${limit}`);
  }

  for (const endpoint of searches) {
    const payload = await fetchJson(endpoint, { headers });
    const items = searchItems(payload);
    if (items.length > 0) {
      return items.slice(0, limit).map((track) => mapHifiApiTrack(track));
    }
  }
  return [];
}

function searchItems(payload: unknown): Array<Record<string, unknown>> {
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  const source =
    (record.data as Record<string, unknown> | undefined)?.["tracks"] ??
    record["tracks"] ??
    record["items"] ??
    record["results"] ??
    record["data"];
  if (Array.isArray(source)) return source.filter((item): item is Record<string, unknown> => !!item && typeof item === "object");
  if (source && typeof source === "object") {
    const nested = (source as Record<string, unknown>).items;
    if (Array.isArray(nested)) return nested.filter((item): item is Record<string, unknown> => !!item && typeof item === "object");
  }
  return [];
}

function mapHifiApiTrack(item: Record<string, unknown>): GatewayTrack {
  const id = firstString(item, ["id", "track_id", "trackId"]) ?? "";
  const title = firstString(item, ["title", "name", "track_name"]) ?? "Unknown";
  const artist = firstString(item, ["artist_name", "artist"]) ?? "Unknown Artist";
  const album = firstString(item, ["album_name", "album"]) ?? undefined;

  let artworkURL: string | undefined;
  const albumObj = item.album;
  if (albumObj && typeof albumObj === "object") {
    const image = (albumObj as Record<string, unknown>).image;
    if (typeof image === "string" && /^https:\/\//.test(image)) artworkURL = image;
    else {
      const imageObj = image as Record<string, unknown> | undefined;
      const cover = imageObj?.large ?? imageObj?.medium ?? imageObj?.["original"];
      if (typeof cover === "string") artworkURL = cover;
    }
  }
  if (!artworkURL) {
    const rawImage = firstString(item, ["image", "artwork", "cover"]);
    if (rawImage && /^https:\/\//.test(rawImage)) artworkURL = rawImage;
  }

  const audioQuality = "24-bit / 96 kHz FLAC";
  return {
    id,
    title,
    artist,
    album,
    artworkURL,
    audioQuality,
    format: "flac",
    provider: "tidal",
    tidal_id: id,
    isHiRes: true,
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: false,
  };
}

/** Probe whether a self-hosted hifi-api instance is reachable (used by /status). */
export async function hifiApiHealth(env: Env): Promise<{ reachable: boolean; atmos: boolean }> {
  const base = instanceBase(env);
  if (!base) return { reachable: false, atmos: false };
  const headers = instanceHeaders(env);
  const root = await fetchText(`${base}/`, { headers });
  const health = await fetchText(`${base}/health`, { headers });
  const reachable = root != null || health != null;
  return { reachable, atmos: false };
}
