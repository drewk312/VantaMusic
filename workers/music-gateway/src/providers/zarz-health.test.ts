import { it } from "node:test";
import assert from "node:assert/strict";
import {
  isZarzPlaybackOpen,
  recordZarzPlaybackFailure,
  recordZarzPlaybackSuccess,
  zarzBreakerSnapshot,
} from "./zarz-health.js";

it("stays closed below the consecutive failure threshold", () => {
  const provider = "qobuz";
  recordZarzPlaybackSuccess(provider);
  recordZarzPlaybackFailure(provider);
  recordZarzPlaybackFailure(provider);
  assert.equal(isZarzPlaybackOpen(provider), false);
});

it("opens after repeated failures and resets on success", () => {
  const provider = "tidal";
  recordZarzPlaybackSuccess(provider);
  recordZarzPlaybackFailure(provider);
  recordZarzPlaybackFailure(provider);
  recordZarzPlaybackFailure(provider);
  assert.equal(isZarzPlaybackOpen(provider), true);
  const snapshot = zarzBreakerSnapshot()[provider];
  assert.ok(snapshot && snapshot.failures >= 3 && snapshot.openMsRemaining > 0);
  recordZarzPlaybackSuccess(provider);
  assert.equal(isZarzPlaybackOpen(provider), false);
  recordZarzPlaybackFailure(provider);
  assert.equal(isZarzPlaybackOpen(provider), false);
});

it("ignores non-zarz providers", () => {
  recordZarzPlaybackSuccess("qobuz");
  for (let i = 0; i < 6; i++) recordZarzPlaybackFailure("soundcloud");
  assert.equal(isZarzPlaybackOpen("soundcloud"), false);
  recordZarzPlaybackSuccess("qobuz");
});
