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
import { searchAll } from "./providers/search";
import { resolveTrack } from "./providers/resolve";
import { streamWithFallback } from "./providers/stream";
import { providerStatus } from "./providers/shared";
import { parseProviderList } from "./types";
import type { Env } from "./types";

function isAuthorized(request: Request, env: Env): boolean {
  const required = env.GATEWAY_API_KEY?.trim();
  if (!required) return true;
  return request.headers.get("X-Api-Key") === required;
}

export async function handleRequest(request: Request, env: Env): Promise<Response> {
  if (request.method === "OPTIONS") return handleOptions();
  if (!isAuthorized(request, env)) return unauthorized();

  const url = new URL(request.url);
  const pathname = url.pathname.replace(/\/+$/, "") || "/";

  if (pathname === "/" || pathname === "/health") {
    return json({ ok: true, gateway: env.GATEWAY_NAME, version: env.GATEWAY_VERSION });
  }

  if (pathname === "/status") {
    return json({
      gateway: env.GATEWAY_NAME,
      version: env.GATEWAY_VERSION,
      searchProviders: parseProviderList(env.ENABLED_SEARCH_PROVIDERS),
      streamProviders: parseProviderList(env.ENABLED_STREAM_PROVIDERS),
      providers: providerStatus(env),
    });
  }

  if (pathname === "/manifest.json") {
    return json(buildManifest(env));
  }

  if (pathname === "/search" || pathname === "/api/search") {
    const query = url.searchParams.get("q") ?? url.searchParams.get("query") ?? "";
    if (!query.trim()) return badRequest("missing query parameter q");
    return json(await searchAll(query.trim(), env));
  }

  if (pathname === "/resolve" || pathname === "/api/resolve") {
    const targetUrl = url.searchParams.get("url");
    const trackId = url.searchParams.get("id") ?? url.searchParams.get("trackId");
    const provider = url.searchParams.get("provider") ?? undefined;
    console.log(
      "VANTA_PLAY_TRACK_REQUEST",
      JSON.stringify({ route: "/resolve", id: trackId ?? null, provider: provider ?? null, url: targetUrl ?? null })
    );
    try {
      const result = await resolveTrack({
        url: targetUrl ?? undefined,
        trackId: trackId ?? undefined,
        provider,
        env,
      });
      return json(result);
    } catch (error) {
      return badRequest(error instanceof Error ? error.message : "resolve_failed");
    }
  }

  const streamId = extractTrackId(pathname);
  if (streamId && request.method === "GET") {
    const quality = normalizeQuality(url.searchParams.get("quality"), env.DEFAULT_STREAM_QUALITY);
    const provider = providerFromQuery(url);
    console.log(
      "VANTA_PLAY_TRACK_REQUEST",
      JSON.stringify({ route: pathname, id: streamId, service: provider ?? "auto", quality })
    );
    const stream = await streamWithFallback(env, streamId, quality, provider, { provider });
    if (!stream) {
      return serviceUnavailable(
        provider ?? "auto",
        "No stream source succeeded. Set QOBUZ_APP_ID+QOBUZ_AUTH_TOKEN, TIDAL_STREAM_UPSTREAM, MUSICDL_BASE_URL, or other upstream secrets. Check GET /status."
      );
    }
    return json(stream);
  }

  if (pathname === "/api/dl" && request.method === "POST") {
    const body = await readJson<{ id?: string; quality?: string; service?: string; provider?: string }>(request);
    if (!body?.id?.trim()) return badRequest("missing id");
    const quality = normalizeQuality(body.quality, env.DEFAULT_STREAM_QUALITY);
    const provider = providerFromQuery(url, body.service ?? body.provider);
    console.log(
      "VANTA_PLAY_TRACK_REQUEST",
      JSON.stringify({ route: "/api/dl", id: body.id.trim(), service: provider ?? "auto", quality })
    );
    const stream = await streamWithFallback(env, body.id.trim(), quality, provider, { provider });
    if (!stream) {
      return serviceUnavailable(provider ?? "auto", "No stream source succeeded for /api/dl. Check GET /status.");
    }
    return json(stream);
  }

  if (pathname.startsWith("/search/")) {
    const query = decodeURIComponent(pathname.slice("/search/".length));
    if (!query.trim()) return badRequest("missing search path");
    return json(await searchAll(query.trim(), env));
  }

  return notFound();
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await handleRequest(request, env);
    } catch (error) {
      console.error("gateway_error", error);
      return json(
        {
          error: "internal_error",
          message: error instanceof Error ? error.message : "unknown",
        },
        500
      );
    }
  },
};
