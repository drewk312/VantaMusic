import type { Env } from "../types";
import { providerVerificationRequired } from "./provider-failures";
import { hasByoaHeaders } from "../providers/oauth-refresh";
import { sessionFromEnv } from "../providers/zarz-signed";
import { communitySessionFromEnv } from "../providers/community-session";

export type StreamFailureCode =
  | "AUTH_REQUIRED"
  | "QUALITY_UNAVAILABLE"
  | "ATMOS_UNAVAILABLE"
  | "SOURCE_OFFLINE";

export interface ClassifiedStreamFailure {
  code: StreamFailureCode;
  message: string;
  status: number;
  retryable: boolean;
}

const ZARZ_PROVIDERS = ["amazon", "deezer", "qobuz", "tidal"] as const;

/**
 * HUMAN_REQUIRED is provider-scoped, not gateway-wide. A challenged provider is
 * removed from the active cascade while the others keep serving, so the whole
 * player must not start returning 401 just because one extension needs a human.
 * - With a provider: only true when THAT provider is waiting on verification.
 * - Without a provider (auto): only true when every Zarz provider is challenged
 *   (there is no other Zarz path left to serve the request).
 */
export async function extensionVerificationRequired(env: Env, provider?: string): Promise<boolean> {
  if (!env.EXTENSION_SESSIONS) return false;
  const focus = provider && (ZARZ_PROVIDERS as readonly string[]).includes(provider)
    ? [provider]
    : [...ZARZ_PROVIDERS];
  type HealthEntry = { status?: string };
  const health = await Promise.all(
    focus.map(async (name) => {
      // Connect writes Worker secrets immediately; DO health can lag until getSession.
      if (sessionFromEnv(env, name as (typeof ZARZ_PROVIDERS)[number])) return { status: "ready" };
      try {
        return await env.EXTENSION_SESSIONS!.getByName(name).health() as unknown as HealthEntry;
      } catch {
        return { status: "unavailable" };
      }
    })
  );
  if (provider && health.length === 1) return health[0]?.status === "verification_required";
  return health.length > 0 && health.every((entry) => entry.status === "verification_required");
}

export function hasLicensedStreamSession(env: Env, request?: Request): boolean {
  return hasByoaHeaders(request) || Boolean(
    (env.NEXT_COMMUNITY_ENABLED && env.NEXT_COMMUNITY_ENABLED !== "false") ||
      Boolean(communitySessionFromEnv(env)) ||
      env.QOBUZ_AUTH_TOKEN?.trim() ||
      env.QOBUZ_APP_ID?.trim() ||
      env.QOBUZ_STREAM_UPSTREAM?.trim() ||
      env.TIDAL_STREAM_UPSTREAM?.trim() ||
      env.TIDAL_API_URL?.trim() ||
      env.TIDAL_API_KEY?.trim() ||
      env.AMAZON_STREAM_UPSTREAM?.trim() ||
      env.DEEZER_STREAM_UPSTREAM?.trim() ||
      env.MUSICDL_BASE_URL?.trim()
  );
}

/**
 * Classify a missing catalog stream. Local/direct playback is out of scope
 * for the gateway — this only describes why a paid/catalog adapter failed.
 */
export function classifyMissingStream(
  quality: string,
  env: Env,
  request?: Request,
  provider?: string
): ClassifiedStreamFailure {
  // Provider-scoped: a challenge from one extension must not report the whole
  // player as needing reconnection while other sources remain available.
  if (providerVerificationRequired(provider) && !hasByoaHeaders(request)) {
    return {
      code: "AUTH_REQUIRED",
      message: provider
        ? `The ${provider} source requires verification. VANTA is automatically using other sources.`
        : "The music source requires verification. Reconnect the extension provider to resume streaming.",
      status: 401,
      retryable: false,
    };
  }
  const requested = quality.trim().toLowerCase();
  if (requested === "atmos" || requested === "dolby_atmos" || requested.includes("atmos")) {
    return {
      code: "ATMOS_UNAVAILABLE",
      message: "No configured source returned Dolby Atmos media for this track.",
      status: 404,
      retryable: false,
    };
  }
  if (requested === "360" || requested === "360ra" || requested === "sony360" || requested === "sony_360" || requested.includes("360 reality")) {
    return {
      code: "QUALITY_UNAVAILABLE",
      message: "No configured source returned Sony 360 Reality Audio for this track.",
      status: 404,
      retryable: false,
    };
  }
  if (!hasLicensedStreamSession(env, request)) {
    return {
      code: "AUTH_REQUIRED",
      message: "This source requires authentication.",
      status: 401,
      retryable: false,
    };
  }
  if (requested === "24" || requested === "hi_res" || requested === "hi_res_lossless") {
    return {
      code: "QUALITY_UNAVAILABLE",
      message: "The requested hi-res quality is not available from the configured catalog source.",
      status: 404,
      retryable: false,
    };
  }
  return {
    code: "SOURCE_OFFLINE",
    message: "No stream source succeeded. Check GET /status.",
    status: 503,
    retryable: true,
  };
}

/**
 * Prefer catalog/quality messaging when Next (or classic community) can still
 * serve — do not blame Amazon/Tidal Zarz reconnect when lettered Next shards
 * are enabled and the track simply has no Atmos/360 mix.
 */
export async function missingStreamFailure(
  quality: string,
  env: Env,
  provider: string | undefined,
  request?: Request
): Promise<ClassifiedStreamFailure> {
  const spatial = /^(atmos|360|360ra|sony.?360)/i.test(quality.trim()) ||
    quality.toLowerCase().includes("atmos") ||
    quality.toLowerCase().includes("360");
  const communityOk = Boolean(communitySessionFromEnv(env));
  // Next needs no CAPTCHA session — treat it as available unless explicitly killed.
  const nextOk = env.NEXT_COMMUNITY_ENABLED !== "false";
  if (!hasByoaHeaders(request) && await extensionVerificationRequired(env, provider)) {
    if (spatial && !communityOk && !nextOk) {
      return {
        code: (quality.toLowerCase().includes("atmos") ? "ATMOS_UNAVAILABLE" : "QUALITY_UNAVAILABLE") as
          | "ATMOS_UNAVAILABLE"
          | "QUALITY_UNAVAILABLE",
        message: quality.toLowerCase().includes("atmos")
          ? "Dolby Atmos needs a reconnect of the Tidal/Amazon source session."
          : "Sony 360 Reality Audio needs a reconnect of the Amazon source session.",
        status: 404,
        retryable: false,
      };
    }
    if (!spatial && !communityOk && !nextOk) {
      return {
        code: "AUTH_REQUIRED" as const,
        message: provider
          ? `The ${provider} source requires verification. VANTA is automatically using other sources.`
          : "All verification sources require reconnection. Some tracks may be unavailable.",
        status: 401,
        retryable: false,
      };
    }
  }
  return classifyMissingStream(quality, env, request, provider);
}
