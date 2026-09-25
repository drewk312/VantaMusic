type RequiredWorkerVars = Pick<
  Cloudflare.Env,
  | "GATEWAY_ID"
  | "GATEWAY_NAME"
  | "GATEWAY_VERSION"
  | "GATEWAY_DESCRIPTION"
  | "ENABLED_SEARCH_PROVIDERS"
  | "ENABLED_STREAM_PROVIDERS"
  | "DEFAULT_STREAM_QUALITY"
  | "ENRICH_SEARCH_RESULTS"
  | "SEARCH_ENRICH_LIMIT"
>;

interface OperatorEnv {
  EXTENSION_AUDIO?: DurableObjectNamespace;
  GATEWAY_API_KEY?: string;
  /** Operator-only diagnostics credential, never distributed to mobile clients. */
  GATEWAY_STATUS_KEY?: string;
  /** AES-GCM session storage secret; defaults to a domain-separated EXTENSION_PROXY_SECRET. */
  SESSION_ENCRYPTION_KEY?: string;
  /** Public base URL of this gateway (e.g. https://vanta-music-gateway.16drewk.workers.dev). */
  GATEWAY_BASE_URL?: string;
  /** HMAC secret used to validate per-user sync bearer tokens. Required outside development. */
  SYNC_AUTH_SECRET?: string;
  /** Firebase project whose ID tokens are accepted for production sync. */
  FIREBASE_PROJECT_ID?: string;
  /** Explicit development-only identity accepted through X-Dev-Sync-User. */
  DEV_SYNC_USER_ID?: string;
  NODE_ENV?: string;
  QOBUZ_STREAM_UPSTREAM?: string;
  TIDAL_STREAM_UPSTREAM?: string;
  TIDAL_API_URL?: string;
  /** Shared secret sent only from the gateway to the private Tidal API. */
  TIDAL_API_KEY?: string;
  /** Public Tidal OAuth client id for PKCE / device-code refresh. */
  TIDAL_OAUTH_CLIENT_ID?: string;
  /** HMAC secret for short-lived public Widevine proxy URLs. */
  DRM_PROXY_SECRET?: string;
  /** Preferred private multi-provider backend implementing the VANTA v1 stream contract. */
  OPERATOR_BACKEND_URL?: string;
  OPERATOR_BACKEND_API_KEY?: string;
  /** Comma-separated provider capabilities advertised by the private backend. */
  OPERATOR_BACKEND_PROVIDERS?: string;
  /**
   * Operator-configured upstream for Amazon Music Widevine licenses. Must be an
   * HTTPS endpoint that accepts a POST `{licenseChallenge}` (or raw challenge)
   * and returns the Widevine license. When unset the gateway falls back to
   * `OPERATOR_BACKEND_URL/v1/drm/amazon/license`. Requires a device/session that
   * Amazon's license service accepts (VMP pass) — cannot be minted from a repo
   * copy alone (see docs/VANTA_AMAZON_DRM_LICENSE_BLOCK_2026-09-24.md).
   */
  AMAZON_DRM_PROXY_URL?: string;
  /** Optional ephemeral key or static key sent to the Amazon DRM upstream. */
  AMAZON_DRM_PROXY_KEY?: string;
  DEEZER_STREAM_UPSTREAM?: string;
  AMAZON_STREAM_UPSTREAM?: string;
  PANDORA_STREAM_UPSTREAM?: string;
  MUSICDL_BASE_URL?: string;
  MUSICDL_API_KEY?: string;
  /** Optional comma-separated SpotiFLAC-Next qzz.io multinode relay URLs to use as a shared lossless fallback. */
  SPOTBYE_NODES?: string;
  QOBUZ_APP_ID?: string;
  QOBUZ_AUTH_TOKEN?: string;
  /** Shared community Qobuz account token (SpotiFLAC "qbz-a" style). Used to request hi-res FLAC (format_id 27/7) when no per-operator QOBUZ_AUTH_TOKEN is set. */
  QOBUZ_COMMUNITY_TOKEN?: string;
  /** Self-hosted SpotiFLAC-style Tidal "hifi-api" instance for FLAC/hi-res (and optionally Atmos). */
  HIFI_API_URL?: string;
  /** Optional secret sent to the hifi-api instance (Authorization bearer / X-Api-Key). */
  HIFI_API_KEY?: string;
  DEEZER_ARL?: string;
  ZARZ_RESOLVE_URL?: string;
  /** SpotiFLAC community session (one-time verification). Signs every /api/dl request. */
  COMMUNITY_INSTALL_ID?: string;
  COMMUNITY_SESSION_ID?: string;
  COMMUNITY_SESSION_SECRET?: string;
  /** RFC3339 expiry of the persisted community session (re-verify when passed). */
  COMMUNITY_SESSION_EXPIRES?: string;
  COMMUNITY_APP_VERSION?: string;
  COMMUNITY_PLATFORM?: string;
  /** Current official SpotiFLAC extension sessions. Qobuz and Tidal are scoped separately. */
  ZARZ_INSTALL_ID?: string;
  ZARZ_QOBUZ_INSTALL_ID?: string;
  ZARZ_TIDAL_INSTALL_ID?: string;
  ZARZ_DEEZER_INSTALL_ID?: string;
  ZARZ_AMAZON_INSTALL_ID?: string;
  ZARZ_QOBUZ_SESSION_ID?: string;
  ZARZ_QOBUZ_SESSION_SECRET?: string;
  ZARZ_QOBUZ_SESSION_EXPIRES?: string;
  ZARZ_TIDAL_SESSION_ID?: string;
  ZARZ_TIDAL_SESSION_SECRET?: string;
  ZARZ_TIDAL_SESSION_EXPIRES?: string;
  ZARZ_DEEZER_SESSION_ID?: string;
  ZARZ_DEEZER_SESSION_SECRET?: string;
  ZARZ_DEEZER_SESSION_EXPIRES?: string;
  ZARZ_AMAZON_SESSION_ID?: string;
  ZARZ_AMAZON_SESSION_SECRET?: string;
  ZARZ_AMAZON_SESSION_EXPIRES?: string;
  SOUNDCLOUD_CLIENT_ID?: string;
  EXTENSION_PROXY_SECRET?: string;
  /** Optional live community relay base (defaults to *-oss.spotbye.qzz.io). */
  COMMUNITY_RELAY_BASE?: string;
  /** Force COMMUNITY_RELAY_BASE to be used even if it doesn't match the provider prefix. */
  COMMUNITY_FORCE_BASE?: string;
  /** Production relay host list per provider (comma-separated origins). Operator-only Worker secret; the only source in production. */
  COMMUNITY_RELAY_QOBUZ?: string;
  COMMUNITY_RELAY_TIDAL?: string;
  COMMUNITY_RELAY_AMAZON?: string;
  /** Community relay API key (operator-only Worker secret; derivation not shipped in source). */
  COMMUNITY_API_KEY?: string;
  /** MusicDL Qobuz debug key (operator-only Worker secret; derivation not shipped in source). */
  MUSICDL_DEBUG_KEY?: string;
  /** Deployment environment; "production" disables builtin dev relay host lists. */
  ENVIRONMENT?: string;
  /** Encrypted Next-shard community path (zero-config; "false" disables, used by tests). */
  NEXT_COMMUNITY_ENABLED?: string;
  /** Client-side anti-abuse token for the Next wrapper (`token` field). Rotatable secret. */
  NEXT_REQUEST_TOKEN?: string;
  /** Monochrome Unified Playback (music-api.geeked.wtf) — lets us use their servers for Amazon/Tidal Atmos. */
  MONOCHROME_API_BASE_URL?: string;
  MONOCHROME_API_TOKEN?: string;
  /** Cloudflare Turnstile JWT for the unified API (mint via sitekey 0x4AAAAAADgxqF6QVMm0GLHH). */
  MONOCHROME_TURNSTILE_JWT?: string;
  /** Allow unified API without JWT (testing only, will 428). */
  MONOCHROME_ALLOW_UNAUTH?: string;
  /** Proxy-decrypt Amazon CENC via /api/decrypt-stream when a decryptionKey is returned. */
  MONOCHROME_PROXY_DECRYPT?: string;
  /** Public URL of this gateway (for building decrypt proxy URLs). */
  GATEWAY_PUBLIC_URL?: string;
  /** Sideload APK + update.json for in-app Update / Download. */
  RELEASES?: R2Bucket;
  APP_VERSION_CODE?: string;
  APP_VERSION_NAME?: string;
  APP_APK_URL?: string;
  APP_DONATE_URL?: string;
  APP_CHANGELOG?: string;
  /** Deezer public FLAC mirror (default https://dzr.tabs-vs-spaces.wtf). */
  DEEZER_PUBLIC_BASE_URL?: string;
  /** AudioMuse-AI base URL (e.g. http://localhost:8000 or https://audiomuse.example.com). Enables /api/audiomuse/* proxy. */
  AUDIOMUSE_BASE_URL?: string;
  AUDIOMUSE_API_KEY?: string;
  AUDIOMUSE_USERNAME?: string;
  AUDIOMUSE_PASSWORD?: string;
  /** Spotify Client Credentials for AI Radio seeding & playlist import. */
  SPOTIFY_CLIENT_ID?: string;
  SPOTIFY_CLIENT_SECRET?: string;
  SPOTIFY_SP_DC?: string;
  /** Optional cache TTL overrides. */
  SEARCH_CACHE_TTL_SECONDS?: string;
  SEARCH_CACHE_EMPTY_TTL_SECONDS?: string;
  HEALTH_CACHE_TTL_SECONDS?: string;
  STREAM_CACHE_TTL_SECONDS?: string;
}

/** Generated bindings plus operator-provided secrets and optional upstreams. */
export type Env = Partial<Cloudflare.Env> & RequiredWorkerVars & OperatorEnv;

export type ProviderId = "qobuz" | "tidal" | "deezer" | "amazon" | "pandora" | "apple" | "soundcloud" | "spotify";

/**
 * Provider families decouple source identity from any single architecture.
 * Zarz is one source family, never the foundation: a challenged ZARZ provider
 * fails over other families without the player noticing.
 */
export type ProviderFamily =
  | "ZARZ"
  | "QOBUZ"
  | "TIDAL"
  | "AMAZON"
  | "DEEZER"
  | "OPERATOR"
  | "COMMUNITY"
  | "PUBLIC"
  | "LOCAL"
  | "METADATA";

/**
 * Explicit spatial format. A track only receives DOLBY_ATMOS when actual
 * provider/container/manifest evidence supports it; SONY_360_REALITY_AUDIO
 * only with MPEG-H/360RA evidence. Never inferred from bitrate, channel
 * count, or "spatial"-ish catalog claims alone.
 */
export type SpatialFormat =
  | "NONE"
  | "DOLBY_ATMOS"
  | "SONY_360_REALITY_AUDIO"
  | "ECLIPSA_AUDIO"
  | "UNKNOWN_SPATIAL";

export interface GatewayTrack {
  id: string;
  title: string;
  artist: string;
  /** Guests / collab partners after the primary billed artist. */
  featuredArtists?: string[];
  album?: string;
  albumId?: string;
  artworkURL?: string;
  duration?: number;
  /** Earliest known release year for this album/recording metadata. */
  releaseYear?: number;
  /** ISO catalog release date when the upstream source actually supplies one. */
  releaseDate?: string;
  /** Prevents popularity-chart fallbacks from masquerading as release metadata. */
  discoveryKind?: "new_release" | "chart";
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
  /** How the spatial flag was established. Catalog/metadata claims are not codec verification. */
  spatialEvidence?: "catalog" | "metadata" | "verified" | "rendered" | "stereo";
  /** Explicit spatial format, derived from evidence rather than labels. */
  spatialFormat?: SpatialFormat;
  /** Catalog says an Atmos mix exists on Tidal/Amazon. Not proof this file is Atmos. */
  atmosMixAvailable?: boolean;
  tidal_id?: string;
  qobuz_id?: string;
  deezer_id?: string;
  amazon_id?: string;
  spotify_id?: string;
  apple_id?: string;
}

export interface GatewayPlaylist {
  id: string;
  name: string;
  curator?: string;
  artworkURL?: string;
  description?: string;
  trackCount?: number;
  source: string;
}

export interface StreamResult {
  url: string;
  /**
   * Goal B: which independent source family produced this candidate. One
   * provider id (e.g. "qobuz") can be served by several families with very
   * different reliability, so the family must be recorded, never guessed.
   */
  family?: ProviderFamily;
  streamUrl?: string;
  format?: string;
  quality?: string;
  mimeType?: string;
  bitrateKbps?: number;
  bitDepth?: number;
  sampleRateHz?: number;
  channelCount?: number;
  expiresAt?: number;
  provider?: ProviderId;
  isDolbyAtmos?: boolean;
  isSpatialAudio?: boolean;
  isSurround?: boolean;
  isHiRes?: boolean;
  /** Explicit spatial format once established; NONE/undefined when stereo or unproven. */
  spatialFormat?: SpatialFormat;
  drm?: StreamDrmConfiguration;
}

/**
 * Source families (Goal B): Zarz is one source family, not the architecture.
 * A candidate carries the family so the cascade and diagnostics can reason
 * about independent fallbacks (Zarz → community → public → compressed).
 */
export function familyForProvider(provider: ProviderId | string | undefined): ProviderFamily {
  switch (provider) {
    case "qobuz": return "QOBUZ";
    case "tidal": return "TIDAL";
    case "amazon": return "AMAZON";
    case "deezer": return "DEEZER";
    default: return "COMMUNITY";
  }
}

export interface StreamDrmConfiguration {
  scheme: "widevine";
  /** Public license URL returned to clients. Added at the gateway edge. */
  licenseUrl?: string;
  licenseRequestHeaders?: Record<string, string>;
  forceDefaultLicenseUri?: boolean;
  /** Internal marker. Never expose a private upstream URL to clients. */
  licenseProxy?: "tidal" | "amazon";
}

export interface ResolveResult {
  isrc?: string;
  tidal_id?: string;
  tidal_atmos_id?: string;
  qobuz_id?: string;
  deezer_id?: string;
  amazon_id?: string;
  amazon_atmos_id?: string;
  spotify_id?: string;
  apple_id?: string;
  pandora_id?: string;
  title?: string;
  artist?: string;
  durationSec?: number;
  external_links?: Record<string, string>;
}

export const ALL_PROVIDER_IDS: ProviderId[] = [
  "soundcloud",
  "spotify",
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






