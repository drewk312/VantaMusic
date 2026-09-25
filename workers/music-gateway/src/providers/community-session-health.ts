import type { Env } from "../types";
import { COMMUNITY_MIN_SKEW_MS, communitySessionFromEnv } from "./community-session";

/** Warn / soft-renew window before COMMUNITY_SESSION_EXPIRES (desktop CAPTCHA cliff). */
export const COMMUNITY_EXPIRY_WARN_MS = 12 * 60 * 60 * 1000;

export type SessionFreshness = "missing" | "expired" | "expiring" | "ok";

export interface NamedSessionHealth {
  name: string;
  present: boolean;
  expiresAt: string | null;
  remainingMs: number | null;
  status: SessionFreshness;
  renewCommand: string;
  /** Desktop COMMUNITY verify has no silent /session/refresh (404). Zarz does. */
  silentRefresh: boolean;
}

type ZarzName = "amazon" | "tidal" | "qobuz" | "deezer";

function remainingFrom(expiresAt: string | null): number | null {
  if (!expiresAt) return null;
  const ms = Date.parse(expiresAt) - Date.now();
  return Number.isFinite(ms) ? ms : null;
}

function statusFromRemaining(idsPresent: boolean, usable: boolean, remainingMs: number | null): SessionFreshness {
  if (!idsPresent) return "missing";
  if (!usable) return "expired";
  if (remainingMs != null && remainingMs < COMMUNITY_EXPIRY_WARN_MS) return "expiring";
  return "ok";
}

export function communityDesktopSessionHealth(env: Env): NamedSessionHealth {
  const idsPresent = Boolean(env.COMMUNITY_SESSION_ID?.trim() && env.COMMUNITY_SESSION_SECRET?.trim());
  const live = communitySessionFromEnv(env);
  const expiresAt = env.COMMUNITY_SESSION_EXPIRES?.trim() || null;
  const remainingMs = remainingFrom(expiresAt);
  return {
    name: "community_desktop",
    present: Boolean(live),
    expiresAt,
    remainingMs,
    status: statusFromRemaining(idsPresent, Boolean(live), remainingMs),
    renewCommand: "node scripts/community-session.mjs desktop",
    silentRefresh: false,
  };
}

function zarzUsable(env: Env, provider: ZarzName, forRenewal = false): boolean {
  const prefix = `ZARZ_${provider.toUpperCase()}`;
  const sessionId = env[`${prefix}_SESSION_ID` as keyof Env];
  const sessionSecret = env[`${prefix}_SESSION_SECRET` as keyof Env];
  const expiresAt = env[`${prefix}_SESSION_EXPIRES` as keyof Env];
  if (typeof sessionId !== "string" || typeof sessionSecret !== "string") return false;
  if (!sessionId.trim() || !sessionSecret.trim()) return false;
  if (typeof expiresAt !== "string" || !expiresAt.trim()) return false;
  const expiresAtMs = Date.parse(expiresAt);
  if (!Number.isFinite(expiresAtMs)) return false;
  if (!forRenewal && expiresAtMs - Date.now() < COMMUNITY_MIN_SKEW_MS) return false;
  return expiresAtMs > Date.now();
}

export function zarzProviderSessionHealth(env: Env, provider: ZarzName): NamedSessionHealth {
  const prefix = `ZARZ_${provider.toUpperCase()}`;
  const sessionId = env[`${prefix}_SESSION_ID` as keyof Env];
  const sessionSecret = env[`${prefix}_SESSION_SECRET` as keyof Env];
  const expiresRaw = env[`${prefix}_SESSION_EXPIRES` as keyof Env];
  const expiresAt = typeof expiresRaw === "string" ? expiresRaw.trim() || null : null;
  const idsPresent = typeof sessionId === "string" && typeof sessionSecret === "string" &&
    Boolean(sessionId.trim() && sessionSecret.trim());
  const usable = zarzUsable(env, provider);
  const renewWindow = zarzUsable(env, provider, true);
  const remainingMs = remainingFrom(expiresAt);
  const status: SessionFreshness = !idsPresent
    ? "missing"
    : usable
      ? statusFromRemaining(true, true, remainingMs)
      : renewWindow
        ? "expiring"
        : "expired";
  return {
    name: `zarz_${provider}`,
    present: usable,
    expiresAt,
    remainingMs,
    status,
    renewCommand: `node scripts/setup-extension-sessions.mjs ${provider}`,
    silentRefresh: true,
  };
}

export function gatewaySessionHealth(env: Env): NamedSessionHealth[] {
  return [
    communityDesktopSessionHealth(env),
    zarzProviderSessionHealth(env, "amazon"),
    zarzProviderSessionHealth(env, "tidal"),
    zarzProviderSessionHealth(env, "qobuz"),
    zarzProviderSessionHealth(env, "deezer"),
  ];
}

/** Cron / request log helper — loud when a CAPTCHA cliff is approaching. */
export function logSessionExpiryWarnings(env: Env): void {
  for (const entry of gatewaySessionHealth(env)) {
    if (entry.status === "ok" || entry.status === "missing") continue;
    console.warn(
      "VANTA_SESSION_EXPIRY",
      JSON.stringify({
        name: entry.name,
        status: entry.status,
        expiresAt: entry.expiresAt,
        remainingMs: entry.remainingMs,
        silentRefresh: entry.silentRefresh,
        renewCommand: entry.renewCommand,
      })
    );
  }
}
