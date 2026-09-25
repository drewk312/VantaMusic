import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { activeEditorial } from "./editorial.js";
describe("reviewed editorial", () => {
  it("shows only active sourced celebrations and expires old cards", () => {
    assert.equal(activeEditorial(Date.parse("2026-08-24")).length, 0);
    const entries = activeEditorial(Date.parse("2026-09-07"));
    assert.equal(entries.length, 1);
    assert.equal(entries[0].sponsored, false);
    assert.ok(entries[0].sourceUrl.startsWith("https://"));
    assert.equal(activeEditorial(Date.parse("2026-09-25")).length, 0);
  });
});
