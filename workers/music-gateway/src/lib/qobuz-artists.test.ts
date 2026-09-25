import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { qobuzArtistLine } from "./qobuz-artists.js";

describe("qobuzArtistLine", () => {
  it("keeps a single performer and drops role tokens", () => {
    assert.equal(qobuzArtistLine({ performer: { name: "Pharrell Williams" } }), "Pharrell Williams");
    assert.equal(
      qobuzArtistLine({
        performer: { name: "Pharrell Williams" },
        performers: "Pharrell Williams, MainArtist, AssociatedPerformer, Composer, Lyricist, Producer",
      }),
      "Pharrell Williams"
    );
  });

  it("appends featured performers and drops composers", () => {
    assert.equal(
      qobuzArtistLine({
        performer: { name: "A$AP Rocky" },
        performers: "A$AP Rocky, MainArtist - Skepta, FeaturedArtist - Happy Perez, Composer",
      }),
      "A$AP Rocky, Skepta"
    );
  });

  it("joins a performers list when performer.name is missing", () => {
    assert.equal(
      qobuzArtistLine({ performers: "Drake, 21 Savage, Project Pat" }),
      "Drake, 21 Savage, Project Pat"
    );
  });

  it("keeps the billed artist and drops Qobuz credit-line roles", () => {
    assert.equal(
      qobuzArtistLine({
        performer: {
          name: "Beyoncé, Beyoncé Knowles, Executive Producer, ComposerLyricist, MusicPublisher",
        },
        performers:
          "Beyoncé, MainArtist, AssociatedPerformer, ComposerLyricist, Executive Producer, MusicPublisher, EMI Music Publishing Limited",
      }),
      "Beyoncé"
    );
    assert.equal(
      qobuzArtistLine({
        performers:
          "The Backing Tracks, MainArtist - Beyoncé Knowles, ComposerLyricist, Songwriter, 2nd Engineer, Edited By",
      }),
      "The Backing Tracks"
    );
  });
});
