import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { dedupeSearchPlaylists, mapDeezerPlaylistItem, playlistNameMatchesQuery } from "./search.js";

describe("search playlists", () => {
  it("maps Deezer playlist cards and drops blanks", () => {
    assert.equal(mapDeezerPlaylistItem({ title: "This Is Metallica" }), null);
    assert.deepEqual(
      mapDeezerPlaylistItem({
        id: 908,
        title: "This Is Metallica",
        picture_xl: "https://example.com/p.jpg",
        nb_tracks: 50,
        user: { name: "Deezer" },
      }),
      {
        id: "908",
        name: "This Is Metallica",
        curator: "Deezer",
        artworkURL: "https://example.com/p.jpg",
        trackCount: 50,
        source: "deezer",
      }
    );
  });

  it("keeps the first playlist when names collide across catalogs", () => {
    const merged = dedupeSearchPlaylists([
      { id: "908", name: "This Is Metallica", source: "deezer" },
      { id: "pl.abc", name: "This Is Metallica", source: "apple" },
      { id: "908", name: "This Is Metallica", source: "deezer" },
      { id: "12", name: "Metallica Essentials", source: "deezer" },
    ]);
    assert.deepEqual(
      merged.map((playlist) => playlist.id),
      ["908", "12"]
    );
  });

  it("keeps playlists whose titles actually mention the query", () => {
    assert.equal(playlistNameMatchesQuery("100% Metallica", "Metallica"), true);
    assert.equal(playlistNameMatchesQuery("Riding music", "Metallica"), false);
    assert.equal(playlistNameMatchesQuery("This Is Metallica", "this is metallica"), true);
  });
});
