import { normalizePublicHttpsUrl } from "./safe-stream-url";
import type { Env, StreamDrmConfiguration, StreamResult } from "../types";

const TOKEN_LIFETIME_SECONDS = 10 * 60;
const MAX_TOKEN_AGE_SECONDS = 15 * 60;
const MAX_LICENSE_REQUEST_BYTES = 1024 * 1024;
const TOKEN_CONTEXT = "vanta:drm:tidal:widevine:v1";
const AMAZON_TOKEN_CONTEXT = "vanta:drm:amazon:widevine:v1";
const encoder = new TextEncoder();

type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

/**
 * Replace an internal license-proxy marker with a short-lived, signed public
 * URL. Private Tidal/operator credentials never cross this boundary.
 */
export async function streamForClient(
  stream: StreamResult,
  publicOrigin: string,
  env: Env,
  nowSeconds = Math.floor(Date.now() / 1000)
): Promise<StreamResult | null> {
  if (!stream.drm) return stream;

  if (!stream.drm.licenseProxy) {
    const drm = normalizePublicDrm(stream.drm);
    return drm ? { ...stream, drm } : null;
  }

  const proxy = stream.drm.licenseProxy;
  if ((proxy !== "tidal" && proxy !== "amazon") || !drmProxyTarget(env, proxy)) return null;
  const secret = env.DRM_PROXY_SECRET?.trim();
  if (!secret) return null;

  const origin = normalizePublicHttpsUrl(publicOrigin);
  if (!origin) return null;
  const expires = nowSeconds + TOKEN_LIFETIME_SECONDS;
  const context = proxy === "amazon" ? AMAZON_TOKEN_CONTEXT : TOKEN_CONTEXT;
  const token = await signToken(secret, expires, context);
  const license = new URL(proxy === "amazon" ? "/drm/amazon/license" : "/drm/tidal/widevine", origin);
  license.searchParams.set("expires", String(expires));
  license.searchParams.set("token", token);

  return {
    ...stream,
    drm: {
      scheme: "widevine",
      licenseUrl: license.toString(),
      licenseRequestHeaders: stream.drm.licenseRequestHeaders,
      forceDefaultLicenseUri: stream.drm.forceDefaultLicenseUri ?? true,
    },
  };
}

/** Proxy a bounded Widevine challenge body to the configured private Tidal service. */
export async function handleTidalWidevineProxy(
  request: Request,
  env: Env,
  fetcher: Fetcher = fetch,
  nowSeconds = Math.floor(Date.now() / 1000)
): Promise<Response> {
  if (request.method !== "POST") {
    return drmJson({ error: "method_not_allowed" }, 405, { Allow: "POST, OPTIONS" });
  }

  const secret = env.DRM_PROXY_SECRET?.trim();
  const target = drmProxyTarget(env);
  if (!secret || !target) {
    return drmJson({ error: "drm_proxy_unavailable" }, 503);
  }

  const url = new URL(request.url);
  const expires = parseExpiry(url.searchParams.get("expires"));
  const token = url.searchParams.get("token")?.trim() ?? "";
  if (
    expires == null ||
    expires < nowSeconds ||
    expires > nowSeconds + MAX_TOKEN_AGE_SECONDS ||
    !token ||
    !(await verifyToken(secret, expires, token))
  ) {
    return drmJson({ error: "invalid_or_expired_drm_token" }, 401);
  }

  const contentLength = Number(request.headers.get("Content-Length") ?? "0");
  if (Number.isFinite(contentLength) && contentLength > MAX_LICENSE_REQUEST_BYTES) {
    return drmJson({ error: "license_request_too_large" }, 413);
  }

  const headers = new Headers();
  headers.set("Accept", request.headers.get("Accept") || "application/octet-stream");
  headers.set("Content-Type", request.headers.get("Content-Type") || "application/octet-stream");
  headers.set("User-Agent", "VANTA-MusicGateway/2.0");
  if (target.apiKey) {
    headers.set("Authorization", `Bearer ${target.apiKey}`);
    headers.set("X-Api-Key", target.apiKey);
  }

  try {
    const upstream = await fetcher(target.url, {
      method: "POST",
      headers,
      body: request.body,
      signal: AbortSignal.timeout(10_000),
    });
    const responseHeaders = new Headers();
    responseHeaders.set("Content-Type", upstream.headers.get("Content-Type") || "application/octet-stream");
    responseHeaders.set("Cache-Control", "no-store");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  } catch (error) {
    console.error(JSON.stringify({
      message: "tidal Widevine proxy failed",
      error: error instanceof Error ? error.message : String(error),
    }));
    return drmJson({ error: "drm_upstream_unavailable" }, 502);
  }
}

/**
 * Amazon Music license pass-through. Mirrors the Tidal proxy but keeps the
 * per-track `x-amz-music-*` license headers the player received with the
 * manifest, and forwards to the operator-configured Amazon upstream. The
 * upstream must own a registered device/session that the Amazon license
 * service accepts (its key is never derivable server-side).
 */
export async function handleAmazonLicenseProxy(
  request: Request,
  env: Env,
  fetcher: Fetcher = fetch,
  nowSeconds = Math.floor(Date.now() / 1000)
): Promise<Response> {
  if (request.method !== "POST") {
    return drmJson({ error: "method_not_allowed" }, 405, { Allow: "POST, OPTIONS" });
  }

  const secret = env.DRM_PROXY_SECRET?.trim();
  const target = amazonLicenseProxyTarget(env);
  if (!secret || !target) {
    return drmJson({ error: "drm_proxy_unavailable" }, 503);
  }

  const url = new URL(request.url);
  const expires = parseExpiry(url.searchParams.get("expires"));
  const token = url.searchParams.get("token")?.trim() ?? "";
  if (
    expires == null ||
    expires < nowSeconds ||
    expires > nowSeconds + MAX_TOKEN_AGE_SECONDS ||
    !token ||
    !(await verifyToken(secret, expires, token, AMAZON_TOKEN_CONTEXT))
  ) {
    return drmJson({ error: "invalid_or_expired_drm_token" }, 401);
  }

  const contentLength = Number(request.headers.get("Content-Length") ?? "0");
  if (Number.isFinite(contentLength) && contentLength > MAX_LICENSE_REQUEST_BYTES) {
    return drmJson({ error: "license_request_too_large" }, 413);
  }

  const headers = new Headers();
  headers.set("Accept", request.headers.get("Accept") || "application/octet-stream");
  headers.set("Content-Type", request.headers.get("Content-Type") || "application/octet-stream");
  headers.set("User-Agent", "VANTA-MusicGateway/2.0");
  for (const [name, value] of request.headers) {
    const lower = name.toLowerCase();
    if (lower.startsWith("x-amz-music-") || lower === "x-amz-target") {
      headers.set(name, value);
    }
  }
  if (target.apiKey) {
    headers.set("Authorization", `Bearer ${target.apiKey}`);
    headers.set("X-Api-Key", target.apiKey);
  }

  try {
    const upstream = await fetcher(target.url, {
      method: "POST",
      headers,
      body: request.body,
      signal: AbortSignal.timeout(10_000),
    });
    const responseHeaders = new Headers();
    responseHeaders.set("Content-Type", upstream.headers.get("Content-Type") || "application/octet-stream");
    responseHeaders.set("Cache-Control", "no-store");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  } catch (error) {
    console.error(JSON.stringify({
      message: "amazon Widevine proxy failed",
      error: error instanceof Error ? error.message : String(error),
    }));
    return drmJson({ error: "drm_upstream_unavailable" }, 502);
  }
}

function normalizePublicDrm(drm: StreamDrmConfiguration): StreamDrmConfiguration | null {
  if (drm.scheme !== "widevine") return null;
  const licenseUrl = normalizePublicHttpsUrl(drm.licenseUrl);
  if (!licenseUrl) return null;
  return {
    scheme: "widevine",
    licenseUrl,
    forceDefaultLicenseUri: drm.forceDefaultLicenseUri ?? true,
  };
}

function drmProxyTarget(env: Env, proxy: "tidal" | "amazon" = "tidal"): { url: string; apiKey?: string } | null {
  if (proxy === "amazon") return amazonLicenseProxyTarget(env);
  const tidalBase = normalizedBase(env.TIDAL_API_URL);
  if (tidalBase) {
    return {
      url: `${tidalBase}/widevine`,
      apiKey: env.TIDAL_API_KEY?.trim() || undefined,
    };
  }
  const hifiBase = normalizedBase(env.HIFI_API_URL);
  if (hifiBase) {
    return {
      url: `${hifiBase}/widevine`,
      apiKey: env.HIFI_API_KEY?.trim() || undefined,
    };
  }
  const operatorBase = normalizedBase(env.OPERATOR_BACKEND_URL);
  if (!operatorBase) return null;
  return {
    url: `${operatorBase}/v1/drm/tidal/widevine`,
    apiKey: env.OPERATOR_BACKEND_API_KEY?.trim() || undefined,
  };
}

/**
 * Operator-configured Amazon license upstream: explicit AMAZON_DRM_PROXY_URL,
 * else the private backend's `/v1/drm/amazon/license`. Fails open (null) when
 * neither is configured so the gateway never fabricates an unusable URL.
 */
function amazonLicenseProxyTarget(env: Env): { url: string; apiKey?: string } | null {
  const direct = normalizedBase(env.AMAZON_DRM_PROXY_URL);
  if (direct) {
    return {
      url: direct,
      apiKey: env.AMAZON_DRM_PROXY_KEY?.trim() || env.OPERATOR_BACKEND_API_KEY?.trim() || undefined,
    };
  }
  const operatorBase = normalizedBase(env.OPERATOR_BACKEND_URL);
  if (!operatorBase) return null;
  return {
    url: `${operatorBase}/v1/drm/amazon/license`,
    apiKey: env.OPERATOR_BACKEND_API_KEY?.trim() || undefined,
  };
}

function normalizedBase(raw: string | undefined): string | null {
  const safe = normalizePublicHttpsUrl(raw);
  return safe?.replace(/\/+$/, "") ?? null;
}

async function signToken(secret: string, expires: number, context = TOKEN_CONTEXT): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    encoder.encode(`${context}\n${expires}`)
  );
  return base64Url(new Uint8Array(signature));
}

async function verifyToken(secret: string, expires: number, provided: string, context = TOKEN_CONTEXT): Promise<boolean> {
  const expected = await signToken(secret, expires, context);
  const [providedHash, expectedHash] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(provided)),
    crypto.subtle.digest("SHA-256", encoder.encode(expected)),
  ]);
  const subtle = crypto.subtle;
  return subtle.timingSafeEqual?.(providedHash, expectedHash) ?? constantTimeBytes(
    new Uint8Array(providedHash),
    new Uint8Array(expectedHash)
  );
}

function constantTimeBytes(left: Uint8Array, right: Uint8Array): boolean {
  let difference = left.byteLength ^ right.byteLength;
  const length = Math.max(left.byteLength, right.byteLength);
  for (let index = 0; index < length; index += 1) {
    difference |= (left[index] ?? 0) ^ (right[index] ?? 0);
  }
  return difference === 0;
}

function parseExpiry(raw: string | null): number | null {
  if (!raw || !/^\d{10}$/.test(raw)) return null;
  const value = Number(raw);
  return Number.isSafeInteger(value) ? value : null;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function drmJson(body: unknown, status: number, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...extraHeaders,
    },
  });
}
