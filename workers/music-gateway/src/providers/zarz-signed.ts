import type { Env, ProviderId, StreamResult } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { inferBitrateKbps, is360Quality, isHiResSignal } from "../lib/stream-quality";
import { extensionAudioUrl } from "../extensions/audio-proxy";
import { amazonHasAtmos } from "../extensions/amazon-audio";
import { amazonDecryptionKey } from "../lib/playable-stream";
import { recordProviderVerification, recordVerificationChallenge, providerVerificationRequired } from "../lib/provider-failures";
import { isZarzPlaybackOpen, recordZarzPlaybackFailure, recordZarzPlaybackSuccess } from "./zarz-health";
import type { SessionRenewalOutcome } from "../extensions/session-maintenance";
import {
  COMMUNITY_MIN_SKEW_MS,
  type CommunitySession,
  signCommunityRequest,
} from "./community-session";

export type ZarzProvider = "qobuz" | "tidal" | "deezer" | "amazon";

const ZARZ_BASE_URL = "https://api.zarz.moe/v2";
const ZARZ_CONTRACT = {
  schemeLabel: "ZARZ-HMAC-V1",
  headerPrefix: "X-Zarz-",
  timeWindowSeconds: 300,
};

const ZARZ_DL_PROVIDER: Record<string, ZarzProvider> = {
  qbz: "qobuz",
  tid: "tidal",
  dzr: "deezer",
  amazeamazeamaze: "amazon",
};

function providerFromPath(path: string): ZarzProvider | undefined {
  const match = /^\/dl\/([a-z0-9]+)/.exec(path);
  return match ? ZARZ_DL_PROVIDER[match[1]] : undefined;
}

const APP_VERSION: Record<ZarzProvider, string> = {
  qobuz: "qobuz-web@1.2.10",
  tidal: "tidal-web@1.2.2",
  deezer: "deezer@1.3.5",
  amazon: "amzn@2.3.3",
};

export function sessionFromEnv(env: Env, provider: ZarzProvider, forRenewal = false): CommunitySession | null {
  const prefix = `ZARZ_${provider.toUpperCase()}`;
  const installId = env[`${prefix}_INSTALL_ID` as keyof Env];
  const sessionId = env[`${prefix}_SESSION_ID` as keyof Env];
  const sessionSecret = env[`${prefix}_SESSION_SECRET` as keyof Env];
  const expiresAt = env[`${prefix}_SESSION_EXPIRES` as keyof Env];
  if (typeof sessionId !== "string" || typeof sessionSecret !== "string") return null;
  if (!sessionId.trim() || !sessionSecret.trim()) return null;
  if (typeof expiresAt !== "string" || !expiresAt.trim()) return null;
  const expiresAtMs = Date.parse(expiresAt);
  if (!Number.isFinite(expiresAtMs) || (!forRenewal && expiresAtMs - Date.now() < COMMUNITY_MIN_SKEW_MS)) return null;
  return {
    installId: (typeof installId === "string" ? installId.trim() : "") || env.ZARZ_INSTALL_ID?.trim() || "vanta-gateway",
    sessionId: sessionId.trim(),
    sessionSecret: sessionSecret.trim(),
    expiresAt: expiresAt.trim(),
    appVersion: APP_VERSION[provider],
    platform: "extension",
  };
}

interface SignedSessionResponse {
  ok: boolean;
  status: number;
  payload: Record<string, unknown> | null;
}

async function signedSessionRequest(
  session: CommunitySession,
  path: string,
  bodyValue: Record<string, unknown>,
  extraHeaders: Record<string, string> = {}
): Promise<SignedSessionResponse> {
  const bodyText = JSON.stringify(bodyValue);
  const headers = await signCommunityRequest(
    session,
    "POST",
    `/v2${path}`,
    "",
    new TextEncoder().encode(bodyText),
    undefined,
    undefined,
    {
        ...ZARZ_CONTRACT,
        rollingKeyEncoding: "base64url",
      userAgent: `SpotiFLAC-Mobile/${session.appVersion}`,
    }
  );
  headers.Accept = "application/json";
  headers["Content-Type"] = "application/json";
  Object.assign(headers, extraHeaders);

  let response: Response;
  try {
    response = await fetch(`${ZARZ_BASE_URL}${path}`, {
      method: "POST",
      headers,
      body: bodyText,
      signal: AbortSignal.timeout(path.startsWith("/dl/") ? 12000 : 4000),
      redirect: "manual",
    });
  } catch (error) {
    console.warn("VANTA_ZARZ_NETWORK", JSON.stringify({ path, error: error instanceof Error ? error.message : "fetch failed" }));
    return { ok: false, status: 0, payload: null };
  }
  const payload = (await response.json().catch(() => null)) as Record<string, unknown> | null;
  if (!response.ok) {
    recordProviderVerification(response.status, payload?.code ?? payload?.error, providerFromPath(path));
    console.warn(
      "VANTA_ZARZ_REQUEST",
      JSON.stringify({
        path,
        status: response.status,
        code: stringValue(payload, "code") ?? stringValue(payload, "error"),
        origin: stringValue(payload, "origin"),
        action: stringValue(payload, "action"),
      })
    );
  }
  return { ok: response.ok, status: response.status, payload };
}

export async function signedJson(
  session: CommunitySession,
  path: string,
  bodyValue: Record<string, unknown>,
  extraHeaders: Record<string, string> = {}
): Promise<Record<string, unknown> | null> {
  const response = await signedSessionRequest(session, path, bodyValue, extraHeaders);
  return response.ok ? response.payload : null;
}

function sessionRenewalFromPayload(
  status: number,
  payload: Record<string, unknown> | null,
  provider?: ZarzProvider
): SessionRenewalOutcome {
  const code = stringValue(payload, "code");
  const origin = stringValue(payload, "origin");
  const action = stringValue(payload, "action");
  if (status === 401 && origin === "gateway" && code === "SESSION_INVALID" && action === "bootstrap_session") {
    return { action: "bootstrap_session" };
  }
  if (status === 428 && origin === "gateway" && (code === "VERIFY_REQUIRED" || action === "verify")) {
    recordVerificationChallenge(provider);
    return { action: "verify", challenge: true };
  }
  const expiresAt = stringValue(payload, "expires_at");
  const sessionId = stringValue(payload, "session_id");
  const sessionSecret = stringValue(payload, "session_secret");
  if (status >= 200 && status < 300 && expiresAt && sessionId && sessionSecret) {
    return { expires_at: expiresAt, session_id: sessionId, session_secret: sessionSecret };
  }
  if (status >= 200 && status < 300 && expiresAt) {
    return { expires_at: expiresAt, session_id: sessionId ?? undefined, session_secret: sessionSecret ?? undefined };
  }
  return null;
}

/** Signed POST /session/refresh. Upstream only does this while the session is still usable. */
export async function refreshZarzSession(
  session: CommunitySession,
  provider?: ZarzProvider
): Promise<SessionRenewalOutcome> {
  const response = await signedSessionRequest(session, "/session/refresh", { install_id: session.installId });
  return sessionRenewalFromPayload(response.status, response.payload, provider);
}

/** Unsigned GET /bootstrap. This is the expired-session path; it may return a session or a challenge. */
export async function bootstrapZarzSession(
  session: CommunitySession,
  provider?: ZarzProvider
): Promise<SessionRenewalOutcome> {
  const url = new URL(`${ZARZ_BASE_URL}/bootstrap`);
  url.searchParams.set("app_version", session.appVersion);
  url.searchParams.set("install_id", session.installId);
  let response: Response;
  try {
    response = await fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json",
        "User-Agent": `SpotiFLAC-Mobile/${session.appVersion}`,
      },
      signal: AbortSignal.timeout(8000),
      redirect: "manual",
    });
  } catch (error) {
    console.warn("VANTA_ZARZ_BOOTSTRAP", JSON.stringify({ error: error instanceof Error ? error.message : "fetch failed" }));
    return null;
  }
  const payload = (await response.json().catch(() => null)) as Record<string, unknown> | null;
  const hasSession = Boolean(stringValue(payload, "session_id") && stringValue(payload, "session_secret") && stringValue(payload, "expires_at"));
  const hasChallenge = Boolean(stringValue(payload, "challenge_id") || stringValue(payload, "challenge_url") || stringValue(payload, "auth_url"));
  const bootstrapLog = JSON.stringify({
    status: response.status,
    hasSession,
    hasChallenge,
    code: stringValue(payload, "code") ?? stringValue(payload, "error"),
    origin: stringValue(payload, "origin"),
    action: stringValue(payload, "action"),
  });
  if (hasSession) console.log("VANTA_ZARZ_BOOTSTRAP", bootstrapLog);
  else console.warn("VANTA_ZARZ_BOOTSTRAP", bootstrapLog);
  if (hasChallenge && !hasSession) {
    recordVerificationChallenge(provider);
    recordProviderVerification(428, "VERIFY_REQUIRED", provider);
    return { action: "verify", challenge: true };
  }
  return sessionRenewalFromPayload(response.status, payload, provider);
}

async function sha256Hex(value: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function mintTicket(
  session: CommunitySession,
  provider: "qbz" | "tid" | "dzr" | "amazeamazeamaze",
  resource: string
): Promise<string | null> {
  const resourceHash = await sha256Hex(`${provider}:track:${resource.toLowerCase()}`);
  const payload = await signedJson(session, "/tickets", {
    capability: "download_ticket",
    provider,
    resource_hash: resourceHash,
  });
  return stringValue(payload, "ticket_id") ?? stringValue(payload, "ticket");
}

async function streamQobuz(
  session: CommunitySession,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  const trackUrl = `https://open.qobuz.com/track/${encodeURIComponent(trackId)}`;
  const ticket = await mintTicket(session, "qbz", trackUrl);
  if (!ticket) return null;
  const requestedQuality = quality === "16" ? "cd" : "hi-res-max";
  const payload = await signedJson(
    session,
    "/dl/qbz",
    {
      quality: requestedQuality,
      upload_to_r2: false,
      id: trackId,
      type: "track",
      url: trackUrl,
    },
    { "X-Zarz-Ticket": ticket }
  );
  const nested = objectValue(payload, "data");
  const url = firstString(payload, nested, ["download_url", "url", "link"]);
  const safeUrl = normalizePublicHttpsUrl(url);
  if (!safeUrl) return null;
  const bitDepth = firstNumber(payload, nested, ["bit_depth"]);
  const sampleRateRaw = firstNumber(payload, nested, ["sampling_rate"]);
  const sampleRateHz = sampleRateRaw && sampleRateRaw < 1000 ? Math.round(sampleRateRaw * 1000) : sampleRateRaw;
  const qualityLabel = bitDepth && bitDepth > 16 ? `${bitDepth}-bit Hi-Res` : "Lossless FLAC";
  return {
    url: safeUrl,
    streamUrl: safeUrl,
    format: "flac",
    mimeType: "audio/flac",
    quality: qualityLabel,
    bitrateKbps: inferBitrateKbps(qualityLabel, "flac"),
    bitDepth: bitDepth ?? undefined,
    sampleRateHz: sampleRateHz ?? undefined,
    provider: "qobuz",
    isHiRes: (bitDepth ?? 0) > 16 || isHiResSignal(qualityLabel, "flac"),
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: false,
  };
}

async function streamTidal(
  env: Env,
  session: CommunitySession,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  const ticket = await mintTicket(session, "tid", trackId);
  if (!ticket) return null;
  const atmosRequested = /atmos|dolby|eac3|joc/i.test(quality);
  const body = atmosRequested
    ? { id: trackId, endpoint: "manifests", formats: ["EAC3_JOC"] }
    : { id: trackId, quality: quality === "16" ? "LOSSLESS" : "HI_RES_LOSSLESS" };
  const payload = await signedJson(session, "/dl/tid", body, { "X-Zarz-Ticket": ticket });
  if (!payload) return null;

  if (atmosRequested) {
    const attributes = objectValue(objectValue(objectValue(payload, "data"), "data"), "attributes");
    const formats = Array.isArray(attributes?.formats) ? attributes.formats.map(String) : [];
    if (!formats.some((format) => format.toUpperCase() === "EAC3_JOC")) return null;
    const manifestUrl = normalizePublicHttpsUrl(stringValue(attributes, "uri"));
    if (!manifestUrl) return null;
    return {
      url: manifestUrl,
      streamUrl: manifestUrl,
      format: "eac3-joc",
      mimeType: "application/dash+xml",
      quality: "Dolby Atmos",
      bitrateKbps: inferBitrateKbps("Dolby Atmos", "eac3-joc"),
      sampleRateHz: 48_000,
      provider: "tidal",
      isDolbyAtmos: true,
      isSpatialAudio: true,
      isSurround: true,
      isHiRes: false,
    };
  }

  const data = objectValue(payload, "data");
  if (!data || stringValue(data, "assetPresentation")?.toUpperCase() === "PREVIEW") return null;
  const encodedManifest = stringValue(data, "manifest");
  if (!encodedManifest) return null;
  const decoded = decodeBase64(encodedManifest);
  if (!decoded) return null;
  const audioQuality = stringValue(data, "audioQuality") ?? (quality === "16" ? "LOSSLESS" : "HI_RES_LOSSLESS");
  const bitDepth = numberValue(data, "bitDepth");
  const sampleRateHz = numberValue(data, "sampleRate");

  if (decoded.trimStart().startsWith("{")) {
    const manifest = JSON.parse(decoded) as Record<string, unknown>;
    const urls = Array.isArray(manifest.urls) ? manifest.urls : [];
    const directUrl = normalizePublicHttpsUrl(typeof urls[0] === "string" ? urls[0] : null);
    if (!directUrl) return null;
    const mimeType = stringValue(manifest, "mimeType") ?? "audio/flac";
    return {
      url: directUrl,
      streamUrl: directUrl,
      format: mimeType.includes("flac") ? "flac" : mimeType,
      mimeType,
      quality: audioQuality,
      bitrateKbps: inferBitrateKbps(audioQuality, mimeType),
      bitDepth: bitDepth ?? undefined,
      sampleRateHz: sampleRateHz ?? undefined,
      provider: "tidal",
      isHiRes: isHiResSignal(audioQuality, mimeType) || (bitDepth ?? 0) > 16,
      isDolbyAtmos: false,
      isSpatialAudio: false,
      isSurround: false,
    };
  }

  if (!/<MPD\b/i.test(decoded)) return null;
  const safeManifestData = encodedManifest.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const gatewayOrigin = env.GATEWAY_BASE_URL?.trim() || "https://vanta-music-gateway.16drewk.workers.dev";
  const manifestUrl = `${gatewayOrigin.replace(/\/+$/, "")}/manifest/mpd?data=${safeManifestData}`;
  return {
    url: manifestUrl,
    streamUrl: manifestUrl,
    format: "flac",
    mimeType: "application/dash+xml",
    quality: audioQuality,
    bitrateKbps: inferBitrateKbps(audioQuality, "flac"),
    bitDepth: bitDepth ?? undefined,
    sampleRateHz: sampleRateHz ?? undefined,
    provider: "tidal",
    isHiRes: isHiResSignal(audioQuality, "flac") || (bitDepth ?? 0) > 16,
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: false,
  };
}

async function streamDeezer(env: Env, session: CommunitySession, trackId: string): Promise<StreamResult | null> {
  const trackUrl = `https://www.deezer.com/track/${trackId}`;
  const ticket = await mintTicket(session, "dzr", trackUrl);
  if (!ticket) return null;
  const descriptor = await signedJson(session, "/dl/dzr", {
    id: trackId, type: "track", platform: "deezer", url: trackUrl,
  }, { "X-Zarz-Ticket": ticket });
  if (!descriptor || descriptor.success !== true) return null;
  const encrypted = descriptor.requires_client_decryption === true || descriptor.requires_client_decryption === "true" ||
    descriptor.deezer_encrypted === true || descriptor.deezer_encrypted === "true" ||
    descriptor.direct_downloadable === false || descriptor.direct_downloadable === "false";
  const direct = normalizePublicHttpsUrl(stringValue(descriptor, "direct_download_url") ?? stringValue(descriptor, "download_url"));
  if (!direct) return null;
  const format = stringValue(descriptor, "deezer_format")?.toLowerCase() === "mp3" ? "mp3" : "flac";
  const expires = Date.now() + 30 * 60000;
  const url = encrypted ? await extensionAudioUrl(env, { provider: "deezer", id: trackId, url: direct, format, expires }) : direct;
  if (!url) return null;
  return { url, streamUrl: url, provider: "deezer", format, mimeType: format === "flac" ? "audio/flac" : "audio/mpeg",
    quality: format === "flac" ? "Lossless FLAC" : "MP3", expiresAt: Math.floor(expires / 1000),
    isHiRes: false, isDolbyAtmos: false, isSpatialAudio: false, isSurround: false };
}

async function streamAmazon(
  env: Env,
  session: CommunitySession,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  if (is360Quality(quality)) {
    return (await streamAmazonCodec(env, session, trackId, "mha1"))
      ?? (await streamAmazonCodec(env, session, trackId, "mpegh"));
  }
  const atmosRequested = /atmos|dolby|eac3|joc|ac-?4/i.test(quality);
  if (!atmosRequested) return streamAmazonCodec(env, session, trackId, "flac");
  return (await streamAmazonCodec(env, session, trackId, "ac4"))
    ?? (await streamAmazonCodec(env, session, trackId, "eac3"));
}

async function streamAmazonCodec(
  env: Env,
  session: CommunitySession,
  trackId: string,
  codec: "flac" | "eac3" | "ac4" | "mha1" | "mpegh"
): Promise<StreamResult | null> {
  const ticket = await mintTicket(session, "amazeamazeamaze", trackId);
  if (!ticket) return null;
  const payload = await signedJson(session, "/dl/amazeamazeamaze", { asin: trackId, codec }, { "X-Zarz-Ticket": ticket });
  const item = Array.isArray(payload) ? payload[0] as Record<string, unknown> : payload;
  const audio = objectValue(item, "audio");
  if (!audio) return null;
  const direct = normalizePublicHttpsUrl(stringValue(audio, "url"));
  const key = amazonDecryptionKey(stringValue(audio, "key") ?? audio.key);
  if (audio.encrypted === true && !key) return null;
  const resolved = stringValue(audio, "codec")?.toLowerCase() ?? "";
  const isAc4 = codec === "ac4" && (resolved === "ac4" || resolved === "ac-4");
  const isEac3 = codec === "eac3" && (resolved === "eac3" || resolved === "ec-3");
  const isFlac = codec === "flac" && resolved === "flac";
  const isMpegh = (codec === "mha1" || codec === "mpegh") &&
    /mha1|mhm1|mpegh|mpeg-h/.test(resolved);
  if (!isAc4 && !isEac3 && !isFlac && !isMpegh) return null;
  if (isEac3 && (!direct || !key || !await amazonHasAtmos(direct))) return null;
  if ((isAc4 || isMpegh) && (!direct || !key)) return null;
  const expires = Date.now() + 30 * 60000;
  const url = direct && key ? await extensionAudioUrl(env, { provider: "amazon", id: trackId, url: direct, key, format: "mp4", expires }) : direct;
  if (!url) return null;
  const bitDepth = numberValue(audio, "bitDepth") ?? numberValue(audio, "bitsPerSample");
  const sampleRate = numberValue(audio, "sampleRate");
  const atmos = isAc4 || isEac3;
  return {
    url,
    streamUrl: url,
    provider: "amazon",
    format: isMpegh ? "mha1" : isAc4 ? "ac-4" : isEac3 ? "eac3-joc" : "flac",
    mimeType: key ? "audio/mp4" : "audio/flac",
    expiresAt: Math.floor(expires / 1000),
    quality: isMpegh ? "360 Reality Audio" : atmos ? "Dolby Atmos" : "Lossless FLAC",
    bitDepth: atmos || isMpegh ? undefined : bitDepth ?? undefined,
    sampleRateHz: sampleRate ? (sampleRate < 1000 ? sampleRate * 1000 : sampleRate) : undefined,
    isHiRes: !atmos && !isMpegh && (bitDepth ?? 0) > 16,
    isDolbyAtmos: atmos,
    isSpatialAudio: atmos || isMpegh,
    isSurround: atmos || isMpegh,
    spatialFormat: isMpegh ? "SONY_360_REALITY_AUDIO" : atmos ? "DOLBY_ATMOS" : undefined,
  };
}

export async function zarzCanPlay(env: Env, provider: ProviderId): Promise<boolean> {
  if (provider !== "qobuz" && provider !== "tidal" && provider !== "deezer" && provider !== "amazon") return false;
  // Circuit breaker: skip a provider with repeated stream-level failures until
  // its cooldown expires, so one broken family cannot drain the request budget.
  if (isZarzPlaybackOpen(provider)) return false;
  // Fresh Worker secrets from Connect supersede a stale DO challenge flag.
  if (sessionFromEnv(env, provider)) return true;
  if (!env.EXTENSION_SESSIONS) return false;
  const health = await env.EXTENSION_SESSIONS.getByName(provider).health().catch(() => null) as
    { status?: string } | null;
  const status = health?.status;
  // A live challenge cannot be completed from the Worker. Skip Zarz so PKCE/BYOA
  // and public sources get the stream budget instead of another bootstrap round-trip.
  if (status === "verification_required" || status === "missing") return false;
  // "refreshing" still has a usable stored grant serving playback.
  return status === "ready" || status === "expired" || status === "retrying" || status === "refreshing" || status === "not_checked" || !status;
}

export async function streamViaZarzSigned(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  if (provider !== "qobuz" && provider !== "tidal" && provider !== "deezer" && provider !== "amazon") return null;
  if (isZarzPlaybackOpen(provider)) return null;
  if (!/^[A-Za-z0-9_-]{4,128}$/.test(trackId)) return null;
  const session = env.EXTENSION_SESSIONS
    ? await env.EXTENSION_SESSIONS.getByName(provider).getSession(provider).catch(() => null)
    : sessionFromEnv(env, provider);
  if (!session) return null;
  const resolve = (active: CommunitySession) => {
    if (provider === "deezer") return /^\d+$/.test(trackId) ? streamDeezer(env, active, trackId) : Promise.resolve(null);
    if (provider === "amazon") return /^B[A-Z0-9]{9}$/i.test(trackId) ? streamAmazon(env, active, trackId, quality) : Promise.resolve(null);
    return provider === "qobuz" ? streamQobuz(active, trackId, quality) : streamTidal(env, active, trackId, quality);
  };
  // Goal I: a challenge is recorded only when THIS provider hit it, not a
  // sibling provider earlier in the same request cascade.
  const challengeBefore = providerVerificationRequired();
  const stream = await resolve(session);
  if (stream) {
    recordZarzPlaybackSuccess(provider);
    return { ...stream, family: "ZARZ" as const };
  }
  const providerChallenged = providerVerificationRequired() && !challengeBefore;
  if (!env.EXTENSION_SESSIONS) {
    if (!providerVerificationRequired()) recordZarzPlaybackFailure(provider);
    return null;
  }
  if (providerChallenged) {
    // Try the provider's supported renewal once before requiring interactive verification.
    const renewed = await env.EXTENSION_SESSIONS.getByName(provider).getSession(provider, true).catch(() => null);
    if (renewed) {
      const recovered = await resolve(renewed);
      if (recovered) {
        recordZarzPlaybackSuccess(provider);
        return { ...recovered, family: "ZARZ" as const };
      }
    }
    return null;
  }
  recordZarzPlaybackFailure(provider);
  return stream;
}

function decodeBase64(value: string): string | null {
  try {
    return atob(value.replace(/-/g, "+").replace(/_/g, "/"));
  } catch {
    return null;
  }
}

function objectValue(value: Record<string, unknown> | null | undefined, key: string): Record<string, unknown> | null {
  const nested = value?.[key];
  return nested && typeof nested === "object" && !Array.isArray(nested)
    ? nested as Record<string, unknown>
    : null;
}

function stringValue(value: Record<string, unknown> | null | undefined, key: string): string | null {
  const item = value?.[key];
  return typeof item === "string" && item.trim() ? item.trim() : null;
}

function numberValue(value: Record<string, unknown> | null | undefined, key: string): number | null {
  const item = value?.[key];
  if (typeof item === "number" && Number.isFinite(item)) return item;
  if (typeof item === "string" && /^\d+(?:\.\d+)?$/.test(item)) return Number(item);
  return null;
}

function firstString(
  primary: Record<string, unknown> | null,
  nested: Record<string, unknown> | null,
  keys: string[]
): string | null {
  for (const key of keys) {
    const value = stringValue(primary, key) ?? stringValue(nested, key);
    if (value) return value;
  }
  return null;
}

function firstNumber(
  primary: Record<string, unknown> | null,
  nested: Record<string, unknown> | null,
  keys: string[]
): number | null {
  for (const key of keys) {
    const value = numberValue(primary, key) ?? numberValue(nested, key);
    if (value != null) return value;
  }
  return null;
}
