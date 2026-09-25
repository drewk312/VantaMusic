import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { classifyMissingStream, hasLicensedStreamSession, extensionVerificationRequired } from "./stream-failure";
import type { Env } from "../types";

const emptyEnv = { NODE_ENV: "development" } as Env;

describe("catalog stream failure classification", () => {
  it("does not treat a missing licensed session as a generic offline error", () => {
    assert.equal(hasLicensedStreamSession(emptyEnv), false);
    const failure = classifyMissingStream("24", emptyEnv);
    assert.equal(failure.code, "AUTH_REQUIRED");
    assert.equal(failure.status, 401);
    assert.match(failure.message, /requires authentication/i);
    assert.equal(/hifi plus|tidal token|torbox|real-debrid/i.test(failure.message), false);
  });

  it("returns QUALITY_UNAVAILABLE when Sony 360 was requested and no MPEG-H stream exists", () => {
    const failure = classifyMissingStream("360", emptyEnv);
    assert.equal(failure.code, "QUALITY_UNAVAILABLE");
    assert.equal(failure.status, 404);
    assert.match(failure.message, /360 Reality Audio/i);
  });

  it("returns QUALITY_UNAVAILABLE for hi-res when a licensed session exists but no stream is returned", () => {
    const env = { QOBUZ_AUTH_TOKEN: "user-token", NODE_ENV: "development" } as Env;
    const failure = classifyMissingStream("24", env);
    assert.equal(failure.code, "QUALITY_UNAVAILABLE");
  });

  it("returns SOURCE_OFFLINE for standard quality when licensed session exists", () => {
    const env = { MUSICDL_BASE_URL: "https://licensed.example", NODE_ENV: "development" } as Env;
    const failure = classifyMissingStream("16", env);
    assert.equal(failure.code, "SOURCE_OFFLINE");
    assert.equal(failure.retryable, true);
  });

  it("detects extension verification from Durable Object health", async () => {
    const env = {
      EXTENSION_SESSIONS: {
        getByName: (provider: string) => ({
          health: async () => ({ status: provider === "deezer" ? "verification_required" : "expired" }),
        }),
      },
    } as unknown as Env;
    assert.equal(await extensionVerificationRequired({} as Env), false);
  });

  it("scopes HUMAN_REQUIRED to the challenged provider and keeps the family serving", async () => {
    const env = {
      EXTENSION_SESSIONS: {
        getByName: (provider: string) => ({
          health: async () => ({ status: provider === "qobuz" ? "verification_required" : "ready" }),
        }),
      },
    } as unknown as Env;
    assert.equal(await extensionVerificationRequired(env, "qobuz"), true);
    assert.equal(await extensionVerificationRequired(env, "tidal"), false);
    assert.equal(await extensionVerificationRequired(env, "deezer"), false);
    assert.equal(await extensionVerificationRequired(env), false);
  });

  it("only fails gateway-wide when every Zarz provider is challenged", async () => {
    const env = {
      EXTENSION_SESSIONS: {
        getByName: () => ({ health: async () => ({ status: "verification_required" }) }),
      },
    } as unknown as Env;
    assert.equal(await extensionVerificationRequired(env), true);
  });

  it("treats a fresh signed session as ready even if DO health lags", async () => {
    const env = {
      ZARZ_INSTALL_ID: "install",
      ZARZ_TIDAL_SESSION_ID: "session",
      ZARZ_TIDAL_SESSION_SECRET: "secret",
      ZARZ_TIDAL_SESSION_EXPIRES: new Date(Date.now() + 3_600_000).toISOString(),
      EXTENSION_SESSIONS: {
        getByName: () => ({ health: async () => ({ status: "verification_required" }) }),
      },
    } as unknown as Env;
    assert.equal(await extensionVerificationRequired(env, "tidal"), false);
  });

  it("treats phone BYOA headers as a licensed session instead of a Zarz reconnect", () => {
    const request = new Request("https://vanta.example/api/dl", {
      headers: { "X-Tidal-Token": "phone-token-value-16" },
    });
    assert.equal(hasLicensedStreamSession(emptyEnv, request), true);
    assert.equal(classifyMissingStream("24", emptyEnv, request).code, "QUALITY_UNAVAILABLE");
    assert.equal(/reconnect the extension/i.test(classifyMissingStream("24", emptyEnv, request).message), false);
  });

  it("returns QUALITY_UNAVAILABLE for 360 when Next is live even if Zarz Amazon needs reconnect", async () => {
    const { missingStreamFailure } = await import("./stream-failure.js");
    const env = {
      EXTENSION_SESSIONS: {
        getByName: () => ({ health: async () => ({ status: "verification_required" }) }),
      },
      NODE_ENV: "development",
    } as unknown as Env;
    const failure = await missingStreamFailure("360", env, "amazon");
    assert.equal(failure.code, "QUALITY_UNAVAILABLE");
    assert.match(failure.message, /No configured source returned Sony 360/i);
    assert.equal(/needs a reconnect/i.test(failure.message), false);
  });

  it("returns ATMOS_UNAVAILABLE catalog miss when Next is enabled without Zarz", async () => {
    const { missingStreamFailure } = await import("./stream-failure.js");
    const env = {
      EXTENSION_SESSIONS: {
        getByName: () => ({ health: async () => ({ status: "verification_required" }) }),
      },
      NODE_ENV: "development",
    } as unknown as Env;
    const failure = await missingStreamFailure("atmos", env, "tidal");
    assert.equal(failure.code, "ATMOS_UNAVAILABLE");
    assert.match(failure.message, /No configured source returned Dolby Atmos/i);
    assert.equal(/needs a reconnect/i.test(failure.message), false);
  });
});
