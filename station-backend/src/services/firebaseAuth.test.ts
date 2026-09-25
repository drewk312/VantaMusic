import { createSign, generateKeyPairSync } from "node:crypto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetFirebaseKeyCacheForTest, verifyFirebaseIdToken } from "./firebaseAuth";

const projectId = "vanta-test-project";
const keyPair = generateKeyPairSync("rsa", { modulusLength: 2048 });
const publicJwk = { ...keyPair.publicKey.export({ format: "jwk" }), kid: "firebase-test-key", use: "sig", alg: "RS256" };

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

beforeEach(() => {
  resetFirebaseKeyCacheForTest();
  vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ keys: [publicJwk] }), {
    headers: { "Cache-Control": "max-age=60" },
  })));
});

afterEach(() => {
  vi.unstubAllGlobals();
  resetFirebaseKeyCacheForTest();
});

describe("Firebase station token verification", () => {
  it("returns the verified Firebase sub as the backend user identity", async () => {
    await expect(verifyFirebaseIdToken(firebaseToken(), projectId)).resolves.toBe("firebase-user-123");
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
      await expect(verifyFirebaseIdToken(firebaseToken(claims, header), projectId)).resolves.toBeNull();
    });
  }

  it("rejects an invalid signature", async () => {
    const token = firebaseToken();
    const parts = token.split(".");
    const sig = parts[2] ?? "";
    const tamperedSig = (sig[0] === "A" ? "B" : "A") + sig.slice(1);
    const tampered = `${parts[0]}.${parts[1]}.${tamperedSig}`;
    await expect(verifyFirebaseIdToken(tampered, projectId)).resolves.toBeNull();
  });
});
