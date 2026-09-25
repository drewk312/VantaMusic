import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { durationFromMpd, tidalFlacManifestToStream, tidalManifestToStream } from "./tidal-api.js";

describe("Tidal manifest adapter", () => {
  it("returns verified Atmos and marks protected manifests for the license proxy", () => {
    const stream = tidalManifestToStream({
      data: {
        data: {
          attributes: {
            uri: "https://media.example/track.mpd",
            formats: ["EAC3_JOC"],
            drmData: "encrypted",
          },
        },
      },
    }, "123", 1_700_000_000);

    assert.equal(stream?.isDolbyAtmos, true);
    assert.equal(stream?.format, "eac3-joc");
    assert.equal(stream?.mimeType, "application/dash+xml");
    assert.equal(stream?.drm?.licenseProxy, "tidal");
    assert.equal(stream?.drm?.licenseUrl, undefined);
    assert.equal(stream?.expiresAt, 1_700_000_480);
  });

  it("rejects inline manifests and non-JOC formats", () => {
    assert.equal(tidalManifestToStream({
      data: { data: { attributes: { uri: "data:application/dash+xml;base64,AAAA", formats: ["EAC3_JOC"] } } },
    }), null);
    assert.equal(tidalManifestToStream({
      data: { data: { attributes: { uri: "https://media.example/track.mpd", formats: ["AAC"] } } },
    }), null);
  });

  it("rejects PREVIEW FLAC manifests as full-length streams", () => {
    const stream = tidalFlacManifestToStream({
      data: {
        attributes: {
          uri: "https://im-cf.manifest.tidal.com/1/manifests/preview.mpd",
          formats: ["FLAC"],
          trackPresentation: "PREVIEW",
        },
      },
    }, "487719101", "16");

    assert.equal(stream, null);
  });

  it("accepts FULL FLAC manifests", () => {
    const stream = tidalFlacManifestToStream({
      data: {
        attributes: {
          uri: "https://im-cf.manifest.tidal.com/1/manifests/full.mpd",
          formats: ["FLAC"],
          trackPresentation: "FULL",
        },
      },
    }, "487719101", "16");

    assert.equal(stream?.url, "https://im-cf.manifest.tidal.com/1/manifests/full.mpd");
    assert.equal(stream?.format, "flac");
  });

  it("rejects EARLY PREVIEW Atmos manifests", () => {
    assert.equal(tidalManifestToStream({
      data: {
        data: {
          attributes: {
            uri: "https://im-cf.manifest.tidal.com/1/manifests/atmos-preview.mpd",
            formats: ["EAC3_JOC"],
            trackPresentation: "PREVIEW",
          },
        },
      },
    }, "487719101"), null);
  });

  it("parses MPD preview durations", () => {
    assert.equal(durationFromMpd('<MPD mediaPresentationDuration="PT29.907S"></MPD>'), 29.907);
    assert.equal(durationFromMpd('<MPD mediaPresentationDuration="PT241.002S"></MPD>'), 241.002);
    assert.equal(durationFromMpd('<MPD mediaPresentationDuration="PT3M43.773S"></MPD>'), 223.773);
    assert.equal(durationFromMpd('<MPD mediaPresentationDuration="PT1H2M3.4S"></MPD>'), 3723.4);
    assert.equal(durationFromMpd("<MPD></MPD>"), null);
  });
});
