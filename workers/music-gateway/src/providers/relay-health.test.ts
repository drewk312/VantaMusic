import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  isRelayPoolDown,
  markRelayPoolDown,
  markRelayPoolUp,
} from "./relay-health.js";

describe("relay-health circuit breaker", () => {
  beforeEach(() => {
    for (const p of ["qobuz", "tidal", "amazon", "deezer"] as const) markRelayPoolUp(p);
  });

  it("is up by default", () => {
    assert.equal(isRelayPoolDown("qobuz"), false);
  });

  it("marks a provider down", () => {
    markRelayPoolDown("qobuz", "test");
    assert.equal(isRelayPoolDown("qobuz"), true);
  });

  it("recovers via markRelayPoolUp", () => {
    markRelayPoolDown("tidal", "test");
    assert.equal(isRelayPoolDown("tidal"), true);
    markRelayPoolUp("tidal");
    assert.equal(isRelayPoolDown("tidal"), false);
  });

  it("keeps providers independent", () => {
    markRelayPoolDown("amazon", "test");
    assert.equal(isRelayPoolDown("amazon"), true);
    assert.equal(isRelayPoolDown("deezer"), false);
  });
});
