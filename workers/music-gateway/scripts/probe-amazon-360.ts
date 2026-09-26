/**
 * Probe Amazon Next shards for quality=360 using the same ECDH path as the Worker.
 * Run: npx tsx scripts/probe-amazon-360.ts
 */
import { streamViaNextCommunity } from "../src/providers/community-next.ts";
import type { Env } from "../src/types.ts";

async function main() {
  const env = {
    COMMUNITY_INSTALL_ID: "probe",
    COMMUNITY_APP_VERSION: "unknown",
    NEXT_COMMUNITY_ENABLED: "true",
    GATEWAY_BASE_URL: "https://vanta-music-gateway.16drewk.workers.dev",
  } as Env;

  const asins = ["B076YT2CBT", "B0G2FCHJ2Z", "B0CVGNYVLK"];
  for (const asin of asins) {
    for (const q of ["360", "24"] as const) {
      const result = await streamViaNextCommunity(env, "amazon", asin, q);
      console.log(
        asin,
        q,
        result
          ? {
              quality: result.quality,
              spatial: result.spatialFormat,
              format: result.format,
              url: (result.url || result.streamUrl || "").slice(0, 80),
            }
          : null,
      );
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
