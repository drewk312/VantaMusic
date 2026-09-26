import { subtle } from "node:crypto";
import { nextApiDl, nextRequestToken } from "../src/providers/community-next.ts";

async function main() {
  const keyPair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);
  const attempt = await nextApiDl(
    "https://a.amzxn.qzz.io",
    "probe",
    "unknown",
    "B076YT2CBT",
    "360",
    keyPair,
    undefined,
    "amazon",
  );
  console.log("ok", attempt.ok, "status", attempt.status);
  if (!attempt.ok) { console.log(attempt.bodyText.slice(0, 300)); return; }
  const payload = JSON.parse(attempt.bodyText);
  console.log("keys", Object.keys(payload));
  console.log(JSON.stringify(payload, null, 2).slice(0, 800));
}
main();
