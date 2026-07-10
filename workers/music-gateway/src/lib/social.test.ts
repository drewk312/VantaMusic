import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  addFriend,
  aggregateFriendActivity,
  getFriendIds,
  putActivity,
} from "./social.js";
import type { Env } from "../types.js";
import type { ActivityEventDto } from "../sync/types.js";

function event(userId: string, startedAtMs: number): ActivityEventDto {
  return {
    userId,
    trackId: `track-${userId}`,
    title: "Title",
    artist: "Artist",
    startedAtMs,
    positionMs: 0,
    durationMs: 180_000,
  };
}

describe("aggregateFriendActivity", () => {
  it("sorts by startedAtMs descending and caps at limit", () => {
    const aggregated = aggregateFriendActivity(
      [event("a", 100), event("b", 300), event("c", 200), null],
      2
    );
    assert.equal(aggregated.length, 2);
    assert.deepEqual(
      aggregated.map((item) => item.userId),
      ["b", "c"]
    );
  });

  it("clamps limit between 1 and 100", () => {
    const many = Array.from({ length: 120 }, (_, index) => event(`u${index}`, index));
    assert.equal(aggregateFriendActivity(many, 0).length, 1);
    assert.equal(aggregateFriendActivity(many, 500).length, 100);
  });
});

describe("social KV resilience", () => {
  it("returns empty friends when KV reads fail", async () => {
    const env = {
      SOCIAL_KV: {
        get: async () => {
          throw new Error("kv down");
        },
      },
    } as unknown as Env;

    assert.deepEqual(await getFriendIds(env, "user"), []);
  });

  it("does not throw when KV writes fail", async () => {
    const env = {
      SOCIAL_KV: {
        get: async () => null,
        put: async () => {
          throw new Error("kv down");
        },
      },
    } as unknown as Env;

    assert.deepEqual(await addFriend(env, "owner", "friend"), { friendIds: ["friend"] });
    await assert.doesNotReject(() => putActivity(env, "owner", event("owner", 100)));
  });
});
