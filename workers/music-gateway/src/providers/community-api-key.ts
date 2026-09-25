import type { Env } from "../types";

/**
 * Community relay API key. Operator-only secret (Cloudflare Worker secret
 * binding in production, .dev.vars locally) — the upstream key-derivation
 * material is deliberately NOT shipped in source, so a copy of this repo
 * cannot mint a working gateway without this account's secrets.
 */
export function getCommunityApiKey(env: Env): string | null {
  return env.COMMUNITY_API_KEY?.trim() || null;
}
