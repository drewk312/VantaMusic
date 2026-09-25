import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { normalizePublicHttpsUrl, normalizeStreamResult } from "./safe-stream-url.js";

describe("safe stream URLs", () => {
  it("accepts public HTTPS audio URLs", () => {
    assert.equal(
      normalizePublicHttpsUrl("https://audio.example.com/song.flac?token=secret"),
      "https://audio.example.com/song.flac?token=secret"
    );
  });

  it("rejects cleartext, credentialed, loopback, and private destinations", () => {
    for (const value of [
      "http://audio.example.com/song.flac",
      "https://user:password@audio.example.com/song.flac",
      "https://localhost/song.flac",
      "https://127.0.0.1/song.flac",
      "https://10.0.0.1/song.flac",
      "https://169.254.169.254/latest/meta-data",
      "https://192.168.1.2/song.flac",
      "https://[::1]/song.flac",
    ]) {
      assert.equal(normalizePublicHttpsUrl(value), null, value);
    }
  });

  it("normalizes both stream result URL fields", () => {
    const result = normalizeStreamResult({
      url: "https://audio.example.com/song.flac",
      provider: "qobuz",
    });
    assert.equal(result?.url, "https://audio.example.com/song.flac");
    assert.equal(result?.streamUrl, "https://audio.example.com/song.flac");
  });
});
