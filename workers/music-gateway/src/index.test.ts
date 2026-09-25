import { describe, it, before, after } from "node:test";
import assert from "node:assert/strict";
import gateway from "./index.js";
import type { Env } from "./types";
import { configureStreamTimingForTests } from "./providers/stream.js";

describe("gateway request handling", () => {
  let restoreTiming: () => void;
  before(() => {
    restoreTiming = configureStreamTimingForTests({
      globalTimeoutMs: 250,
      fastPathMs: 20,
      expandBudgetMs: 20,
      spatialTimeoutMs: 40,
    });
  });
  after(() => restoreTiming());
  it("serves a branded product surface at the root and keeps health machine-readable", async () => {
    const config = {
      GATEWAY_NAME: "VANTA Music Gateway",
      GATEWAY_VERSION: "test",
      ENABLED_SEARCH_PROVIDERS: "",
      ENABLED_STREAM_PROVIDERS: "",
      NODE_ENV: "development",
    } as Env;
    const root = await gateway.fetch(new Request("https://vanta.example/"), config);
    assert.match(root.headers.get("content-type") ?? "", /text\/html/);
    assert.match(await root.text(), /Every note/);

    const health = await gateway.fetch(new Request("https://vanta.example/health"), config);
    assert.match(health.headers.get("content-type") ?? "", /application\/json/);
    assert.equal(((await health.json()) as { ok?: boolean }).ok, true);
  });

  it("does not turn incomplete stream config into an internal server error", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/stream/track-123"),
      { NODE_ENV: "development" } as Env
    );

    assert.equal(response.status, 401);
    const body = (await response.json()) as { error?: string; retryable?: boolean; message?: string };
    assert.equal(body.error, "AUTH_REQUIRED");
    assert.equal(body.retryable, false);
    assert.match(body.message ?? "", /requires authentication/i);
    assert.equal(/hifi plus|tidal token|torbox|real-debrid/i.test(body.message ?? ""), false);
  });

  it("returns ATMOS_UNAVAILABLE instead of AUTH_REQUIRED when Atmos cannot be sourced", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/stream/track-123?quality=atmos"),
      { NODE_ENV: "development" } as Env
    );

    assert.equal(response.status, 404);
    const body = (await response.json()) as { error?: string };
    assert.equal(body.error, "ATMOS_UNAVAILABLE");
  });

  it("keeps Sony 360 as a quality miss instead of rewriting it to 24-bit stereo", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/stream/track-123?quality=360"),
      { NODE_ENV: "development" } as Env
    );
    assert.equal(response.status, 404);
    const body = (await response.json()) as { error?: string };
    assert.equal(body.error, "QUALITY_UNAVAILABLE");
  });

  it("does not use unofficial Tidal fallbacks when Atmos is requested without a Tidal session", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/stream/track-123?quality=atmos&provider=tidal"),
      {
        NODE_ENV: "development",
        ENABLED_STREAM_PROVIDERS: "qobuz,tidal,deezer,amazon,pandora",
        DEFAULT_COMMUNITY_GATEWAY: "https://qobuz-tidal-eclipse.cyrusna29.workers.dev",
      } as Env
    );
    assert.equal(response.status, 404);
    assert.equal(((await response.json()) as { error?: string }).error, "ATMOS_UNAVAILABLE");
  });

  it("keeps catalog routes usable when the optional gateway key is not configured", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/search?q=test"),
      {
        NODE_ENV: "development",
        ENABLED_SEARCH_PROVIDERS: "",
      } as Env
    );
    assert.equal(response.status, 200);
  });

  it("enforces the gateway key when an operator configures one", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/search?q=test"),
      {
        GATEWAY_API_KEY: "configured-test-key",
        ENABLED_SEARCH_PROVIDERS: "",
        KV_WRITES_ENABLED: "false",
      } as unknown as Env
    );
    assert.equal(response.status, 401);
    assert.equal(((await response.json()) as { error?: string }).error, "unauthorized");
  });

  it("gates media and update routes too once a key is configured", async () => {
    const env = {
      GATEWAY_API_KEY: "configured-test-key",
      ENABLED_SEARCH_PROVIDERS: "",
      ENABLED_STREAM_PROVIDERS: "",
      KV_WRITES_ENABLED: "false",
    } as unknown as Env;
    const cases = [
      "https://vanta.example/manifest/mpd?data=QWJj",
      "https://vanta.example/api/manifest/mpd?data=QWJj",
      "https://vanta.example/audio/extension",
      "https://vanta.example/app/update",
      "https://vanta.example/app/download",
    ];
    for (const url of cases) {
      const denied = await gateway.fetch(new Request(url), env);
      assert.equal(denied.status, 401, `${url} should reject without a key`);
      const accepted = await gateway.fetch(new Request(url, { headers: { "X-Api-Key": "configured-test-key" } }), env);
      assert.notEqual(accepted.status, 401, `${url} should not be rejected with a valid key`);
    }
  });

  it("rejects arbitrary resolver URLs before forwarding them", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/resolve?url=https%3A%2F%2Fexample.com%2Fsecret"),
      { NODE_ENV: "development" } as Env
    );
    assert.equal(response.status, 400);
    assert.equal(((await response.json()) as { error?: string }).error, "unsupported_url");
  });

  it("reports Tidal Atmos as unlicensed when no Tidal upstream is configured", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/status"),
      {
        NODE_ENV: "development",
        ENABLED_SEARCH_PROVIDERS: "deezer,qobuz,apple",
        ENABLED_STREAM_PROVIDERS: "qobuz,tidal,deezer,amazon,pandora",
      } as Env
    );
    assert.equal(response.status, 200);
    const body = (await response.json()) as {
      searchProviders?: string[];
      providers?: { tidal?: { search?: boolean; licensed?: boolean; notes?: string[] } };
    };
    assert.deepEqual(body.searchProviders, ["deezer", "qobuz", "apple"]);
    assert.equal(body.providers?.tidal?.search, false);
    assert.equal(body.providers?.tidal?.licensed, false);
    assert.match(body.providers?.tidal?.notes?.join(" ") ?? "", /E-AC-3|Atmos/i);
  });

  it("serves decoded DASH MPD manifests from /manifest/mpd", async () => {
    const xml = '<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="PT3M43.773S"></MPD>';
    const b64 = Buffer.from(xml).toString("base64url");
    const response = await gateway.fetch(
      new Request(`https://vanta.example/manifest/mpd?data=${b64}`),
      { NODE_ENV: "development" } as Env
    );
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-type") ?? "", /application\/dash\+xml/);
    assert.equal(await response.text(), xml);
  });

  it("rejects invalid or missing data in /manifest/mpd", async () => {
    const missing = await gateway.fetch(
      new Request("https://vanta.example/manifest/mpd"),
      { NODE_ENV: "development" } as Env
    );
    assert.equal(missing.status, 400);
    assert.equal(((await missing.json()) as { error?: string }).error, "missing_data");

    const invalid = await gateway.fetch(
      new Request("https://vanta.example/manifest/mpd?data=%%%"),
      { NODE_ENV: "development" } as Env
    );
    assert.equal(invalid.status, 400);
  });

  it("reports the configured rate limit instead of one when native remaining is unknown", async () => {
    const response = await gateway.fetch(
      new Request("https://gateway.test/manifest.json"),
      {
        GATEWAY_ID: "test",
        GATEWAY_NAME: "test",
        GATEWAY_VERSION: "test",
        GATEWAY_DESCRIPTION: "test",
        ENABLED_SEARCH_PROVIDERS: "",
        ENABLED_STREAM_PROVIDERS: "",
        DEFAULT_STREAM_QUALITY: "24",
        ENRICH_SEARCH_RESULTS: "false",
        SEARCH_ENRICH_LIMIT: "0",
        RATE_LIMIT_MAX_REQUESTS: "120",
        RATE_LIMITER: { limit: async () => ({ success: true }) },
      } as unknown as Env
    );
    assert.equal(response.headers.get("X-RateLimit-Limit"), "120");
    assert.equal(response.headers.has("X-RateLimit-Remaining"), false);
  });

  it("preserves DASH and spatial metadata in the canonical /api/dl response", async () => {
    const cached = {
      url: "https://vanta.example/manifest/mpd?data=abc",
      mimeType: "application/dash+xml",
      format: "EAC3_JOC",
      quality: "Dolby Atmos",
      provider: "tidal",
      isDolbyAtmos: true,
      isSpatialAudio: true,
      isSurround: true,
    };
    const response = await gateway.fetch(
      new Request("https://vanta.example/api/dl", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: "123", service: "tidal", quality: "atmos" }),
      }),
      {
        NODE_ENV: "development",
        CACHE: {
          get: async (key: string) => key === "stream:123:tidal:atmos" ? JSON.stringify(cached) : null,
          put: async () => {},
        },
      } as unknown as Env
    );
    assert.equal(response.status, 200);
    const body = await response.json() as typeof cached & { success?: boolean; streamUrl?: string };
    assert.equal(body.success, true);
    assert.equal(body.streamUrl, cached.url);
    assert.equal(body.mimeType, "application/dash+xml");
    assert.equal(body.format, "EAC3_JOC");
    assert.equal(body.isDolbyAtmos, true);
  });

  it("does not ask for a Zarz reconnect when the phone already sent BYOA headers", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/api/dl", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Tidal-Token": "phone-token-value-16",
        },
        body: JSON.stringify({ id: "381791126", service: "qobuz", quality: "24" }),
      }),
      { NODE_ENV: "development", NEXT_COMMUNITY_ENABLED: "false" } as Env
    );
    const body = await response.json() as { error?: string; message?: string };
    assert.equal(/reconnect the extension/i.test(body.message ?? ""), false);
    assert.equal(body.error, "QUALITY_UNAVAILABLE");
    assert.equal(response.status, 404);
  });
});

describe("session diagnostics privacy", () => {
  const config = { NODE_ENV: "development", ENABLED_SEARCH_PROVIDERS: "", ENABLED_STREAM_PROVIDERS: "", GATEWAY_STATUS_KEY: "operator-diagnostics-key" } as Env;
  it("keeps renewal internals out of public status", async () => {
    const response = await gateway.fetch(new Request("https://vanta.example/status"), config);
    const body = await response.json() as Record<string, unknown>;
    assert.equal(response.status, 200);
    assert.equal("sessionRenewal" in body, false);
    assert.equal("playbackBreakers" in body, false);
    assert.match(response.headers.get("Cache-Control") ?? "", /no-store/);
  });
  it("requires a configured operator key for detailed status", async () => {
    const denied = await gateway.fetch(new Request("https://vanta.example/admin/status"), config);
    assert.equal(denied.status, 401);
    const accepted = await gateway.fetch(new Request("https://vanta.example/admin/status", { headers: { "X-Status-Key": config.GATEWAY_STATUS_KEY! } }), config);
    assert.equal(accepted.status, 200);
    assert.ok("sessionRenewal" in (await accepted.json() as Record<string, unknown>));
    const unconfigured = await gateway.fetch(new Request("https://vanta.example/admin/status"), { ...config, GATEWAY_STATUS_KEY: undefined });
    assert.equal(unconfigured.status, 401);
  });
});
