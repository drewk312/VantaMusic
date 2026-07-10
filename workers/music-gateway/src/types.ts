export interface Env {
  GATEWAY_ID: string;
  GATEWAY_NAME: string;
  GATEWAY_VERSION: string;
  GATEWAY_DESCRIPTION: string;
  ENABLED_SEARCH_PROVIDERS: string;
  ENABLED_STREAM_PROVIDERS: string;
  DEFAULT_STREAM_QUALITY: string;
  ENRICH_SEARCH_RESULTS: string;
  SEARCH_ENRICH_LIMIT: string;
  GATEWAY_API_KEY?: string;
  /** HMAC secret used to validate per-user sync bearer tokens. Required outside development. */
  SYNC_AUTH_SECRET?: string;
  /** Firebase project whose ID tokens are accepted for production sync. */
  FIREBASE_PROJECT_ID?: string;
  /** Explicit development-only identity accepted through X-Dev-Sync-User. */
  DEV_SYNC_USER_ID?: string;
  ENVIRONMENT?: string;
  NODE_ENV?: string;
  QOBUZ_STREAM_UPSTREAM?: string;
  TIDAL_STREAM_UPSTREAM?: string;
  TIDAL_API_URL?: string;
  DEEZER_STREAM_UPSTREAM?: string;
  AMAZON_STREAM_UPSTREAM?: string;
  PANDORA_STREAM_UPSTREAM?: string;
  MUSICDL_BASE_URL?: string;
  MUSICDL_API_KEY?: string;
  QOBUZ_APP_ID?: string;
  QOBUZ_AUTH_TOKEN?: string;
  DEEZER_ARL?: string;
  ZARZ_RESOLVE_URL?: string;
  DEFAULT_COMMUNITY_GATEWAY?: string;
  /** Comma-separated backup community gateway base URLs tried after the primary. */
  FALLBACK_COMMUNITY_GATEWAYS?: string;
  GDSTUDIO_API_URL?: string;
  RATE_LIMIT_WINDOW_SECONDS?: string;
  RATE_LIMIT_MAX_REQUESTS?: string;
  SEARCH_CACHE_TTL_SECONDS?: string;
  SEARCH_CACHE_EMPTY_TTL_SECONDS?: string;
  HEALTH_CACHE_TTL_SECONDS?: string;
  STREAM_CACHE_TTL_SECONDS?: string;
  CACHE?: KVNamespace;
  /** Friend graph, listening activity, and library snapshots. */
  SOCIAL_KV?: KVNamespace;
}

export type ProviderId = "qobuz" | "tidal" | "deezer" | "amazon" | "pandora" | "apple";

export interface GatewayTrack {
  id: string;
  title: string;
  artist: string;
  album?: string;
  albumId?: string;
  artworkURL?: string;
  duration?: number;
  trackNumber?: number;
  discNumber?: number;
  audioQuality?: string;
  isrc?: string;
  format?: string;
  explicit?: boolean;
  provider?: ProviderId;
  isDolbyAtmos?: boolean;
  isSpatialAudio?: boolean;
  isSurround?: boolean;
  isHiRes?: boolean;
  tidal_id?: string;
  qobuz_id?: string;
  deezer_id?: string;
  amazon_id?: string;
  spotify_id?: string;
  apple_id?: string;
}

export interface StreamResult {
  url: string;
  streamUrl?: string;
  format?: string;
  quality?: string;
  mimeType?: string;
  bitrateKbps?: number;
  expiresAt?: number;
  provider?: ProviderId;
  isDolbyAtmos?: boolean;
  isSpatialAudio?: boolean;
  isSurround?: boolean;
  isHiRes?: boolean;
}

export interface ResolveResult {
  isrc?: string;
  tidal_id?: string;
  qobuz_id?: string;
  deezer_id?: string;
  amazon_id?: string;
  spotify_id?: string;
  apple_id?: string;
  pandora_id?: string;
  external_links?: Record<string, string>;
}

export const ALL_PROVIDER_IDS: ProviderId[] = [
  "qobuz",
  "tidal",
  "deezer",
  "amazon",
  "pandora",
  "apple",
];

export function parseProviderList(raw: string | undefined): ProviderId[] {
  return (raw ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter((value): value is ProviderId => ALL_PROVIDER_IDS.includes(value as ProviderId));
}

export function truthy(value: string | undefined): boolean {
  if (!value) return false;
  const normalized = value.trim().toLowerCase();
  return normalized === "1" || normalized === "true" || normalized === "yes" || normalized === "on";
}

export function enrichLimit(env: Env): number {
  const parsed = Number.parseInt(env.SEARCH_ENRICH_LIMIT ?? "8", 10);
  if (!Number.isFinite(parsed) || parsed < 0) return 8;
  return Math.min(parsed, 20);
}

export function qobuzFormatId(quality: string): string {
  return quality === "16" ? "6" : "27";
}






