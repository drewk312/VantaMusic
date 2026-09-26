import { communitySessionFromEnv } from "../src/providers/community-session.js";
import { readFileSync } from "node:fs";
const env = {};
for (const line of readFileSync(new URL("../.dev.vars", import.meta.url), "utf8").split(/\r?\n/)) {
  const m = line.match(/^(COMMUNITY_[A-Z_]+)=(.+)$/);
  if (m) env[m[1]] = m[2];
}
const expiresAt = env.COMMUNITY_SESSION_EXPIRES?.trim();
console.log("expiresAt", expiresAt, "parsed", Date.parse(expiresAt));
const r = communitySessionFromEnv(env);
console.log("session result:", r ? "OK" : "NULL");