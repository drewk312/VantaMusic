import { it } from "node:test";
import assert from "node:assert/strict";
import { sealSession, unsealSession, readStoredSession } from "./session-storage";
const secret = "test-storage-key-32-characters-minimum";
const session = { installId: "install", sessionId: "credential-id", sessionSecret: "private-credential", platform: "extension", appVersion: "test", expiresAt: "2026-12-01T00:00:00Z" };
it("encrypts credentials with unique nonces and authenticates ciphertext", async () => {
  const first = await sealSession(session, secret);
  const second = await sealSession(session, secret);
  assert.notEqual(first.iv, second.iv);
  assert.equal(JSON.stringify(first).includes(session.sessionSecret), false);
  assert.deepEqual(await unsealSession(first, secret), session);
  await assert.rejects(unsealSession(first, secret + "wrong"));
  await assert.rejects(unsealSession({ ...first, ciphertext: (first.ciphertext[0] === "A" ? "B" : "A") + first.ciphertext.slice(1) }, secret));
  await assert.rejects(sealSession(session, "short"));
});
it("migrates a legacy credential only after its encrypted replacement is stored", async () => {
  const values = new Map<string, unknown>([["session", session]]);
  const storage = {
    async get<T>(key: string) { return values.get(key) as T | undefined; },
    async put<T>(key: string, value: T) { values.set(key, value); },
    async delete(key: string) { return values.delete(key); }
  };
  assert.deepEqual(await readStoredSession(storage, secret), session);
  assert.equal(values.has("session"), false);
  assert.ok(values.has("session:sealed:v1"));
  assert.deepEqual(await readStoredSession(storage, secret), session);
});
it("retains the old record when encrypted migration cannot be saved", async () => {
  let deleted = false;
  const storage = {
    async get<T>(key: string) { return (key === "session" ? session : undefined) as T | undefined; },
    async put<T>(_key: string, _value: T) { throw new Error("storage unavailable"); },
    async delete(_key: string) { deleted = true; }
  };
  await assert.rejects(readStoredSession(storage, secret));
  assert.equal(deleted, false);
});
