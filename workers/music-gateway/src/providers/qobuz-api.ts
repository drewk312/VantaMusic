import { md5Hex } from "../lib/md5";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { inferBitrateKbps, isHiResSignal } from "../lib/stream-quality";
import { qobuzArtistLine } from "../lib/qobuz-artists";
import { splitArtistCredits } from "../lib/artist-credits";
import type { Env, GatewayTrack, StreamResult } from "../types";
import { fetchJson, fetchText } from "./shared";

const UA = "VANTA-MusicGateway/2.0";
const QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2";

interface QobuzCreds {
  appId: string;
  appSecret: string;
}

let cachedCreds: QobuzCreds | null = null;

export async function getQobuzCredentials(): Promise<QobuzCreds | null> {
  if (cachedCreds) return cachedCreds;

  const scraped = await scrapeQobuzOpenCredentials();
  if (scraped) {
    cachedCreds = scraped;
    return scraped;
  }

  return null;
}

async function scrapeQobuzOpenCredentials(): Promise<QobuzCreds | null> {
  const shell = await fetchText("https://open.qobuz.com/track/1", {
    headers: { "User-Agent": UA, Accept: "text/html" },
  });
  if (!shell) return null;

  const scriptMatch = shell.match(/<script[^>]+src="([^"]+\/js\/main\.js|\/resources\/[^"]+\/js\/main\.js)"/i);
  if (!scriptMatch?.[1]) return null;

  let bundleUrl = scriptMatch[1];
  if (bundleUrl.startsWith("/")) bundleUrl = `https://open.qobuz.com${bundleUrl}`;

  const bundle = await fetchText(bundleUrl, { headers: { "User-Agent": UA } });
  if (!bundle) return null;

  const configMatch = bundle.match(/app_id:"(\d{9})",app_secret:"([a-f0-9]{32})"/);
  if (!configMatch) return null;

  return { appId: configMatch[1], appSecret: configMatch[2] };
}

export function isQobuzSampleUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    const range = parsed.searchParams.get("range");
    if (!range) return false;
    const match = range.match(/^(\d+)-(\d+)$/);
    if (!match) return false;
    const start = Number(match[1]);
    const end = Number(match[2]);
    return end - start <= 40;
  } catch {
    return false;
  }
}

function qobuzSignature(path: string, params: Record<string, string>, timestamp: string, secret: string): string {
  const normalizedPath = path.replace(/^\/+|\/+$/g, "").replace(/\//g, "");
  const keys = Object.keys(params)
    .filter((k) => !["app_id", "request_ts", "request_sig"].includes(k))
    .sort();
  let payload = normalizedPath;
  for (const key of keys) {
    payload += key + params[key];
  }
  payload += timestamp + secret;
  return md5Hex(payload);
}

async function qobuzSignedGet(
  path: string,
  params: Record<string, string>,
  auth?: { userAuthToken?: string }
): Promise<unknown | null> {
  const creds = await getQobuzCredentials();
  if (!creds) return null;
  const timestamp = String(Math.floor(Date.now() / 1000));
  const signedParams = { ...params };
  if (auth?.userAuthToken) signedParams.user_auth_token = auth.userAuthToken;
  const query = new URLSearchParams({ ...signedParams, app_id: creds.appId, request_ts: timestamp });
  const sig = qobuzSignature(path, Object.fromEntries(query.entries()), timestamp, creds.appSecret);
  query.set("request_sig", sig);

  const url = `${QOBUZ_API_BASE}/${path.replace(/^\/+/, "")}?${query.toString()}`;
  const headers: Record<string, string> = {
    "User-Agent": UA,
    Accept: "application/json",
    "X-App-Id": creds.appId,
  };
  if (auth?.userAuthToken) headers["X-User-Auth-Token"] = auth.userAuthToken;
  return fetchJson(url, { headers });
}

export async function searchQobuzPublic(query: string, limit = 25): Promise<GatewayTrack[]> {
  const payload = (await qobuzSignedGet("track/search", { query, limit: String(limit) })) as {
    tracks?: { items?: Array<Record<string, unknown>> };
  } | null;

  return (payload?.tracks?.items ?? []).map((item) => mapQobuzTrack(item));
}

function mapQobuzTrack(item: Record<string, unknown>): GatewayTrack {
  const id = String(item.id ?? "");
  const bitDepth = typeof item.maximum_bit_depth === "number" ? item.maximum_bit_depth : 16;
  const sampleRate = typeof item.maximum_sampling_rate === "number" ? item.maximum_sampling_rate : 44.1;
  const album = item.album as { title?: string; id?: string | number; image?: { large?: string }; release_date_original?: string; release_date_stream?: string } | undefined;
  const audioQuality = `${bitDepth}-bit / ${sampleRate} kHz FLAC`;
  const channelCount = typeof item.maximum_channel_count === "number" ? item.maximum_channel_count : 2;
  const credits = splitArtistCredits(qobuzArtistLine(item));

  return {
    id,
    title: String(item.title ?? "Unknown"),
    artist: credits.primary || qobuzArtistLine(item),
    featuredArtists: credits.featured.length > 0 ? credits.featured : undefined,
    album: album?.title ? String(album.title) : undefined,
    albumId: album?.id != null ? String(album.id) : undefined,
    artworkURL: album?.image?.large,
    duration: typeof item.duration === "number" ? item.duration : undefined,
    releaseYear: parseReleaseYear(album?.release_date_original ?? album?.release_date_stream),
    trackNumber: typeof item.track_number === "number" ? item.track_number : undefined,
    discNumber: typeof item.media_number === "number" ? item.media_number : undefined,
    audioQuality: `${bitDepth}-bit / ${sampleRate} kHz FLAC`,
    isrc: typeof item.isrc === "string" ? item.isrc : undefined,
    format: "flac",
    explicit: Boolean(item.parental_warning),
    provider: "qobuz",
    qobuz_id: id,
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: channelCount > 2,
    isHiRes: typeof item.hires_streamable === "boolean" ? item.hires_streamable : isHiResSignal(audioQuality, "flac", sampleRate, bitDepth),
  };
}

function parseReleaseYear(value: string | undefined): number | undefined {
  const match = value?.match(/^(\d{4})/);
  if (!match) return undefined;
  const year = Number(match[1]);
  return year >= 1900 && year <= new Date().getUTCFullYear() + 1 ? year : undefined;
}

export async function lookupQobuzTrackByIsrc(isrc: string): Promise<string | null> {
  const payload = (await qobuzSignedGet("track/search", { query: isrc, limit: "1" })) as {
    tracks?: { items?: Array<{ id?: number | string }> };
  } | null;
  const id = payload?.tracks?.items?.[0]?.id;
  return id != null ? String(id) : null;
}

export interface QobuzTrackMeta {
  id: string;
  title: string;
  artist: string;
  album?: string;
  isrc?: string;
  durationSec?: number;
}

/** Public Qobuz track metadata (no auth) — used to map a Qobuz id to other catalogs. */
export async function getQobuzTrackMeta(trackId: string): Promise<QobuzTrackMeta | null> {
  const id = String(trackId).trim();
  if (!id) return null;
  const payload = (await qobuzSignedGet("track/get", { track_id: id })) as {
    id?: number | string;
    title?: string;
    isrc?: string;
    duration?: number;
    performer?: { name?: string };
    performers?: string;
    album?: { title?: string };
  } | null;
  if (!payload?.title) return null;
  return {
    id: payload.id != null ? String(payload.id) : id,
    title: payload.title,
    artist: qobuzArtistLine(payload as Record<string, unknown>),
    album: payload.album?.title,
    isrc: payload.isrc,
    durationSec: typeof payload.duration === "number" ? payload.duration : undefined,
  };
}

export async function streamQobuzByoa(
  env: Env,
  trackId: string,
  quality: string,
  request: Request
): Promise<StreamResult | null> {
  const token = withByoaQobuzToken(env, request);
  if (!token) return null;
  return streamQobuzAuthenticated(env, trackId, quality, request);
}

/**
 * Community token path: uses a shared community Qobuz account token
 * (SpotiFLAC "qbz-a" style) so `format_id 27`/`7` hi-res FLAC can be
 * requested even when the operator has not configured their own QOBUZ_AUTH_TOKEN.
 * Falls back to anonymous quality sets when no community token is present.
 */
export async function streamQobuzPublic(
  env: Env,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  if (!trackId.trim()) return null;

  const communityToken = env.QOBUZ_COMMUNITY_TOKEN?.trim();

  // With a community token we can request hi-res (format_id 27) and 7 the
  // same way the SpotiFLAC "community qbz-a" source does.
  const formatIds =
    quality === "16" ? ["6", "5"] : communityToken ? ["27", "7", "6", "5"] : ["7", "6", "5"];

  for (const formatId of formatIds) {
    const payload = await qobuzSignedGet(
      "track/getFileUrl",
      {
        track_id: trackId.trim(),
        format_id: formatId,
        intent: "stream",
      },
      { userAuthToken: communityToken ?? undefined }
    );
    const stream = qobuzFileUrlToStream(payload, quality);
    if (stream) return stream;
  }
  return null;
}

function byoaQobuzToken(request: Request): string | null {
  return request.headers.get("X-Qobuz-Token")?.trim() || request.headers.get("x-qobuz-token")?.trim() || null;
}

export function withByoaQobuzToken(env: Env, request: Request): string | null {
  return byoaQobuzToken(request) || env.QOBUZ_AUTH_TOKEN?.trim() || null;
}

/** Account stream via track/getFileUrl when the operator has configured a Qobuz session or BYOA header. */
export async function streamQobuzAuthenticated(
  env: Env,
  trackId: string,
  quality: string,
  request?: Request
): Promise<StreamResult | null> {
  const token = (request ? withByoaQobuzToken(env, request) : env.QOBUZ_AUTH_TOKEN?.trim()) || null;
  if (!token || !trackId.trim()) return null;

  const formatIds = quality === "16" ? ["6", "5"] : ["27", "7", "6", "5"];
  for (const formatId of formatIds) {
    const payload = await qobuzSignedGet(
      "track/getFileUrl",
      {
        track_id: trackId.trim(),
        format_id: formatId,
        intent: "stream",
      },
      { userAuthToken: token }
    );
    const stream = qobuzFileUrlToStream(payload, quality);
    if (stream) return stream;
  }
  return null;
}

export function qobuzFileUrlToStream(payload: unknown, quality: string): StreamResult | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const url = typeof record.url === "string" ? normalizePublicHttpsUrl(record.url) : null;
  if (!url) return null;
  if (isQobuzSampleUrl(url)) {
    console.warn(
      "VANTA_QOBUZ_SAMPLE_REJECTED",
      JSON.stringify({ urlHost: (() => { try { return new URL(url).host; } catch { return null; } })(), reason: "range-limited preview (no user_auth_token)" })
    );
    return null;
  }

  const mimeType = typeof record.mime_type === "string" ? record.mime_type : "audio/flac";
  const format = mimeType.includes("flac") ? "flac" : mimeType.replace(/^audio\//, "");
  const bitDepth = typeof record.bit_depth === "number" ? record.bit_depth : undefined;
  const samplingRate =
    typeof record.sampling_rate === "number"
      ? record.sampling_rate >= 1000
        ? record.sampling_rate / 1000
        : record.sampling_rate
      : undefined;
  const qualityLabel =
    bitDepth && samplingRate
      ? `${bitDepth}-bit / ${samplingRate} kHz ${format.toUpperCase()}`
      : format === "flac" ? "Lossless FLAC" : format.toUpperCase();

  return {
    url,
    streamUrl: url,
    format,
    quality: qualityLabel,
    mimeType,
    bitrateKbps: inferBitrateKbps(qualityLabel, format),
    bitDepth,
    sampleRateHz: samplingRate != null ? (samplingRate >= 1000 ? samplingRate : Math.round(samplingRate * 1000)) : undefined,
    provider: "qobuz",
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: false,
    isHiRes: isHiResSignal(qualityLabel, format, samplingRate, bitDepth),
  };
}















