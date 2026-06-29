import { md5Hex } from "../lib/md5";
import { inferBitrateKbps, qualityLabelFromBitrate } from "../lib/stream-quality";
import { raceFirst } from "../lib/race-first";
import type { Env, ProviderId, StreamResult } from "../types";
import { getCommunityApiKey } from "./community-api-key";
import { getCommunityDownloadUrl } from "./community-crypto";
import { streamViaMusicDlPublic } from "./musicdl-public";
import { fetchJson, fetchText } from "./shared";

const UA = "VANTA-MusicGateway/2.0";

function mapQuality(quality: string): string {
  return quality === "16" ? "16" : "24";
}

function mapWjheQuality(quality: string): { quality: number; format: string } {
  return quality === "16" ? { quality: 1000, format: "flac" } : { quality: 2000, format: "flac" };
}

function extractStreamUrl(payload: unknown): string | null {
  if (!payload) return null;
  if (typeof payload === "string") {
    const trimmed = payload.trim().replace(/^"|"$/g, "");
    return trimmed.startsWith("http") ? trimmed : null;
  }
  if (typeof payload !== "object") return null;

  const record = payload as Record<string, unknown>;
  for (const key of ["url", "streamUrl", "stream_url", "downloadUrl", "download_url", "link", "location"]) {
    const value = record[key];
    if (typeof value === "string" && value.startsWith("http")) return value;
  }

  for (const nested of Object.values(record)) {
    const found = extractStreamUrl(nested);
    if (found) return found;
  }
  return null;
}

function toStreamResult(url: string, provider: ProviderId, quality?: string, format = "flac"): StreamResult {
  const bitrateKbps = inferBitrateKbps(quality, format);
  return {
    url,
    streamUrl: url,
    format,
    quality: quality ?? qualityLabelFromBitrate(bitrateKbps, format),
    mimeType: format.includes("/") ? format : `audio/${format}`,
    bitrateKbps,
    provider,
  };
}

function communityGatewayBases(env: Env): string[] {
  const primary = (env.DEFAULT_COMMUNITY_GATEWAY ?? "https://qobuz-tidal-eclipse.cyrusna29.workers.dev").replace(
    /\/$/,
    ""
  );
  const extras = (env.FALLBACK_COMMUNITY_GATEWAYS ?? "")
    .split(",")
    .map((value) => value.trim().replace(/\/$/, ""))
    .filter(Boolean);
  return [...new Set([primary, ...extras])];
}

export async function streamViaCommunityGateway(
  env: Env,
  trackId: string,
  provider: ProviderId,
  quality: string
): Promise<StreamResult | null> {
  const q = mapQuality(quality);
  const bases = communityGatewayBases(env);

  return raceFirst(
    bases.flatMap((base) => [
      () => streamViaCommunityGet(base, trackId, provider, q),
      () => streamViaCommunityPost(base, trackId, provider, q),
    ])
  );
}

async function streamViaCommunityGet(
  base: string,
  trackId: string,
  provider: ProviderId,
  quality: string
): Promise<StreamResult | null> {
  const urls = [
    `${base}/stream/${encodeURIComponent(trackId)}?provider=${provider}&quality=${quality}`,
    `${base}/stream/${encodeURIComponent(trackId)}?quality=${quality}`,
    `${base}/api/stream/${encodeURIComponent(trackId)}?provider=${provider}&quality=${quality}`,
    `${base}/stream/${encodeURIComponent(trackId)}`,
  ];

  for (const endpoint of urls) {
    const payload = await fetchJson(endpoint);
    const url = extractStreamUrl(payload);
    if (url) {
      const label = (payload as { quality?: string })?.quality ?? quality;
      return toStreamResult(url, provider, label);
    }
  }
  return null;
}

async function streamViaCommunityPost(
  base: string,
  trackId: string,
  provider: ProviderId,
  quality: string
): Promise<StreamResult | null> {
  const postPayload = await fetchJson(`${base}/api/dl`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: trackId, quality, service: provider }),
  });
  const postUrl = extractStreamUrl(postPayload);
  if (!postUrl) return null;
  return toStreamResult(postUrl, provider, (postPayload as { quality?: string })?.quality ?? quality);
}

export async function streamViaWjhe(trackId: string, quality: string): Promise<StreamResult | null> {
  const { quality: wjheQ, format } = mapWjheQuality(quality);
  const url = `https://music.wjhe.top/api/music/qobuz/url?ID=${encodeURIComponent(trackId)}&quality=${wjheQ}&format=${format}`;
  const payload = await fetchJson(url);
  const streamUrl = extractStreamUrl(payload);
  return streamUrl ? toStreamResult(streamUrl, "qobuz") : null;
}

async function gdstudioTs9(host: string): Promise<string> {
  const fallback = String(Date.now()).slice(0, 9);
  const body = await fetchText(`https://${host}/time`);
  const trimmed = body?.trim() ?? "";
  return trimmed.length >= 9 ? trimmed.slice(0, 9) : fallback;
}

function gdstudioVersion(): string {
  return "20260510";
}

function gdstudioMirrorUrls(env: Env): string[] {
  return [
    env.GDSTUDIO_API_URL ?? "https://music.gdstudio.xyz/api.php",
    "https://music.gdstudio.org/api.php",
    "https://music-api.gdstudio.org/api.php",
  ];
}

function gdstudioSignature(host: string, trackId: string, ts9: string): string {
  const escaped = encodeURIComponent(trackId).replace(/\+/g, "%20");
  const base = `${host}|${gdstudioVersion()}|${ts9}|${escaped}`;
  const digest = md5Hex(base).toUpperCase();
  return digest.slice(-8);
}

function gdstudioBitrate(quality: string): string {
  return quality === "16" ? "740" : "999";
}

export async function streamViaGDStudio(
  trackId: string,
  quality: string,
  apiUrl: string,
  source: "qobuz" | "tidal" | "deezer" = "qobuz"
): Promise<StreamResult | null> {
  const parsed = new URL(apiUrl);
  const host = parsed.host;
  const ts9 = await gdstudioTs9(host);
  const body = new URLSearchParams({
    types: "url",
    id: trackId,
    source,
    br: gdstudioBitrate(quality),
    s: gdstudioSignature(host, trackId, ts9),
  });

  const payload = await fetchText(apiUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
      Origin: `https://${host}`,
      Referer: `https://${host}/`,
      "User-Agent": UA,
    },
    body: body.toString(),
  });

  if (!payload) return null;
  const provider: ProviderId = source === "tidal" ? "tidal" : source === "deezer" ? "deezer" : "qobuz";
  try {
    const json = JSON.parse(payload) as unknown;
    const url = extractStreamUrl(json);
    if (url) return toStreamResult(url, provider);
  } catch {
    const match = payload.match(/https?:\/\/[^\s"'<>]+/);
    if (match?.[0]) return toStreamResult(match[0], provider);
  }
  return null;
}

function gdstudioAttempts(
  trackId: string,
  quality: string,
  apiUrls: string[],
  source: "qobuz" | "tidal" | "deezer"
): Array<() => Promise<StreamResult | null>> {
  return apiUrls.map((api) => () => streamViaGDStudio(trackId, quality, api, source));
}

function qobuzMirrorAttempts(
  env: Env,
  trackId: string,
  quality: string,
  gdstudioUrls: string[]
): Array<() => Promise<StreamResult | null>> {
  return [
    () => streamViaCommunityGateway(env, trackId, "qobuz", quality),
    ...gdstudioAttempts(trackId, quality, gdstudioUrls, "qobuz"),
    () => streamViaEncryptedCommunity("qobuz", trackId, quality),
    () => streamViaWjhe(trackId, quality),
    () => streamViaMusicDlPublic(trackId, quality),
  ];
}

export async function streamViaEncryptedCommunity(
  kind: "qobuz" | "tidal" | "amazon",
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  const endpoint = await getCommunityDownloadUrl(kind);
  if (!endpoint) return null;

  const apiKey = await getCommunityApiKey();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
    "User-Agent": UA,
  };
  if (apiKey) headers["x-api-key"] = apiKey;

  const payload = await fetchJson(endpoint, {
    method: "POST",
    headers,
    body: JSON.stringify({
      id: trackId,
      quality: mapQuality(quality),
      service: kind,
      country: "US",
    }),
  });

  const url = extractStreamUrl(payload);
  return url ? toStreamResult(url, kind === "amazon" ? "amazon" : kind) : null;
}

export async function streamViaAmazonSpotbye(trackId: string, quality: string): Promise<StreamResult | null> {
  const asin = trackId.match(/(B[0-9A-Z]{9})/i)?.[1] ?? trackId;
  const endpoints = [
    "https://amazon.spotbye.qzz.io/api/dl",
    "https://amazon.spotbye.qzz.io/api/download",
  ];

  for (const endpoint of endpoints) {
    const payload = await fetchJson(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", "User-Agent": UA },
      body: JSON.stringify({ id: asin, quality: mapQuality(quality), country: "US" }),
    });
    const url = extractStreamUrl(payload);
    if (url) return toStreamResult(url, "amazon");
  }
  return null;
}

export type StreamCrossIds = { qobuz?: string; tidal?: string };

export async function streamWithPublicFallbacks(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string,
  crossIds: StreamCrossIds = {}
): Promise<StreamResult | null> {
  const gdstudioUrls = gdstudioMirrorUrls(env);

  if (provider === "deezer") {
    const attempts: Array<() => Promise<StreamResult | null>> = [];
    if (crossIds.tidal) {
      attempts.push(() => streamViaCommunityGateway(env, crossIds.tidal!, "tidal", quality));
      attempts.push(() => streamViaEncryptedCommunity("tidal", crossIds.tidal!, quality));
      attempts.push(...gdstudioAttempts(crossIds.tidal!, quality, gdstudioUrls, "tidal"));
    }
    const qobuzId = crossIds.qobuz;
    if (qobuzId) {
      attempts.push(...qobuzMirrorAttempts(env, qobuzId, quality, gdstudioUrls));
    }
    attempts.push(() => streamViaCommunityGateway(env, trackId, "deezer", quality));
    attempts.push(...gdstudioAttempts(trackId, quality, gdstudioUrls, "deezer"));
    return raceFirst(attempts);
  }

  if (provider === "qobuz") {
    const qobuzId = crossIds.qobuz ?? trackId;
    return raceFirst(qobuzMirrorAttempts(env, qobuzId, quality, gdstudioUrls));
  }

  if (provider === "tidal") {
    return raceFirst([
      () => streamViaCommunityGateway(env, trackId, "tidal", quality),
      () => streamViaEncryptedCommunity("tidal", trackId, quality),
      ...gdstudioAttempts(trackId, quality, gdstudioUrls, "tidal"),
    ]);
  }

  if (provider === "amazon") {
    return raceFirst([
      () => streamViaAmazonSpotbye(trackId, quality),
      () => streamViaEncryptedCommunity("amazon", trackId, quality),
      () => streamViaCommunityGateway(env, trackId, "amazon", quality),
    ]);
  }

  if (provider === "pandora") {
    return raceFirst([
      () => streamViaCommunityGateway(env, trackId, "pandora", quality),
      () => streamViaCommunityGateway(env, trackId, "pandora", quality === "16" ? "24" : quality),
    ]);
  }

  return null;
}
