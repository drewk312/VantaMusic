import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { handleAmazonLicenseProxy, handleTidalWidevineProxy, streamForClient } from "./drm-proxy.js";
import type { Env, StreamResult } from "../types.js";

const NOW = 1_700_000_000;
const env = {
  DRM_PROXY_SECRET: "unit-test-secret",
  TIDAL_API_URL: "https://tidal-private.example",
  TIDAL_API_KEY: "private-upstream-key",
} as Env;

const protectedStream: StreamResult = {
  url: "https://media.example/track.mpd",
  streamUrl: "https://media.example/track.mpd",
  provider: "tidal",
  format: "eac3-joc",
  drm: { scheme: "widevine", licenseProxy: "tidal" },
};

describe("Widevine proxy", () => {
  it("replaces the internal proxy marker with a signed, public license URL", async () => {
    const result = await streamForClient(protectedStream, "https://vanta.example", env, NOW);
    const licenseUrl = result?.drm?.licenseUrl ?? "";
    assert.match(licenseUrl, /^https:\/\/vanta\.example\/drm\/tidal\/widevine\?/);
    assert.equal(result?.drm?.licenseProxy, undefined);
    assert.equal(JSON.stringify(result).includes("private-upstream-key"), false);
    assert.equal(JSON.stringify(result).includes("tidal-private.example"), false);
  });

  it("validates the token and streams the challenge to the private service", async () => {
    const result = await streamForClient(protectedStream, "https://vanta.example", env, NOW);
    const request = new Request(result!.drm!.licenseUrl!, {
      method: "POST",
      headers: { "Content-Type": "application/octet-stream" },
      body: "widevine-challenge",
    });
    let forwarded = false;
    const response = await handleTidalWidevineProxy(request, env, async (input, init) => {
      forwarded = true;
      assert.equal(String(input), "https://tidal-private.example/widevine");
      assert.equal(new Headers(init?.headers).get("X-Api-Key"), "private-upstream-key");
      assert.equal(await new Response(init?.body).text(), "widevine-challenge");
      return new Response("license-bytes", { status: 200, headers: { "Content-Type": "application/octet-stream" } });
    }, NOW);

    assert.equal(forwarded, true);
    assert.equal(response.status, 200);
    assert.equal(await response.text(), "license-bytes");
  });

  it("rejects a tampered token before contacting the upstream", async () => {
    const result = await streamForClient(protectedStream, "https://vanta.example", env, NOW);
    const tampered = new URL(result!.drm!.licenseUrl!);
    tampered.searchParams.set("token", "tampered");
    const response = await handleTidalWidevineProxy(
      new Request(tampered, { method: "POST", body: "challenge" }),
      env,
      async () => { throw new Error("must not be called"); },
      NOW
    );
    assert.equal(response.status, 401);
  });

  it("forwards Widevine license requests to a configured HIFI_API_URL instance", async () => {
    const hifiEnv = {
      DRM_PROXY_SECRET: "unit-test-secret",
      HIFI_API_URL: "https://hifi.example",
      HIFI_API_KEY: "hifi-upstream-key",
    } as Env;
    const result = await streamForClient(protectedStream, "https://vanta.example", hifiEnv, NOW);
    const request = new Request(result!.drm!.licenseUrl!, { method: "POST", body: "challenge" });
    let target: string | null = null;
    const response = await handleTidalWidevineProxy(request, hifiEnv, async (input, init) => {
      target = String(input);
      assert.equal(new Headers(init?.headers).get("X-Api-Key"), "hifi-upstream-key");
      return new Response("license-bytes", { status: 200 });
    }, NOW);
    assert.equal(target, "https://hifi.example/widevine");
    assert.equal(response.status, 200);
  });
});

describe("Amazon Widevine proxy", () => {
  const amazonEnv = {
    DRM_PROXY_SECRET: "unit-test-secret",
    AMAZON_DRM_PROXY_URL: "https://amazon-private.example/api/dmlvs/v1/getLicenseForPlaybackV3",
    AMAZON_DRM_PROXY_KEY: "amazon-upstream-key",
    TIDAL_API_URL: "https://tidal-private.example",
    TIDAL_API_KEY: "private-upstream-key",
  } as Env;

  const amazonStream: StreamResult = {
    url: "https://d17vo8z6jop21h.cloudfront.net/api/DashDrm.mpd?dmid=x&drm=wv",
    streamUrl: "https://d17vo8z6jop21h.cloudfront.net/api/DashDrm.mpd?dmid=x&drm=wv",
    provider: "amazon",
    format: "eac3-joc",
    drm: {
      scheme: "widevine",
      licenseProxy: "amazon",
      forceDefaultLicenseUri: true,
      licenseRequestHeaders: {
        "x-amz-music-customer-id": "A1CUST",
        "x-amz-music-device-id": "DEV-1",
        "x-amz-target": "com.amazon.device.drm.getLicenseForPlaybackV3",
      },
    },
  };

  it("rewrites the amazon marker to a signed public license URL and keeps per-track headers", async () => {
    const result = await streamForClient(amazonStream, "https://vanta.example", amazonEnv, NOW);
    assert.match(result?.drm?.licenseUrl ?? "", /^https:\/\/vanta\.example\/drm\/amazon\/license\?/);
    assert.equal(result?.drm?.licenseProxy, undefined);
    assert.equal(result?.drm?.licenseRequestHeaders?.["x-amz-music-customer-id"], "A1CUST");
    assert.equal(JSON.stringify(result).includes("amazon-upstream-key"), false);
    assert.equal(JSON.stringify(result).includes("amazon-private.example"), false);
  });

  it("streams the Widevine challenge to the amazon upstream with per-track headers", async () => {
    const result = await streamForClient(amazonStream, "https://vanta.example", amazonEnv, NOW);
    const request = new Request(result!.drm!.licenseUrl!, {
      method: "POST",
      headers: {
        "Content-Type": "application/octet-stream",
        "x-amz-music-customer-id": "A1CUST",
        "x-amz-music-device-id": "DEV-1",
        "x-amz-target": "com.amazon.device.drm.getLicenseForPlaybackV3",
      },
      body: "widevine-challenge",
    });
    let forwarded = false;
    const response = await handleAmazonLicenseProxy(request, amazonEnv, async (input, init) => {
      forwarded = true;
      assert.equal(String(input), "https://amazon-private.example/api/dmlvs/v1/getLicenseForPlaybackV3");
      const headers = new Headers(init?.headers);
      assert.equal(headers.get("X-Api-Key"), "amazon-upstream-key");
      assert.equal(headers.get("Authorization"), "Bearer amazon-upstream-key");
      assert.equal(headers.get("x-amz-music-customer-id"), "A1CUST");
      assert.equal(headers.get("x-amz-target"), "com.amazon.device.drm.getLicenseForPlaybackV3");
      assert.equal(await new Response(init?.body).text(), "widevine-challenge");
      return new Response("license-bytes", { status: 200, headers: { "Content-Type": "application/octet-stream" } });
    }, NOW);

    assert.equal(forwarded, true);
    assert.equal(response.status, 200);
    assert.equal(await response.text(), "license-bytes");
  });

  it("rejects a tampered amazon token before contacting the upstream", async () => {
    const result = await streamForClient(amazonStream, "https://vanta.example", amazonEnv, NOW);
    const tampered = new URL(result!.drm!.licenseUrl!);
    tampered.searchParams.set("token", "tampered");
    const response = await handleAmazonLicenseProxy(
      new Request(tampered, { method: "POST", body: "challenge" }),
      amazonEnv,
      async () => { throw new Error("must not be called"); },
      NOW
    );
    assert.equal(response.status, 401);
  });

  it("rejects a tidal-context token on the amazon route", async () => {
    const tidalResult = await streamForClient(protectedStream, "https://vanta.example", amazonEnv, NOW);
    const licenseUrl = tidalResult!.drm!.licenseUrl!;
    const response = await handleAmazonLicenseProxy(
      new Request(licenseUrl, { method: "POST", body: "challenge" }),
      amazonEnv,
      async () => { throw new Error("must not be called"); },
      NOW
    );
    assert.equal(response.status, 401);
  });

  it("fails open with 503 when no amazon upstream is configured", async () => {
    const bareEnv = { DRM_PROXY_SECRET: "unit-test-secret" } as Env;
    const result = await streamForClient(amazonStream, "https://vanta.example", bareEnv, NOW);
    assert.equal(result, null);
    const response = await handleAmazonLicenseProxy(
      new Request("https://vanta.example/drm/amazon/license?expires=1700000100&token=x", { method: "POST", body: "challenge" }),
      bareEnv,
      async () => { throw new Error("must not be called"); },
      NOW
    );
    assert.equal(response.status, 503);
  });

  it("falls back to the operator backend /v1/drm/amazon/license target", async () => {
    const operatorEnv = {
      DRM_PROXY_SECRET: "unit-test-secret",
      OPERATOR_BACKEND_URL: "https://operator.example",
      OPERATOR_BACKEND_API_KEY: "operator-key",
    } as Env;
    const result = await streamForClient(amazonStream, "https://vanta.example", operatorEnv, NOW);
    const request = new Request(result!.drm!.licenseUrl!, { method: "POST", body: "challenge" });
    let target: string | null = null;
    const response = await handleAmazonLicenseProxy(request, operatorEnv, async (input, init) => {
      target = String(input);
      assert.equal(new Headers(init?.headers).get("X-Api-Key"), "operator-key");
      return new Response("license-bytes", { status: 200 });
    }, NOW);
    assert.equal(target, "https://operator.example/v1/drm/amazon/license");
    assert.equal(response.status, 200);
  });
});
