import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { randomHex, signCommunityRequest, communitySessionFromEnv } from "./community-session.js";
import { streamViaSignedCommunity } from "./community-signed.js";

function sampleSession() {
  return {
    installId: "abc123",
    sessionId: "sid-123",
    sessionSecret: "secret-value",
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    appVersion: "VANTA-MusicGateway/2.0",
    platform: "desktop",
  };
}

describe("community session signing", () => {
  it("matches the desktop Go raw-byte rolling-key signature", async () => {
    const headers = await signCommunityRequest(
      { ...sampleSession(), appVersion: "unknown" }, "POST", "/api/dl", "",
      new TextEncoder().encode("{}"), "2026-01-01T00:00:00.000Z", "00112233445566778899aabb"
    );
    assert.equal(headers["X-Sig-Signature"], "TFSurChx1geTJsA--KHTTS-YaYPytDiwEGRGHSmvbpQ");
  });
  it("derives a base64url signature with the SPOTIFLAC-HMAC-V1 scheme", async () => {
    const session = sampleSession();
    const body = new TextEncoder().encode(JSON.stringify({ id: "42", quality: "atmos" }));
    const headers = await signCommunityRequest(session, "POST", "/api/dl", "", body);

    assert.equal(headers["X-Sig-Session"], "sid-123");
    assert.equal(headers["X-Sig-Platform"], "desktop");
    assert.match(headers["X-Sig-Timestamp"], /\.000Z$/);
    assert.equal(headers["X-Sig-Nonce"].length, 24);
    assert.equal(headers["X-Sig-Body-SHA256"].length, 64);
    assert.match(headers["X-Sig-Signature"], /^[-_A-Za-z0-9]+$/);
  });

  it("is deterministic given fixed inputs (regression guard)", async () => {
    const session = {
      ...sampleSession(),
      appVersion: "qobuz-web@1.2.10",
      platform: "extension",
    };
    const body = new TextEncoder().encode("{}");
    const contract = {
      schemeLabel: "ZARZ-HMAC-V1",
      headerPrefix: "X-Zarz-",
      timeWindowSeconds: 300,
      userAgent: "SpotiFLAC-Mobile/qobuz-web@1.2.10",
      rollingKeyEncoding: "base64url" as const,
    };
    const headers = await signCommunityRequest(
      session,
      "POST",
      "/tickets",
      "",
      body,
      "2026-01-01T00:00:00.000Z",
      "00112233445566778899aabb",
      contract
    );
    assert.equal(headers["X-Zarz-Signature"], "LYrPfpVJh-ApPso4Zgc6VwxyS0_BeHogxtDpl2Rr1WM");
  });

  it("includes the query string in the signature", async () => {
    const session = sampleSession();
    const body = new TextEncoder().encode("{}");
    const withQuery = await signCommunityRequest(session, "GET", "/api/dl", "?x=1", body, "2026-01-01T00:00:00.000Z", "abc");
    const without = await signCommunityRequest(session, "GET", "/api/dl", "", body, "2026-01-01T00:00:00.000Z", "abc");
    assert.notEqual(withQuery["X-Sig-Signature"], without["X-Sig-Signature"]);
  });

  it("returns null from env when no session credentials are configured", () => {
    const session = communitySessionFromEnv({} as never);
    assert.equal(session, null);
  });

  it("round-trips a session from env", () => {
    const env = {
      COMMUNITY_SESSION_ID: "sid",
      COMMUNITY_SESSION_SECRET: "sec",
      COMMUNITY_INSTALL_ID: "iid",
      COMMUNITY_SESSION_EXPIRES: new Date(Date.now() + 60 * 60_000).toISOString(),
    } as never;
    const session = communitySessionFromEnv(env);
    assert.ok(session);
    assert.equal(session?.sessionId, "sid");
    assert.equal(session?.installId, "iid");
  });

  it("rejects an already-expired env session", () => {
    const env = {
      COMMUNITY_SESSION_ID: "sid",
      COMMUNITY_SESSION_SECRET: "sec",
      COMMUNITY_SESSION_EXPIRES: new Date(Date.now() - 60_000).toISOString(),
    } as never;
    assert.equal(communitySessionFromEnv(env), null);
  });

  it("generates the right nonce length", () => {
    assert.equal(randomHex(12).length, 24);
  });

  it("returns null for non-community providers", async () => {
    const result = await streamViaSignedCommunity({} as never, "deezer", "123", "16");
    assert.equal(result, null);
  });

  it("returns null when no session is configured (no live network call)", async () => {
    const result = await streamViaSignedCommunity({} as never, "amazon", "B0X", "atmos");
    assert.equal(result, null);
  });
});
