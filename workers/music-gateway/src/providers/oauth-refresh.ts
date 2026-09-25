import type { Env } from "../types";

/** Public Tidal Android/desktop client id used by the device-code / PKCE flow. */
export const TIDAL_OAUTH_CLIENT_ID = "txNoH4kkV41MfH25";
const TIDAL_TOKEN_URL = "https://auth.tidal.com/v1/oauth2/token";
const REFRESH_SKEW_MS = 120_000;

export interface OauthTokens {
  accessToken: string;
  refreshToken?: string;
  expiresAtMs?: number;
  clientId?: string;
}

export function hasByoaHeaders(request?: Request): boolean {
  if (!request) return false;
  return Boolean(
    request.headers.get("X-Qobuz-Token")?.trim() ||
      request.headers.get("X-Tidal-Token")?.trim() ||
      request.headers.get("X-Deezer-Arl")?.trim() ||
      request.headers.get("X-Amazon-Token")?.trim()
  );
}

export function parseTidalOauth(raw: string | undefined | null): OauthTokens | null {
  const value = raw?.trim();
  if (!value) return null;
  if (value.startsWith("{")) {
    try {
      const parsed = JSON.parse(value) as Record<string, unknown>;
      const access = stringField(parsed, "access_token") ?? stringField(parsed, "accessToken");
      if (!access) return null;
      const refresh = stringField(parsed, "refresh_token") ?? stringField(parsed, "refreshToken");
      const clientId = stringField(parsed, "client_id") ?? stringField(parsed, "clientId");
      return {
        accessToken: access,
        refreshToken: refresh,
        expiresAtMs: expiryMs(parsed),
        clientId: clientId,
      };
    } catch {
      return null;
    }
  }
  if (value.length >= 16 && !value.includes(" ")) return { accessToken: value };
  return null;
}

export function tidalAccessExpired(tokens: OauthTokens, now = Date.now()): boolean {
  if (!tokens.expiresAtMs) return false;
  return tokens.expiresAtMs - now <= REFRESH_SKEW_MS;
}

export async function refreshTidalAccessToken(
  tokens: OauthTokens,
  clientId = tokens.clientId ?? TIDAL_OAUTH_CLIENT_ID
): Promise<OauthTokens | null> {
  if (!tokens.refreshToken) return null;
  let response: Response;
  try {
    response = await fetch(TIDAL_TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: tokens.refreshToken,
        client_id: clientId,
      }),
      signal: AbortSignal.timeout(8_000),
    });
  } catch {
    console.warn("VANTA_OAUTH_REFRESH", JSON.stringify({ provider: "tidal", ok: false, reason: "fetch_failed" }));
    return null;
  }
  if (!response.ok) {
    console.warn("VANTA_OAUTH_REFRESH", JSON.stringify({ provider: "tidal", ok: false, status: response.status }));
    return null;
  }
  const payload = (await response.json().catch(() => null)) as Record<string, unknown> | null;
  const access = payload ? stringField(payload, "access_token") : null;
  if (!access) return null;
  const refresh = (payload ? stringField(payload, "refresh_token") : null) ?? tokens.refreshToken;
  const expiresIn = numberField(payload, "expires_in");
  console.log("VANTA_OAUTH_REFRESH", JSON.stringify({ provider: "tidal", ok: true, hasRefresh: Boolean(refresh) }));
  return {
    accessToken: access,
    refreshToken: refresh,
    expiresAtMs: expiresIn ? Date.now() + expiresIn * 1000 : undefined,
    clientId,
  };
}

/** PKCE/device-flow access token: refresh locally when expired, else use the bearer as-is. */
export async function resolveTidalAccessToken(env: Env, request?: Request): Promise<string | null> {
  const header = request?.headers.get("X-Tidal-Token") ?? request?.headers.get("x-tidal-token");
  const parsed = parseTidalOauth(header);
  if (parsed) {
    if (parsed.refreshToken && tidalAccessExpired(parsed)) {
      const refreshed = await refreshTidalAccessToken(parsed, parsed.clientId ?? env.TIDAL_OAUTH_CLIENT_ID ?? TIDAL_OAUTH_CLIENT_ID);
      if (refreshed?.accessToken) return refreshed.accessToken;
    }
    if (parsed.accessToken) return parsed.accessToken;
  }
  const envKey = env.TIDAL_API_KEY?.trim();
  return envKey || null;
}

function stringField(record: Record<string, unknown>, key: string): string | undefined {
  const value = record[key];
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function numberField(record: Record<string, unknown> | null, key: string): number | undefined {
  const value = record?.[key];
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && /^\d+$/.test(value)) return Number(value);
  return undefined;
}

function expiryMs(parsed: Record<string, unknown>): number | undefined {
  const expiresAt = parsed.expires_at ?? parsed.expiresAt;
  if (typeof expiresAt === "number" && Number.isFinite(expiresAt)) {
    return expiresAt < 32_000_000_000 ? expiresAt * 1000 : expiresAt;
  }
  if (typeof expiresAt === "string" && expiresAt.trim()) {
    const parsedMs = Date.parse(expiresAt);
    if (Number.isFinite(parsedMs)) return parsedMs;
  }
  const expiresIn = numberField(parsed, "expires_in");
  return expiresIn ? Date.now() + expiresIn * 1000 : undefined;
}
