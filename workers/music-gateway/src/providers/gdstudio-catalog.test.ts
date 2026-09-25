import { afterEach, it } from "node:test";
import assert from "node:assert/strict";
import type { Env } from "../types";
import { neteaseUrlExpiresAtSeconds, streamViaGdstudioExact } from "./gdstudio-catalog.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

function flacBytes() {
  return new Response(new Uint8Array([0x66, 0x4c, 0x61, 0x43, 0, 0, 0, 0, 0, 0, 0, 0]), {
    status: 206,
    headers: { "Content-Type": "audio/flac" },
  });
}

function mp3Bytes() {
  return new Response(new Uint8Array([0x49, 0x44, 0x33, 0, 0, 0, 0, 0, 0, 0, 0, 0]), {
    status: 206,
    headers: { "Content-Type": "audio/mpeg" },
  });
}

it("accepts GD Studio only as FLAC when the audio probe is FLAC", async () => {
  globalThis.fetch = (async (input) => {
    const url = String(input);
    if (url.includes("types=search") && url.includes("source=netease")) {
      return Response.json([
        { id: "20186194", name: "Spirit in the Sky", artist: ["Norman Greenbaum"], source: "netease" },
      ]);
    }
    if (url.includes("types=url") && url.includes("br=999")) {
      return Response.json({ url: "https://m701.music.126.net/track.flac", br: 999 });
    }
    if (url.includes("m701.music.126.net")) return flacBytes();
    return new Response("", { status: 404 });
  }) as typeof fetch;

  const stream = await streamViaGdstudioExact(
    { GDSTUDIO_API_URL: "https://music-api.gdstudio.xyz/api.php" } as Env,
    "Spirit in the Sky",
    "Norman Greenbaum",
    "24",
    "qobuz",
    "flac"
  );
  assert.equal(stream?.format, "flac");
  assert.equal(stream?.mimeType, "audio/flac");
  assert.ok(stream?.expiresAt);
  assert.ok((stream?.expiresAt ?? 0) > Date.now() / 1000);
  assert.ok((stream?.expiresAt ?? 0) < Date.now() / 1000 + 120);
});

it("does not treat labeled-999 MPEG as FLAC", async () => {
  globalThis.fetch = (async (input) => {
    const url = String(input);
    if (url.includes("types=search")) {
      return Response.json([
        { id: "20186194", name: "Spirit in the Sky", artist: ["Norman Greenbaum"], source: "netease" },
      ]);
    }
    if (url.includes("types=url")) {
      return Response.json({ url: "https://m701.music.126.net/track.mp3", br: 1566 });
    }
    if (url.includes("m701.music.126.net")) return mp3Bytes();
    return new Response("", { status: 404 });
  }) as typeof fetch;

  assert.equal(
    await streamViaGdstudioExact({} as Env, "Spirit in the Sky", "Norman Greenbaum", "24", "qobuz", "flac"),
    null
  );
  const mp3 = await streamViaGdstudioExact({} as Env, "Spirit in the Sky", "Norman Greenbaum", "24", "qobuz", "mp3");
  assert.equal(mp3?.format, "mp3");
});

it("rejects remix/cover hits unless the original title already has them", async () => {
  globalThis.fetch = (async (input) => {
    const url = String(input);
    if (url.includes("types=search")) {
      return Response.json([
        { id: "1", name: "Spirit in the Sky (Cover)", artist: ["Norman Greenbaum"], source: "netease" },
      ]);
    }
    return mp3Bytes();
  }) as typeof fetch;

  assert.equal(
    await streamViaGdstudioExact({} as Env, "Spirit in the Sky", "Norman Greenbaum", "24", "qobuz", "mp3"),
    null
  );
});

it("parses NetEase path timestamps as CST issued-at plus 15 minutes", () => {
  const expiry = neteaseUrlExpiresAtSeconds(
    "https://m801.music.126.net/20260912091831/c62bb141.flac"
  );
  const expected = Math.floor(Date.parse("2026-09-12T09:18:31+08:00") / 1000) + 15 * 60;
  assert.equal(expiry, expected);
});

it("skips NetEase URLs whose path timestamp is already expired", async () => {
  globalThis.fetch = (async (input) => {
    const url = String(input);
    if (url.includes("types=search") && url.includes("source=netease")) {
      return Response.json([
        { id: "20186194", name: "Spirit in the Sky", artist: ["Norman Greenbaum"], source: "netease" },
      ]);
    }
    if (url.includes("types=url")) {
      return Response.json({
        url: "https://m801.music.126.net/20200101000000/dead.flac",
        br: 999,
      });
    }
    return flacBytes();
  }) as typeof fetch;

  assert.equal(
    await streamViaGdstudioExact(
      { GDSTUDIO_API_URL: "https://music-api.gdstudio.xyz/api.php" } as Env,
      "Spirit in the Sky",
      "Norman Greenbaum",
      "24",
      "qobuz",
      "flac"
    ),
    null
  );
});
