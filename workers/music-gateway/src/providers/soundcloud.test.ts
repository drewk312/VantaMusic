import { it } from "node:test";
import assert from "node:assert/strict";
import { searchSoundCloud, soundCloudPlayable, streamSoundCloud, streamSoundCloudExact } from "./soundcloud";
import { streamWithFallback } from "./stream";
import type { Env } from "../types";

const env = { SOUNDCLOUD_CLIENT_ID: "a".repeat(32), ENABLED_STREAM_PROVIDERS: "soundcloud" } as Env;
const track = { id: 12345, title: "Test Song", user: { username: "Artist" }, duration: 180000, full_duration: 180000,
  streamable: true, policy: "ALLOW", track_authorization: "track-token",
  media: { transcodings: [{ url: "https://api-v2.soundcloud.com/media/test", snipped: false, format: { protocol: "progressive", mime_type: "audio/mpeg" } }] } };

it("rejects SoundCloud previews, unavailable tracks, and restricted policies", () => {
  assert.equal(soundCloudPlayable(track), true);
  for (const patch of [{ policy: "SNIP" }, { policy: "BLOCK" }, { streamable: false }, { duration: 30000 },
    { media: { transcodings: [{ snipped: true, format: { protocol: "progressive" } }] } }]) {
    assert.equal(soundCloudPlayable({ ...track, ...patch }), false);
  }
});

it("returns public SoundCloud tracks with seconds and the correct catalog identity", async (t) => {
  t.mock.method(globalThis, "fetch", async () => Response.json({ collection: [track, { ...track, id: 2, policy: "SNIP" }] }));
  const tracks = await searchSoundCloud("Test", env);
  assert.equal(tracks.length, 1);
  assert.equal(tracks[0].provider, "soundcloud");
  assert.equal(tracks[0].duration, 180);
  assert.equal(tracks[0].isHiRes, false);
});

it("resolves provider-prefixed IDs through the gateway without touching another numeric catalog", async (t) => {
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const url = new URL(String(input));
    assert.equal(url.hostname, "api-v2.soundcloud.com");
    if (url.pathname === "/tracks/12345") return Response.json(track);
    assert.equal(url.searchParams.get("track_authorization"), "track-token");
    return Response.json({ url: "https://cf-media.sndcdn.com/test.mp3" });
  });
  const stream = await streamWithFallback(env, "soundcloud:12345", "24");
  assert.equal(stream?.url, "https://cf-media.sndcdn.com/test.mp3");
  assert.equal(stream?.provider, "soundcloud");
  assert.equal(stream?.bitrateKbps, 128);
  assert.equal(stream?.isDolbyAtmos, false);
});

it("never forwards track authorization to an upstream-supplied foreign host", async (t) => {
  let calls = 0;
  t.mock.method(globalThis, "fetch", async () => {
    calls++;
    return Response.json({ ...track, media: { transcodings: [{ ...track.media.transcodings[0], url: "https://attacker.example/media" }] } });
  });
  assert.equal(await streamSoundCloud("12345", env), null);
  assert.equal(calls, 1);
});

it("only uses SoundCloud as an exact-duration last resort", async (t) => {
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const url = String(input);
    if (url.includes("/search/tracks")) {
      return Response.json({
        collection: [
          { ...track, id: 99, title: "Test Song Remix", duration: 180000, full_duration: 180000 },
          track,
        ],
      });
    }
    if (url.includes("/tracks/12345")) return Response.json(track);
    return Response.json({ url: "https://cf-media.sndcdn.com/exact.mp3" });
  });
  const remix = await streamSoundCloudExact(env, "Test Song", "Artist", 180);
  assert.equal(remix?.url, "https://cf-media.sndcdn.com/exact.mp3");
  assert.equal(await streamSoundCloudExact(env, "Test Song", "Artist", 240), null);
});
