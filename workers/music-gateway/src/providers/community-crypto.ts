import type { Env } from "../types";

export type CommunityUrlKind = "qobuz" | "tidal" | "amazon";

function relaySecretForKind(env: Env, kind: CommunityUrlKind): string | undefined {
  switch (kind) {
    case "qobuz":
      return env.COMMUNITY_RELAY_QOBUZ;
    case "tidal":
      return env.COMMUNITY_RELAY_TIDAL;
    case "amazon":
      return env.COMMUNITY_RELAY_AMAZON;
  }
}

/**
 * Relay endpoint for the classic community pool. Hosts are operator-only
 * secrets (Cloudflare Worker secret binding in production, .dev.vars
 * locally) — the upstream host-derivation material is deliberately NOT
 * shipped in source, so a copy of this repo cannot mint a working gateway
 * without this account's secrets.
 */
export function getCommunityDownloadUrl(kind: CommunityUrlKind, env: Env): string | null {
  const raw = relaySecretForKind(env, kind)?.trim();
  if (!raw) return null;
  const base = raw.split(",")[0]?.trim().replace(/\/+$/, "");
  if (!base || !base.startsWith("http")) return null;
  return `${base}/api/dl`;
}
