import { describe, it } from "node:test";
import assert from "node:assert/strict";
import type { Env } from "../types";
import { fetchJson, streamProvidersInOrder } from "./shared.js";

const catalogEnv = {
  ENABLED_STREAM_PROVIDERS: "qobuz,tidal,deezer,amazon,pandora",
} as Env;

it("accepts relay URLs returned as plain text without rereading a consumed response", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("https://media.example/full.flac"));
  assert.deepEqual(await fetchJson("https://relay.example"), { url: "https://media.example/full.flac" });
});

describe("streamProvidersInOrder", () => {
  it("asks Tidal and Amazon for Atmos without requiring a user Tidal token", () => {
    assert.deepEqual(streamProvidersInOrder(catalogEnv, undefined, "atmos"), ["tidal", "amazon"]);
    assert.deepEqual(streamProvidersInOrder(catalogEnv, "tidal", "atmos"), ["tidal", "amazon"]);
    assert.deepEqual(streamProvidersInOrder(catalogEnv, "qobuz", "atmos"), ["tidal", "amazon"]);
  });

  it("does not use Qobuz as an Atmos source", () => {
    const env = {
      ...catalogEnv,
      TIDAL_STREAM_UPSTREAM: "https://licensed.example/tidal",
    } as Env;
    assert.deepEqual(streamProvidersInOrder(env, undefined, "atmos"), ["tidal", "amazon"]);
    assert.deepEqual(streamProvidersInOrder(env, "qobuz", "atmos"), ["tidal", "amazon"]);
    assert.deepEqual(streamProvidersInOrder(env, "tidal", "atmos"), ["tidal", "amazon"]);
  });

  it("keeps Qobuz first for standard hi-res FLAC", () => {
    assert.deepEqual(streamProvidersInOrder(catalogEnv, undefined, "24")[0], "qobuz");
  });

  it("asks Amazon only for Sony 360 Reality Audio", () => {
    assert.deepEqual(streamProvidersInOrder(catalogEnv, undefined, "360"), ["amazon"]);
    assert.deepEqual(streamProvidersInOrder(catalogEnv, "amazon", "360"), ["amazon"]);
    assert.deepEqual(streamProvidersInOrder(catalogEnv, "tidal", "360"), ["amazon"]);
  });
});
