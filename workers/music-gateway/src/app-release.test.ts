import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { handleAppRelease, readAppUpdateManifest } from "./app-release.js";
import type { Env } from "./types";

const env = {
  GATEWAY_PUBLIC_URL: "https://vanta-music-gateway.16drewk.workers.dev",
  APP_VERSION_CODE: "2",
  APP_VERSION_NAME: "1.1",
  APP_DONATE_URL: "https://ko-fi.com/vanta",
  APP_CHANGELOG: "Studio catalog search.",
} as Env;

describe("app-release", () => {
  it("returns a public update manifest", async () => {
    const manifest = await readAppUpdateManifest(env);
    assert.equal(manifest.versionCode, 2);
    assert.equal(manifest.versionName, "1.1");
    assert.equal(manifest.donateUrl, "https://ko-fi.com/vanta");
    assert.equal(manifest.apkUrl, "https://vanta-music-gateway.16drewk.workers.dev/app/download");
  });

  it("serves JSON from /app/update", async () => {
    const response = await handleAppRelease(
      new Request("https://vanta.example/app/update"),
      env,
      "/app/update"
    );
    assert.ok(response);
    assert.equal(response.status, 200);
    const body = await response.json() as { versionCode: number; donateUrl: string };
    assert.equal(body.versionCode, 2);
    assert.equal(body.donateUrl, "https://ko-fi.com/vanta");
  });

  it("returns 404 when no APK has been published", async () => {
    const response = await handleAppRelease(
      new Request("https://vanta.example/app/download"),
      env,
      "/app/download"
    );
    assert.ok(response);
    assert.equal(response.status, 404);
  });
});
