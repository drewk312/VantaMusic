import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { hasAtmosCodecSignal, inferBitrateKbps, inferContainerFromUrl, qualityLabelFromBitrate } from "../lib/stream-quality";
import type { Env, ProviderId, StreamResult } from "../types";
import { fetchJson } from "./shared";
import { amazonDirectUrlIsLocked, amazonPayloadDecryptionKey, isGatewayDecryptUrl } from "../lib/playable-stream";
import { extensionAudioUrl } from "../extensions/audio-proxy";

/** Resolve against VANTA's authenticated, self-hosted multi-provider contract. */
export async function streamFromOperatorBackend(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  const base = normalizePublicHttpsUrl(env.OPERATOR_BACKEND_URL)?.replace(/\/+$/, "");
  const key = env.OPERATOR_BACKEND_API_KEY?.trim();
  if (!base || !key || !trackId.trim()) return null;
  const capabilities = (env.OPERATOR_BACKEND_PROVIDERS ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean);
  if (capabilities.length > 0 && !capabilities.includes(provider)) return null;

  const endpoint = new URL(`${base}/v1/streams/${encodeURIComponent(provider)}/${encodeURIComponent(trackId)}`);
  endpoint.searchParams.set("quality", quality);
  const payload = await fetchJson(endpoint.toString(), {
    headers: {
      Authorization: `Bearer ${key}`,
      "X-Api-Key": key,
    },
  });
  const record = unwrapRecord(payload);
  const stream = normalizeOperatorStreamPayload(payload, provider);
  if (!stream) return null;

  if (provider === "amazon" && !stream.drm) {
    return decorateAmazonClearKey(env, trackId, stream, record);
  }
  return stream;
}

/**
 * Amazon clear-key delivery from the operator contract. When the operator
 * returns `{url, key|key_specs}` (relay-style AES key), wrap the raw CDN URL
 * through the gateway decrypt proxy so ExoPlayer never sees CENC. When the URL
 * is a locked CloudFront CENC URL with NO usable key, drop the step so the
 * cascade can fall through to public/community paths (REQUEST_BLOCKED proof).
 */
export async function decorateAmazonClearKey(
  env: Env,
  trackId: string,
  stream: StreamResult,
  record: Record<string, unknown> | null
): Promise<StreamResult | null> {
  if (isGatewayDecryptUrl(stream.url) || /\/manifest\/mpd/i.test(stream.url)) return stream;
  const payloadKey = amazonPayloadDecryptionKey(record);
  if (amazonDirectUrlIsLocked("amazon", stream.url, payloadKey)) return null;
  if (!payloadKey) return stream;

  const proxied = await extensionAudioUrl(env, {
    provider: "amazon",
    id: trackId,
    url: stream.url,
    key: payloadKey,
    format: "mp4",
    expires: Date.now() + 30 * 60_000,
  });
  if (!proxied) return null;
  return { ...stream, url: proxied, streamUrl: proxied };
}

export function normalizeOperatorStreamPayload(
  payload: unknown,
  requestedProvider: ProviderId
): StreamResult | null {
  const record = unwrapRecord(payload);
  if (!record) return null;

  const url = normalizePublicHttpsUrl(firstString(record, ["streamUrl", "stream_url", "url"]));
  if (!url) return null;

  const responseProvider = firstString(record, ["provider", "service"]);
  if (responseProvider && responseProvider.toLowerCase() !== requestedProvider) return null;

  const quality = firstString(record, ["quality", "qualityLabel", "audioQuality"]);
  const codec = firstString(record, ["codec", "format"]);
  const mimeType = firstString(record, ["mimeType", "mime_type", "contentType"]);
  const format = codec ?? inferContainerFromUrl(url);
  const bitrateKbps = firstNumber(record, ["bitrateKbps", "bitrate_kbps", "bitrate"])
    ?? inferBitrateKbps(quality, format);
  const verifiedAtmos = hasAtmosCodecSignal(quality, codec, mimeType);
  const drmRecord = objectValue(record.drm);
  const protectedWidevine = (requestedProvider === "tidal" || requestedProvider === "amazon") &&
    drmRecord?.scheme?.toString().toLowerCase() === "widevine";

  return {
    url,
    streamUrl: url,
    provider: requestedProvider,
    quality: quality ?? qualityLabelFromBitrate(bitrateKbps, format),
    format,
    mimeType: mimeType ?? (format?.includes("/") ? format : format ? `audio/${format}` : undefined),
    bitrateKbps,
    bitDepth: firstNumber(record, ["bitDepth", "bit_depth"]) ?? undefined,
    sampleRateHz: firstNumber(record, ["sampleRateHz", "sample_rate_hz", "sampleRate"]) ?? undefined,
    channelCount: firstNumber(record, ["channelCount", "channel_count", "channels"]) ?? undefined,
    expiresAt: firstNumber(record, ["expiresAt", "expires_at", "expiration"]) ?? undefined,
    isDolbyAtmos: verifiedAtmos,
    isSpatialAudio: verifiedAtmos,
    isSurround: verifiedAtmos,
    isHiRes: booleanValue(record.isHiRes) ?? false,
    drm: protectedWidevine
      ? {
          scheme: "widevine",
          licenseProxy: requestedProvider === "amazon" ? "amazon" : "tidal",
          forceDefaultLicenseUri: true,
          licenseRequestHeaders: safeLicenseRequestHeaders(drmRecord?.licenseRequestHeaders ?? drmRecord?.licenseHeaders ?? drmRecord?.headers),
        }
      : undefined,
  };
}

/** Pass only safe public per-track DRM headers to clients; never operator credentials. */
function safeLicenseRequestHeaders(value: unknown): Record<string, string> | undefined {
  const record = objectValue(value);
  if (!record) return undefined;
  const blocked = new Set(["authorization", "x-api-key", "cookie", "set-cookie", "proxy-authorization"]);
  const out: Record<string, string> = {};
  for (const [name, value] of Object.entries(record)) {
    const key = name.trim();
    if (!key || blocked.has(key.toLowerCase()) || !/^[A-Za-z0-9-]{1,64}$/.test(key)) continue;
    if (typeof value !== "string") continue;
    const trimmed = value.trim();
    if (!trimmed || trimmed.length > 2048 || /[\r\n]/.test(trimmed)) continue;
    out[key] = trimmed;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

function unwrapRecord(payload: unknown): Record<string, unknown> | null {
  const root = objectValue(payload);
  if (!root) return null;
  return objectValue(root.stream) ?? objectValue(root.data) ?? root;
}

function objectValue(value: unknown): Record<string, unknown> | null {
  return value != null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
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
    if (typeof value === "string" && /^\d+(?:\.\d+)?$/.test(value)) return Number(value);
  }
  return null;
}

function booleanValue(value: unknown): boolean | null {
  if (typeof value === "boolean") return value;
  if (value === "true" || value === 1) return true;
  if (value === "false" || value === 0) return false;
  return null;
}
