import type { CommunitySession } from "../providers/community-session";

// Start signed refresh once less than this remains (typical Zarz grant ~6h).
const REFRESH_WINDOW_MS = 3 * 60 * 60000;
// Never wait longer than this between successful renewals while the grant is still valid.
const MAX_IDLE_MS = 2 * 60 * 60000;
const MIN_GAP_MS = 5 * 60000;
const MIN_VALID_MS = 5 * 60000;
const PLAYBACK_SKEW_MS = 30_000;
/** Directive backoff for temporary refresh errors while the grant stays usable:
 *  ~1 min → ~5 min → ~15 min, then increasingly conservative, capped at 1h. */
const TEMP_FAILURE_BACKOFF_MS = [60_000, 300_000, 900_000, 1_800_000] as const;
const JITTER_MAX_MS = 12 * 60000;

export function retryBackoffMs(failures: number): number {
  const index = Math.max(0, Math.min(failures - 1, TEMP_FAILURE_BACKOFF_MS.length - 1));
  return Math.min(3_600_000, TEMP_FAILURE_BACKOFF_MS[index]);
}

/** Deterministic per-provider refresh offset (bounded jitter).
 *  Same seed always yields the same offset; different providers diverge. */
export function staggerJitterMs(seed: string): number {
  let hash = 2166136261;
  for (let i = 0; i < seed.length; i++) {
    hash ^= seed.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0) % (JITTER_MAX_MS + 1);
}

/**
 * Schedule the next signed refresh: half-life for short grants, at most every
 * 2h for long ones. `jitterMs` pushes the renewal from 50% toward the 65%
 * lifetime mark so providers within one install do not refresh simultaneously.
 */
export function nextRenewal(now: number, expires: number, jitterMs = 0): number {
  const remaining = Math.max(0, expires - now);
  const half = now + remaining / 2;
  const jitter = Math.min(Math.max(0, jitterMs), Math.floor(remaining * 0.15));
  if (remaining <= REFRESH_WINDOW_MS) {
    return Math.max(now + MIN_GAP_MS, half + jitter);
  }
  const capped = Math.min(half, now + MAX_IDLE_MS);
  return Math.max(now + MIN_GAP_MS, capped + jitter);
}

export type RenewalStatus = "ready" | "retrying" | "expired" | "missing" | "verification_required";

/** Directive label mapping. A challenged or failed provider is still served
 *  when its grant is usable; DEGRADED/HUMAN_REQUIRED are observability only. */
export type DirectiveSessionState = "READY" | "REFRESHING" | "DEGRADED" | "HUMAN_REQUIRED" | "EXPIRED";

export function directiveSessionState(state: RenewalState): DirectiveSessionState {
  switch (state.status) {
    case "ready":
      return state.nextAttemptAt <= Date.now() ? "REFRESHING" : "READY";
    case "retrying":
      return "DEGRADED";
    case "verification_required":
      return "HUMAN_REQUIRED";
    case "missing":
    case "expired":
      return "EXPIRED";
  }
}
export type SessionSource = "env" | "stored" | "refresh" | "bootstrap";
export interface RenewalState {
  failures: number;
  nextAttemptAt: number;
  checkedAt: number;
  status: RenewalStatus;
  /** Epoch ms when the current session was issued or last rotated. */
  issuedAt?: number;
  /** Epoch ms of the last successful refresh/bootstrap rotation. */
  lastSuccessfulRefresh?: number;
  /** Epoch ms of the last refresh/bootstrap attempt (success or failure). */
  lastRefreshAttempt?: number;
  /** Where the current session came from. */
  sessionSource?: SessionSource;
}

/** Upstream signed refresh vs unsigned bootstrap. Refresh is never used after expiry. */
export interface SessionRenewalOps {
  refresh: (session: CommunitySession) => Promise<SessionRenewalOutcome>;
  bootstrap: (session: CommunitySession) => Promise<SessionRenewalOutcome>;
}

export type SessionRenewalOutcome = {
  expires_at?: string;
  session_id?: string;
  session_secret?: string;
  action?: string;
  challenge?: boolean;
} | null;

export function sessionIsUsable(session: CommunitySession | undefined, now: number): boolean {
  if (!session?.sessionId?.trim() || !session.sessionSecret?.trim()) return false;
  const expires = Date.parse(session.expiresAt);
  return Number.isFinite(expires) && expires > now;
}

/** True when maintainSession would try a signed refresh right now. */
export function refreshIsDue(
  session: CommunitySession | undefined,
  state: RenewalState | undefined,
  now: number,
  force: boolean
): boolean {
  if (!session) return false;
  const expires = Date.parse(session.expiresAt);
  return (
    force ||
    !sessionIsUsable(session, now) ||
    !Number.isFinite(expires) ||
    expires - now <= REFRESH_WINDOW_MS ||
    (state != null && state.nextAttemptAt <= now)
  );
}

function applyRotation(
  current: CommunitySession,
  result: SessionRenewalOutcome,
  now: number
): CommunitySession | null {
  if (!result || typeof result.expires_at !== "string") return null;
  const nextExpiry = Date.parse(result.expires_at);
  if (!Number.isFinite(nextExpiry) || nextExpiry <= now + MIN_VALID_MS) return null;
  return {
    ...current,
    expiresAt: result.expires_at,
    sessionId: typeof result.session_id === "string" && result.session_id ? result.session_id : current.sessionId,
    sessionSecret: typeof result.session_secret === "string" && result.session_secret ? result.session_secret : current.sessionSecret,
  };
}

function needsBootstrap(result: SessionRenewalOutcome): boolean {
  return result?.action === "bootstrap_session" || result?.action === "verify" || result?.challenge === true;
}

function canAttempt(state: RenewalState, now: number, force: boolean, expired: boolean): boolean {
  // A verification challenge is terminal until the scheduled retry or a new grant.
  // Expiry and forced playback retries must not turn it into a bootstrap loop.
  if (state.status === "verification_required") return state.nextAttemptAt <= now;
  if (expired) {
    // Signed-refresh backoff must not strand an already-dead session. Retry
    // unsigned bootstrap at most once a minute until a grant or challenge.
    return now - state.checkedAt >= 60_000 || state.nextAttemptAt <= now;
  }
  return state.nextAttemptAt <= now || (force && now - state.checkedAt >= 60_000);
}

function failedState(
  state: RenewalState,
  now: number,
  expires: number,
  verificationRequired: boolean
): RenewalState {
  const failures = Math.min(state.failures + 1, 10);
  const expired = !(expires > now + PLAYBACK_SKEW_MS);
  const backoff = verificationRequired
    ? 300_000
    : expired
      ? // A dead grant retries unsigned bootstrap at most once a minute until a
        // grant or challenge; the ladder only governs still-usable grants.
        60_000
      : retryBackoffMs(failures);
  return {
    ...state,
    failures,
    checkedAt: now,
    status: verificationRequired ? "verification_required" : expired ? "expired" : "retrying",
    nextAttemptAt: now + backoff,
    lastRefreshAttempt: now,
  };
}

export interface MaintainOptions {
  /** Bounded jitter applied to the next renewal so providers stagger. */
  jitterMs?: number;
  /** Where the incoming session came from when no rotation state exists yet. */
  source?: SessionSource;
}

export async function maintainSession(
  session: CommunitySession | undefined,
  previous: RenewalState | undefined,
  now: number,
  force: boolean,
  ops: SessionRenewalOps,
  options: MaintainOptions = {}
) {
  let current = session;
  let state: RenewalState = previous ?? {
    failures: 0,
    nextAttemptAt: 0,
    checkedAt: now,
    status: "missing",
    sessionSource: options.source ?? "env",
  };
  if (!current) {
    return { session: undefined, state: { ...state, checkedAt: now, status: "missing" as const, nextAttemptAt: now + 3_600_000 } };
  }

  let expires = Date.parse(current.expiresAt);
  const usable = sessionIsUsable(current, now);
  // Idle cadence only after we already have a scheduled renewal (not on first seed).
  const due =
    force ||
    !usable ||
    !Number.isFinite(expires) ||
    expires - now <= REFRESH_WINDOW_MS ||
    (previous != null && state.nextAttemptAt <= now);

  if (due && canAttempt(state, now, force, !usable)) {
    let result: SessionRenewalOutcome = null;
    if (usable) {
      result = await ops.refresh(current).catch(() => null);
      const refreshed = applyRotation(current, result, now);
      if (refreshed) {
        return {
          session: refreshed,
          state: {
            failures: 0,
            checkedAt: now,
            status: "ready" as const,
            nextAttemptAt: nextRenewal(now, Date.parse(refreshed.expiresAt), options.jitterMs),
            issuedAt: now,
            lastSuccessfulRefresh: now,
            lastRefreshAttempt: now,
            sessionSource: "refresh" as const,
          },
        };
      }
      // Upstream only bootstraps on SESSION_INVALID / VERIFY_REQUIRED, not on generic refresh failures.
      if (!needsBootstrap(result)) {
        state = failedState(state, now, Number.isFinite(expires) ? expires : 0, false);
        return { session: current, state };
      }
    }

    result = await ops.bootstrap(current).catch(() => null);
    const restored = applyRotation(current, result, now);
    if (restored) {
      return {
        session: restored,
        state: {
          failures: 0,
          checkedAt: now,
          status: "ready" as const,
          nextAttemptAt: nextRenewal(now, Date.parse(restored.expiresAt), options.jitterMs),
          issuedAt: now,
          lastSuccessfulRefresh: now,
          lastRefreshAttempt: now,
          sessionSource: "bootstrap" as const,
        },
      };
    }
    state = failedState(state, now, Number.isFinite(expires) ? expires : 0, needsBootstrap(result));
  } else if (!due) {
    const ideal = nextRenewal(now, expires, options.jitterMs);
    const scheduled =
      previous && previous.status === "ready" && previous.nextAttemptAt > now
        ? Math.min(previous.nextAttemptAt, ideal)
        : ideal;
    state = {
      ...state,
      failures: 0,
      checkedAt: now,
      status: "ready",
      nextAttemptAt: scheduled,
      sessionSource: state.sessionSource ?? options.source ?? (previous ? "stored" : "env"),
    };
  } else if (expires <= now + PLAYBACK_SKEW_MS && state.status !== "verification_required") {
    state = { ...state, status: "expired" };
  }
  return { session: current, state };
}
