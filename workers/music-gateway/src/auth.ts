import type { Env } from "./types";

export interface SyncIdentity {
  userId: string;
}

export type SyncAuthentication =
  | { ok: true; identity: SyncIdentity }
  | { ok: false; status: 401 | 503; error: string };

interface SyncTokenClaims {
  sub?: string;
  exp?: number;
  iat?: number;
  aud?: string;
  iss?: string;
}

type FirebaseJwk = JsonWebKey & { kid?: string };

let firebaseKeys: { expiresAt: number; values: Record<string, FirebaseJwk> } | null = null;

export function isDevelopment(env: Env): boolean {
  return env.ENVIRONMENT === "development" || env.NODE_ENV === "development";
}

export async function authenticateSyncRequest(request: Request, env: Env): Promise<SyncAuthentication> {
  const firebaseProjectId = env.FIREBASE_PROJECT_ID?.trim();
  const token = bearerToken(request.headers.get("Authorization"));
  if (firebaseProjectId) {
    if (!token) return { ok: false, status: 401, error: "sync_auth_required" };
    const claims = await verifyFirebaseToken(token, firebaseProjectId);
    const userId = normalizeUserId(claims?.sub);
    if (!userId) return { ok: false, status: 401, error: "invalid_sync_token" };
    return { ok: true, identity: { userId } };
  }

  // HMAC tokens are retained solely for explicit local development. A production
  // deployment without a Firebase project must never accept an alternate identity.
  if (!isDevelopment(env)) {
    return { ok: false, status: 503, error: "sync_auth_not_configured" };
  }

  const secret = env.SYNC_AUTH_SECRET?.trim();
  if (!secret) {
    const configuredDevUser = normalizeUserId(env.DEV_SYNC_USER_ID);
    const requestedDevUser = normalizeUserId(request.headers.get("X-Dev-Sync-User"));
    if (configuredDevUser && requestedDevUser === configuredDevUser) {
      return { ok: true, identity: { userId: configuredDevUser } };
    }
    return { ok: false, status: 401, error: "sync_auth_required" };
  }

  if (!token) return { ok: false, status: 401, error: "sync_auth_required" };

  const claims = await verifySignedToken(token, secret);
  const userId = normalizeUserId(claims?.sub);
  const now = Math.floor(Date.now() / 1000);
  if (!userId || !claims?.exp || !Number.isInteger(claims.exp) || claims.exp <= now) {
    return { ok: false, status: 401, error: "invalid_sync_token" };
  }

  return { ok: true, identity: { userId } };
}

/** Test-only cache reset for deterministic Firebase JWK fixtures. */
export function resetFirebaseKeyCacheForTest(): void {
  firebaseKeys = null;
}

async function verifyFirebaseToken(token: string, projectId: string): Promise<SyncTokenClaims | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [encodedHeader, encodedPayload, encodedSignature] = parts;
  if (!encodedHeader || !encodedPayload || !encodedSignature) return null;
  try {
    const header = JSON.parse(decodeBase64Url(encodedHeader)) as { alg?: string; kid?: string };
    if (header.alg !== "RS256" || !header.kid) return null;
    const claims = JSON.parse(decodeBase64Url(encodedPayload)) as SyncTokenClaims;
    const now = Math.floor(Date.now() / 1000);
    if (
      !claims.sub || !claims.exp || !claims.iat || claims.exp <= now || claims.iat > now ||
      claims.aud !== projectId || claims.iss !== `https://securetoken.google.com/${projectId}`
    ) return null;

    const keys = await firebaseKeysForVerification();
    const jwk = keys[header.kid];
    if (!jwk) return null;
    const key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );
    const verified = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      key,
      decodeBase64UrlToBytes(encodedSignature),
      new TextEncoder().encode(`${encodedHeader}.${encodedPayload}`)
    );
    return verified ? claims : null;
  } catch {
    return null;
  }
}

async function firebaseKeysForVerification(): Promise<Record<string, FirebaseJwk>> {
  if (firebaseKeys && firebaseKeys.expiresAt > Date.now()) return firebaseKeys.values;
  const response = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!response.ok) throw new Error("firebase_keys_unavailable");
  const cacheControl = response.headers.get("Cache-Control") ?? "";
  const maxAge = Number.parseInt(cacheControl.match(/max-age=(\d+)/)?.[1] ?? "3600", 10);
  const payload = await response.json() as { keys?: FirebaseJwk[] };
  const values = Object.fromEntries((payload.keys ?? []).flatMap((key) => key.kid ? [[key.kid, key]] : []));
  firebaseKeys = { values, expiresAt: Date.now() + Math.max(60, maxAge) * 1000 };
  return values;
}

function bearerToken(value: string | null): string | null {
  if (!value?.startsWith("Bearer ")) return null;
  const token = value.slice("Bearer ".length).trim();
  return token || null;
}

async function verifySignedToken(token: string, secret: string): Promise<SyncTokenClaims | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [encodedHeader, encodedPayload, encodedSignature] = parts;
  if (!encodedHeader || !encodedPayload || !encodedSignature) return null;

  try {
    const header = JSON.parse(decodeBase64Url(encodedHeader)) as { alg?: string; typ?: string };
    if (header.alg !== "HS256" || header.typ !== "JWT") return null;

    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    );
    const valid = await crypto.subtle.verify(
      "HMAC",
      key,
      decodeBase64UrlToBytes(encodedSignature),
      new TextEncoder().encode(`${encodedHeader}.${encodedPayload}`)
    );
    if (!valid) return null;
    return JSON.parse(decodeBase64Url(encodedPayload)) as SyncTokenClaims;
  } catch {
    return null;
  }
}

function normalizeUserId(value: string | undefined | null): string | null {
  // Firebase subjects are opaque, case-sensitive identifiers. Do not transform
  // them before using them as the canonical cloud identity.
  const normalized = value?.trim();
  return normalized && normalized.length <= 128 && !/[\u0000-\u001f\u007f]/.test(normalized) ? normalized : null;
}

function decodeBase64Url(value: string): string {
  return new TextDecoder().decode(decodeBase64UrlToBytes(value));
}

function decodeBase64UrlToBytes(value: string): Uint8Array {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (value.length % 4)) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}
