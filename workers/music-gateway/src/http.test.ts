import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  extractTrackId,
  normalizeQuality,
  providerFromQuery,
  splitCatalogTrackId,
  badRequest,
  notFound,
  unauthorized,
  normalizeSupportedTrackUrl,
} from "./http.js";

describe("http helpers", () => {
  it("extracts track ids from stream/resolve/download paths", () => {
    assert.equal(extractTrackId("/stream/abc123"), "abc123");
    assert.equal(extractTrackId("/api/stream/abc%20123"), "abc 123");
    assert.equal(extractTrackId("/resolve/abc123"), "abc123");
    assert.equal(extractTrackId("/download/abc123"), "abc123");
    assert.equal(extractTrackId("/search"), null);
  });

  it("splits prefixed catalog ids", () => {
    assert.deepEqual(splitCatalogTrackId("qobuz:61515440"), { provider: "qobuz", id: "61515440" });
    assert.deepEqual(splitCatalogTrackId("61515440"), { id: "61515440" });
    assert.deepEqual(splitCatalogTrackId("not-a-catalog:value"), { id: "not-a-catalog:value" });
  });

  it("ignores malformed percent-encoded track ids", () => {
    assert.equal(extractTrackId("/api/stream/%E0%A4%A"), null);
  });

  it("normalizes quality to 16 or 24", () => {
    assert.equal(normalizeQuality("24", "16"), "24");
    assert.equal(normalizeQuality("16", "24"), "16");
    assert.equal(normalizeQuality("atmos", "24"), "atmos");
    assert.equal(normalizeQuality("EAC3_JOC", "24"), "atmos");
    assert.equal(normalizeQuality("ac4", "24"), "atmos");
    assert.equal(normalizeQuality("HI_RES", "24"), "hi_res");
    assert.equal(normalizeQuality("360", "24"), "360");
    assert.equal(normalizeQuality("360RA", "24"), "360");
    assert.equal(normalizeQuality("sony_360", "16"), "360");
    assert.equal(normalizeQuality("auto", "24"), "auto");
    assert.equal(normalizeQuality("foo", "24"), "24");
    assert.equal(normalizeQuality(null, "16"), "16");
  });

  it("reads provider/service from query and body", () => {
    const url = new URL("https://example.com/stream?id=1&provider=qobuz");
    assert.equal(providerFromQuery(url), "qobuz");
    assert.equal(providerFromQuery(url, "tidal"), "tidal");
  });

  it("returns structured error responses", async () => {
    const r1 = badRequest("missing id");
    assert.equal(r1.status, 400);
    assert.deepEqual(await r1.json(), { error: "missing id" });

    const r2 = notFound("track_not_found");
    assert.equal(r2.status, 404);
    assert.deepEqual(await r2.json(), { error: "track_not_found" });

    const r3 = unauthorized();
    assert.equal(r3.status, 401);
  });

  it("accepts known HTTPS music links and rejects credentialed or arbitrary URLs", () => {
    assert.match(normalizeSupportedTrackUrl("https://open.spotify.com/track/abc") ?? "", /^https:\/\/open\.spotify\.com/);
    assert.equal(normalizeSupportedTrackUrl("https://example.com/private"), null);
    assert.equal(normalizeSupportedTrackUrl("https://user:password@open.spotify.com/track/abc"), null);
    assert.equal(normalizeSupportedTrackUrl("http://open.spotify.com/track/abc"), null);
  });
});
