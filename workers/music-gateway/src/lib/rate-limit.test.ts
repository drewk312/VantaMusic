import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { checkRateLimit } from "./rate-limit.js";
import type { Env } from "../types";

function makeRequest(headers = {}) {
  return new Request("https://example.com/search?q=test", { headers });
}

describe("rate-limit", () => {
  it("allows requests when no cache is configured", async () => {
    const env: Env = {} as Env;
    const result = await checkRateLimit(makeRequest(), { ...env, NODE_ENV: "development" });
    assert.equal(result.allowed, true);
    assert.equal(result.state.remaining, 120);
  });

  it("uses API key when present", async () => {
    const env: Env = {} as Env;
    const result = await checkRateLimit(makeRequest({ "X-Api-Key": "secret-key-123" }), { ...env, NODE_ENV: "development" });
    assert.equal(result.key, "apikey:secret-key-123".slice(0, 23)); // 16 chars after prefix
  });
});

it("fails closed without KV outside development", async () => {
  const result = await checkRateLimit(makeRequest(), {} as Env);
  assert.equal(result.allowed, false);
  assert.equal(result.storageUnavailable, true);
});
