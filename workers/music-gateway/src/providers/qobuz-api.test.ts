import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { qobuzFileUrlToStream } from "./qobuz-api.js";

describe("qobuz file url mapping", () => {
  it("labels a compressed fallback from its actual MIME rather than requested FLAC", () => {
    const stream = qobuzFileUrlToStream({ url: "https://streaming.qobuz.com/song.mp3", mime_type: "audio/mpeg" }, "24");
    assert.equal(stream?.mimeType, "audio/mpeg");
    assert.doesNotMatch(stream?.quality ?? "", /FLAC|24-bit/i);
  });
  it("maps an official getFileUrl payload to a lossless stream", () => {
    const stream = qobuzFileUrlToStream(
      {
        url: "https://streaming.qobuz.com/track.flac?exp=1",
        mime_type: "audio/flac",
        bit_depth: 24,
        sampling_rate: 44100,
      },
      "24"
    );
    assert.equal(stream?.provider, "qobuz");
    assert.equal(stream?.format, "flac");
    assert.match(stream?.quality ?? "", /24-bit/);
    assert.ok((stream?.bitrateKbps ?? 0) >= 1411);
  });

  it("rejects missing or non-https urls", () => {
    assert.equal(qobuzFileUrlToStream({}, "24"), null);
    assert.equal(qobuzFileUrlToStream({ url: "http://streaming.qobuz.com/x" }, "24"), null);
  });

  it("rejects range-limited sample preview urls", () => {
    const sample = qobuzFileUrlToStream(
      {
        url: "https://streaming-qobuz-std.akamaized.net/file?eid=44198683&fmt=5&profile=raw&range=20-30&app_id=712109809&etsp=1787784040&hmac=x",
        mime_type: "audio/flac",
      },
      "16"
    );
    assert.equal(sample, null);
  });

  it("accepts full-track urls without a range limit", () => {
    const full = qobuzFileUrlToStream(
      {
        url: "https://streaming-qobuz-std.akamaized.net/file?eid=44198683&fmt=6&profile=raw&app_id=712109809&etsp=1787784040&hmac=x",
        mime_type: "audio/flac",
      },
      "16"
    );
    assert.equal(full?.provider, "qobuz");
  });
});
