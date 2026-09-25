import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { decorateAmazonClearKey, normalizeOperatorStreamPayload } from "./operator-backend.js";
import type { StreamResult } from "../types.js";

describe("operator backend contract", () => {
  it("parses verified, protected Tidal Atmos without accepting a private license URL", () => {
    const stream = normalizeOperatorStreamPayload({
      stream: {
        provider: "tidal",
        url: "https://media.example/track.mpd",
        quality: "Dolby Atmos",
        codec: "E-AC-3",
        mimeType: "application/dash+xml",
        channels: 8,
        drm: { scheme: "widevine", licenseUrl: "https://private.example/license" },
      },
    }, "tidal");

    assert.equal(stream?.isDolbyAtmos, true);
    assert.equal(stream?.channelCount, 8);
    assert.equal(stream?.drm?.licenseProxy, "tidal");
    assert.equal(stream?.drm?.licenseUrl, undefined);
  });

  it("normalizes protected Amazon Widevine and passes only safe per-track license headers", () => {
    const stream = normalizeOperatorStreamPayload({
      provider: "amazon",
      url: "https://d17vo8z6jop21h.cloudfront.net/api/DashDrm.mpd?dmid=x&drm=wv",
      drm: {
        scheme: "widevine",
        licenseUrl: "https://private.example/license",
        licenseRequestHeaders: {
          "x-amz-music-customer-id": "A1CUST",
          "x-amz-target": "com.amazon.device.drm.getLicenseForPlaybackV3",
          "Authorization": "Bearer secret-credential",
          "Cookie": "session=leak",
        },
      },
    }, "amazon");

    assert.equal(stream?.drm?.licenseProxy, "amazon");
    assert.equal(stream?.drm?.licenseUrl, undefined);
    assert.equal(stream?.drm?.licenseRequestHeaders?.["x-amz-music-customer-id"], "A1CUST");
    assert.equal(stream?.drm?.licenseRequestHeaders?.["x-amz-target"], "com.amazon.device.drm.getLicenseForPlaybackV3");
    assert.equal(stream?.drm?.licenseRequestHeaders?.["Authorization"], undefined);
    assert.equal(stream?.drm?.licenseRequestHeaders?.["Cookie"], undefined);
    assert.equal(JSON.stringify(stream).includes("secret-credential"), false);
  });

  it("does not promote ordinary E-AC-3 or a mismatched provider to Atmos", () => {
    const stereo = normalizeOperatorStreamPayload({
      provider: "tidal",
      url: "https://media.example/track.mpd",
      codec: "E-AC-3",
    }, "tidal");
    assert.equal(stereo?.isDolbyAtmos, false);
    assert.equal(normalizeOperatorStreamPayload({
      provider: "amazon",
      url: "https://media.example/track.flac",
    }, "tidal"), null);
  });
});

describe("amazon clear-key delivery", () => {
  const env = { EXTENSION_PROXY_SECRET: "unit-test-secret" } as import("../types.js").Env;

  function stream(url: string): StreamResult {
    return {
      url,
      streamUrl: url,
      provider: "amazon",
      format: "mp4",
    };
  }

  it("wraps a locked CloudFront URL in the gateway decrypt proxy when a hex key is supplied", async () => {
    const wrapped = await decorateAmazonClearKey(
      env,
      "B0CZW15FZZ",
      stream("https://d30z.example.host/cloudfront/encrypted.m4a"),
      { key: "00112233445566778899aabbccddeeff", quality: "HD" }
    );
    assert.ok(wrapped);
    assert.match(wrapped!.streamUrl!, /^https:\/\/vanta-music-gateway\.16drewk\.workers\.dev\/audio\/extension\?token=/);
  });

  it("accepts kid:key key_specs and wraps with the AES key half", async () => {
    const wrapped = await decorateAmazonClearKey(
      env,
      "B0CZW15FZZ",
      stream("https://d30z.example.host/cloudfront/encrypted.m4a"),
      { key_specs: ["00112233445566778899aabbccddeeff:aabbccddeeff00112233445566778899"] }
    );
    assert.ok(wrapped);
    assert.match(wrapped!.streamUrl!, /\/audio\/extension\?token=/);
  });

  it("returns null for a locked CloudFront URL with no usable key (cascade continues)", async () => {
    const dropped = await decorateAmazonClearKey(
      env,
      "B0CZW15FZZ",
      stream("https://d2cwpp38qwkleu.cloudfront.net/encrypted.m4a"),
      { quality: "UHD" }
    );
    assert.equal(dropped, null);
  });

  it("leaves a decrypt-proxy URL untouched", async () => {
    const alreadyProxied = "https://vanta-music-gateway.16drewk.workers.dev/audio/extension?token=x";
    const kept = await decorateAmazonClearKey(
      env,
      "B0CZW15FZZ",
      stream(alreadyProxied),
      { key: "00112233445566778899aabbccddeeff" }
    );
    assert.equal(kept?.streamUrl, alreadyProxied);
  });

  it("returns null when the decrypt proxy is not configured", async () => {
    const bare = {} as import("../types.js").Env;
    const dropped = await decorateAmazonClearKey(
      bare,
      "B0CZW15FZZ",
      stream("https://d30z.example.host/cloudfront/encrypted.m4a"),
      { key: "00112233445566778899aabbccddeeff" }
    );
    assert.equal(dropped, null);
  });
});
