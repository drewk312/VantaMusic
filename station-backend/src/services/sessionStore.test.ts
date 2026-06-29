import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { SessionStore } from "./sessionStore";

describe("SessionStore", () => {
  it("creates and validates partner tokens", () => {
    const store = new SessionStore();
    const token = store.createPartnerToken({ deviceModel: "Pixel", appVersion: "1.0", ip: "127.0.0.1" });
    assert.ok(token.startsWith("pt_"));
    assert.equal(store.validatePartnerToken(token), true);
    assert.equal(store.validatePartnerToken("invalid"), false);
  });

  it("authenticates demo user", async () => {
    const store = new SessionStore();
    const user = await store.authenticate("demo", "any");
    assert.ok(user);
    assert.equal(user?.id, "user_demo");
    assert.equal(user?.hasPremium, true);
    assert.equal(store.validateUserToken(user!.token), "user_demo");
  });

  it("creates and retrieves stations", async () => {
    const store = new SessionStore();
    const station = await store.createStation({
      userId: "user_demo",
      seed: { kind: "GENRE", displayName: "jazz" },
      recommendations: [],
      resolvedTracks: [],
      trackCount: 0,
    });
    assert.ok(station.token.startsWith("stok_"));
    assert.equal((await store.getStation(station.token))?.id, station.id);
    const listed = await store.listStations("user_demo");
    assert.equal(listed.length, 1);
    await store.deleteStation(station.id);
    assert.equal(await store.getStation(station.token), undefined);
  });

  it("stores and filters feedback", async () => {
    const store = new SessionStore();
    const station = await store.createStation({
      userId: "user_demo",
      seed: { kind: "GENRE", displayName: "rock" },
      recommendations: [{ id: "t1", title: "Song", artist: "Artist" }],
      resolvedTracks: [{ id: "t1", title: "Song", artist: "Artist", album: "Album", artworkUrl: "", streamUrl: "", durationSec: 0, quality: "flac", source: "qobuz" }],
      trackCount: 1,
    });
    await store.addFeedback({ userId: "user_demo", stationToken: station.token, trackId: "t1", isPositive: true });
    await store.addFeedback({ userId: "user_demo", stationToken: station.token, trackId: "t1", isPositive: false });
    const positive = await store.getFeedback({ userId: "user_demo", stationToken: station.token, includePositive: true, includeNegative: false });
    assert.equal(positive.length, 1);
    assert.equal(positive[0]!.isPositive, true);
  });
});

