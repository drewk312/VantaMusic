import { buildManifest } from "./manifest";
import {
  badRequest,
  extractTrackId,
  handleOptions,
  json,
  normalizeQuality,
  normalizeSupportedTrackUrl,
  notFound,
  providerFromQuery,
  readJson,
  serviceUnavailable,
  unauthorized,
} from "./http";
import { checkRateLimit } from "./lib/rate-limit";
import { checkProviderHealth } from "./lib/health";
import { getCachedStream, putCachedStream, streamCacheKey, streamCacheTtl } from "./lib/cache";
import { deriveSpatialFormat } from "./lib/stream-quality";
import { getMetrics, incrementErrors, incrementRateLimited, incrementRequests, incrementRoute, resetMetrics } from "./lib/metrics";
import { searchAll } from "./providers/search";
import { resolveTrack } from "./providers/resolve";
import { getEditorialNewReleases } from "./providers/releases";
import { appleEditorialRows, applePlaylistTracks } from "./providers/apple-editorial";
import { buildHomeFeed } from "./providers/home-feed";
import { importSpotifyPlaylist, spotifyEditorialPlaylists } from "./providers/spotify-web";
import { acceptStreamForRequestedQuality, streamWithFallback } from "./providers/stream";
import { resolveTidalMusicVideo } from "./providers/tidal-api";
import { providerStatus } from "./providers/shared";
import { getDeviceLibrary, putDeviceLibrary, isValidPairCode } from "./lib/device-library-sync";
import { zarzBreakerSnapshot } from "./providers/zarz-health";
import { handleSyncRoute } from "./sync/routes";
import { authenticateSyncRequest, isDevelopment } from "./auth";
import type { SyncIdentity } from "./auth";
import { parseProviderList, ALL_PROVIDER_IDS } from "./types";
import type { Env, StreamResult } from "./types";
import { gatewayPage } from "./gateway-page";
import { routeExtensionAudio } from "./extensions/audio-state";
import { missingStreamFailure } from "./lib/stream-failure";
import { withProviderFailures } from "./lib/provider-failures";
import { handleAppRelease } from "./app-release";
import { gatewaySessionHealth, logSessionExpiryWarnings } from "./providers/community-session-health";
import { checkNextServicesHealth, nextShardTelemetry, nextLicenseBlockSnapshot } from "./providers/community-next";
import { handleAmazonLicenseProxy, handleTidalWidevineProxy, streamForClient } from "./lib/drm-proxy";

async function equalSecret(a: string, b: string): Promise<boolean> {
  const hash = async (value: string) => new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
  const [left, right] = await Promise.all([hash(a), hash(b)]);
  let difference = 0;
  for (let i = 0; i < left.length; i++) difference |= left[i] ^ right[i];
  return difference === 0;
}

function isAuthorized(request: Request, env: Env): boolean {
  const required = env.GATEWAY_API_KEY?.trim();
  if (!required) return true;
  return request.headers.get("X-Api-Key") === required;
}

function requiresGatewayAuth(pathname: string): boolean {
  return (
    pathname !== "/" &&
    pathname !== "/health" &&
    // DRM license routes are public by design: ExoPlayer's Widevine license
    // fetch cannot attach the gateway key, and every request is already gated
    // by the short-lived HMAC token minted per stream via streamForClient.
    pathname !== "/drm/amazon/license" &&
    pathname !== "/drm/tidal/widevine"
  );
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
  logBase: Record<string, unknown>,
  request?: Request
): Promise<StreamResult | null> {
  const key = streamCacheKey(trackId, provider, quality);
  const cached = await getCachedStream(env, key);
  if (cached && acceptStreamForRequestedQuality(cached, quality)) {
    console.log("VANTA_STREAM_CACHE_HIT", JSON.stringify({ ...logBase, id: trackId, service: provider ?? "auto", quality }));
    return ensureSpatialFormat(cached);
  }
  try {
    const stream = await streamWithFallback(env, trackId, quality, provider, { provider }, request);
    if (stream) {
      await putCachedStream(env, key, ensureSpatialFormat(stream), streamCacheTtl(env));
    }
    return stream ? ensureSpatialFormat(stream) : null;
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

/** Stamp the explicit spatial format derived from proven stream signals. */
function ensureSpatialFormat(stream: StreamResult): StreamResult {
  if (stream.spatialFormat) return stream;
  return { ...stream, spatialFormat: deriveSpatialFormat(stream) };
}

/**
 * Turn an internal DRM license-proxy marker into a short-lived signed public
 * license URL for the requesting client. Returns the stream unchanged for
 * plain (non-proxy) streams so 302 redirects keep working.
 */
async function publicStreamForClient(stream: StreamResult, request: Request, env: Env): Promise<StreamResult | null> {
  if (!stream.drm?.licenseProxy) return stream;
  return streamForClient(stream, new URL(request.url).origin, env, Math.floor(Date.now() / 1000));
}

function isStreamExpired(stream: StreamResult): boolean {
  if (!stream.expiresAt) return false;
  // expiresAt may be seconds or milliseconds; treat values before year 3000 as seconds.
  const threshold = 32_000_000_000;
  const expiresMs = stream.expiresAt > threshold ? stream.expiresAt : stream.expiresAt * 1000;
  return expiresMs < Date.now();
}

export async function handleRequest(request: Request, env: Env, requestIdValue?: string): Promise<Response> {
  return withProviderFailures(() => handleRequestWithFailures(request, env, requestIdValue));
}

async function handleRequestWithFailures(request: Request, env: Env, requestIdValue?: string): Promise<Response> {
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
  env = { ...env, GATEWAY_BASE_URL: url.origin };
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
  const rateHeaders: Record<string, string> = {
    "X-RateLimit-Limit": String(rate.limit),
    "X-RateLimit-Reset": String(rate.state.resetAt),
    "X-Request-Id": id,
  };
  if (rate.state.remaining !== null) {
    rateHeaders["X-RateLimit-Remaining"] = String(rate.state.remaining);
  }

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

  if (pathname === "/audio/extension") return routeExtensionAudio(request, env);

  if (pathname === "/drm/amazon/license") return handleAmazonLicenseProxy(request, env);
  if (pathname === "/drm/tidal/widevine") return handleTidalWidevineProxy(request, env);

  const appRelease = await handleAppRelease(request, env, pathname);
  if (appRelease) return appRelease;

  if (pathname === "/") {
    const health = await checkProviderHealth(env);
    const response = gatewayPage(env.GATEWAY_NAME || "VANTA Music Gateway", env.GATEWAY_VERSION || "", health);
    for (const [key, value] of Object.entries(rateHeaders)) response.headers.set(key, value);
    return response;
  }

  if (pathname === "/health") {
    const health = await checkProviderHealth(env);
    const sessions = gatewaySessionHealth(env);
    const next = await checkNextServicesHealth(env);
    const sessionCliff = sessions.some((s) => s.status === "expired" || s.status === "expiring");
    if (sessionCliff) logSessionExpiryWarnings(env);
    const nextDegraded = next.total > 0 && next.online < next.total;
    return json(
      {
        ok: true,
        gateway: env.GATEWAY_NAME,
        version: env.GATEWAY_VERSION,
        health: health.filter((h) => h.healthy).length === health.length && !sessionCliff && !nextDegraded ? "ok" : "degraded",
        providers: health,
        sessions,
        next: {
          total: next.total,
          online: next.online,
          offline: next.offline,
          services: next.services.map((s) => ({
            host: s.host,
            provider: s.provider,
            online: s.online,
            region: s.region,
            qualities: s.qualities,
            latencyMs: s.latencyMs,
          })),
        },
      },
      200,
      rateHeaders
    );
  }

  if (pathname === "/device-sync/library" || pathname === "/api/device-sync/library") {
    incrementRoute("sync");
    if (request.method === "GET") {
      const code = url.searchParams.get("code") ?? url.searchParams.get("pairCode") ?? "";
      if (!isValidPairCode(code)) {
        return withHeaders(badRequest("pair code must be 4-8 letters/numbers"), rateHeaders);
      }
      const library = await getDeviceLibrary(env, code);
      if (!library) {
        return errorResponse("library_not_found", "No library for that code yet. Push from your phone first.", 404, id, true, rateHeaders);
      }
      return json(library, 200, rateHeaders);
    }
    if (request.method === "POST") {
      const body = await readJson<{
        pairCode?: string;
        deviceName?: string;
        tracks?: unknown;
      }>(request);
      const pairCode = body?.pairCode ?? "";
      if (!isValidPairCode(pairCode)) {
        return withHeaders(badRequest("pair code must be 4-8 letters/numbers"), rateHeaders);
      }
      if (!Array.isArray(body?.tracks)) {
        return withHeaders(badRequest("tracks array required"), rateHeaders);
      }
      const saved = await putDeviceLibrary(env, {
        pairCode,
        deviceName: body?.deviceName ?? "VANTA",
        updatedAtMs: Date.now(),
        tracks: body.tracks as [],
      });
      if (!saved) {
        return errorResponse("library_save_failed", "Could not save device library.", 503, id, true, rateHeaders);
      }
      return json({ ok: true, pairCode: saved.pairCode, trackCount: saved.tracks.length, updatedAtMs: saved.updatedAtMs }, 200, rateHeaders);
    }
    return withHeaders(badRequest("method_not_allowed"), rateHeaders);
  }

  if (pathname === "/status" || pathname === "/admin/status") {
    const diagnosticKey = env.GATEWAY_STATUS_KEY?.trim() || env.GATEWAY_API_KEY?.trim();
    const suppliedKey = request.headers.get("X-Status-Key") || request.headers.get("X-Api-Key");
    const detailed = Boolean(diagnosticKey && suppliedKey && await equalSecret(suppliedKey, diagnosticKey));
    if (pathname === "/admin/status" && !detailed) return unauthorized();
    const health = await checkProviderHealth(env);
    const sessions = gatewaySessionHealth(env);
    const next = await checkNextServicesHealth(env);
    return json(
      {
        gateway: env.GATEWAY_NAME,
        version: env.GATEWAY_VERSION,
        searchProviders: parseProviderList(env.ENABLED_SEARCH_PROVIDERS),
        streamProviders: parseProviderList(env.ENABLED_STREAM_PROVIDERS),
        providers: providerStatus(env),
        health,
        sessions,
        next: { total: next.total, online: next.online, offline: next.offline, services: next.services },
        ...(detailed ? {
          sessionRenewal: env.EXTENSION_SESSIONS ? Object.fromEntries(await Promise.all(
            (["amazon", "deezer", "qobuz", "tidal"] as const).map(async (provider) => [provider,
              await env.EXTENSION_SESSIONS!.getByName(provider).health().catch(() => ({ status: "unavailable" }))])
          )) : {},
          playbackBreakers: zarzBreakerSnapshot(),
          nextShardTelemetry: nextShardTelemetry(),
          licenseBlocks: nextLicenseBlockSnapshot(),
        } : {}),
      },
      200,
      { ...rateHeaders, "Cache-Control": "private, no-store", "Vary": "X-Status-Key, X-Api-Key" }
    );
  }

  if (pathname === "/manifest.json") {
    return json(buildManifest(env), 200, rateHeaders);
  }

  if (pathname === "/manifest/mpd" || pathname === "/api/manifest/mpd") {
    const rawData = url.searchParams.get("data") ?? url.searchParams.get("d");
    if (!rawData) {
      return errorResponse("missing_data", "Missing manifest data query parameter.", 400, id, false, rateHeaders);
    }
    try {
      const normalizedB64 = rawData.replace(/-/g, "+").replace(/_/g, "/");
      const xml = atob(normalizedB64);
      return new Response(xml, {
        status: 200,
        headers: {
          "Content-Type": "application/dash+xml; charset=utf-8",
          "Access-Control-Allow-Origin": "*",
          "Cache-Control": "public, max-age=3600",
          ...rateHeaders,
        },
      });
    } catch {
      return errorResponse("invalid_manifest", "Could not decode manifest data.", 400, id, false, rateHeaders);
    }
  }

  if (pathname === "/api/editorial") {
    const { activeEditorial } = await import("./providers/editorial");
    return json({ entries: activeEditorial(), updatedAt: new Date().toISOString() }, 200, rateHeaders);
  }

  if (pathname.startsWith("/apple/playlist/") && (pathname.endsWith("/tracks") || pathname.endsWith("/tracks/"))) {
    incrementRoute("apple_editorial");
    const playlistId = url.pathname.split("/")[3] ?? "";
    const limit = Number.parseInt(url.searchParams.get("limit") || "100", 10);
    const storefront = url.searchParams.get("storefront") ?? "us";
    try {
      const tracks = await applePlaylistTracks(storefront, playlistId, Number.isFinite(limit) ? limit : 100);
      return json({ playlistId, storefront, tracks }, 200, rateHeaders);
    } catch {
      return json({ playlistId, storefront, tracks: [] }, 200, rateHeaders);
    }
  }

  if (pathname === "/home" || pathname === "/api/home") {
    incrementRoute("new_releases");
    const limit = Number.parseInt(url.searchParams.get("limit") || "12", 10);
    try {
      const feed = await buildHomeFeed(Number.isFinite(limit) ? limit : 12, env);
      return json(feed, 200, rateHeaders);
    } catch {
      return json({ updatedAt: new Date().toISOString(), storefront: "us", playlists: [], freshDrops: [], popularTracks: [], trendingNow: [] }, 200, rateHeaders);
    }
  }

  if (pathname === "/spotify/editorial" || pathname === "/api/spotify/editorial") {
    incrementRoute("apple_editorial");
    const limit = Number.parseInt(url.searchParams.get("limit") || "12", 10);
    try {
      const cards = await spotifyEditorialPlaylists(env, Number.isFinite(limit) ? limit : 12);
      return json({ source: "spotify", playlists: cards, updatedAt: new Date().toISOString() }, 200, rateHeaders);
    } catch {
      return json({ source: "spotify", playlists: [], updatedAt: new Date().toISOString() }, 200, rateHeaders);
    }
  }

  if (pathname.startsWith("/spotify/playlist/") && (pathname.endsWith("/tracks") || pathname.endsWith("/tracks/"))) {
    incrementRoute("apple_editorial");
    const playlistId = url.pathname.split("/")[3] ?? "";
    const limit = Number.parseInt(url.searchParams.get("limit") || "1000", 10);
    try {
      const tracks = await importSpotifyPlaylist(playlistId, env, Number.isFinite(limit) ? limit : 1000);
      return json({ playlistId, source: "spotify", tracks }, 200, rateHeaders);
    } catch {
      return json({ playlistId, source: "spotify", tracks: [] }, 200, rateHeaders);
    }
  }

  if (pathname === "/apple/editorial" || pathname === "/api/apple/editorial") {
    incrementRoute("apple_editorial");
    const limit = Number.parseInt(url.searchParams.get("limit") || "12", 10);
    const storefront = url.searchParams.get("storefront") ?? undefined;
    try {
      const rows = await appleEditorialRows(storefront, Number.isFinite(limit) ? limit : 12);
      return json(
        rows
          ? { ...rows, updatedAt: new Date().toISOString() }
          : { storefront: storefront ?? "us", playlists: [], freshDrops: [], popularTracks: [], updatedAt: new Date().toISOString() },
        200,
        rateHeaders
      );
    } catch {
      return json({ storefront: storefront ?? "us", playlists: [], freshDrops: [], popularTracks: [], updatedAt: new Date().toISOString() }, 200, rateHeaders);
    }
  }

  if (pathname === "/new-releases" || pathname === "/api/new-releases") {
    const limit = Number.parseInt(url.searchParams.get("limit") || "18", 10);
    incrementRoute("new_releases");
    try {      const tracks = await getEditorialNewReleases(limit);
      return json({ tracks }, 200, rateHeaders);
    } catch {
      return json({ tracks: [] }, 200, rateHeaders);
    }
  }

  if (pathname === "/video" || pathname === "/api/video") {
    const query = (url.searchParams.get("q") ?? url.searchParams.get("query") ?? "").trim();
    if (!query) return withHeaders(badRequest("missing query parameter q"), rateHeaders);
    const provider = (providerFromQuery(url) ?? "tidal").toLowerCase();
    incrementRoute("stream");
    if (provider !== "tidal") {
      return errorResponse(
        "unsupported_video_provider",
        "Only provider=tidal music videos are supported (Qobuz has no native MV API).",
        400,
        id,
        false,
        rateHeaders
      );
    }
    const stream = await resolveTidalMusicVideo(env, query, request);
    if (!stream?.url) {
      return errorResponse(
        "video_unavailable",
        "No Tidal music video found for that query.",
        404,
        id,
        true,
        rateHeaders
      );
    }
    return json(
      {
        url: stream.url,
        streamUrl: stream.streamUrl ?? stream.url,
        mimeType: stream.mimeType,
        quality: stream.quality,
        format: stream.format ?? "video",
        provider: stream.provider ?? "tidal",
        bitrateKbps: 0,
      },
      200,
      rateHeaders
    );
  }

  if (pathname === "/search" || pathname === "/api/search") {
    const query = url.searchParams.get("q") ?? url.searchParams.get("query") ?? "";
    if (!query.trim()) return withHeaders(badRequest("missing query parameter q"), rateHeaders);
    const selectedProvider = providerFromQuery(url);
    if (selectedProvider && !ALL_PROVIDER_IDS.some((provider) => provider === selectedProvider)) {
      return withHeaders(badRequest("unsupported search provider"), rateHeaders);
    }
    incrementRoute("search");
    console.log("VANTA_SEARCH_REQUEST", JSON.stringify({ ...logBase, query }));
    const searchEnv = selectedProvider ? { ...env, ENABLED_SEARCH_PROVIDERS: selectedProvider } : env;
    return json(await searchAll(query.trim(), searchEnv), 200, rateHeaders);
  }

  if (pathname === "/resolve" || pathname === "/api/resolve") {
    const rawTargetUrl = url.searchParams.get("url");
    const targetUrl = rawTargetUrl ? normalizeSupportedTrackUrl(rawTargetUrl) : null;
    const trackId = url.searchParams.get("id") ?? url.searchParams.get("trackId");
    const provider = url.searchParams.get("provider") ?? undefined;
    if (rawTargetUrl && !targetUrl) {
      return errorResponse("unsupported_url", "Only supported HTTPS music links are accepted.", 400, id, false, rateHeaders);
    }
    if (!targetUrl && !trackId?.trim()) {
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
    const stream = await resolveStreamWithCache(env, streamId, quality, provider, logBase, request);
    if (!stream?.url) {
      const failure = await missingStreamFailure(quality, env, provider, request);
      return errorResponse(
        failure.code,
        failure.message,
        failure.status,
        id,
        failure.retryable,
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
    const clientStream = await publicStreamForClient(stream, request, env);
    if (clientStream && clientStream !== stream && clientStream.drm?.licenseUrl) {
      // DRM'd full-URL stream: the client needs drm/licenseUrl to play, so
      // return JSON instead of a bare redirect to the locked manifest.
      incrementRoute("drm_license");
      return json({ ...clientStream, success: true, streamUrl: clientStream.url }, 200, rateHeaders);
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
    const stream = await resolveStreamWithCache(env, idToStream, quality, provider, logBase, request);
    if (!stream?.url) {
      const failure = await missingStreamFailure(quality, env, provider, request);
      return errorResponse(
        failure.code,
        failure.message,
        failure.status,
        id,
        failure.retryable,
        rateHeaders
      );
    }
    const clientStream = await publicStreamForClient(stream, request, env);
    if (clientStream && clientStream !== stream && clientStream.drm?.licenseUrl) {
      incrementRoute("drm_license");
      return json({ ...clientStream, success: true, streamUrl: clientStream.url }, 200, rateHeaders);
    }
    return new Response(null, {
      status: 302,
      headers: {
        Location: stream.url,
        ...rateHeaders,
      },
    });
  }

  if ((pathname === "/api/dl" || pathname === "/dl") && request.method === "POST") {
    let body: Record<string, unknown> = {};
    try {
      body = (await request.json()) as Record<string, unknown>;
    } catch {
      return errorResponse("bad_request", "Invalid JSON payload", 400, id, false, rateHeaders);
    }
    const trackId = typeof body.id === "string" ? body.id : "";
    if (!trackId.trim()) {
      return errorResponse("missing_id", "Missing track id in request body.", 400, id, false, rateHeaders);
    }
    const rawQuality = typeof body.quality === "string" ? body.quality : env.DEFAULT_STREAM_QUALITY;
    const quality = normalizeQuality(rawQuality, env.DEFAULT_STREAM_QUALITY);
    const service = typeof body.service === "string" ? body.service : (typeof body.provider === "string" ? body.provider : undefined);
    incrementRoute("stream");
    console.log(
      "VANTA_PLAY_TRACK_REQUEST",
      JSON.stringify({ ...logBase, route: "/api/dl", id: trackId, service: service ?? "auto", quality })
    );
    const stream = await resolveStreamWithCache(env, trackId, quality, service, logBase, request);
    if (!stream?.url) {
      const failure = await missingStreamFailure(quality, env, service, request);
      return errorResponse(
        failure.code,
        failure.message,
        failure.status,
        id,
        failure.retryable,
        rateHeaders
      );
    }
    const clientStream = await publicStreamForClient(stream, request, env);
    if (!clientStream) {
      const failure = await missingStreamFailure(quality, env, service, request);
      return errorResponse(
        failure.code,
        failure.message,
        failure.status,
        id,
        failure.retryable,
        rateHeaders
      );
    }
    return json({ ...clientStream, success: true, streamUrl: clientStream.url }, 200, rateHeaders);
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
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    // Desktop COMMUNITY has no silent refresh — warn before the CAPTCHA cliff.
    logSessionExpiryWarnings(env);
    if (!env.EXTENSION_SESSIONS) return;
    for (const provider of ["amazon", "deezer", "qobuz", "tidal"] as const) {
      try {
        const stub = env.EXTENSION_SESSIONS.getByName(provider);
        const health = await stub.health().catch(() => null) as
          { status?: string; nextAttemptAt?: number } | null;
        // Force when the idle/expiry alarm is due so long grants keep sliding.
        const force = Boolean(health?.nextAttemptAt && health.nextAttemptAt <= Date.now());
        await stub.getSession(provider, force);
        console.log("VANTA_SESSION_MAINTENANCE", JSON.stringify({ provider, force, ...await stub.health() }));
      } catch { console.warn("VANTA_SESSION_MAINTENANCE", JSON.stringify({ provider, status: "retry_next_run" })); }
    }
  },
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
