import { it } from "node:test";
import assert from "node:assert/strict";
import { resolveTrack } from "./resolve";

it("resolves Amazon audio for a Deezer selection without accepting a mismatched ISRC", async (t) => {
  let mappedIsrc = "USIR10211559";
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const url = String(input);
    if (url.startsWith("https://api.deezer.com/track/")) return Response.json({ id: 1109731, isrc: "USIR10211559", title: "Bring Me To Life", artist: { name: "Evanescence" }, link: "https://www.deezer.com/track/1109731" });
    if (url.startsWith("https://api.odesli.co/")) return Response.json({ type: "song", metadataByUniqueId: { source: { isrc: mappedIsrc } }, links: { amazonMusic: { url: "https://music.amazon.com/albums/B0CZVW38Y1?trackAsin=B0CZW15FZZ" } } });
    return new Response("", { status: 404 });
  });
  assert.equal((await resolveTrack({ provider: "deezer", trackId: "1109731" })).amazon_id, "B0CZW15FZZ");
  mappedIsrc = "USUM72401991";
  assert.equal((await resolveTrack({ provider: "deezer", trackId: "1109731" })).amazon_id, undefined);
});

it("does not treat an album match as a track match", async (t) => {
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => String(input).startsWith("https://api.odesli.co/")
    ? Response.json({ type: "album", links: { deezer: { url: "https://www.deezer.com/track/12345" } } }) : new Response("", { status: 404 }));
  assert.equal((await resolveTrack({ provider: "amazon", trackId: "B0CZW15FZZ" })).deezer_id, undefined);
});
