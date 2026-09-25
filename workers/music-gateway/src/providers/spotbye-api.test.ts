import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { isNodeHealthy, nodeIndex, qualityParam, spotbyeNodesFor } from "./spotbye-api.js";

describe("spotbye multinode helpers", () => {
  it("selects built-in nodes per provider when unconfigured", () => {
    const env = {} as never;
    assert.ok(spotbyeNodesFor(env, "tidal").length === 5);
    assert.match(spotbyeNodesFor(env, "tidal")[0], /a\.tdlxn\.qzz\.io/);
    assert.match(spotbyeNodesFor(env, "qobuz")[0], /a\.qbzxn\.qzz\.io/);
    assert.match(spotbyeNodesFor(env, "amazon")[0], /a\.amzxn\.qzz\.io/);
    assert.match(spotbyeNodesFor(env, "deezer")[0], /a\.dzrxn\.qzz\.io/);
  });

  it("filters configured nodes to the matching provider prefix", () => {
    const env = { SPOTBYE_NODES: "https://a.qbzxn.qzz.io,https://b.tdlxn.qzz.io" } as never;
    const tidal = spotbyeNodesFor(env, "tidal");
    assert.equal(tidal.length, 1);
    assert.match(tidal[0], /b\.tdlxn/);
    assert.equal(spotbyeNodesFor(env, "qobuz").length, 1);
  });

  it("derives the node index from the node name", () => {
    assert.equal(nodeIndex("https://a.tdlxn.qzz.io", "tidal"), 0);
    assert.equal(nodeIndex("https://c.qbzxn.qzz.io", "qobuz"), 2);
    assert.equal(nodeIndex("https://e.amzxn.qzz.io", "amazon"), 4);
    assert.equal(nodeIndex("https://b.dzrxn.qzz.io", "deezer"), 1);
    // Legacy numeric form still maps if an operator overrides SPOTBYE_NODES.
    assert.equal(nodeIndex("https://tdl-1.spotbye.qzz.io", "tidal"), 0);
    assert.equal(nodeIndex("https://qbz-9.spotbye.qzz.io", "qobuz"), 8);
  });

  it("gates nodes against the public status set", () => {
    const up = new Set(["tidal_a", "qobuz_b", "amazon_e"]);
    assert.equal(isNodeHealthy(up, "tidal", 0), true);
    assert.equal(isNodeHealthy(up, "tidal", 1), false);
    assert.equal(isNodeHealthy(up, "qobuz", 1), true);
    assert.equal(isNodeHealthy(up, "amazon", 4), true);
    assert.equal(isNodeHealthy(null, "tidal", 2), true);
    assert.equal(isNodeHealthy(up, "pandora", 0), false);
  });

  it("normalizes quality to node values", () => {
    assert.equal(qualityParam("16"), "16");
    assert.equal(qualityParam("24"), "24");
    assert.equal(qualityParam("atmos"), "atmos");
    assert.equal(qualityParam("dolby_atmos"), "atmos");
    assert.equal(qualityParam("hi_res"), "24");
  });
});
