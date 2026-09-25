import { it } from "node:test";
import assert from "node:assert/strict";
import { shouldAdoptSession } from "./session-policy";
import type { CommunitySession } from "../providers/community-session";

const stored: CommunitySession = {
  installId: "old", sessionId: "rotated", sessionSecret: "test", platform: "extension",
  appVersion: "test", expiresAt: "2026-09-08T06:00:00Z",
};
it("adopts a newly verified installation despite an older expiry", () => {
  assert.equal(shouldAdoptSession({ ...stored, installId: "new", expiresAt: "2026-09-08T03:00:00Z" }, stored), true);
});
it("does not undo automatic rotation with the original environment seed", () => {
  assert.equal(shouldAdoptSession({ ...stored, sessionId: "original", expiresAt: "2026-09-08T03:00:00Z" }, stored), false);
});
