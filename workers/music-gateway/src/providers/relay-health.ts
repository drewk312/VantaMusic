import type { ProviderId } from "../types";

/**
 * In-memory circuit breaker for the community relay pools (SpotiFLAC shards and
 * the signed /api/dl cascade). When every node in a pool returns a down signal
 * (503/428/400/network error) the pool is marked down for a short window, so a
 * single request does not re-hammer every shard and exhaust the 50-subrequest
 * Worker budget. The breaker is only consulted as a fast-fail gate; pools are
 * re-probed after the window expires.
 */

const DEFAULT_DOWN_WINDOW_MS = 5_000;

const downUntil: Record<string, number> = {};

export function isRelayPoolDown(scope: string): boolean {
  const until = downUntil[scope];
  if (!until) return false;
  if (Date.now() >= until) {
    delete downUntil[scope];
    return false;
  }
  return true;
}

export function markRelayPoolDown(scope: string, reason: string, windowMs = DEFAULT_DOWN_WINDOW_MS): void {
  downUntil[scope] = Date.now() + windowMs;
  console.warn(
    "VANTA_SOURCE",
    JSON.stringify({ scope, event: "relay_pool_down", reason, windowMs })
  );
}

export function markRelayPoolUp(scope: string): void {
  delete downUntil[scope];
}