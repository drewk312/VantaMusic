import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { seconds, searchCacheTtl, healthCacheTtl } from "./cache.js";
import type { Env } from "../types";

function env(overrides: Partial<Env> = {}): Env {
  return {
    GATEWAY_ID: "test",
    GATEWAY_NAME: "Test Gateway",
    GATEWAY_VERSION: "1.0",
    GATEWAY_DESCRIPTION: "test",
    ENABLED_SEARCH_PROVIDERS: "deezer",
    ENABLED_STREAM_PROVIDERS: "deezer",
    DEFAULT_STREAM_QUALITY: "16",
    ENRICH_SEARCH_RESULTS: "false",
    SEARCH_ENRICH_LIMIT: "8",
    ...overrides,
  };
}

describe("cache helpers", () => {
  it("uses defaults when env keys are missing", () => {
    assert.equal(searchCacheTtl(env(), 5), 600);
    assert.equal(searchCacheTtl(env(), 0), 60);
    assert.equal(healthCacheTtl(env()), 30);
  });

  it("parses custom ttl values", () => {
    const e = env({
      SEARCH_CACHE_TTL_SECONDS: "1200",
      SEARCH_CACHE_EMPTY_TTL_SECONDS: "120",
      HEALTH_CACHE_TTL_SECONDS: "60",
    });
    assert.equal(searchCacheTtl(e, 5), 1200);
    assert.equal(searchCacheTtl(e, 0), 120);
    assert.equal(healthCacheTtl(e), 60);
  });

  it("caps values and falls back on invalid input", () => {
    const e = env({
      SEARCH_CACHE_TTL_SECONDS: "not-a-number",
      SEARCH_CACHE_EMPTY_TTL_SECONDS: "99999999",
      HEALTH_CACHE_TTL_SECONDS: "-10",
    });
    assert.equal(searchCacheTtl(e, 5), 600);
    assert.equal(searchCacheTtl(e, 0), 86_400);
    assert.equal(healthCacheTtl(e), 30);
  });
});
