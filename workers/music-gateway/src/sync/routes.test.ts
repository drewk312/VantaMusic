import { describe, it } from "node:test";
import assert from "node:assert/strict";
import gateway from "../index.js";
import type { Env } from "../types.js";

class MemoryKv {
  private readonly values = new Map<string, string>();
  async get(key: string): Promise<string | null> { return this.values.get(key) ?? null; }
  async put(key: string, value: string): Promise<void> { this.values.set(key, value); }
}

function base64Url(value: string): string {
  return Buffer.from(value).toString("base64url");
}

async function tokenFor(userId: string, secret: string): Promise<string> {
  const header = base64Url(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = base64Url(JSON.stringify({ sub: userId, exp: Math.floor(Date.now() / 1000) + 60 }));
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${header}.${payload}`));
  return `${header}.${payload}.${Buffer.from(signature).toString("base64url")}`;
}

function env(): Env {
  const cache = new MemoryKv();
  return {
    GATEWAY_ID: "vanta", GATEWAY_NAME: "Vanta", GATEWAY_VERSION: "test", GATEWAY_DESCRIPTION: "test",
    ENABLED_SEARCH_PROVIDERS: "", ENABLED_STREAM_PROVIDERS: "", DEFAULT_STREAM_QUALITY: "24",
    ENRICH_SEARCH_RESULTS: "false", SEARCH_ENRICH_LIMIT: "0", SYNC_AUTH_SECRET: "test-secret",
    NODE_ENV: "development",
    CACHE: cache as unknown as KVNamespace, SOCIAL_KV: cache as unknown as KVNamespace,
  };
}

describe("sync route authentication", () => {
  it("rejects an unauthenticated sync request", async () => {
    const response = await gateway.fetch(new Request("https://vanta.example/sync/library/user-a"), env());
    assert.equal(response.status, 401);
  });

  it("rejects cross-user paths and body identities", async () => {
    const config = env();
    const token = await tokenFor("user-a", "test-secret");
    const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
    const crossUser = await gateway.fetch(new Request("https://vanta.example/sync/library/user-b", { headers }), config);
    assert.equal(crossUser.status, 403);

    const mismatch = await gateway.fetch(new Request("https://vanta.example/sync/library/user-a", {
      method: "POST", headers,
      body: JSON.stringify({ vantaUserId: "user-b", deviceName: "test", generatedAtMs: 1 }),
    }), config);
    assert.equal(mismatch.status, 403);
  });

  it("accepts a matching authenticated identity", async () => {
    const config = env();
    const token = await tokenFor("user-a", "test-secret");
    const response = await gateway.fetch(new Request("https://vanta.example/sync/library/user-a", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({ vantaUserId: "user-a", deviceName: "test", generatedAtMs: 1 }),
    }), config);
    assert.equal(response.status, 200);
  });
});
