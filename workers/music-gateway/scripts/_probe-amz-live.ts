import { nextApiDl } from "../src/providers/community-next.ts";

const ids = ["B0CZW15FZZ", "B076YT2CBT", "B0CVGNYVLK"];
const shards = ["a", "b", "c", "d", "e"];

async function main() {
  for (const shard of shards) {
    for (const id of ids) {
      try {
        const kp = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);
        const a = await nextApiDl(
          `https://${shard}.amzxn.qzz.io`,
          "probe",
          "unknown",
          id,
          "24",
          kp,
          undefined,
          "amazon"
        );
        console.log(`shard=${shard} id=${id} status=${a.status} ok=${a.ok} body=${a.bodyText.slice(0, 260).replace(/\n/g, " ")}`);
      } catch (e) {
        console.log(`shard=${shard} id=${id} ERR ${String(e).slice(0, 120)}`);
      }
      await new Promise((r) => setTimeout(r, 1400));
    }
  }
}

main();