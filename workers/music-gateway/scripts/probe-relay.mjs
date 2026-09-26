import { readFileSync } from "node:fs";
import { communitySessionFromEnv, signCommunityRequest } from "../src/providers/community-session.ts";

const env = {};
for (const line of readFileSync(new URL("../.dev.vars", import.meta.url), "utf8").split(/\r?\n/)) {
  const m = line.match(/^(COMMUNITY_[A-Z_]+)=(.+)$/);
  if (m) env[m[1]] = m[2];
}
const session = communitySessionFromEnv(env);
if (!session) { console.log("NO SESSION IN ENV"); process.exit(1); }
console.log("session expires:", session.expiresAt);

const targets = process.argv.slice(2).length > 0 ? process.argv.slice(2) : ["https://tdl-oss.spotbye.qzz.io", "https://amz-oss.spotbye.qzz.io"];
  for (const base of targets) {
  const url = `${base}/api/dl`;
  const target = new URL(url);
  const body = new TextEncoder().encode(JSON.stringify({ id: "283628183", quality: "16" }));
  try {
    const headers = await signCommunityRequest(session, "POST", target.pathname, target.search, body);
    headers["Content-Type"] = "application/json";
    headers["Accept"] = "application/json";
    const res = await fetch(target, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", ...headers },
      body,
      signal: AbortSignal.timeout(15000),
    });
    const text = await res.text();
    console.log(res.status, base, text.replace(/\s+/g, " ").slice(0, 260));
  } catch (err) {
    console.log("PROBE_FAIL", base, err instanceof Error ? err.message : String(err));
  }
}
