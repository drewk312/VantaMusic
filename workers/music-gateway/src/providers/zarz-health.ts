import type { ProviderId } from "../types";

/**
 * In-memory circuit breaker for Zarz provider playback (Goal I). Tracks
 * consecutive stream-level failures per provider; after the threshold a
 * provider is temporarily skipped in the cascade and re-probed after a
 * growing cooldown. Session-level challenge handling lives in the
 * ExtensionSessions DO (verification_required); this only covers repeated
 * non-challenge failures so playback never wedges on one broken family.
 */

const FAILURE_THRESHOLD = 3;
const BASE_COOLDOWN_MS = 30_000;
const MAX_COOLDOWN_MS = 10 * 60_000;

const BREAKER_PROVIDERS: ProviderId[] = ["qobuz", "tidal", "deezer", "amazon"];

const consecutiveFailures: Partial<Record<ProviderId, number>> = {};
const openUntil: Partial<Record<ProviderId, number>> = {};

export function isZarzPlaybackOpen(provider: ProviderId): boolean {
  if (!(BREAKER_PROVIDERS as ProviderId[]).includes(provider)) return false;
  const until = openUntil[provider];
  if (!until) return false;
  if (Date.now() >= until) {
    delete openUntil[provider];
    return false;
  }
  return true;
}

export function recordZarzPlaybackSuccess(provider: ProviderId): void {
  delete consecutiveFailures[provider];
  delete openUntil[provider];
}

export function recordZarzPlaybackFailure(provider: ProviderId): void {
  if (!(BREAKER_PROVIDERS as ProviderId[]).includes(provider)) return;
  const failures = (consecutiveFailures[provider] ?? 0) + 1;
  consecutiveFailures[provider] = failures;
  if (failures >= FAILURE_THRESHOLD) {
    const cooldown = Math.min(
      MAX_COOLDOWN_MS,
      BASE_COOLDOWN_MS * 2 ** (failures - FAILURE_THRESHOLD)
    );
    const until = Date.now() + cooldown;
    if (until > (openUntil[provider] ?? 0)) {
      openUntil[provider] = until;
      console.warn(
        "VANTA_SOURCE",
        JSON.stringify({
          provider,
          event: "zarz_breaker_open",
          consecutiveFailures: failures,
          cooldownMs: cooldown,
        })
      );
    }
  }
}

export function zarzBreakerSnapshot(): Record<string, { failures: number; openMsRemaining: number }> {
  const now = Date.now();
  const snapshot: Record<string, { failures: number; openMsRemaining: number }> = {};
  for (const provider of BREAKER_PROVIDERS) {
    const failures = consecutiveFailures[provider] ?? 0;
    const openMsRemaining = Math.max(0, (openUntil[provider] ?? 0) - now);
    if (failures || openMsRemaining) snapshot[provider] = { failures, openMsRemaining };
  }
  return snapshot;
}
