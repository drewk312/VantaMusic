import { createSign, generateKeyPairSync } from "node:crypto";
import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it } from "node:test";
import { authenticateSyncRequest, resetFirebaseKeyCacheForTest } from "./auth.js";
import type { Env } from "./types";

const projectId = "vanta-test-project";
const keyPair = generateKeyPairSync("rsa", { modulusLength: 2048 });
const publicJwk = { ...keyPair.publicKey.export({ format: "jwk" }), kid: "firebase-test-key", use: "sig", alg: "RS256" } as JsonWebKey;
const originalFetch = globalThis.fetch;

function firebaseToken(claims: Record<string, unknown> = {}, header: Record<string, unknown> = {}): string {
  const now = Math.floor(Date.now() / 1000);
  const encodedHeader = Buffer.from(JSON.stringify({ alg: "RS256", kid: "firebase-test-key", ...header })).toString("base64url");
  const encodedPayload = Buffer.from(JSON.stringify({
    sub: "firebase-user-123",
    aud: projectId,
    iss: `https://securetoken.google.com/${projectId}`,
    iat: now - 30,
    exp: now + 300,
    ...claims,
  })).toString("base64url");
  const signer = createSign("RSA-SHA256");
  signer.update(`${encodedHeader}.${encodedPayload}`);
  signer.end();
  return `${encodedHeader}.${encodedPayload}.${signer.sign(keyPair.privateKey).toString("base64url")}`;
}

function firebaseEnv(): Env {
  return { NODE_ENV: "production", FIREBASE_PROJECT_ID: projectId } as Env;
}

beforeEach(() => {
  resetFirebaseKeyCacheForTest();
  globalThis.fetch = async (input) => {
    assert.equal(String(input), "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
    return new Response(JSON.stringify({ keys: [publicJwk] }), { headers: { "Cache-Control": "max-age=60" } });
  };
});

afterEach(() => {
  globalThis.fetch = originalFetch;
  resetFirebaseKeyCacheForTest();
});

describe("Firebase sync token verification", () => {
  it("accepts a valid Firebase token and uses its sub as the identity", async () => {
    const result = await authenticateSyncRequest(new Request("https://vanta.example/sync/library/firebase-user-123", {
      headers: { Authorization: `Bearer ${firebaseToken()}` },
    }), firebaseEnv());
    assert.deepEqual(result, { ok: true, identity: { userId: "firebase-user-123" } });
  });

  it("preserves a case-sensitive Firebase subject", async () => {
    const result = await authenticateSyncRequest(new Request("https://vanta.example/sync/library/FirebaseUserABC", {
      headers: { Authorization: `Bearer ${firebaseToken({ sub: "FirebaseUserABC" })}` },
    }), firebaseEnv());
    assert.deepEqual(result, { ok: true, identity: { userId: "FirebaseUserABC" } });
  });

  for (const [name, claims, header] of [
    ["expired token", { exp: Math.floor(Date.now() / 1000) - 1 }, {}],
    ["future issued-at token", { iat: Math.floor(Date.now() / 1000) + 60 }, {}],
    ["wrong audience", { aud: "another-project" }, {}],
    ["wrong issuer", { iss: "https://securetoken.google.com/another-project" }, {}],
    ["missing subject", { sub: "" }, {}],
    ["bad algorithm", {}, { alg: "HS256" }],
  ] as const) {
    it(`rejects a ${name}`, async () => {
      const result = await authenticateSyncRequest(new Request("https://vanta.example/sync/library/firebase-user-123", {
        headers: { Authorization: `Bearer ${firebaseToken(claims, header)}` },
      }), firebaseEnv());
      assert.deepEqual(result, { ok: false, status: 401, error: "invalid_sync_token" });
    });
  }

  it("rejects an invalid signature", async () => {
    const token = firebaseToken();
    const parts = token.split(".");
    parts[2] = "dGFtpered-signature-not-valid"; // corrupt signature payload
    const result = await authenticateSyncRequest(new Request("https://vanta.example/sync/library/firebase-user-123", {
      headers: { Authorization: `Bearer ${parts.join(".")}` },
    }), firebaseEnv());
    assert.deepEqual(result, { ok: false, status: 401, error: "invalid_sync_token" });
  });

  it("fails closed in production when Firebase is not configured", async () => {
    const result = await authenticateSyncRequest(new Request("https://vanta.example/sync/library/user-a", {
      headers: { Authorization: "Bearer legacy-hmac-token" },
    }), { NODE_ENV: "production", SYNC_AUTH_SECRET: "legacy-secret" } as Env);
    assert.deepEqual(result, { ok: false, status: 503, error: "sync_auth_not_configured" });
  });
});

