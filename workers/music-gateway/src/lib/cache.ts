import type { Env } from "../types";

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
  | "HEALTH_CACHE_TTL_SECONDS";

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
