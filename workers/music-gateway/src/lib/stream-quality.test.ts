import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  hasDolbyAtmosSignal,
  hasSpatialAudioSignal,
  hasSurroundSignal,
  inferBitrateKbps,
} from "./stream-quality.js";

describe("stream quality helpers", () => {
  it("detects explicit Dolby Atmos and spatial labels", () => {
    assert.equal(hasDolbyAtmosSignal("Dolby Atmos", "audio/eac3-joc"), true);
    assert.equal(hasSpatialAudioSignal("Dolby Atmos"), true);
    assert.equal(hasSurroundSignal("Dolby Atmos"), true);
  });

  it("does not infer Atmos from plain hi-res lossless quality", () => {
    assert.equal(hasDolbyAtmosSignal("24-bit / 96 kHz FLAC", "flac"), false);
    assert.equal(hasSpatialAudioSignal("24-bit / 96 kHz FLAC", "flac"), false);
    assert.equal(inferBitrateKbps("24-bit / 96 kHz FLAC", "flac") > 0, true);
  });
});
