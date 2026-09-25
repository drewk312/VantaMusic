import { it } from "node:test";
import assert from "node:assert/strict";
import { Blowfish } from "egoroof-blowfish";
import { deezerKey, deezerTransform, extensionAudioUrl, handleExtensionAudio } from "./audio-proxy";
import type { Env } from "../types";

const id = "1109731";
const env = { EXTENSION_PROXY_SECRET: "test-secret", GATEWAY_BASE_URL: "https://vanta.example" } as Env;
const plain = Uint8Array.from({ length: 15111 }, (_, i) => i % 251);
plain.set(new TextEncoder().encode("fLaC"));
plain.fill(0, 2040, 2048);
const encrypted = plain.slice();
const cipher = new Blowfish(deezerKey(id), Blowfish.MODE.CBC, Blowfish.PADDING.NULL);
for (let offset = 0; offset + 2048 <= plain.length; offset += 6144) {
  cipher.setIv(Uint8Array.from([0, 1, 2, 3, 4, 5, 6, 7]));
  encrypted.set(cipher.encode(plain.subarray(offset, offset + 2048)).subarray(0, 2048), offset);
}

it("decodes full audio across arbitrary chunk boundaries and preserves zero tails", async () => {
  const input = new ReadableStream<Uint8Array>({ start(controller) {
    for (let offset = 0; offset < encrypted.length; offset += 711) controller.enqueue(encrypted.slice(offset, offset + 711));
    controller.close();
  } });
  assert.deepEqual(new Uint8Array(await new Response(input.pipeThrough(deezerTransform(id))).arrayBuffer()), plain);
});

for (const [start, end] of [[0, 255], [1000, 3500], [6101, 8999], [14900, 15110]]) {
  it(`serves exact decrypted byte ranges ${start}-${end}`, async (t) => {
    t.mock.method(globalThis, "fetch", async (_input: unknown, init?: RequestInit) => {
      const range = new Headers(init?.headers).get("Range")!.match(/^bytes=(\d+)-(\d+)$/)!;
      const first = Number(range[1]); const last = Math.min(Number(range[2]), encrypted.length - 1);
      return new Response(encrypted.slice(first, last + 1), { status: 206, headers: {
        "Content-Range": `bytes ${first}-${last}/${encrypted.length}`, "Content-Length": String(last - first + 1),
      } });
    });
    const url = await extensionAudioUrl(env, { provider: "deezer", id, url: "https://cdn.example/audio", format: "flac", expires: Date.now() + 60000 });
    const response = await handleExtensionAudio(new Request(url!, { headers: { Range: `bytes=${start}-${end}` } }), env);
    assert.equal(response.status, 206);
    assert.equal(response.headers.get("Content-Range"), `bytes ${start}-${end}/${plain.length}`);
    assert.deepEqual(new Uint8Array(await response.arrayBuffer()), plain.slice(start, end + 1));
  });
}

it("rejects expired and tampered audio tickets before contacting an upstream", async (t) => {
  t.mock.method(globalThis, "fetch", () => { throw Error("Must not fetch"); });
  const expired = await extensionAudioUrl(env, { provider: "deezer", id, url: "https://cdn.example/audio", format: "flac", expires: Date.now() - 1 });
  assert.equal((await handleExtensionAudio(new Request(expired!), env)).status, 401);
  assert.equal((await handleExtensionAudio(new Request("https://vanta.example/audio/extension?token=forged"), env)).status, 401);
});
