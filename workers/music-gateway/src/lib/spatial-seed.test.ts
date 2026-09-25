import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { enrichSpatialFromSeed } from "./spatial-seed.js";

describe("spatial catalog seed", () => {
  it("labels seed-derived Atmos as catalog evidence, not codec verification", () => {
    const track = enrichSpatialFromSeed({
      id: "1",
      title: "Blinding Lights",
      artist: "The Weeknd",
      provider: "deezer",
    });

    assert.equal(track.atmosMixAvailable, true);
    assert.equal(track.isDolbyAtmos, false);
    assert.equal(track.spatialEvidence, "catalog");
  });

  it("does not mark Tidal/Amazon catalog rows as Atmos without a codec", () => {
    const track = enrichSpatialFromSeed({
      id: "2",
      title: "Blinding Lights",
      artist: "The Weeknd",
      provider: "tidal",
    });

    assert.equal(track.atmosMixAvailable, true);
    assert.equal(track.isDolbyAtmos, false);
    assert.equal(track.spatialEvidence, "catalog");
  });

  it("marks Atmos true only when the delivered codec is E-AC-3 / JOC", () => {
    const track = enrichSpatialFromSeed({
      id: "3",
      title: "Blinding Lights",
      artist: "The Weeknd",
      provider: "tidal",
      format: "eac3-joc",
    });

    assert.equal(track.isDolbyAtmos, true);
    assert.equal(track.spatialEvidence, "verified");
  });
});
