import { afterEach, it } from "node:test";
import assert from "node:assert/strict";
import type { Env } from "../types";
import {
  hasByoaHeaders,
  parseTidalOauth,
  refreshTidalAccessToken,
  resolveTidalAccessToken,
  tidalAccessExpired,
} from "./oauth-refresh.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

it("parses Tidal PKCE/device JSON and a bare access token", () => {
  const tokens = parseTidalOauth(JSON.stringify({
    access_token: "access-1",
    refresh_token: "refresh-1",
    expires_at: Math.floor(Date.now() / 1000) + 3600,
    client_id: "tidal-client",
  }));
  assert.equal(tokens?.accessToken, "access-1");
  assert.equal(tokens?.refreshToken, "refresh-1");
  assert.equal(tokens?.clientId, "tidal-client");
  assert.equal(tidalAccessExpired(tokens!), false);
  assert.equal(parseTidalOauth("bare-access-token-value")?.accessToken, "bare-access-token-value");
});

it("refreshes an expired Tidal access token without exposing secrets", async () => {
  globalThis.fetch = (async (_input, init) => {
    assert.equal(String(_input), "https://auth.tidal.com/v1/oauth2/token");
    const body = String(init?.body);
    assert.match(body, /grant_type=refresh_token/);
    assert.match(body, /refresh_token=refresh-1/);
    return Response.json({ access_token: "access-2", refresh_token: "refresh-2", expires_in: 3600 });
  }) as typeof fetch;
  const next = await refreshTidalAccessToken({
    accessToken: "old",
    refreshToken: "refresh-1",
    expiresAtMs: Date.now() - 1000,
  });
  assert.equal(next?.accessToken, "access-2");
  assert.equal(next?.refreshToken, "refresh-2");
});

it("refreshes an expired request header before returning the access token", async () => {
  globalThis.fetch = (async () => Response.json({
    access_token: "access-2",
    refresh_token: "refresh-2",
    expires_in: 3600,
  })) as typeof fetch;
  const request = new Request("https://vanta.example/api/dl", {
    headers: {
      "X-Tidal-Token": JSON.stringify({
        access_token: "old",
        refresh_token: "refresh-1",
        expires_at: Math.floor(Date.now() / 1000) - 10,
      }),
    },
  });
  assert.equal(await resolveTidalAccessToken({} as Env, request), "access-2");
});

it("uses a request header token before the worker secret", async () => {
  const env = { TIDAL_API_KEY: "env-secret" } as Env;
  const request = new Request("https://vanta.example/api/dl", {
    headers: { "X-Tidal-Token": JSON.stringify({ access_token: "phone-token", refresh_token: "r" }) },
  });
  assert.equal(await resolveTidalAccessToken(env, request), "phone-token");
  assert.equal(await resolveTidalAccessToken(env), "env-secret");
  assert.equal(hasByoaHeaders(request), true);
  assert.equal(hasByoaHeaders(new Request("https://vanta.example/")), false);
});
