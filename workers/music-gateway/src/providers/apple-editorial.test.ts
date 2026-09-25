import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { appleSongToGatewayTrack } from "./apple-music.js";
import { playlistToEditorialCard, findNewMusicPlaylist } from "./apple-editorial.js";

describe("apple editorial mapping", () => {
  it("maps a charted editorial playlist into a card with 600px artwork", () => {
    const card = playlistToEditorialCard({
      id: "pl.1",
      attributes: {
        playlistName: "Today's Hits",
        curatorName: "Apple Music Pop",
        description: "The <b>biggest</b> songs right now.",
        artwork: { url: "https://is1-ssl.mzstatic.com/thumb/{w}x{h}{f}.jpg" },
      },
    });
    assert.equal(card?.name, "Today's Hits");
    assert.equal(card?.curator, "Apple Music Pop");
    assert.equal(card?.description, "The biggest songs right now.");
    assert.equal(card?.artworkURL?.includes("600x600jpg"), true);
  });

  it("drops playlists without artwork instead of inventing a card", () => {
    const card = playlistToEditorialCard({ id: "pl.2", attributes: { playlistName: "No Art" } });
    assert.equal(card, null);
  });

  it("finds the rolling new-music playlist by name", () => {
    const found = findNewMusicPlaylist([
      { id: "pl.3", attributes: { playlistName: "A-List Pop" } },
      { id: "pl.4", attributes: { playlistName: "New Music Daily" } },
    ]);
    assert.equal((found as Record<string, unknown> | null)?.id, "pl.4");
  });

  it("keeps ISRC and apple_id on editorial tracks so playback resolves elsewhere", () => {
    const track = appleSongToGatewayTrack(
      {
        id: "1440857238",
        attributes: { name: "Song", artistName: "Artist", isrc: "USXXX0000001", durationInMillis: 200000 },
      },
      "us"
    );
    assert.equal(track?.id, "apple:1440857238");
    assert.equal(track?.isrc, "USXXX0000001");
    assert.equal(track?.apple_id, "1440857238");
  });
});
