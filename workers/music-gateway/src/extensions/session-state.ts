import { readStoredSession, writeStoredSession } from "./session-storage";
import { DurableObject } from "cloudflare:workers";
import type { Env } from "../types";
import type { CommunitySession } from "../providers/community-session";
import { bootstrapZarzSession, refreshZarzSession, sessionFromEnv, type ZarzProvider } from "../providers/zarz-signed";
import { shouldAdoptSession } from "./session-policy";
import { maintainSession, refreshIsDue, staggerJitterMs, type RenewalState, type RenewalStatus, type SessionSource } from "./session-maintenance";

/**
 * Canonical session lifecycle states exposed to diagnostics (Goal A):
 * READY       healthy, proactive renewal already scheduled
 * REFRESHING  signed refresh/bootstrap in flight (observability only; playback keeps serving)
 * DEGRADED    session still usable but ambient refresh is failing (bounded backoff)
 * HUMAN_REQUIRED upstream answered verification_required — provider is parked
 * EXPIRED     no usable grant; unsigned bootstrap is the only path left
 */
export type CanonicalSessionState = "READY" | "REFRESHING" | "DEGRADED" | "HUMAN_REQUIRED" | "EXPIRED";

export function canonicalSessionState(status: RenewalStatus | string | undefined): CanonicalSessionState {
  switch (status) {
    case "ready": return "READY";
    case "refreshing": return "REFRESHING";
    case "retrying": return "DEGRADED";
    case "verification_required": return "HUMAN_REQUIRED";
    case "expired": return "EXPIRED";
    case "missing": return "EXPIRED";
    default: return "EXPIRED";
  }
}

/** Stored rotation plus alarms; cron repairs missing alarms without listener traffic. */
export class ExtensionSessions extends DurableObject<Env> {
  private pending?: Promise<CommunitySession | null>;
  private get encryptionSecret(): string {
    return this.env.SESSION_ENCRYPTION_KEY || this.env.EXTENSION_PROXY_SECRET || "";
  }
  async getSession(provider: ZarzProvider, forceRefresh = false): Promise<CommunitySession | null> {
    if (!["amazon", "deezer", "qobuz", "tidal"].includes(provider)) return null;
    if (this.pending) {
      // An in-flight load answers non-forced callers, but a forced refresh
      // (e.g. bind-renewal) must not be silently swallowed by it.
      if (!forceRefresh) return this.pending;
      return this.pending.then(() => this.getSession(provider, true));
    }
    this.pending = this.loadSession(provider, forceRefresh);
    try { return await this.pending; } finally { this.pending = undefined; }
  }
  async health() {
    const state = await this.ctx.storage.get<RenewalState>("renewal");
    const refreshing = await this.ctx.storage.get<boolean>("refreshing");
    const session = await readStoredSession(this.ctx.storage, this.encryptionSecret);
    const rawStatus = refreshing ? "refreshing" : state?.status;
    // "refreshing" is a persisted in-flight flag, not a terminal RenewalStatus.
    const status: RenewalStatus | "not_checked" | "refreshing" = refreshing
      ? "refreshing"
      : state?.status ?? "not_checked";
    const upstream = {
      ...state,
      status,
      checkedAt: state?.checkedAt ?? null,
      nextAttemptAt: state?.nextAttemptAt ?? null,
      expiresAt: session?.expiresAt ?? null,
      issuedAt: state?.issuedAt ?? null,
      lastSuccessfulRefresh: state?.lastSuccessfulRefresh ?? null,
      lastRefreshAttempt: state?.lastRefreshAttempt ?? null,
      consecutiveRefreshFailures: state?.failures ?? 0,
      source: state?.sessionSource ?? null,
      canonical: canonicalSessionState(rawStatus),
    } as RenewalState & {
      expiresAt: string | null;
      issuedAt: number | null;
      lastSuccessfulRefresh: number | null;
      lastRefreshAttempt: number | null;
      consecutiveRefreshFailures: number;
      source: SessionSource | null;
      canonical: CanonicalSessionState;
    };
    return upstream;
  }
  private async loadSession(provider: ZarzProvider, force: boolean): Promise<CommunitySession | null> {
    let session = await readStoredSession(this.ctx.storage, this.encryptionSecret);
    let state = await this.ctx.storage.get<RenewalState>("renewal");
    // Expired seeds may only be sent to unsigned bootstrap, never to playback.
    const initial = sessionFromEnv(this.env, provider, !session);
    let source: SessionSource;
    if (initial && shouldAdoptSession(initial, session)) {
      session = initial;
      state = undefined;
      source = "env";
    } else {
      source = session ? "stored" : "env";
    }
    await this.ctx.storage.put("provider", provider);
    const now = Date.now();
    const jitterMs = staggerJitterMs(`${provider}:${session?.installId ?? ""}`);
    // Persist a retry before network I/O so a worker termination cannot strand the session.
    await this.ctx.storage.setAlarm(Math.max(now + 60000, state?.nextAttemptAt ?? 0));
    if (refreshIsDue(session, state, now, force)) {
      // Goal A: an in-flight renewal is observable without blocking playback.
      await this.ctx.storage.put("refreshing", true);
    }
    const result = await maintainSession(session, state, now, force, {
      refresh: refreshZarzSession,
      bootstrap: bootstrapZarzSession,
    }, { jitterMs, source });
    await this.ctx.storage.delete("refreshing");
    if (result.session) await writeStoredSession(this.ctx.storage, result.session, this.encryptionSecret);
    await this.ctx.storage.put("renewal", result.state);
    await this.ctx.storage.setAlarm(Math.max(Date.now() + 60000, result.state.nextAttemptAt));
    const valid = result.session && Date.parse(result.session.expiresAt) > Date.now() + 30000;
    if (force && result.state.status !== "ready") return null;
    return valid ? result.session! : null;
  }
  async alarm(): Promise<void> {
    const provider = await this.ctx.storage.get<ZarzProvider>("provider");
    if (provider) await this.getSession(provider);
  }
}
