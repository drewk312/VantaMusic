import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { amazonDecryptionKey, amazonDirectUrlIsLocked } from "./playable-stream.js";

describe("playable-stream", () => {
  it("accepts a 32-char hex Amazon key", () => {
    assert.equal(amazonDecryptionKey("00112233445566778899aabbccddeeff"), "00112233445566778899aabbccddeeff");
    assert.equal(amazonDecryptionKey("short"), null);
    assert.equal(amazonDecryptionKey(undefined), null);
  });

  it("extracts the AES key half from kid:key Amazon Next payloads", () => {
    assert.equal(
      amazonDecryptionKey("00a4ad5ccdcbd8ccf4dd293626dadac3:e221d08e4d2cafd6af7d4f82c9891de7"),
      "e221d08e4d2cafd6af7d4f82c9891de7"
    );
    assert.equal(amazonDecryptionKey(["bad", "00a4ad5ccdcbd8ccf4dd293626dadac3:e221d08e4d2cafd6af7d4f82c9891de7"]), "e221d08e4d2cafd6af7d4f82c9891de7");
    assert.equal(
      amazonDirectUrlIsLocked(
        "amazon",
        "https://d123.cloudfront.net/file.mp4",
        "00a4ad5ccdcbd8ccf4dd293626dadac3:e221d08e4d2cafd6af7d4f82c9891de7"
      ),
      false
    );
  });

  it("treats raw Amazon CloudFront URLs without a key as locked", () => {
    assert.equal(
      amazonDirectUrlIsLocked("amazon", "https://d123.cloudfront.net/file.mp4"),
      true
    );
    assert.equal(
      amazonDirectUrlIsLocked("amazon", "https://d123.cloudfront.net/file.mp4", "00112233445566778899aabbccddeeff"),
      false
    );
    assert.equal(
      amazonDirectUrlIsLocked("amazon", "https://vanta.example/manifest/mpd?data=abc"),
      false
    );
    assert.equal(
      amazonDirectUrlIsLocked("amazon", "https://vanta.example/api/decrypt-stream?ticket=abc"),
      false
    );
    assert.equal(
      amazonDirectUrlIsLocked("qobuz", "https://streaming-qobuz-std.akamaized.net/file"),
      false
    );
  });
});
