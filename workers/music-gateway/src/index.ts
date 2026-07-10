import { buildManifest } from "./manifest";
import {
  badRequest,
  extractTrackId,
  handleOptions,
  json,
  normalizeQuality,
  notFound,
  providerFromQuery,
  readJson,
  serviceUnavailable,
  unauthorized,
} from "./http";
import { checkRateLimit } from "./lib/rate-limit";
import { checkProviderHealth } from "./lib/health";
import { getCachedStream, putCachedStream, streamCacheKey, streamCacheTtl } from "./lib/cache";
import { getMetrics, incrementErrors, incrementRateLimited, incrementRequests, incrementRoute, resetMetrics } from "./lib/metrics";
import { searchAll } from "./providers/search";
import { resolveTrack } from "./providers/resolve";
import { streamWithFallback } from "./providers/stream";
import { providerStatus } from "./providers/shared";
import { handleSyncRoute } from "./sync/routes";
import { authenticateSyncRequest, isDevelopment } from "./auth";
import type { SyncIdentity } from "./auth";
import { parseProviderList } from "./types";
import type { Env, StreamResult } from "./types";

function isAuthorized(request: Request, env: Env): boolean {
  const required = env.GATEWAY_API_KEY?.trim();
  if (!required) return true;
  return request.headers.get("X-Api-Key") === required;
}

function requiresGatewayAuth(pathname: string): boolean {
  return pathname !== "/" && pathname !== "/health";
}

function requestId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

interface StructuredError {
  error: string;
  message: string;
  retryable: boolean;
  requestId: string;
}

function errorResponse(
  code: string,
  message: string,
  status: number,
  requestId: string,
  retryable = false,
  extraHeaders: Record<string, string> = {}
): Response {
  const body: StructuredError = { error: code, message, retryable, requestId };
  return json(body, status, extraHeaders);
}

async function resolveStreamWithCache(
  env: Env,
  trackId: string,
  quality: string,
  provider: string | undefined,
  logBase: Record<string, unknown>
): Promise<StreamResult | null> {
  const key = streamCacheKey(trackId, provider, quality);
  const cached = await getCachedStream(env, key);
  if (cached) {
    console.log("VANTA_STREAM_CACHE_HIT", JSON.stringify({ ...logBase, id: trackId, service: provider ?? "auto", quality }));
    return cached;
  }
  try {
    const stream = await streamWithFallback(env, trackId, quality, provider, { provider });
    if (stream) {
      await putCachedStream(env, key, stream, streamCacheTtl(env));
    }
    return stream;
  } catch (err) {
    incrementErrors();
    console.error(
      "VANTA_STREAM_RESOLVE_ERROR",
      JSON.stringify({
        ...logBase,
        id: trackId,
        service: provider ?? "auto",
        quality,
        error: err instanceof Error ? err.message : String(err),
      })
    );
    return null;
  }
}

function isStreamExpired(stream: StreamResult): boolean {
  if (!stream.expiresAt) return false;
  // expiresAt may be seconds or milliseconds; treat values before year 3000 as seconds.
  const threshold = 32_000_000_000;
  const expiresMs = stream.expiresAt > threshold ? stream.expiresAt : stream.expiresAt * 1000;
  return expiresMs < Date.now();
}

export async function handleRequest(request: Request, env: Env, requestIdValue?: string): Promise<Response> {
  if (request.method === "OPTIONS") return handleOptions();

  const id = requestIdValue ?? requestId();
  let url: URL;
  try {
    url = new URL(request.url);
  } catch (err) {
    return errorResponse(
      "bad_request",
      "Invalid request URL.",
      400,
      id,
      false,
      { "X-Request-Id": id }
    );
  }
  const pathname = url.pathname.replace(/\/+$/, "") || "/";
  const isSyncRoute = pathname.startsWith("/sync/");
  let syncIdentity: SyncIdentity | undefined;

  if (isSyncRoute) {
    const authentication = await authenticateSyncRequest(request, env);
    if (!authentication.ok) return errorResponse(authentication.error, "Sync authentication is required.", authentication.status, id);
    syncIdentity = authentication.identity;
  } else if (requiresGatewayAuth(pathname)) {
    // Free/no-config mode: no GATEWAY_API_KEY required. If a key is configured, it is still enforced.
    if (!isAuthorized(request, env)) return unauthorized();
  }
  const rate = await checkRateLimit(request, env);
  const rateHeaders = {
    "X-RateLimit-Limit": String(rate.state.remaining + (rate.allowed ? 1 : 0)),
    "X-RateLimit-Remaining": String(rate.state.remaining),
    "X-RateLimit-Reset": String(rate.state.resetAt),
    "X-Request-Id": id,
  };

  if (!rate.allowed) {
    if (rate.storageUnavailable) {
      return errorResponse(
        "rate_limit_unavailable",
        "Rate-limit storage is unavailable.",
        503,
        id,
        true,
        rateHeaders
      );
    }
    incrementRateLimited();
    return errorResponse(
      "rate_limited",
      "Too many requests. Slow down or upgrade your plan.",
      429,
      id,
      true,
      rateHeaders
    );
  }

  incrementRequests();

  const logBase = {
    req: id,
    route: pathname,
    method: request.method,
    ip: rate.key,
  };

  if (pathname === "/" || pathname === "/health") {
    const health = await checkProviderHealth(env);
    return json(
      {
        ok: true,
        gateway: env.GATEWAY_NAME,
        version: env.GATEWAY_VERSION,
        health: health.filter((h) => h.healthy).length === health.length ? "ok" : "degraded",
        providers: health,
      },
      200,
      rateHeaders
    );
  }

  if (pathname === "/status") {
    const health = await checkProviderHealth(env);
    return json(
      {
        gateway: env.GATEWAY_NAME,
        version: env.GATEWAY_VERSION,
        searchProviders: parseProviderList(env.ENABLED_SEARCH_PROVIDERS),
        streamProviders: parseProviderList(env.ENABLED_STREAM_PROVIDERS),
        providers: providerStatus(env),
        health,
      },
      200,
      rateHeaders
    );
  }

  if (pathname === "/manifest.json") {
    return json(buildManifest(env), 200, rateHeaders);
  }

  if (pathname === "/search" || pathname === "/api/search") {
    const query = url.searchParams.get("q") ?? url.searchParams.get("query") ?? "";
    if (!query.trim()) return withHeaders(badRequest("missing query parameter q"), rateHeaders);
    incrementRoute("search");
    console.log("VANTA_SEARCH_REQUEST", JSON.stringify({ ...logBase, query }));
    return json(await searchAll(query.trim(), env), 200, rateHeaders);
  }

  if (pathname === "/resolve" || pathname === "/api/resolve") {
    const targetUrl = url.searchParams.get("url");
    const trackId = url.searchParams.get("id") ?? url.searchParams.get("trackId");
    const provider = url.searchParams.get("provider") ?? undefined;
    if (!targetUrl?.trim() && !trackId?.trim()) {
      return errorResponse(
        "missing_parameters",
        "Provide either url or id/trackId to resolve.",
        400,
        id,
        false,
        rateHeaders
      );
    }
    incrementRoute("resolve");
    console.log(
      "VANTA_RESOLVE_REQUEST",
      JSON.stringify({ ...logBase, id: trackId ?? null, provider: provider ?? null, url: targetUrl ?? null })
    );
    try {
      const result = await resolveTrack({
        url: targetUrl ?? undefined,
        trackId: trackId ?? undefined,
        provider,
        env,
      });
      return json(result, 200, rateHeaders);
    } catch (error) {
      incrementErrors();
      const message = error instanceof Error ? error.message : "resolve_failed";
      return errorResponse(
        "resolve_failed",
        message,
        400,
        id,
        false,
        rateHeaders
      );
    }
  }

  const streamId = extractTrackId(pathname);
  if (streamId && request.method === "GET") {
    const quality = normalizeQuality(url.searchParams.get("quality"), env.DEFAULT_STREAM_QUALITY);
    const provider = providerFromQuery(url);
    incrementRoute("stream");
    console.log(
      "VANTA_PLAY_TRACK_REQUEST",
      JSON.stringify({ ...logBase, id: streamId, service: provider ?? "auto", quality })
    );
    const stream = await resolveStreamWithCache(env, streamId, quality, provider, logBase);
    if (!stream?.url) {
      return errorResponse(
        "no_stream_source",
        "No stream source succeeded. Set QOBUZ_APP_ID+QOBUZ_AUTH_TOKEN, TIDAL_STREAM_UPSTREAM, MUSICDL_BASE_URL, or other upstream secrets. Check GET /status.",
        503,
        id,
        true,
        rateHeaders
      );
    }
    if (isStreamExpired(stream)) {
      return errorResponse(
        "stream_expired",
        "Resolved stream URL is expired. Retry or request a fresh resolve.",
        410,
        id,
        true,
        rateHeaders
      );
    }
    return new Response(null, {
      status: 302,
      headers: {
        Location: stream.url,
        ...rateHeaders,
      },
    });
  }

  if (pathname === "/play" || pathname === "/api/play") {
    const targetUrl = url.searchParams.get("url");
    const trackId = url.searchParams.get("id") ?? url.searchParams.get("trackId");
    const provider = providerFromQuery(url);
    const quality = normalizeQuality(url.searchParams.get("quality"), env.DEFAULT_STREAM_QUALITY);
    if (!targetUrl?.trim() && !trackId?.trim()) {
      return errorResponse(
        "missing_parameters",
        "Provide either url or id/trackId to play.",
        400,
        id,
        false,
        rateHeaders
      );
    }
    incrementRoute("play");
    console.log(
      "VANTA_PLAY_REDIRECT_REQUEST",
      JSON.stringify({ ...logBase, id: trackId ?? null, provider: provider ?? null, url: targetUrl ?? null, quality })
    );
    const idToStream = targetUrl?.trim()
      ? await resolveAndExtractId(targetUrl.trim(), provider, env)
      : trackId!.trim();
    if (!idToStream) {
      return errorResponse(
        "resolve_failed",
        "Could not extract a playable track id from the provided URL.",
        400,
        id,
        false,
        rateHeaders
      );
    }
    const stream = await resolveStreamWithCache(env, idToStream, quality, provider, logBase);
    if (!stream?.url) {
      return errorResponse(
        "no_stream_source",
        "No stream source succeeded for /play. Check GET /status.",
        503,
        id,
        true,
        rateHeaders
      );
    }
    return new Response(null, {
      status: 302,
      headers: {
        Location: stream.url,
        ...rateHeaders,
      },
    });
  }

  if (pathname === "/api/dl" && request.method === "POST") {
    const body = await readJson<{ id?: string; quality?: string; service?: string; provider?: string }>(request);
    if (!body?.id?.trim()) return withHeaders(badRequest("missing id"), rateHeaders);
    const quality = normalizeQuality(body.quality, env.DEFAULT_STREAM_QUALITY);
    const provider = providerFromQuery(url, body.service ?? body.provider);
    incrementRoute("download");
    console.log(
      "VANTA_PLAY_TRACK_REQUEST",
      JSON.stringify({ ...logBase, id: body.id.trim(), service: provider ?? "auto", quality })
    );
    const stream = await resolveStreamWithCache(env, body.id.trim(), quality, provider, logBase);
    if (!stream) {
      return errorResponse(
        "no_stream_source",
        "No stream source succeeded for /api/dl. Check GET /status.",
        503,
        id,
        true,
        rateHeaders
      );
    }
    if (isStreamExpired(stream)) {
      return errorResponse(
        "stream_expired",
        "Resolved stream URL is expired. Retry or request a fresh resolve.",
        410,
        id,
        true,
        rateHeaders
      );
    }
    return json(stream, 200, rateHeaders);
  }

  if (pathname.startsWith("/search/")) {
    let query: string;
    try {
      query = decodeURIComponent(pathname.slice("/search/".length));
    } catch (err) {
      return errorResponse(
        "bad_request",
        "Invalid search path encoding.",
        400,
        id,
        false,
        rateHeaders
      );
    }
    if (!query.trim()) return withHeaders(badRequest("missing search path"), rateHeaders);
    incrementRoute("search");
    console.log("VANTA_SEARCH_REQUEST", JSON.stringify({ ...logBase, query }));
    return json(await searchAll(query.trim(), env), 200, rateHeaders);
  }

  let syncResponse: Response | null;
  try {
    syncResponse = await handleSyncRoute(request, env, pathname, rateHeaders, syncIdentity);
  } catch (err) {
    incrementErrors();
    console.error(
      "VANTA_SYNC_ROUTE_ERROR",
      JSON.stringify({ ...logBase, error: err instanceof Error ? err.message : String(err) })
    );
    syncResponse = errorResponse(
      "sync_unavailable",
      "Sync is temporarily unavailable. Retry later.",
      503,
      id,
      true,
      rateHeaders
    );
  }
  if (syncResponse) {
    incrementRoute("sync");
    console.log("VANTA_SYNC_REQUEST", JSON.stringify({ ...logBase, resource: pathname }));
    return syncResponse;
  }

  return withHeaders(notFound(), rateHeaders);
}

async function resolveAndExtractId(
  targetUrl: string,
  provider: string | undefined,
  env: Env
): Promise<string | null> {
  try {
    const resolved = await resolveTrack({ url: targetUrl, provider, env });
    return (
      resolved.tidal_id ??
      resolved.qobuz_id ??
      resolved.deezer_id ??
      resolved.amazon_id ??
      resolved.apple_id ??
      null
    );
  } catch {
    return null;
  }
}

function withHeaders(response: Response, headers: Record<string, string>): Response {
  for (const [key, value] of Object.entries(headers)) {
    response.headers.set(key, value);
  }
  return response;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const id = requestId();
    try {
      return await handleRequest(request, env, id);
    } catch (error) {
      incrementErrors();
      console.error(
        "gateway_error",
        JSON.stringify({ requestId: id, error: error instanceof Error ? error.message : String(error) })
      );
      return json(
        {
          error: "internal_error",
          message: error instanceof Error ? error.message : "unknown",
          requestId: id,
        },
        500,
        { "X-Request-Id": id }
      );
    }
  },
};
