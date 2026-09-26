import { nextApiDl } from "../src/providers/community-next.ts";

const targets = [
  { id: "B076YT2CBT", q: "16" },
  { id: "B076YT2CBT", q: "atmos" },
  { id: "B076YT2CBT", q: "opus" },
  { id: "B0CVGNYVLK", q: "16" },
  { id: "B0CVGNYVLK", q: "atmos" },
  { id: "B0CZW15FZZ", q: "16" },
  { id: "B0CZW15FZZ", q: "atmos" },
];

async function main() {
  for (const { id, q } of targets) {
    for (const shard of ["a", "b"]) {
      try {
        const kp = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);
        const a = await nextApiDl(
          `https://${shard}.amzxn.qzz.io`,
          "probe",
          "unknown",
          id,
          q,
          kp,
          undefined,
          "amazon"
        );
        const short = a.bodyText.slice(0, 220).replace(/\n/g, " ");
        console.log(`shard=${shard} id=${id} q=${q} status=${a.status} ok=${a.ok} body=${short}`);
        if (a.ok) break;
      } catch (e) {
        console.log(`shard=${shard} id=${id} q=${q} ERR ${String(e).slice(0, 100)}`);
      }
      await new Promise((r) => setTimeout(r, 1200));
    }
  }
}

main();