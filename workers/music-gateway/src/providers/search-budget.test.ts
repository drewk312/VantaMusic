import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { formatSeedLimit } from "./search.js";

describe("format-only search budget", () => {
  it("keeps the default provider plan below forty estimated subrequests", () => {
    assert.equal(formatSeedLimit(["deezer", "qobuz", "apple"]), 6);
  });

  it("accounts for Qobuz credential discovery and fallback requests", () => {
    assert.equal(formatSeedLimit(["qobuz"]), 10);
  });

  it("does not plan seed work without configured providers", () => {
    assert.equal(formatSeedLimit([]), 0);
  });
});
