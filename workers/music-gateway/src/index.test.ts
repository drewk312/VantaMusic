import { describe, it } from "node:test";
import assert from "node:assert/strict";
import gateway from "./index.js";
import type { Env } from "./types";

describe("gateway request handling", () => {
  it("does not turn incomplete stream config into an internal server error", async () => {
    const response = await gateway.fetch(
      new Request("https://vanta.example/stream/track-123"),
      { NODE_ENV: "development" } as Env
    );

    assert.equal(response.status, 503);
    const body = (await response.json()) as { error?: string; retryable?: boolean };
    assert.equal(body.error, "no_stream_source");
    assert.equal(body.retryable, true);
  });
});
