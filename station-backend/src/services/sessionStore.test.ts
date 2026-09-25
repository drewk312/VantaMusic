import { describe, it } from "vitest";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, statSync, utimesSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { SessionStore } from "./sessionStore";

describe("SessionStore", () => {
  it("creates and validates partner tokens", () => {
    const store = new SessionStore();
    const token = store.createPartnerToken({ deviceModel: "Pixel", appVersion: "1.0", ip: "127.0.0.1" });
    assert.ok(token);
    assert.ok(token.startsWith("pt_"));
    assert.equal(store.validatePartnerToken(token), true);
    assert.equal(store.validatePartnerToken("invalid"), false);
  });

  it("rate limits partner-token issuance per client", () => {
    const store = new SessionStore({ partnerLoginsPerMinute: 2 });
    const meta = { deviceModel: "Pixel", appVersion: "1.0", ip: "203.0.113.8" };
    assert.ok(store.createPartnerToken(meta));
    assert.ok(store.createPartnerToken(meta));
    assert.equal(store.createPartnerToken(meta), null);
  });

  it("caps persisted partner tokens and does not rewrite for an unknown token", () => {
    const directory = mkdtempSync(join(tmpdir(), "vanta-partner-store-"));
    const persistencePath = join(directory, "sessions.json");
    try {
      const store = new SessionStore({ persistencePath, maxPartnerTokens: 2 });
      for (let index = 0; index < 3; index += 1) {
        assert.ok(store.createPartnerToken({
          deviceModel: `device-${index}`,
          appVersion: "1.0",
          ip: `203.0.113.${index + 1}`,
        }));
      }
      const snapshot = JSON.parse(readFileSync(persistencePath, "utf8")) as {
        partnerTokens: Array<[string, unknown]>;
      };
      assert.equal(snapshot.partnerTokens.length, 2);

      const oldTime = new Date(1_000_000);
      utimesSync(persistencePath, oldTime, oldTime);
      const before = statSync(persistencePath).mtimeMs;
      assert.equal(store.validatePartnerToken("pt_unknown"), false);
      assert.equal(statSync(persistencePath).mtimeMs, before);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  it("only authenticates the demo user in explicit development mode", async () => {
    const productionStore = new SessionStore({ environment: "production", demoPassword: "test-password" });
    assert.equal(await productionStore.authenticate("demo", "test-password"), null);

    const store = new SessionStore({ environment: "development", demoPassword: "test-password" });
    assert.equal(await store.authenticate("demo", "wrong-password"), null);
    const user = await store.authenticate("demo", "test-password");
    assert.ok(user);
    assert.equal(user?.id, "user_demo");
    assert.equal(user?.hasPremium, true);
    assert.equal(store.validateUserToken(user!.token), "user_demo");
  });

  it("expires tokens and enforces the model request quota", async () => {
    const store = new SessionStore({
      environment: "development",
      demoPassword: "test-password",
      userTokenTtlMs: 0,
      modelRequestsPerMinute: 2,
    });
    const user = await store.authenticate("demo", "test-password");
    assert.ok(user);
    assert.equal(store.validateUserToken(user!.token), null);
    assert.equal(store.consumeModelQuota("user_demo", 0), true);
    assert.equal(store.consumeModelQuota("user_demo", 1), true);
    assert.equal(store.consumeModelQuota("user_demo", 2), false);
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

  it("restores durable sessions from an atomic snapshot", async () => {
    const directory = mkdtempSync(join(tmpdir(), "vanta-session-store-"));
    const persistencePath = join(directory, "sessions.json");
    try {
      const first = new SessionStore({
        environment: "development",
        demoPassword: "test-password",
        persistencePath,
      });
      const user = await first.authenticate("demo", "test-password");
      const station = await first.createStation({
        userId: user!.id,
        seed: { kind: "GENRE", displayName: "ambient" },
        recommendations: [],
        resolvedTracks: [],
        trackCount: 0,
      });

      const snapshot = JSON.parse(readFileSync(persistencePath, "utf8")) as { version: number };
      assert.equal(snapshot.version, 1);

      const restored = new SessionStore({ persistencePath });
      assert.equal(restored.validateUserToken(user!.token), "user_demo");
      assert.equal((await restored.getStation(station.token))?.id, station.id);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });
});

