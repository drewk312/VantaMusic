import { it } from "node:test";
import assert from "node:assert/strict";
import type { Env } from "../types";
import { configureStreamTimingForTests, raceWithDeadline, streamFromProvider, streamWithFallback } from "./stream.js";

it("does not fall back from a missing Atmos mix to stereo FLAC inside the gateway", async (t) => {
  const requested: string[] = [];
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    if (!String(input).startsWith("https://operator.example/")) return new Response("", { status: 404 });
    const quality = new URL(String(input)).searchParams.get("quality")!;
    requested.push(quality);
    if (quality === "atmos") return new Response("", { status: 404 });
    return Response.json({ url: "https://media.example/song.flac", codec: "flac", provider: "tidal" });
  });
  const result = await streamWithFallback({
    ENABLED_STREAM_PROVIDERS: "tidal", OPERATOR_BACKEND_URL: "https://operator.example",
    OPERATOR_BACKEND_API_KEY: "test",
  } as Env, "12345", "atmos", "tidal");
  assert.equal(result, null);
  assert.ok(requested.includes("atmos"));
  assert.ok(!requested.includes("24"));
});

it("keeps a preferred source that succeeds after the fast-path window", async (t) => {
  t.after(configureStreamTimingForTests({ fastPathMs: 25, expandBudgetMs: 15, globalTimeoutMs: 400 }));
  let requests = 0;
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const url = String(input);
    if (url.startsWith("https://operator.example/")) {
      requests++;
      await new Promise((resolve) => setTimeout(resolve, 40));
      return Response.json({ url: "https://media.example/song.flac", codec: "flac", provider: "tidal" });
    }
    return new Response("", { status: 404 });
  });
  const env = {
    ENABLED_STREAM_PROVIDERS: "tidal",
    OPERATOR_BACKEND_URL: "https://operator.example",
    OPERATOR_BACKEND_API_KEY: "test",
  } as Env;
  const result = await streamWithFallback(env, "12345", "24", "tidal");
  assert.equal(result?.url, "https://media.example/song.flac");
  assert.equal(requests, 1);
});

it("tries a configured Atmos relay and rejects its stereo responses", async (t) => {
  let codec = "EAC3_JOC";
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    assert.match(String(input), /^https:\/\/relay.example/);
    return Response.json({ url: "https://media.example/song.mpd", codec, quality: "Dolby Atmos" });
  });
  const env = { TIDAL_STREAM_UPSTREAM: "https://relay.example" } as Env;
  assert.equal((await streamFromProvider(env, "tidal", "12345", "atmos"))?.isDolbyAtmos, true);
  codec = "aac";
  assert.equal(await streamFromProvider(env, "tidal", "12345", "atmos"), null);
});

it("does not start requests after the global deadline", async () => {
  let started = false;
  assert.equal(await raceWithDeadline(Date.now() - 1, async () => { started = true; return "late"; }), null);
  assert.equal(started, false);
});

it("uses exact recording IDs that arrive after the initial expansion window", async (t) => {
  t.after(configureStreamTimingForTests({ fastPathMs: 20, expandBudgetMs: 15, globalTimeoutMs: 400 }));
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    if (url.startsWith("https://api.odesli.co/")) {
      await new Promise((resolve) => setTimeout(resolve, 35));
      return Response.json({ links: { deezer: { url: "https://www.deezer.com/track/1109731" } } });
    }
    if (url === "https://operator.example/api/dl?provider=deezer&id=1109731&quality=24") {
      return Response.json({ url: "https://media.example/exact.flac", codec: "flac", provider: "deezer" });
    }
    if (url.startsWith("https://operator.example/") && url.includes("1109731")) {
      return Response.json({ url: "https://media.example/exact.flac", codec: "flac", provider: "deezer" });
    }
    return new Response("", { status: 404 });
  });
  const result = await streamWithFallback({ ENABLED_STREAM_PROVIDERS: "deezer", OPERATOR_BACKEND_URL: "https://operator.example", OPERATOR_BACKEND_API_KEY: "test" } as Env,
    "B0CZW15FZZ", "24", "amazon");
  assert.equal(result?.url, "https://media.example/exact.flac");
});
