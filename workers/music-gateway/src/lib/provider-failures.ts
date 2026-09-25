import { AsyncLocalStorage } from "node:async_hooks";

// Each resolution keeps its own evidence, including concurrent provider attempts.
// A human challenge is scoped per provider family so one challenged Zarz
// extension never poisons the others (or the final failure classification).
const failures = new AsyncLocalStorage<{ challenged: Set<string> }>();

const UNATTRIBUTED = "";

export function withProviderFailures<T>(operation: () => T): T {
  return failures.run({ challenged: new Set<string>() }, operation);
}

export function recordVerificationChallenge(provider?: string): void {
  const state = failures.getStore();
  state?.challenged.add(provider?.trim().toLowerCase() || UNATTRIBUTED);
}

export function recordProviderVerification(status: number, code: unknown, provider?: string): void {
  if (status === 428 && code === "VERIFY_REQUIRED") recordVerificationChallenge(provider);
}

/**
 * Provider-scoped human-challenge flag.
 * - With a provider: only true when that provider (or an unattributed response
 *   inside its own resolution path) recorded a challenge.
 * - Without a provider: true when any challenge was recorded in the resolution.
 */
export function providerVerificationRequired(provider?: string): boolean {
  const challenged = failures.getStore()?.challenged;
  if (!challenged || challenged.size === 0) return false;
  if (provider) {
    const key = provider.trim().toLowerCase();
    return challenged.has(key) || challenged.has(UNATTRIBUTED);
  }
  return true;
}
