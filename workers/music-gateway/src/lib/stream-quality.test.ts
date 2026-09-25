import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { acceptStreamForRequestedQuality } from "../providers/stream.js";
import type { ProviderId } from "../types.js";
import {
  deriveSpatialFormat,
  hasDolbyAtmosSignal,
  hasIamfSignal,
  hasSony360Signal,
  hasSpatialAudioSignal,
  hasSurroundSignal,
  inferBitrateKbps,
  inferContainerFromUrl,
  isAtmosQuality,
  qualityLabelFromBitrate,
  streamFidelityScore,
} from "./stream-quality.js";

describe("stream quality helpers", () => {
  it("detects explicit Dolby Atmos and spatial labels", () => {
    assert.equal(hasDolbyAtmosSignal("Dolby Atmos", "audio/eac3-joc"), true);
    assert.equal(hasSpatialAudioSignal("Dolby Atmos", "audio/eac3-joc"), true);
    assert.equal(hasSurroundSignal("Dolby Atmos", "audio/eac3-joc"), true);
    assert.equal(hasDolbyAtmosSignal("24-bit / 48 kHz FLAC", "flac"), false);
    assert.equal(isAtmosQuality("atmos"), true);
    assert.equal(isAtmosQuality("ac4"), true);
    assert.equal(isAtmosQuality("24"), false);
    assert.equal(isAtmosQuality("HI_RES"), false);
  });

  it("does not infer Atmos from plain hi-res lossless quality", () => {
    assert.equal(hasDolbyAtmosSignal("24-bit / 96 kHz FLAC", "flac"), false);
    assert.equal(hasSpatialAudioSignal("24-bit / 96 kHz FLAC", "flac"), false);
    assert.equal(inferBitrateKbps("24-bit / 96 kHz FLAC", "flac") > 0, true);
  });

  it("requires JOC or an explicit immersive profile for E-AC-3 and AC-4", () => {
    assert.equal(hasDolbyAtmosSignal("audio/eac3"), false);
    assert.equal(hasDolbyAtmosSignal("audio/ac4"), false);
    assert.equal(hasDolbyAtmosSignal("Dolby Atmos", "audio/eac3"), true);
    assert.equal(hasDolbyAtmosSignal("EAC3_JOC"), true);
  });

  it("does not stamp unlabeled M4A as 256 kbps AAC", () => {
    assert.equal(inferBitrateKbps(null, "m4a"), 0);
    assert.equal(inferBitrateKbps("atmos", "m4a"), 0);
    assert.equal(inferContainerFromUrl("https://cdn.example/Welcome To The Jungle.m4a"), "m4a");
    assert.equal(qualityLabelFromBitrate(3284, "m4a"), "Hi-Res M4A");
  });

  it("ranks a 3284 kbps Atmos M4A above CD FLAC and YouTube", () => {
    const atmos = streamFidelityScore({
      bitrateKbps: 3284,
      format: "m4a",
      quality: "atmos",
      isDolbyAtmos: true,
    });
    const flac = streamFidelityScore({ bitrateKbps: 1411, format: "flac", quality: "16-bit" });
    const youtube = streamFidelityScore({ bitrateKbps: 128, format: "webm" });
    assert.ok(atmos > flac);
    assert.ok(flac > youtube);
  });
});

describe("spatial format derivation", () => {
  it("detects Sony 360 Reality Audio evidence but not plain numbers", () => {
    assert.equal(hasSony360Signal("MPEG-H 360 Reality Audio", "flac"), true);
    assert.equal(hasSony360Signal("360RA"), true);
    assert.equal(hasSony360Signal("360"), false);
    assert.equal(hasSony360Signal("mha1"), false);
    assert.equal(hasSony360Signal("Dolby Atmos"), false);
  });

  it("detects Eclipsa Audio / IAMF but keeps shared sample entries unattributed", () => {
    assert.equal(hasIamfSignal("audio/iamf"), true);
    assert.equal(hasIamfSignal("Eclipsa Audio"), true);
    assert.equal(hasIamfSignal("mha1"), false);
    assert.equal(hasIamfSignal("Dolby Atmos"), false);
  });

  it("derives explicit formats only from proven signals", () => {
    assert.equal(deriveSpatialFormat({ isDolbyAtmos: true, format: "eac3-joc" }), "DOLBY_ATMOS");
    assert.equal(deriveSpatialFormat({ format: "audio/iamf" }), "ECLIPSA_AUDIO");
    assert.equal(deriveSpatialFormat({ quality: "Eclipsa Audio" }), "ECLIPSA_AUDIO");
    assert.equal(deriveSpatialFormat({ format: "audio/mha1" }), "UNKNOWN_SPATIAL");
    assert.equal(deriveSpatialFormat({ quality: "360 Reality Audio" }), "SONY_360_REALITY_AUDIO");
    assert.equal(deriveSpatialFormat({ mimeType: "audio/mpeg-h" }), "SONY_360_REALITY_AUDIO");
    assert.equal(deriveSpatialFormat({ isSpatialAudio: true, format: "flac" }), "UNKNOWN_SPATIAL");
    assert.equal(deriveSpatialFormat({ isSurround: true }), "UNKNOWN_SPATIAL");
    assert.equal(deriveSpatialFormat({ format: "flac", quality: "24-bit / 96 kHz" }), "NONE");
    assert.equal(deriveSpatialFormat({}), "NONE");
    // A bare Atmos label with no codec proof is not an explicit format: the
    // caller must not elevate to a spatial tier on marketing claims alone.
    assert.equal(deriveSpatialFormat({ quality: "Dolby Atmos", format: undefined, mimeType: undefined }), "NONE");
    // Ordinary E-AC-3 without an immersive profile is not spatial.
    assert.equal(deriveSpatialFormat({ format: "audio/eac3" }), "NONE");
  });
});

describe("stream accept gate", () => {
  const qobuz = "qobuz" as ProviderId;
  const tidal = "tidal" as ProviderId;

  it("rejects range-limited Qobuz sample URLs regardless of quality", () => {
    const sample16 = { provider: qobuz, url: "https://stream.qobuz.com/download?quality=5&range=20-30" };
    const sample24 = { provider: qobuz, url: "https://stream.qobuz.com/track/481312/stream.mp3?fmt=5&range=0-29" };
    assert.equal(acceptStreamForRequestedQuality(sample16, "16"), null);
    assert.equal(acceptStreamForRequestedQuality(sample24, "24"), null);
  });

  it("accepts non-sample lossy streams", () => {
    const full = { provider: qobuz, url: "https://stream.qobuz.com/download?quality=5" };
    assert.deepEqual(acceptStreamForRequestedQuality(full, "16"), full);
  });

  it("keeps Atmos only when codec signal is present", () => {
    const atmos = { provider: tidal, url: "https://x/m.mpd", isDolbyAtmos: true, format: "eac3-joc" };
    const fakeAtmos = { provider: tidal, url: "https://x/m.mpd", isDolbyAtmos: true, format: "flac" };
    assert.equal(acceptStreamForRequestedQuality(atmos, "atmos"), atmos);
    assert.equal(acceptStreamForRequestedQuality(fakeAtmos, "atmos"), null);
    assert.equal(acceptStreamForRequestedQuality(fakeAtmos, "16"), null);
    assert.equal(acceptStreamForRequestedQuality(atmos, "24"), null);
  });
});
