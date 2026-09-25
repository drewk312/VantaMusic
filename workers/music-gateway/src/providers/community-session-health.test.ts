import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  COMMUNITY_EXPIRY_WARN_MS,
  communityDesktopSessionHealth,
  gatewaySessionHealth,
} from "./community-session-health.js";
import type { Env } from "../types";

describe("community-session-health", () => {
  it("marks a live desktop session as ok", () => {
    const env = {
      COMMUNITY_SESSION_ID: "sess",
      COMMUNITY_SESSION_SECRET: "sec",
      COMMUNITY_SESSION_EXPIRES: new Date(Date.now() + COMMUNITY_EXPIRY_WARN_MS + 60_000).toISOString(),
    } as Env;
    const health = communityDesktopSessionHealth(env);
    assert.equal(health.status, "ok");
    assert.equal(health.present, true);
    assert.equal(health.silentRefresh, false);
  });

  it("marks sessions inside the warn window as expiring", () => {
    const env = {
      COMMUNITY_SESSION_ID: "sess",
      COMMUNITY_SESSION_SECRET: "sec",
      COMMUNITY_SESSION_EXPIRES: new Date(Date.now() + 60 * 60_000).toISOString(),
    } as Env;
    assert.equal(communityDesktopSessionHealth(env).status, "expiring");
  });

  it("marks expired desktop sessions", () => {
    const env = {
      COMMUNITY_SESSION_ID: "sess",
      COMMUNITY_SESSION_SECRET: "sec",
      COMMUNITY_SESSION_EXPIRES: new Date(Date.now() - 60_000).toISOString(),
    } as Env;
    const health = communityDesktopSessionHealth(env);
    assert.equal(health.status, "expired");
    assert.equal(health.present, false);
  });

  it("reports zarz amazon silently refreshable", () => {
    const env = {
      ZARZ_AMAZON_SESSION_ID: "a",
      ZARZ_AMAZON_SESSION_SECRET: "b",
      ZARZ_AMAZON_SESSION_EXPIRES: new Date(Date.now() + COMMUNITY_EXPIRY_WARN_MS + 60_000).toISOString(),
    } as Env;
    const amazon = gatewaySessionHealth(env).find((s) => s.name === "zarz_amazon");
    assert.equal(amazon?.status, "ok");
    assert.equal(amazon?.silentRefresh, true);
  });
});
