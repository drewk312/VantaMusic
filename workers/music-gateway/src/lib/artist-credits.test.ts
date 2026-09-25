import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mergeArtistCredits, splitArtistCredits } from "./artist-credits.js";

describe("splitArtistCredits", () => {
  it("splits Apple-style ampersand collabs", () => {
    assert.deepEqual(splitArtistCredits("Ella Langley & Morgan Wallen", "Ella Langley"), {
      primary: "Ella Langley",
      featured: ["Morgan Wallen"],
    });
  });

  it("keeps duo band names intact", () => {
    assert.deepEqual(splitArtistCredits("Simon & Garfunkel"), {
      primary: "Simon & Garfunkel",
      featured: [],
    });
  });

  it("parses feat. markers", () => {
    assert.deepEqual(splitArtistCredits("Drake feat. Lil Wayne"), {
      primary: "Drake",
      featured: ["Lil Wayne"],
    });
  });

  it("merges Apple partners onto a Deezer primary", () => {
    const merged = mergeArtistCredits(
      { artist: "Ella Langley" },
      { artist: "Ella Langley & Morgan Wallen", featuredArtists: ["Morgan Wallen"] }
    );
    assert.equal(merged.primary, "Ella Langley");
    assert.deepEqual(merged.featured, ["Morgan Wallen"]);
  });

  it("drops Qobuz session-credit dumps down to the real guest", () => {
    const credits = splitArtistCredits(
      "Ella Langley, Morgan Wallen, Austin Goodloe, Electric Guitar, Aaron Sterling, Drums"
    );
    assert.equal(credits.primary, "Ella Langley");
    assert.deepEqual(credits.featured, ["Morgan Wallen"]);
  });

  it("prefers a short Apple guest list over a Qobuz dump during merge", () => {
    const merged = mergeArtistCredits(
      {
        artist: "Ella Langley",
        featuredArtists: [
          "Morgan Wallen",
          "Austin Goodloe",
          "Ben West",
          "Electric Guitar",
          "Aaron Sterling",
        ],
      },
      { artist: "Ella Langley & Morgan Wallen", featuredArtists: ["Morgan Wallen"] }
    );
    assert.equal(merged.primary, "Ella Langley");
    assert.deepEqual(merged.featured, ["Morgan Wallen"]);
  });
});
