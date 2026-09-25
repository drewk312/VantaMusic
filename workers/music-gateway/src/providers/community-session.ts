import type { Env } from "../types";

export interface CommunitySession {
  installId: string;
  sessionId: string;
  sessionSecret: string;
  expiresAt: string;
  appVersion: string;
  platform: string;
}

export interface SignedSessionContract {
  schemeLabel: string;
  headerPrefix: string;
  timeWindowSeconds: number;
  userAgent: string;
  rollingKeyEncoding: "raw" | "base64url";
}

export const COMMUNITY_DEFAULT_APP_VERSION = "unknown";
export const COMMUNITY_MIN_SKEW_MS = 5 * 60 * 1000;

const COMMUNITY_SIGNING_SCHEME = "SPOTIFLAC-HMAC-V1";

/**
 * Load the persisted SpotiFLAC community session from operator env vars.
 * Falls back to the upstream default app version so a session minted by the
 * stock desktop client (SpotiFLAC/<version>) still verifies.
 */
export function communitySessionFromEnv(env: Env): CommunitySession | null {
  const sessionId = env.COMMUNITY_SESSION_ID?.trim();
  const sessionSecret = env.COMMUNITY_SESSION_SECRET?.trim();
  if (!sessionId || !sessionSecret) return null;

  const expiresAt = env.COMMUNITY_SESSION_EXPIRES?.trim();
  if (expiresAt) {
    const parsed = Date.parse(expiresAt);
    if (Number.isFinite(parsed) && parsed - Date.now() < COMMUNITY_MIN_SKEW_MS) {
      return null;
    }
  }

  return {
    installId: env.COMMUNITY_INSTALL_ID?.trim() || "shared-gateway-install",
    sessionId,
    sessionSecret,
    expiresAt: expiresAt || "",
    appVersion: env.COMMUNITY_APP_VERSION?.trim() || COMMUNITY_DEFAULT_APP_VERSION,
    platform: env.COMMUNITY_PLATFORM?.trim() || "desktop",
  };
}

function toUtf8Bytes(value: string): Uint8Array {
  return new TextEncoder().encode(value);
}

function toBase64url(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString("base64url");
}

function bytesToHex(bytes: Uint8Array): string {
  let out = "";
  for (let i = 0; i < bytes.length; i++) {
    out += bytes[i].toString(16).padStart(2, "0");
  }
  return out;
}

async function hmacSha256(key: Uint8Array, message: Uint8Array): Promise<Uint8Array> {
  // Copy into an ArrayBuffer-backed view. Node's Buffer types may otherwise
  // expose SharedArrayBuffer here, which WebCrypto's importKey rejects.
  const keyBytes = new Uint8Array(key.byteLength);
  keyBytes.set(key);
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyBytes,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", cryptoKey, message);
  return new Uint8Array(signature);
}

export function randomHex(size: number): string {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return bytesToHex(bytes);
}

function currentTimestamp(): string {
  return new Date().toISOString().replace(/\.\d{3}Z$/, ".000Z");
}

/**
 * Sign a SpotiFLAC community request. Mirrors upstream `signCommunityRequest`:
 * rolling key = HMAC_SHA256(secret, "<window>:<sessionId>") where
 * window = floor(unixSeconds / 300), then a deterministic signing string is
 * HMAC'd with that rolling key. Returns the set of X-Sig-* headers.
 */
export async function signCommunityRequest(
  session: CommunitySession,
  method: string,
  escapedPath: string,
  query: string,
  body: Uint8Array,
  timestamp?: string,
  nonce?: string,
  contractOverrides: Partial<SignedSessionContract> = {}
): Promise<Record<string, string>> {
  const contract: SignedSessionContract = {
    schemeLabel: contractOverrides.schemeLabel ?? COMMUNITY_SIGNING_SCHEME,
    headerPrefix: contractOverrides.headerPrefix ?? "X-Sig-",
    timeWindowSeconds: contractOverrides.timeWindowSeconds ?? 300,
    userAgent: contractOverrides.userAgent ?? "SpotiFLAC/1.5.2 (Windows NT 10.0; Win64; x64)",
    rollingKeyEncoding: contractOverrides.rollingKeyEncoding ?? "raw",
  };
  const ts = timestamp ?? currentTimestamp();
  const nonceValue = nonce ?? randomHex(12);
  const bodyHash = bytesToHex(new Uint8Array(await crypto.subtle.digest("SHA-256", body)));

  const parsed = Date.parse(ts);
  const window = Math.floor(
    Math.floor((Number.isFinite(parsed) ? parsed : Date.now()) / 1000) /
      Math.max(1, contract.timeWindowSeconds)
  );

  const rollingInput = toUtf8Bytes(`${window}:${session.sessionId}`);
  // Desktop Go communityHMAC returns raw bytes. Mobile extensions use a
  // separate contract and explicitly select its base64url text encoding.
  const rollingDigest = await hmacSha256(toUtf8Bytes(session.sessionSecret), rollingInput);
  const rollingKey = contract.rollingKeyEncoding === "base64url"
    ? toUtf8Bytes(toBase64url(rollingDigest)) : rollingDigest;

  const signingInput = [
    contract.schemeLabel,
    method.toUpperCase(),
    escapedPath,
    query ?? "",
    bodyHash,
    ts,
    nonceValue,
    session.sessionId,
    session.appVersion,
    session.platform,
  ].join("\n");

  const digest = await hmacSha256(rollingKey, toUtf8Bytes(signingInput));
  const signature = toBase64url(digest);

  const prefix = contract.headerPrefix;
  return {
    [`${prefix}Session`]: session.sessionId,
    [`${prefix}Timestamp`]: ts,
    [`${prefix}Nonce`]: nonceValue,
    [`${prefix}Body-SHA256`]: bodyHash,
    [`${prefix}Signature`]: signature,
    [`${prefix}App-Version`]: session.appVersion,
    [`${prefix}Platform`]: session.platform,
    "User-Agent": contract.userAgent,
  };
}
