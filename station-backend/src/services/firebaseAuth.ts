import { createPublicKey, verify } from "node:crypto";

interface FirebaseClaims {
  sub?: string;
  aud?: string;
  iss?: string;
  exp?: number;
  iat?: number;
}

interface FirebaseJwk {
  kid?: string;
  kty?: string;
  n?: string;
  e?: string;
  alg?: string;
  use?: string;
}

let keyCache: { expiresAt: number; keys: Map<string, FirebaseJwk> } | null = null;

/** Test-only cache reset for deterministic Firebase JWK fixtures. */
export function resetFirebaseKeyCacheForTest(): void {
  keyCache = null;
}

export async function verifyFirebaseIdToken(token: string, projectId: string): Promise<string | null> {
  const [encodedHeader, encodedPayload, encodedSignature, extra] = token.split(".");
  if (!encodedHeader || !encodedPayload || !encodedSignature || extra) return null;

  try {
    const header = decodeJson<{ alg?: string; kid?: string }>(encodedHeader);
    const claims = decodeJson<FirebaseClaims>(encodedPayload);
    const now = Math.floor(Date.now() / 1000);
    if (
      header?.alg !== "RS256" || !header.kid || !claims?.sub || !claims.exp || !claims.iat ||
      claims.exp <= now || claims.iat > now || claims.aud !== projectId ||
      claims.iss !== `https://securetoken.google.com/${projectId}`
    ) return null;

    const jwk = (await firebaseKeys()).get(header.kid);
    if (!jwk || jwk.kty !== "RSA") return null;
    const key = createPublicKey({ key: jwk, format: "jwk" });
    const valid = verify(
      "RSA-SHA256",
      Buffer.from(`${encodedHeader}.${encodedPayload}`),
      key,
      Buffer.from(encodedSignature, "base64url")
    );
    return valid ? claims.sub : null;
  } catch {
    return null;
  }
}

function decodeJson<T>(value: string): T | null {
  try {
    return JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as T;
  } catch {
    return null;
  }
}

async function firebaseKeys(): Promise<Map<string, FirebaseJwk>> {
  if (keyCache && keyCache.expiresAt > Date.now()) return keyCache.keys;
  const response = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!response.ok) throw new Error("firebase_keys_unavailable");
  const payload = await response.json() as { keys?: FirebaseJwk[] };
  const keys = new Map((payload.keys ?? []).flatMap((key) => key.kid ? [[key.kid, key] as const] : []));
  const maxAge = Number.parseInt(response.headers.get("Cache-Control")?.match(/max-age=(\d+)/)?.[1] ?? "3600", 10);
  keyCache = { keys, expiresAt: Date.now() + Math.max(60, maxAge) * 1000 };
  return keys;
}
