import { md5Hex } from "../lib/md5";
import type { Env, GatewayTrack } from "../types";
import { fetchJson, fetchText } from "./shared";

const UA = "VANTA-MusicGateway/2.0";
const QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2";
const DEFAULT_APP_ID = "712109809";
const DEFAULT_APP_SECRET = "***REMOVED***";

interface QobuzCreds {
  appId: string;
  appSecret: string;
}

let cachedCreds: QobuzCreds | null = null;

export async function getQobuzCredentials(): Promise<QobuzCreds> {
  if (cachedCreds) return cachedCreds;

  const scraped = await scrapeQobuzOpenCredentials();
  if (scraped) {
    cachedCreds = scraped;
    return scraped;
  }

  cachedCreds = { appId: DEFAULT_APP_ID, appSecret: DEFAULT_APP_SECRET };
  return cachedCreds;
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

async function qobuzSignedGet(path: string, params: Record<string, string>): Promise<unknown | null> {
  const creds = await getQobuzCredentials();
  const timestamp = String(Math.floor(Date.now() / 1000));
  const query = new URLSearchParams({ ...params, app_id: creds.appId, request_ts: timestamp });
  const sig = qobuzSignature(path, Object.fromEntries(query.entries()), timestamp, creds.appSecret);
  query.set("request_sig", sig);

  const url = `${QOBUZ_API_BASE}/${path.replace(/^\/+/, "")}?${query.toString()}`;
  return fetchJson(url, {
    headers: { "User-Agent": UA, Accept: "application/json", "X-App-Id": creds.appId },
  });
}

export async function searchQobuzPublic(query: string, limit = 25): Promise<GatewayTrack[]> {
  const payload = (await qobuzSignedGet("track/search", { query, limit: String(limit) })) as {
    tracks?: { items?: Array<Record<string, unknown>> };
  } | null;

  return (payload?.tracks?.items ?? []).map((item) => mapQobuzTrack(item));
}

function mapQobuzTrack(item: Record<string, unknown>): GatewayTrack {
  const performer = item.performer as { name?: string } | undefined;
  const album = item.album as { title?: string; id?: string | number; image?: { large?: string } } | undefined;
  const id = String(item.id ?? "");
  const bitDepth = typeof item.maximum_bit_depth === "number" ? item.maximum_bit_depth : 16;
  const sampleRate = typeof item.maximum_sampling_rate === "number" ? item.maximum_sampling_rate : 44.1;

  return {
    id,
    title: String(item.title ?? "Unknown"),
    artist: performer?.name ?? "Unknown Artist",
    album: album?.title ? String(album.title) : undefined,
    albumId: album?.id != null ? String(album.id) : undefined,
    artworkURL: album?.image?.large,
    duration: typeof item.duration === "number" ? item.duration : undefined,
    trackNumber: typeof item.track_number === "number" ? item.track_number : undefined,
    discNumber: typeof item.media_number === "number" ? item.media_number : undefined,
    audioQuality: `${bitDepth}-bit / ${sampleRate} kHz FLAC`,
    isrc: typeof item.isrc === "string" ? item.isrc : undefined,
    format: "flac",
    explicit: Boolean(item.parental_warning),
    provider: "qobuz",
    qobuz_id: id,
  };
}

export async function lookupQobuzTrackByIsrc(isrc: string): Promise<string | null> {
  const payload = (await qobuzSignedGet("track/search", { query: isrc, limit: "1" })) as {
    tracks?: { items?: Array<{ id?: number | string }> };
  } | null;
  const id = payload?.tracks?.items?.[0]?.id;
  return id != null ? String(id) : null;
}
