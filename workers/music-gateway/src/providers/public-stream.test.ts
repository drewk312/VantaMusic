import { afterEach, it } from "node:test";
import assert from "node:assert/strict";
import type { Env } from "../types";
import { streamWithPublicFallbacks } from "./public-stream.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

it("uses a Qobuz public URL mirror before treating the track as gone", async () => {
  globalThis.fetch = (async (input) => {
    const url = String(input);
    if (url.startsWith("https://music.wjhe.top/api/music/qobuz/url")) {
      return Response.json({ url: "https://streaming-qobuz-std.akamaized.net/full-track.flac" });
    }
    return new Response("", { status: 404 });
  }) as typeof fetch;

  const result = await streamWithPublicFallbacks({} as Env, "qobuz", "3135556", "24");
  assert.equal(result?.url, "https://streaming-qobuz-std.akamaized.net/full-track.flac");
  assert.equal(result?.provider, "qobuz");
  assert.equal(result?.isDolbyAtmos, false);
});

it("resolves Deezer FLAC from an ISRC instead of a numeric track id", async () => {
  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    if (url.includes("api.deezer.com/track/")) return new Response("", { status: 404 });
    if (url.includes("isrc=USUM71703861") && url.includes("dzr.tabs-vs-spaces.wtf")) {
      if (init?.method === "HEAD") return new Response(null, { status: 200 });
      return new Response(null, { status: 200 });
    }
    return new Response("", { status: 404 });
  }) as typeof fetch;

  const result = await streamWithPublicFallbacks(
    {} as Env,
    "tidal",
    "12345",
    "24",
    { isrc: "USUM71703861" }
  );
  assert.ok(result?.url.includes("USUM71703861"));
  assert.equal(result?.provider, "deezer");
});
