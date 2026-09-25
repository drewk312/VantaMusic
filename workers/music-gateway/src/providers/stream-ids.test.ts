import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { pickIdForProvider } from "./stream.js";

describe("pickIdForProvider", () => {
  it("uses the mapped id when present", () => {
    assert.equal(pickIdForProvider({ qobuz: "99" }, "qobuz", "61515440"), "99");
  });

  it("uses a matching source hint", () => {
    assert.equal(pickIdForProvider({}, "qobuz", "61515440", "qobuz"), "61515440");
  });

  it("treats bare numeric ids as Qobuz when no hint is given", () => {
    assert.equal(pickIdForProvider({}, "qobuz", "61515440"), "61515440");
    assert.equal(pickIdForProvider({}, "tidal", "61515440"), "");
  });
});
