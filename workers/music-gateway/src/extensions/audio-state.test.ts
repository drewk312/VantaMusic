import { it } from "node:test";
import assert from "node:assert/strict";
import { routeExtensionAudio } from "./audio-state";
import type { Env } from "../types";

it("forwards range requests and streams every byte without processing at the edge", async () => {
  const payload = new Uint8Array(2 * 1024 * 1024).fill(73);
  let forwarded: Request | undefined;
  const env = { EXTENSION_AUDIO: {
    newUniqueId: () => "request-instance",
    get: () => ({ fetch: async (request: Request) => {
      forwarded = request;
      return new Response(payload, { status: 206, headers: { "Content-Range": `bytes 1024-${1023 + payload.length}/3000000` } });
    } }),
  } } as unknown as Env;
  const request = new Request("https://vanta.example/audio/extension?token=opaque", { headers: { Range: "bytes=1024-" } });
  const response = await routeExtensionAudio(request, env);
  assert.equal(forwarded, request);
  assert.equal(forwarded?.headers.get("Range"), "bytes=1024-");
  assert.equal(response.status, 206);
  assert.deepEqual(new Uint8Array(await response.arrayBuffer()), payload);
});
it("fails visibly when the audio execution binding is missing", async () => {
  assert.equal((await routeExtensionAudio(new Request("https://vanta.example/audio/extension"), {} as Env)).status, 503);
});
