import { md5Hex } from "../lib/md5";
import {
  hasDolbyAtmosSignal,
  hasSpatialAudioSignal,
  hasSurroundSignal,
  inferBitrateKbps,
  inferContainerFromUrl,
  qualityLabelFromBitrate,
} from "../lib/stream-quality";
import { raceFirst } from "../lib/race-first";
import type { Env, ProviderId, StreamResult } from "../types";
import { getCommunityApiKey } from "./community-api-key";
import { getCommunityDownloadUrl } from "./community-crypto";
import { streamViaMusicDlPublic } from "./musicdl-public";
import { streamViaDeezerByTrackId } from "./deezer-public";
import { fetchJson, fetchText } from "./shared";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { isRelayPoolDown as isRelayPoolDownEnc, markRelayPoolDown as markRelayPoolDownEnc, markRelayPoolUp as markRelayPoolUpEnc } from "./relay-health";

const UA = "VANTA-MusicGateway/2.0";

function mapQuality(quality: string): string {
  const value = quality.trim().toLowerCase();
  if (value === "atmos" || value === "dolby_atmos" || value.includes("atmos")) return "atmos";
  if (value === "hi_res" || value === "hi_res_lossless") return "HI_RES";
  return quality === "16" ? "16" : "24";
}

function mapWjheQuality(quality: string): { quality: number; format: string } {
  return quality === "16" ? { quality: 1000, format: "flac" } : { quality: 2000, format: "flac" };
}

function extractStreamUrl(payload: unknown): string | null {
  if (!payload) return null;
  if (typeof payload === "string") {
    const trimmed = payload.trim().replace(/^"|"$/g, "");
    return normalizePublicHttpsUrl(trimmed);
  }
  if (typeof payload !== "object") return null;

  const record = payload as Record<string, unknown>;
  for (const key of ["url", "streamUrl", "stream_url", "downloadUrl", "download_url", "link", "location"]) {
    const value = record[key];
    if (typeof value === "string") {
      const safeUrl = normalizePublicHttpsUrl(value);
      if (safeUrl) return safeUrl;
    }
  }

  for (const nested of Object.values(record)) {
    const found = extractStreamUrl(nested);
    if (found) return found;
  }
  return null;
}

function toStreamResult(url: string, provider: ProviderId, quality?: string, format?: string): StreamResult {
  const resolvedFormat = format ?? inferContainerFromUrl(url) ?? (hasDolbyAtmosSignal(quality) ? "m4a" : undefined);
  const bitrateKbps = inferBitrateKbps(quality, resolvedFormat);
  return {
    url,
    streamUrl: url,
    format: resolvedFormat,
    quality: quality ?? qualityLabelFromBitrate(bitrateKbps, resolvedFormat),
    mimeType: resolvedFormat?.includes("/") ? resolvedFormat : (resolvedFormat ? `audio/${resolvedFormat}` : undefined),
    bitrateKbps,
    provider,
    isDolbyAtmos: hasDolbyAtmosSignal(quality, resolvedFormat),
    isSpatialAudio: hasSpatialAudioSignal(quality, resolvedFormat),
    isSurround: hasSurroundSignal(quality, resolvedFormat),
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

  // These community gateway bases are the OSS/demand relays. When a base went
  // through the relay breaker and was marked down for this provider, skip the
  // whole fan-out so a single request does not burn the subrequest budget.
  if (isRelayPoolDownEnc(`enc:${provider}`)) {
    console.warn(
      "VANTA_COMMUNITY_BREAKER",
      JSON.stringify({ scope: `enc:${provider}`, trackId, action: "skip", reason: "relay pool marked down" })
    );
    return null;
  }

  const result = await raceFirst(
    bases.flatMap((base) => [
      () => streamViaCommunityGet(base, trackId, provider, q),
      () => streamViaCommunityPost(base, trackId, provider, q),
    ])
  );

  if (result) {
    markRelayPoolUpEnc(`enc:${provider}`);
    return result;
  }
  return null;
}

async function streamViaCommunityGet(
  base: string,
  trackId: string,
  provider: ProviderId,
  quality: string
): Promise<StreamResult | null> {
  // Single URL pattern (was 4) — save subrequests
  const endpoint = `${base}/stream/${encodeURIComponent(trackId)}?provider=${provider}&quality=${quality}`;
  const payload = await fetchJson(endpoint);
  const url = extractStreamUrl(payload);
  if (url) {
    const label = (payload as { quality?: string })?.quality ?? quality;
    return toStreamResult(url, provider, label);
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
  // Single mirror (was 3) — save subrequests
  return [env.GDSTUDIO_API_URL ?? "https://music.gdstudio.xyz/api.php"];
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
  _gdstudioUrls: string[]
): Array<() => Promise<StreamResult | null>> {
  return [
    () => streamViaCommunityGateway(env, trackId, "qobuz", quality),
    () => streamViaWjhe(trackId, quality),
    () => streamViaEncryptedCommunity(env, "qobuz", trackId, quality),
    () => streamViaMusicDlPublic(env, trackId, quality),
  ];
}

export async function streamViaEncryptedCommunity(
  env: Env,
  kind: "qobuz" | "tidal" | "amazon",
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  // The "encrypted request required" relay pool is down when the breaker says so.
  const providerKind: ProviderId = kind === "amazon" ? "amazon" : kind === "tidal" ? "tidal" : "qobuz";
  if (isRelayPoolDownEnc(`enc:${providerKind}`)) {
    console.warn(
      "VANTA_COMMUNITY_BREAKER",
      JSON.stringify({ scope: `enc:${providerKind}`, trackId, action: "skip", reason: "relay pool marked down" })
    );
    return null;
  }

  const endpoint = getCommunityDownloadUrl(kind, env);
  if (!endpoint) return null;

  const apiKey = getCommunityApiKey(env);
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
  if (url) {
    markRelayPoolUpEnc(`enc:${providerKind}`);
    return toStreamResult(url, kind === "amazon" ? "amazon" : kind);
  }
  markRelayPoolDownEnc(`enc:${providerKind}`, "encrypted community returned no stream");
  return null;
}

export async function streamViaAmazonSpotbye(trackId: string, quality: string): Promise<StreamResult | null> {
  if (isRelayPoolDownEnc(`enc:amazon`)) {
    console.warn(
      "VANTA_COMMUNITY_BREAKER",
      JSON.stringify({ scope: "enc:amazon", trackId, action: "skip", reason: "relay pool marked down" })
    );
    return null;
  }
  const asin = trackId.match(/(B[0-9A-Z]{9})/i)?.[1] ?? trackId;
  // Single endpoint (was 2) — save subrequests
  const endpoint = "https://amazon.spotbye.qzz.io/api/dl";

  const payload = await fetchJson(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json", "User-Agent": UA },
    body: JSON.stringify({ id: asin, quality: mapQuality(quality), country: "US" }),
  });
  const url = extractStreamUrl(payload);
  if (url) {
    markRelayPoolUpEnc(`enc:amazon`);
    return toStreamResult(url, "amazon");
  }
  markRelayPoolDownEnc(`enc:amazon`, "amazon spotbye relay returned no stream");
  return null;
}

export type StreamCrossIds = {
  qobuz?: string;
  tidal?: string;
  isrc?: string;
  deezer?: string;
  amazon?: string;
  title?: string;
  artist?: string;
  durationSec?: number;
};

function deezerPublicAttempt(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string,
  crossIds: StreamCrossIds
): () => Promise<StreamResult | null> {
  const deezerId = crossIds.deezer ?? (provider === "deezer" ? trackId : "");
  return () => streamViaDeezerByTrackId(env, deezerId, crossIds.isrc, quality);
}

export async function streamWithPublicFallbacks(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string,
  crossIds: StreamCrossIds = {}
): Promise<StreamResult | null> {
  const gdstudioUrls = gdstudioMirrorUrls(env);
  const deezerPublic = deezerPublicAttempt(env, provider, trackId, quality, crossIds);

  if (provider === "deezer") {
    const attempts: Array<() => Promise<StreamResult | null>> = [deezerPublic];
    if (crossIds.tidal) {
      attempts.push(() => streamViaCommunityGateway(env, crossIds.tidal!, "tidal", quality));
      attempts.push(() => streamViaEncryptedCommunity(env, "tidal", crossIds.tidal!, quality));
    }
    const qobuzId = crossIds.qobuz;
    if (qobuzId) {
      attempts.push(...qobuzMirrorAttempts(env, qobuzId, quality, gdstudioUrls));
    }
    attempts.push(() => streamViaCommunityGateway(env, trackId, "deezer", quality));
    return raceFirst(attempts);
  }

  if (provider === "qobuz") {
    const qobuzId = crossIds.qobuz ?? trackId;
    return raceFirst([...qobuzMirrorAttempts(env, qobuzId, quality, gdstudioUrls), deezerPublic]);
  }

  if (provider === "tidal") {
    return raceFirst([
      () => streamViaCommunityGateway(env, trackId, "tidal", quality),
      () => streamViaEncryptedCommunity(env, "tidal", trackId, quality),
      deezerPublic,
    ]);
  }

  if (provider === "amazon") {
    return raceFirst([
      () => streamViaAmazonSpotbye(trackId, quality),
      () => streamViaEncryptedCommunity(env, "amazon", trackId, quality),
      () => streamViaCommunityGateway(env, trackId, "amazon", quality),
      deezerPublic,
    ]);
  }

  if (provider === "pandora") {
    return raceFirst([
      () => streamViaCommunityGateway(env, trackId, "pandora", quality),
      () => streamViaCommunityGateway(env, trackId, "pandora", quality === "16" ? "24" : quality),
      deezerPublic,
    ]);
  }

  return null;
}





