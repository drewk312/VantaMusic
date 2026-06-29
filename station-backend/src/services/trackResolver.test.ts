import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { TrackResolver } from "./trackResolver";

describe("TrackResolver", () => {
  it("detects seed kinds from text", async () => {
    const resolver = new TrackResolver();
    const tests = [
      { text: "1980s rock", expected: "ERA" },
      { text: "chill study music", expected: "ACTIVITY" },
      { text: "upbeat pop", expected: "MOOD" },
      { text: "jazz classics", expected: "GENRE" },
      { text: "Bad Guy by Billie Eilish", expected: "TRACK" },
    ];
    for (const { text, expected } of tests) {
      const seed = await resolver.resolveSeed({ rawText: text });
      assert.equal(seed.kind, expected, `expected ${expected} for "${text}"`);
      assert.equal(seed.displayName, text);
    }
  });

  it("respects overrideType", async () => {
    const resolver = new TrackResolver();
    const seed = await resolver.resolveSeed({ rawText: "anything", overrideType: "ARTIST", artistName: "Queen" });
    assert.equal(seed.kind, "ARTIST");
    assert.equal(seed.artistName, "Queen");
  });

  it("resolves recommendations to track stubs", async () => {
    const resolver = new TrackResolver();
    const tracks = await resolver.resolveBatch([
      { id: "r1", title: "Song", artist: "Artist", album: "Album" },
    ]);
    assert.equal(tracks.length, 1);
    assert.equal(tracks[0]!.title, "Song");
    assert.equal(tracks[0]!.source, "gemini_ai");
  });
});


