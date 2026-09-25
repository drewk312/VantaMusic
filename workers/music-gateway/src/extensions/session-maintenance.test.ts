import { it } from "node:test";
import assert from "node:assert/strict";
import { maintainSession, nextRenewal, refreshIsDue, staggerJitterMs, type SessionRenewalOps } from "./session-maintenance";

const now = Date.parse("2026-09-09T00:00:00Z");
const session = {
  installId: "test",
  sessionId: "old",
  sessionSecret: "secret",
  platform: "extension",
  appVersion: "test",
  expiresAt: new Date(now - 60000).toISOString(),
};

function ops(partial: Partial<SessionRenewalOps>): SessionRenewalOps {
  return {
    refresh: async () => {
      throw new Error("signed refresh should not run");
    },
    bootstrap: async () => {
      throw new Error("unsigned bootstrap should not run");
    },
    ...partial,
  };
}

it("restores an expired session only through unsigned bootstrap", async () => {
  let refreshCalls = 0;
  const r = await maintainSession(
    session,
    undefined,
    now,
    false,
    ops({
      refresh: async () => {
        refreshCalls++;
        return { expires_at: new Date(now + 3600000).toISOString() };
      },
      bootstrap: async () => ({
        session_id: "new",
        session_secret: "rotated",
        expires_at: new Date(now + 3600000).toISOString(),
      }),
    })
  );
  assert.equal(refreshCalls, 0);
  assert.equal(r.session?.sessionId, "new");
  assert.equal(r.state.status, "ready");
  assert.equal(r.state.nextAttemptAt, now + 1800000);
});

it("keeps an expired credential expired and schedules retry on bootstrap rejection", async () => {
  const r = await maintainSession(session, undefined, now, false, ops({ bootstrap: async () => null }));
  assert.equal(r.session?.expiresAt, session.expiresAt);
  assert.equal(r.state.status, "expired");
  assert.equal(r.state.nextAttemptAt, now + 60000);
  let calls = 0;
  const next = await maintainSession(
    r.session,
    r.state,
    now + 1000,
    true,
    ops({
      bootstrap: async () => {
        calls++;
        return null;
      },
    })
  );
  assert.equal(calls, 0);
  assert.equal(next.state.nextAttemptAt, r.state.nextAttemptAt);
});

it("marks a bootstrap challenge as verification_required without extending expiry", async () => {
  const r = await maintainSession(
    session,
    undefined,
    now,
    false,
    ops({ bootstrap: async () => ({ action: "verify", challenge: true }) })
  );
  assert.equal(r.session?.sessionId, "old");
  assert.equal(r.state.status, "verification_required");
  assert.equal(r.state.nextAttemptAt, now + 300000);
});

it("bounded temporary refresh retries follow the 1/5/15-minute ladder while the grant stays usable", async () => {
  const active = { ...session, expiresAt: new Date(now + 45 * 60000).toISOString() };
  let refreshCalls = 0;
  const a = await maintainSession(active, undefined, now, false, ops({
    refresh: async () => { refreshCalls++; return null; },
  }));
  assert.equal(a.state.status, "retrying");
  assert.equal(a.state.failures, 1);
  assert.equal(a.state.nextAttemptAt, now + 60000);
  const b = await maintainSession(a.session, a.state, a.state.nextAttemptAt, false, ops({
    refresh: async () => { refreshCalls++; return null; },
  }));
  assert.equal(b.state.failures, 2);
  assert.equal(b.state.nextAttemptAt, now + 360000);
  const c = await maintainSession(b.session, b.state, b.state.nextAttemptAt, false, ops({
    refresh: async () => { refreshCalls++; return null; },
  }));
  assert.equal(c.state.failures, 3);
  assert.equal(c.state.nextAttemptAt, now + 360000 + 900000);
  assert.equal(refreshCalls, 3);
});

it("recovers after a transient bootstrap outage with persistent exponential backoff", async () => {
  const a = await maintainSession(
    session,
    undefined,
    now,
    false,
    ops({
      bootstrap: async () => {
        throw Error("offline");
      },
    })
  );
  const b = await maintainSession(a.session, a.state, now + 60000, false, ops({ bootstrap: async () => null }));
  assert.equal(b.state.nextAttemptAt, now + 120000);
  const c = await maintainSession(
    b.session,
    b.state,
    now + 120000,
    false,
    ops({ bootstrap: async () => ({ expires_at: new Date(now + 3600000).toISOString() }) })
  );
  assert.equal(c.state.failures, 0);
  assert.equal(c.state.status, "ready");
});

it("does not hammer healthy sessions or manufacture missing credentials", async () => {
  let calls = 0;
  const refresh = async () => {
    calls++;
    return null;
  };
  await maintainSession(
    { ...session, expiresAt: new Date(now + 5 * 3600000).toISOString() },
    undefined,
    now,
    false,
    ops({ refresh })
  );
  const missing = await maintainSession(undefined, undefined, now, false, ops({ refresh }));
  assert.equal(calls, 0);
  assert.equal(missing.state.status, "missing");
});

it("allows a throttled forced refresh before the normal expiry window", async () => {
  let calls = 0;
  await maintainSession(
    { ...session, expiresAt: new Date(now + 3600000).toISOString() },
    { failures: 0, checkedAt: now - 60000, status: "ready", nextAttemptAt: now + 3000000 },
    now,
    true,
    ops({
      refresh: async () => {
        calls++;
        return null;
      },
    })
  );
  assert.equal(calls, 1);
});

it("renews a still-valid session with signed refresh before expiry", async () => {
  let bootstrapCalls = 0;
  let calls = 0;
  const active = { ...session, expiresAt: new Date(now + 45 * 60000).toISOString() };
  const a = await maintainSession(
    active,
    undefined,
    now,
    false,
    ops({
      refresh: async () => {
        calls++;
        return null;
      },
      bootstrap: async () => {
        bootstrapCalls++;
        return null;
      },
    })
  );
  assert.equal(calls, 1);
  assert.equal(bootstrapCalls, 0);
  assert.equal(a.state.status, "retrying");
  await maintainSession(
    a.session,
    a.state,
    now + 1000,
    false,
    ops({
      refresh: async () => {
        calls++;
        return null;
      },
    })
  );
  assert.equal(calls, 1);
});

it("bootstraps after SESSION_INVALID instead of retrying a revoked secret", async () => {
  const active = { ...session, expiresAt: new Date(now + 45 * 60000).toISOString() };
  let refreshCalls = 0;
  const r = await maintainSession(
    active,
    undefined,
    now,
    false,
    ops({
      refresh: async () => {
        refreshCalls++;
        return { action: "bootstrap_session" };
      },
      bootstrap: async () => ({
        session_id: "boot",
        session_secret: "next",
        expires_at: new Date(now + 3600000).toISOString(),
      }),
    })
  );
  assert.equal(refreshCalls, 1);
  assert.equal(r.session?.sessionId, "boot");
  assert.equal(r.state.status, "ready");
});

it("does not repeatedly renew a short accepted grant", async () => {
  let calls = 0;
  const bootstrap = async () => {
    calls++;
    return { expires_at: new Date(now + 20 * 60000).toISOString() };
  };
  const a = await maintainSession(session, undefined, now, false, ops({ bootstrap }));
  assert.equal(a.state.nextAttemptAt, now + 10 * 60000);
  await maintainSession(a.session, a.state, now + 60000, false, ops({ bootstrap }));
  assert.equal(calls, 1);
});

it("does not inherit signed-refresh backoff for an already expired session", async () => {
  let calls = 0;
  const r = await maintainSession(
    session,
    { failures: 6, nextAttemptAt: now + 32 * 60000, checkedAt: now - 5 * 60000, status: "expired" },
    now,
    false,
    ops({
      bootstrap: async () => {
        calls++;
        return {
          session_id: "boot",
          session_secret: "next",
          expires_at: new Date(now + 3600000).toISOString(),
        };
      },
    })
  );
  assert.equal(calls, 1);
  assert.equal(r.state.status, "ready");
  assert.equal(r.session?.sessionId, "boot");
});

it("healthy polling does not postpone the pre-expiry alarm", async () => {
  const active = { ...session, expiresAt: new Date(now + 5 * 3600000).toISOString() };
  const a = await maintainSession(active, undefined, now, false, ops({ refresh: async () => null }));
  const b = await maintainSession(a.session, a.state, now + 5 * 60000, false, ops({ refresh: async () => null }));
  // Long grants renew at most every 2h (MAX_IDLE), not only in the final hour.
  assert.equal(a.state.nextAttemptAt, now + 2 * 3600000);
  assert.equal(b.state.nextAttemptAt, a.state.nextAttemptAt);
});

it("renews on the idle cadence before the expiry window", async () => {
  let calls = 0;
  const active = { ...session, expiresAt: new Date(now + 5 * 3600000).toISOString() };
  const a = await maintainSession(active, undefined, now, false, ops({ refresh: async () => null }));
  await maintainSession(
    a.session,
    a.state,
    a.state.nextAttemptAt,
    false,
    ops({
      refresh: async () => {
        calls++;
        return { expires_at: new Date(now + 6 * 3600000).toISOString() };
      },
    })
  );
  assert.equal(calls, 1);
});

it("bounded jitter lands renewal inside the 50-65% lifetime band", () => {
  // 3.5h grant: half-life (1.75h) governs because the 2h idle cadence does not clobber it.
  const expires = now + 3.5 * 3600000;
  const jitter = staggerJitterMs("qobuz:install-a");
  const target = nextRenewal(now, expires, jitter);
  const consumed = (target - now) / (expires - now);
  assert.ok(consumed >= 0.5, "should not refresh before the 50% mark");
  assert.ok(consumed <= 0.65, "should not refresh after the 65% mark");
});

it("still honors the aggressive 2h idle cadence for long grants", () => {
  const expires = now + 24 * 3600000;
  const target = nextRenewal(now, expires, staggerJitterMs("amazon:install-a"));
  assert.ok(target <= now + 2 * 3600000 + 12 * 60000);
});

it("jitter is deterministic per provider and diverges between providers", () => {
  const a = staggerJitterMs("qobuz:install-a");
  const b = staggerJitterMs("qobuz:install-a");
  const c = staggerJitterMs("tidal:install-a");
  assert.equal(a, b);
  assert.notEqual(a, c);
});

it("records refresh/rotation observability fields on successful renewal", async () => {
  const active = { ...session, expiresAt: new Date(now + 45 * 60000).toISOString() };
  const r = await maintainSession(
    active,
    undefined,
    now,
    false,
    ops({
      refresh: async () => ({ session_id: "next", session_secret: "secret", expires_at: new Date(now + 3600000).toISOString() }),
    }),
    { jitterMs: 240000, source: "env" }
  );
  assert.equal(r.state.sessionSource, "refresh");
  assert.equal(r.state.issuedAt, now);
  assert.equal(r.state.lastSuccessfulRefresh, now);
  assert.equal(r.state.lastRefreshAttempt, now);
  assert.equal(r.state.failures, 0);
  assert.ok(r.state.nextAttemptAt > now + 30 * 60000);
});

it("remembers the previous successful rotation after a transient refresh failure", async () => {
  const active = { ...session, expiresAt: new Date(now + 45 * 60000).toISOString() };
  const r = await maintainSession(
    active,
    { failures: 0, checkedAt: now - 120000, status: "ready", nextAttemptAt: now, issuedAt: now - 600000, lastSuccessfulRefresh: now - 600000, sessionSource: "env" },
    now,
    false,
    ops({ refresh: async () => null })
  );
  assert.equal(r.state.status, "retrying");
  assert.equal(r.state.failures, 1);
  assert.equal(r.state.lastRefreshAttempt, now);
  assert.equal(r.state.lastSuccessfulRefresh, now - 600000);
  assert.equal(r.state.sessionSource, "env");
});

it("refreshIsDue matches maintainSession scheduling", async () => {
  const futureSession = { installId: "t", sessionId: "s", sessionSecret: "sec", platform: "extension", appVersion: "t", expiresAt: new Date(now + 4 * 3600000).toISOString() };
  assert.equal(refreshIsDue(undefined, undefined, now, false), false);
  assert.equal(refreshIsDue(undefined, undefined, now, true), false);
  assert.equal(refreshIsDue(futureSession, undefined, now, false), false);
  assert.equal(refreshIsDue(futureSession, undefined, now, true), true);
  const state = { failures: 0, nextAttemptAt: now - 1000, checkedAt: now, status: "ready" as const };
  assert.equal(refreshIsDue(futureSession, state, now, false), true);
  const scheduled: typeof state = { ...state, nextAttemptAt: now + 3600000 };
  assert.equal(refreshIsDue(futureSession, scheduled, now, false), false);
  const nearExpiry = { ...futureSession, expiresAt: new Date(now + 2 * 3600000).toISOString() };
  assert.equal(refreshIsDue(nearExpiry, scheduled, now, false), true);
});

it("preserves verification status and backoff during expired-session playback retries", async () => {
  let calls = 0;
  const operations = ops({ bootstrap: async () => { calls++; return { action: "verify", challenge: true }; } });
  const challenged = await maintainSession(session, undefined, now, false, operations);
  for (const offset of [1000, 60000, 120000, 299999]) {
    const waiting = await maintainSession(challenged.session, challenged.state, now + offset, true, operations);
    assert.equal(waiting.state.status, "verification_required");
    assert.equal(waiting.state.nextAttemptAt, now + 300000);
  }
  assert.equal(calls, 1);
  const recovered = await maintainSession(challenged.session, challenged.state, now + 300000, false,
    ops({ bootstrap: async () => ({ session_id: "renewed", session_secret: "renewed-secret", expires_at: new Date(now + 3600000).toISOString() }) }));
  assert.equal(recovered.state.status, "ready");
  assert.equal(recovered.state.failures, 0);
});
