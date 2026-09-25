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
    assert.equal(result.state.remaining, 119);
  });

  it("uses API key when present", async () => {
    const env: Env = {} as Env;
    const result = await checkRateLimit(makeRequest({ "X-Api-Key": "secret-key-123" }), { ...env, NODE_ENV: "development" });
    assert.match(result.key, /^apikey:[0-9a-f]{8}$/);
    assert.equal(result.key.includes("secret-key"), false);
  });
});

it("fails closed without the platform binding outside development", async () => {
  const result = await checkRateLimit(makeRequest(), {} as Env);
  assert.equal(result.allowed, false);
  assert.equal(result.storageUnavailable, true);
});

it("uses the native Cloudflare Rate Limiting binding in production", async () => {
  const keys: string[] = [];
  const env = {
    RATE_LIMITER: {
      async limit({ key }: { key: string }) {
        keys.push(key);
        return { success: keys.length === 1 };
      },
    },
  } as unknown as Env;
  const first = await checkRateLimit(makeRequest({ "CF-Connecting-IP": "203.0.113.10" }), env);
  const second = await checkRateLimit(makeRequest({ "CF-Connecting-IP": "203.0.113.10" }), env);
  assert.equal(first.allowed, true);
  assert.equal(second.allowed, false);
  assert.equal(first.storageUnavailable, false);
  assert.equal(first.state.remaining, null);
  assert.equal(keys.length, 2);
});

it("fails closed in production when the native binding throws", async () => {
  const env = {
    RATE_LIMITER: {
      async limit() {
        throw new Error("binding unavailable");
      },
    },
  } as unknown as Env;
  const result = await checkRateLimit(makeRequest({ "CF-Connecting-IP": "203.0.113.11" }), env);
  assert.equal(result.allowed, false);
  assert.equal(result.storageUnavailable, true);
});
