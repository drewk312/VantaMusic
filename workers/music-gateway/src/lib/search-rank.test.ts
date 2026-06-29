import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { rankTracks, extractArtistsAndAlbums } from "./search-rank.js";
import type { GatewayTrack, ProviderId } from "../types";

function track(overrides: Partial<GatewayTrack> = {}): GatewayTrack {
  return {
    id: "1",
    title: "Song",
    artist: "Artist",
    provider: "deezer" as ProviderId,
    format: "flac",
    explicit: false,
    ...overrides,
  };
}

describe("search-rank", () => {
  it("ranks exact title matches higher", () => {
    const query = "bad guy billie eilish";
    const tracks = [
      track({ title: "Bad Guy", artist: "Billie Eilish" }),
      track({ title: "bad guy (remix)", artist: "Billie Eilish" }),
      track({ title: "Bad Guy", artist: "Some Cover Band" }),
    ];
    const ranked = rankTracks(query, tracks);
    assert.equal(ranked[0].artist, "Billie Eilish");
    assert.equal(ranked[0].title, "Bad Guy");
  });

  it("deduplicates artists and albums", () => {
    const tracks = [
      track({ title: "T1", artist: "A1", album: "Album" }),
      track({ title: "T2", artist: "A1", album: "Album" }),
      track({ title: "T3", artist: "A2" }),
    ];
    const { artists, albums } = extractArtistsAndAlbums(tracks);
    assert.equal(artists.length, 2);
    assert.equal(albums.length, 1);
  });
});
