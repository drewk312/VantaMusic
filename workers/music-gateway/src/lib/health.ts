import type { Env } from "../types";
import { fetchJson } from "../providers/shared";
import { healthCacheTtl } from "./cache";

export interface ProviderHealth {
  provider: string;
  healthy: boolean;
  latencyMs: number;
  checkedAt: number;
  error?: string;
}

/** Lightweight smoke test of a provider's upstream endpoint. */
async function smokeTest(env: Env, provider: string): Promise<{ ok: boolean; latencyMs: number; error?: string }> {
  const start = Date.now();
  try {
    switch (provider) {
      case "deezer": {
        const result = await fetchJson("https://api.deezer.com/search?q=test&limit=1");
        return { ok: Array.isArray((result as { data?: unknown[] })?.data), latencyMs: Date.now() - start };
      }
      case "qobuz": {
        // Qobuz search requires auth; just hit the public catalog endpoint to check DNS/TLS.
        const res = await fetch("https://www.qobuz.com/api.json/0.2/catalog/search?query=test&limit=1", {
          headers: { Accept: "application/json" },
        });
        return { ok: res.ok || res.status === 401, latencyMs: Date.now() - start };
      }
      case "apple": {
        const result = await fetchJson("https://itunes.apple.com/search?term=test&limit=1");
        return { ok: Array.isArray((result as { results?: unknown[] })?.results), latencyMs: Date.now() - start };
      }
      default:
        return { ok: true, latencyMs: 0 };
    }
  } catch (err) {
    return { ok: false, latencyMs: Date.now() - start, error: err instanceof Error ? err.message : String(err) };
  }
}

/** Return cached health status or run smoke tests. */
export async function checkProviderHealth(env: Env): Promise<ProviderHealth[]> {
  const cacheKey = "health:providers";
  if (env.CACHE) {
    const cached = await env.CACHE.get(cacheKey, "json") as ProviderHealth[] | null;
    if (cached && cached.length > 0 && Date.now() - cached[0].checkedAt < healthCacheTtl(env) * 1000) {
      return cached;
    }
  }

  const providers = ["deezer", "qobuz", "apple"];
  const results = await Promise.all(
    providers.map(async (provider) => {
      const { ok, latencyMs, error } = await smokeTest(env, provider);
      return {
        provider,
        healthy: ok,
        latencyMs,
        checkedAt: Date.now(),
        error,
      };
    })
  );

  if (env.CACHE) {
    await env.CACHE.put(cacheKey, JSON.stringify(results), { expirationTtl: healthCacheTtl(env) });
  }

  return results;
}
