import type { Env, StreamResult } from "../types";

type StringEnvKey =
  | "GATEWAY_ID"
  | "GATEWAY_NAME"
  | "GATEWAY_VERSION"
  | "GATEWAY_DESCRIPTION"
  | "ENABLED_SEARCH_PROVIDERS"
  | "ENABLED_STREAM_PROVIDERS"
  | "DEFAULT_STREAM_QUALITY"
  | "ENRICH_SEARCH_RESULTS"
  | "SEARCH_ENRICH_LIMIT"
  | "GATEWAY_API_KEY"
  | "QOBUZ_STREAM_UPSTREAM"
  | "TIDAL_STREAM_UPSTREAM"
  | "TIDAL_API_URL"
  | "DEEZER_STREAM_UPSTREAM"
  | "AMAZON_STREAM_UPSTREAM"
  | "PANDORA_STREAM_UPSTREAM"
  | "MUSICDL_BASE_URL"
  | "MUSICDL_API_KEY"
  | "QOBUZ_APP_ID"
  | "QOBUZ_AUTH_TOKEN"
  | "DEEZER_ARL"
  | "ZARZ_RESOLVE_URL"
  | "DEFAULT_COMMUNITY_GATEWAY"
  | "FALLBACK_COMMUNITY_GATEWAYS"
  | "GDSTUDIO_API_URL"
  | "RATE_LIMIT_WINDOW_SECONDS"
  | "RATE_LIMIT_MAX_REQUESTS"
  | "SEARCH_CACHE_TTL_SECONDS"
  | "SEARCH_CACHE_EMPTY_TTL_SECONDS"
  | "HEALTH_CACHE_TTL_SECONDS"
  | "STREAM_CACHE_TTL_SECONDS";

/** Parse an integer env var with a bounded default. */
export function seconds(env: Env, key: StringEnvKey, defaultValue: number, max = 86_400): number {
  const raw = env[key];
  if (!raw) return defaultValue;
  const parsed = Number.parseInt(raw.trim(), 10);
  if (!Number.isFinite(parsed) || parsed < 0) return defaultValue;
  return Math.min(parsed, max);
}

/** Cache TTL for search results: longer for non-empty, shorter for empty. */
export function searchCacheTtl(env: Env, resultCount: number): number {
  const hitTtl = seconds(env, "SEARCH_CACHE_TTL_SECONDS", 600);
  const missTtl = seconds(env, "SEARCH_CACHE_EMPTY_TTL_SECONDS", 60);
  return resultCount > 0 ? hitTtl : missTtl;
}

/** TTL for lightweight upstream health snapshots. */
export function healthCacheTtl(env: Env): number {
  return seconds(env, "HEALTH_CACHE_TTL_SECONDS", 30);
}

/** TTL for resolved stream URLs. Kept short because signed URLs expire. */
export function streamCacheTtl(env: Env): number {
  return seconds(env, "STREAM_CACHE_TTL_SECONDS", 300);
}

/** Stable cache key for a stream request. */
export function streamCacheKey(trackId: string, provider: string | undefined, quality: string): string {
  return `stream:${trackId}:${provider ?? "auto"}:${quality}`;
}

/** Read a cached stream result, respecting the upstream expiry timestamp. */
export async function getCachedStream(env: Env, key: string): Promise<StreamResult | null> {
  if (!env.CACHE) return null;
  try {
    const value = await env.CACHE.get(key);
    if (!value) return null;
    const parsed = JSON.parse(value) as StreamResult;
    if (parsed.expiresAt && parsed.expiresAt * 1000 < Date.now()) {
      return null;
    }
    return parsed;
  } catch (err) {
    console.error("VANTA_STREAM_CACHE_READ_ERROR", err);
    return null;
  }
}

/** Write a stream result to KV. TTL is capped between 1s and 24h. */
export async function putCachedStream(env: Env, key: string, result: StreamResult, ttl: number): Promise<void> {
  if (!env.CACHE) return;
  try {
    const safeTtl = Math.min(Math.max(1, Math.floor(ttl)), 86_400);
    await env.CACHE.put(key, JSON.stringify(result), { expirationTtl: safeTtl });
  } catch (err) {
    console.error("VANTA_STREAM_CACHE_WRITE_ERROR", err);
  }
}

