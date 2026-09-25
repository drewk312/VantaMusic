import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { mapQualityToTidalFmt, parseStreamResponse } from "./hifi-api.js";
import { tidalFlacManifestToStream, tidalManifestToStream } from "./tidal-api.js";

describe("hifi-api tidal stream mapping", () => {
  it("maps quality to tidal numeric format", () => {
    assert.equal(mapQualityToTidalFmt("16"), "16");
    assert.equal(mapQualityToTidalFmt("24"), "27");
    assert.equal(mapQualityToTidalFmt("hi_res"), "27");
    assert.equal(mapQualityToTidalFmt("hi_res_lossless"), "27");
    assert.equal(mapQualityToTidalFmt("lossless"), "16");
    assert.equal(mapQualityToTidalFmt("high"), "9");
  });

  it("maps a download-music payload to a hi-res flac stream", () => {
    const stream = parseStreamResponse(
      {
        url: "https://stream.example.com/track.flac?token=abc",
        format: "flac",
        quality: "Hi-Res FLAC",
        bitrateKbps: 9216,
      },
      "tidal",
      "24"
    );
    assert.equal(stream?.provider, "tidal");
    assert.equal(stream?.format, "flac");
    assert.equal(stream?.isHiRes, true);
    assert.equal(stream?.bitrateKbps, 9216);
  });

  it("maps a 16-bit lossless payload", () => {
    const stream = parseStreamResponse(
      {
        url: "https://stream.example.com/track.flac?token=def",
      },
      "tidal",
      "16"
    );
    assert.equal(stream?.provider, "tidal");
    assert.match(stream?.quality ?? "", /16-bit/);
    assert.equal(stream?.isHiRes, false);
  });

  it("rejects non-https urls", () => {
    assert.equal(parseStreamResponse({ url: "http://stream.example.com/x" }, "tidal", "24"), null);
    assert.equal(parseStreamResponse({}, "tidal", "24"), null);
  });

  it("detects atmos from an EAC3_JOC signal", () => {
    const stream = parseStreamResponse(
      {
        url: "https://stream.example.com/atmos.m4a",
        format: "EAC3_JOC",
        quality: "Dolby Atmos",
      },
      "tidal",
      "atmos"
    );
    assert.equal(stream?.isDolbyAtmos, true);
    assert.equal(stream?.isSpatialAudio, true);
  });

  it("routes an Atmos request through the verified EAC3_JOC manifest adapter", () => {
    const stream = tidalManifestToStream(
      {
        data: {
          data: {
            attributes: {
              uri: "https://dash.example.com/atmos.mpd",
              formats: ["AACLC", "EAC3_JOC"],
              trackPresentation: "DOLBY_ATMOS",
              drmData: "widevine-license",
            },
          },
        },
      },
      "track-123"
    );
    assert.equal(stream?.provider, "tidal");
    assert.equal(stream?.isDolbyAtmos, true);
    assert.equal(stream?.format, "eac3-joc");
    assert.equal(stream?.drm?.scheme, "widevine");
    assert.equal(stream?.drm?.licenseProxy, "tidal");
  });

  it("does not accept a manifest lacking EAC3_JOC as Atmos", () => {
    const stream = tidalManifestToStream(
      {
        data: {
          data: {
            attributes: { uri: "https://dash.example.com/track.mpd", formats: ["AACLC"] },
          },
        },
      },
      "track-123"
    );
    assert.equal(stream, null);
  });

  it("maps a hi-res openapi manifest to a flac stream", () => {
    const stream = tidalFlacManifestToStream(
      {
        data: {
          attributes: {
            uri: "https://im-cf.manifest.tidal.com/1/manifests/abc.mpd?Expires=123",
            formats: ["LOSSLESS", "HI_RES"],
            drmData: null,
          },
        },
      },
      "283628183",
      "24"
    );
    assert.equal(stream?.provider, "tidal");
    assert.equal(stream?.format, "flac");
    assert.equal(stream?.isHiRes, true);
    assert.equal(stream?.isDolbyAtmos, false);
    assert.ok(!stream?.drm);
  });

  it("rejects an openapi manifest with only lossy formats", () => {
    const stream = tidalFlacManifestToStream(
      {
        data: {
          attributes: { uri: "https://im-cf.manifest.tidal.com/1/manifests/aac.mpd", formats: ["AACLC"] },
        },
      },
      "283628183",
      "24"
    );
    assert.equal(stream, null);
  });

  it("marks a drm-protected flac manifest", () => {
    const stream = tidalFlacManifestToStream(
      {
        data: {
          attributes: { uri: "https://im-cf.manifest.tidal.com/1/manifests/drm.mpd", formats: ["LOSSLESS"], drmData: "x" },
        },
      },
      "283628183",
      "16"
    );
    assert.equal(stream?.isHiRes, false);
    assert.equal(stream?.drm?.scheme, "widevine");
  });
});
