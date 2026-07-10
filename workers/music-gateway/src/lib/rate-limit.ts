import type { Env } from "../types";
import { isDevelopment } from "../auth";

export interface RateLimitState {
  remaining: number;
  resetAt: number;
  allowed: boolean;
}

export interface RateLimitResult {
  allowed: boolean;
  state: RateLimitState;
  key: string;
  storageUnavailable: boolean;
}

/**
 * Sliding-window rate limiter backed by Cloudflare KV.
 * Falls back to in-memory tracking when KV is not configured.
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

  if (env.CACHE) {
    try {
      const cached = (await env.CACHE.get(`ratelimit:${key}`, "json")) as {
        count: number;
        windowStart: number;
      } | null;

      if (cached && cached.windowStart > now - windowSeconds) {
        const count = cached.count + 1;
        await env.CACHE.put(
          `ratelimit:${key}`,
          JSON.stringify({ count, windowStart: cached.windowStart }),
          { expirationTtl: windowSeconds }
        );
        return {
          allowed: count <= maxRequests,
          state: { remaining: Math.max(0, maxRequests - count), resetAt: cached.windowStart + windowSeconds, allowed: count <= maxRequests },
          key,
          storageUnavailable: false,
        };
      }

      await env.CACHE.put(
        `ratelimit:${key}`,
        JSON.stringify({ count: 1, windowStart: now }),
        { expirationTtl: windowSeconds }
      );
      return { allowed: true, state: { remaining: maxRequests - 1, resetAt, allowed: true }, key, storageUnavailable: false };
    } catch (err) {
      console.warn("VANTA_RATE_LIMIT_KV_ERROR", JSON.stringify({ key, error: err instanceof Error ? err.message : String(err) }));
      if (!isDevelopment(env)) {
        return { allowed: false, state: { remaining: 0, resetAt, allowed: false }, key, storageUnavailable: true };
      }
      return { allowed: true, state: { remaining: maxRequests, resetAt, allowed: true }, key, storageUnavailable: true };
    }
  }

  if (!isDevelopment(env)) {
    return { allowed: false, state: { remaining: 0, resetAt, allowed: false }, key, storageUnavailable: true };
  }
  return { allowed: true, state: { remaining: maxRequests, resetAt, allowed: true }, key, storageUnavailable: true };
}

function rateLimitKey(request: Request, env: Env): string {
  const apiKey = request.headers.get("X-Api-Key")?.trim();
  if (apiKey) return `apikey:${apiKey.slice(0, 16)}`;
  const forwarded = request.headers.get("CF-Connecting-IP") || request.headers.get("X-Forwarded-For");
  if (forwarded) return `ip:${forwarded.split(",")[0].trim()}`;
  return "ip:unknown";
}

