import { it } from "node:test";
import assert from "node:assert/strict";
import { withProviderFailures, recordProviderVerification } from "./provider-failures";
import { classifyMissingStream } from "./stream-failure";
import type { Env } from "../types";

const env = { MUSICDL_BASE_URL: "https://example.com" } as Env;

it("preserves verified authentication failures ahead of quality guesses", () => {
  withProviderFailures(() => {
    recordProviderVerification(428, "VERIFY_REQUIRED");
    for (const quality of ["16", "24", "atmos"]) {
      const failure = classifyMissingStream(quality, env);
      assert.equal(failure.code, "AUTH_REQUIRED");
      assert.equal(failure.retryable, false);
      assert.match(failure.message, /requires verification/);
    }
  });
});

it("does not leak verification failures into concurrent or subsequent requests", async () => {
  await Promise.all([
    withProviderFailures(async () => {
      recordProviderVerification(428, "VERIFY_REQUIRED");
      await Promise.resolve();
      assert.equal(classifyMissingStream("16", env).code, "AUTH_REQUIRED");
    }),
    withProviderFailures(async () => {
      await Promise.resolve();
      assert.equal(classifyMissingStream("16", env).code, "SOURCE_OFFLINE");
    }),
  ]);
  assert.equal(classifyMissingStream("16", env).code, "SOURCE_OFFLINE");
});
