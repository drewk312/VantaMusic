import { readFileSync } from "node:fs";
import { streamViaSignedCommunity } from "../src/providers/community-signed.ts";
const env = {};
for (const line of readFileSync(new URL("../.dev.vars", import.meta.url), "utf8").split(/\r?\n/)) {
  const m = line.match(/^(COMMUNITY_[A-Z_]+)=(.+)$/);
  if (m) env[m[1]] = m[2];
}
const r = await streamViaSignedCommunity(env, "tidal", "283628183", "16");
console.log(r ? "STREAM OK " + (r.url ?? r.streamUrl ?? "").slice(0, 80) : "null (no result)");

