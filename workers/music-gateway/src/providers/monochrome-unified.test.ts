import { it } from "node:test";
import assert from "node:assert/strict";
import type { Env } from "../types";
import { streamViaMonochromeUnified } from "./monochrome-unified.js";

const env = { MONOCHROME_API_BASE_URL: "https://mono.example", MONOCHROME_API_TOKEN: "test", MONOCHROME_TURNSTILE_JWT: "test" } as Env;
const resource = { url: "https://media.example/audio", kind: "manifest", delivery: "dash", codec: "ec-3", container: "mp4", quality: "DOLBY_ATMOS_EAC3_HIGH" };

it("uses a native Tidal ID and preserves the DASH codec and MIME type", async (t) => {
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    assert.equal(new URL(String(input)).searchParams.get("tidal_id"), "12345");
    return Response.json({ playback: [resource] });
  });
  const stream = await streamViaMonochromeUnified(env, "tidal", "12345", "atmos");
  assert.equal(stream?.mimeType, "application/dash+xml");
  assert.equal(stream?.format, "ec-3");
  assert.equal(stream?.isDolbyAtmos, true);
});

it("never sends Qobuz IDs as Tidal IDs and passes the recording ISRC", async (t) => {
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const query = new URL(String(input)).searchParams;
    assert.equal(query.has("tidal_id"), false);
    assert.equal(query.get("isrc"), "USQX91300108");
    return Response.json({ playback: [{ ...resource, codec: "flac", quality: "LOSSLESS", delivery: "hls" }] });
  });
  assert.equal((await streamViaMonochromeUnified(env, "qobuz", "12345", "16", { isrc: "USQX91300108" }))?.mimeType, "application/vnd.apple.mpegurl");
});

it("skips ciphertext and stereo candidates to find a playable Atmos resource", async (t) => {
  t.mock.method(globalThis, "fetch", async () => Response.json({ playback: [
    { ...resource, encryption: { key_id: "protected" } },
    { ...resource, codec: "aac", quality: "HIGH" },
    resource,
  ] }));
  assert.equal((await streamViaMonochromeUnified({ ...env, MONOCHROME_PROXY_DECRYPT: "true" }, "tidal", "12345", "atmos"))?.format, "ec-3");
});

it("does not infer Atmos from the requested quality", async (t) => {
  t.mock.method(globalThis, "fetch", async () => Response.json({ playback: [{ ...resource, quality: undefined }] }));
  assert.equal(await streamViaMonochromeUnified(env, "tidal", "12345", "atmos"), null);
});

it("skips verification-required sources without returning media", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("", { status: 428 }));
  assert.equal(await streamViaMonochromeUnified(env, "tidal", "12345", "24"), null);
});
