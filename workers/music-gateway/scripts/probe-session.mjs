import { readFileSync } from "node:fs";
import { communitySessionFromEnv } from "../src/providers/community-session.js";
const env = {};
for (const line of readFileSync(new URL("../.dev.vars", import.meta.url), "utf8").split(/\r?\n/)) {
  const m = line.match(/^(COMMUNITY_[A-Z_]+)=(.+)$/);
  if (m) env[m[1]] = m[2];
}
const session = communitySessionFromEnv(env);
console.log("session:", session ? "OK expires=" + session.expiresAt : "NULL");
console.log("env keys present:", Object.keys(env).join(","));