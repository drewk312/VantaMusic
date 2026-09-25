import { afterEach, it } from "node:test";
import assert from "node:assert/strict";
import type { Env } from "../types";
import { isDeezerIsrc, streamViaDeezerByTrackId } from "./deezer-public.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

it("accepts catalog ISRCs and rejects numeric Deezer ids as ISRCs", () => {
  assert.equal(isDeezerIsrc("GBUM71029604"), true);
  assert.equal(isDeezerIsrc("2801558052"), false);
});

it("looks up a real ISRC before hitting the public FLAC mirror", async () => {
  const seen: string[] = [];
  globalThis.fetch = (async (input, init) => {
    const url = String(input);
    seen.push(url);
    if (url === "https://api.deezer.com/track/2801558052") {
      return Response.json({ isrc: "GBUM71029604", title: "Get Lucky", artist: { name: "Daft Punk" }, duration: 248 });
    }
    if (url.includes("https://dzr.tabs-vs-spaces.wtf/stream/") && url.includes("GBUM71029604")) {
      if (init?.method === "HEAD") return new Response(null, { status: 200 });
      return new Response(null, { status: 200 });
    }
    return new Response("", { status: 404 });
  }) as typeof fetch;

  const result = await streamViaDeezerByTrackId({} as Env, "2801558052", undefined, "24");
  assert.ok(result?.url.includes("isrc=GBUM71029604"));
  assert.equal(result?.format, "flac");
  assert.equal(seen.some((url) => url.includes("isrc=2801558052")), false);
});
