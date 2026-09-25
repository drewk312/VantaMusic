import { it } from "node:test";
import assert from "node:assert/strict";
import { amazonCatalogTracks } from "./amazon-catalog";
import { filterTracks } from "../lib/content-purity";

it("keeps real Amazon track cards without invented durations or quality claims", () => {
  const tracks = amazonCatalogTracks({ items: [
    { primaryText: { text: "BIRDS OF A FEATHER" }, secondaryText: { text: "Billie Eilish" }, primaryLink: { deeplink: "/albums/B0CZVW38Y1?trackAsin=B0CZW15FZZ" } },
    { primaryText: { text: "Album" }, secondaryText: { text: "Billie Eilish" }, primaryLink: { deeplink: "/albums/B0CZVW38Y1" } },
    { primaryText: { text: "Untrusted" }, secondaryText: { text: "Artist" }, primaryLink: { deeplink: "https://example.com/tracks/B0CZW15FZZ" } },
  ] });
  assert.equal(tracks.length, 1); assert.equal(tracks[0].id, "B0CZW15FZZ");
  assert.equal(tracks[0].duration, undefined); assert.equal(tracks[0].isDolbyAtmos, false);
  assert.equal(filterTracks(tracks, "Billie Eilish Birds of a Feather").length, 1);
  assert.equal(filterTracks([{ ...tracks[0], duration: 0 }]).length, 0);
});
