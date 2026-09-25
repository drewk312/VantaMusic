import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { getEditorialNewReleases } from "./releases.js";

describe("editorial new releases", () => {
  it("falls back to the live chart when Deezer editorial is empty", async () => {
    const requested: string[] = [];
    const tracks = await getEditorialNewReleases(5, async (url) => {
      requested.push(url);
      if (url.includes("/editorial/")) return { data: [] };
      if (url.includes("/chart/")) {
        return {
          data: [
            {
              id: 3818963601,
              title: "Dracula (JENNIE Remix)",
              duration: 209,
              artist: { name: "Tame Impala" },
              album: {
                id: 910510411,
                title: "Dracula (Remix)",
                cover_medium: "https://example.test/dracula.jpg",
              },
            },
          ],
        };
      }
      return null;
    });

    assert.equal(tracks.length, 1);
    assert.equal(tracks[0]?.id, "3818963601");
    assert.equal(tracks[0]?.provider, "deezer");
    assert.equal(tracks[0]?.title, "Dracula (JENNIE Remix)");
    assert.equal(tracks[0]?.discoveryKind, "chart");
    assert.ok(requested.some((url) => url.includes("/chart/0/tracks")));
    assert.equal(requested.some((url) => url.includes("marketingtools.apple.com")), false);
  });

  it("uses Apple discovery metadata when Deezer feeds are unavailable", async () => {
    const requested: string[] = [];
    const tracks = await getEditorialNewReleases(5, async (url) => {
      requested.push(url);
      if (url.includes("marketingtools.apple.com")) {
        return {
          feed: {
            results: [
              {
                id: "1844932150",
                name: "Choosin' Texas",
                artistName: "Ella Langley",
                releaseDate: "2025-10-17",
                artworkUrl100: "https://example.test/100x100bb.jpg",
              },
            ],
          },
        };
      }
      return url.includes("/editorial/") ? { data: [] } : null;
    });

    assert.equal(tracks.length, 1);
    assert.equal(tracks[0]?.id, "1844932150");
    assert.equal(tracks[0]?.provider, "apple");
    assert.equal(tracks[0]?.releaseYear, 2025);
    assert.equal(tracks[0]?.releaseDate, "2025-10-17");
    assert.equal(tracks[0]?.discoveryKind, "chart");
    assert.equal(tracks[0]?.artworkURL, "https://example.test/600x600bb.jpg");
    assert.ok(requested.some((url) => url.includes("/chart/0/tracks")));
    assert.ok(requested.some((url) => url.includes("/chart/0?")));
    assert.ok(requested.some((url) => url.includes("marketingtools.apple.com")));
  });

  it("keeps editorial releases primary when that feed has tracks", async () => {
    const requested: string[] = [];
    const tracks = await getEditorialNewReleases(5, async (url) => {
      requested.push(url);
      if (url.includes("/editorial/")) {
        return { data: [{ id: 22, title: "Fresh Album", release_date: "2026-08-14", artist: { name: "New Artist" } }] };
      }
      if (url.includes("/album/22/tracks")) {
        return {
          data: [
            {
              id: 33,
              title: "Fresh Song",
              artist: { name: "New Artist" },
              album: { id: 22, title: "Fresh Album" },
            },
          ],
        };
      }
      return null;
    });

    assert.equal(tracks.length, 1);
    assert.equal(tracks[0]?.title, "Fresh Song");
    assert.equal(tracks[0]?.releaseDate, "2026-08-14");
    assert.equal(tracks[0]?.discoveryKind, "new_release");
    assert.equal(requested.some((url) => url.includes("/chart/")), false);
    assert.equal(requested.some((url) => url.includes("marketingtools.apple.com")), false);
  });

  it("marks recent Apple songs as new_release with releaseDate", async () => {
    const today = new Date().toISOString().slice(0, 10);
    const tracks = await getEditorialNewReleases(5, async (url) => {
      if (url.includes("marketingtools.apple.com")) {
        return {
          feed: {
            results: [
              {
                id: "6812477290",
                name: "Last Thing You Need",
                artistName: "Morgan Wallen",
                releaseDate: today,
                artworkUrl100: "https://example.test/100x100bb.jpg",
              },
            ],
          },
        };
      }
      return null;
    });

    assert.equal(tracks.length, 1);
    assert.equal(tracks[0]?.id, "6812477290");
    assert.equal(tracks[0]?.provider, "apple");
    assert.equal(tracks[0]?.releaseDate, today);
    assert.equal(tracks[0]?.discoveryKind, "new_release");
  });
});
