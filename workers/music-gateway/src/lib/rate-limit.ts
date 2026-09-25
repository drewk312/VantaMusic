import type { Env } from "../types";
import { isDevelopment } from "../auth";

type LocalRateLimitEntry = { count: number; windowStart: number };
const localRateLimits = new Map<string, LocalRateLimitEntry>();

export interface RateLimitState {
  /** Null when the platform binding does not expose an exact counter. */
  remaining: number | null;
  resetAt: number;
  allowed: boolean;
}

export interface RateLimitResult {
  allowed: boolean;
  state: RateLimitState;
  key: string;
  limit: number;
  storageUnavailable: boolean;
}

/**
 * Production rate limiter backed by Cloudflare's native Rate Limiting binding.
 * Isolate-local counters are retained only for explicit local development.
 */
export async function checkRateLimit(
  request: Request,
  env: Env
): Promise<RateLimitResult> {
  const key = rateLimitKey(request, env);
  const windowSeconds = Math.max(1, parseInt(env.RATE_LIMIT_WINDOW_SECONDS ?? "60", 10) || 60);
  const maxRequests = Math.max(1, parseInt(env.RATE_LIMIT_MAX_REQUESTS ?? "120", 10) || 120);
  const now = Math.floor(Date.now() / 1000);
  const resetAt = now + windowSeconds;

  if (env.RATE_LIMITER) {
    try {
      const { success } = await env.RATE_LIMITER.limit({ key });
      return {
        allowed: success,
        state: { remaining: success ? null : 0, resetAt, allowed: success },
        key,
        limit: maxRequests,
        storageUnavailable: false,
      };
    } catch (err) {
      console.error("VANTA_RATE_LIMIT_BINDING_ERROR", JSON.stringify({ key, error: err instanceof Error ? err.message : String(err) }));
    }
  }

  if (isDevelopment(env)) {
    const entry = localRateLimits.get(key);
    const active = entry && entry.windowStart > now - windowSeconds
      ? entry
      : { count: 0, windowStart: now };
    active.count += 1;
    localRateLimits.set(key, active);
    if (localRateLimits.size > 2_000) {
      for (const [storedKey, stored] of localRateLimits) {
        if (stored.windowStart <= now - windowSeconds) localRateLimits.delete(storedKey);
      }
    }
    const allowed = active.count <= maxRequests;
    return {
      allowed,
      state: {
        remaining: Math.max(0, maxRequests - active.count),
        resetAt: active.windowStart + windowSeconds,
        allowed,
      },
      key,
      limit: maxRequests,
      storageUnavailable: true,
    };
  }
  return {
    allowed: false,
    state: { remaining: 0, resetAt, allowed: false },
    key,
    limit: maxRequests,
    storageUnavailable: true,
  };
}

function rateLimitKey(request: Request, env: Env): string {
  const apiKey = request.headers.get("X-Api-Key")?.trim();
  if (apiKey) return `apikey:${stableIdentifier(apiKey)}`;
  const authorization = request.headers.get("Authorization")?.trim();
  if (authorization) return `authorization:${stableIdentifier(authorization)}`;
  const forwarded = request.headers.get("CF-Connecting-IP") || request.headers.get("X-Forwarded-For");
  if (forwarded) return `ip:${stableIdentifier(forwarded.split(",")[0].trim())}`;
  return "ip:unknown";
}

function stableIdentifier(value: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}
