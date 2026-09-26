#!/usr/bin/env node
/**
 * One-time Monochrome Unified Playback session helper.
 *
 * Monochrome's music-api.geeked.wtf requires:
 *   Authorization: Bearer amp_29b2lIr4mze4tK-P8QDOxfMZ9anCgJ9_uGTUks3nIyo
 *   X-Turnstile-JWT: <JWT from POST /api/auth/turnstile>
 *
 * The JWT is minted by solving a Cloudflare Turnstile challenge
 * (sitekey 0x4AAAAAADgxqF6QVMm0GLHH) in the browser, then:
 *   POST {base}/api/auth/turnstile { turnstile_token }
 *   ← { jwt / token }
 *
 * This script helps you capture that JWT and store it as a Worker secret.
 * You can either:
 *   1) Open Monochrome in a browser, complete Turnstile, and paste the JWT from DevTools
 *   2) Let this script open the Turnstile challenge and intercept it
 *
 * For now we implement the simple paste flow — you extract the JWT from
 * localStorage `unified-turnstile-jwt` or the Network tab.
 */

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { createInterface } from "node:readline";

const DEFAULT_BASE = "https://music-api.geeked.wtf";
const DEFAULT_TOKEN = "amp_29b2lIr4mze4tK-P8QDOxfMZ9anCgJ9_uGTUks3nIyo";
const SITE_KEY = "0x4AAAAAADgxqF6QVMm0GLHH";

function ask(q) {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((res) => rl.question(q, (a) => { rl.close(); res(a); }));
}

async function main() {
  console.log("=== VANTA Monochrome Unified — Turnstile JWT helper ===\n");
  console.log(`Unified API: ${DEFAULT_BASE}`);
  console.log(`Bearer token: ${DEFAULT_TOKEN}`);
  console.log(`Turnstile sitekey: ${SITE_KEY}\n`);
  console.log("Monochrome's backend (music-api.geeked.wtf) powers Amazon/Tidal Atmos & FLAC.");
  console.log("It requires a Turnstile JWT (X-Turnstile-JWT) in addition to the Bearer token.\n");
  console.log("How to get the JWT:");
  console.log("  1. Open https://monochrome.tf in a browser (or any official instance)");
  console.log("  2. Open DevTools → Application → Local Storage → https://monochrome.tf");
  console.log("  3. Find key `unified-turnstile-jwt` or `UNIFIED_TURNSTILE_JWT` — copy its value");
  console.log("  4. Or: Network tab → filter `turnstile` → POST to /api/auth/turnstile → Response → copy `jwt`/`token`\n");
  console.log("Alternatively, you can paste a curl-captured JWT directly.\n");

  const base = (await ask(`API base [${DEFAULT_BASE}]: `)).trim() || DEFAULT_BASE;
  const token = (await ask(`Bearer token [${DEFAULT_TOKEN.slice(0, 12)}...]: `)).trim() || DEFAULT_TOKEN;
  const jwt = (await ask("Paste Turnstile JWT: ")).trim();

  if (!jwt) {
    console.error("No JWT provided — aborting.");
    process.exit(1);
  }

  // Verify the JWT works with a test lookup
  console.log("\nVerifying JWT with a test lookup...");
  try {
    const params = new URLSearchParams({
      track: "Test",
      artist: "Test",
      quality: "LOSSLESS",
      intent: "stream",
    });
    const res = await fetch(`${base}/api/v2/track/?${params}`, {
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
        "X-Turnstile-JWT": jwt,
      },
    });
    console.log(`  → ${res.status} ${res.statusText}`);
    if (res.status === 428 || res.status === 401 || res.status === 403) {
      console.warn("  JWT appears invalid/expired — the gateway will still try SpotiFLAC fallback.");
    } else if (res.ok) {
      console.log("  JWT verified (got JSON response).");
    } else {
      console.log("  Unexpected status — but JWT is stored anyway.");
    }
  } catch (e) {
    console.warn(`  Verify failed: ${e.message} — storing JWT anyway.`);
  }

  const varsPath = resolve(process.cwd(), ".dev.vars");
  const examplePath = resolve(process.cwd(), ".dev.vars.example");
  let existing = "";
  if (existsSync(varsPath)) existing = readFileSync(varsPath, "utf8");
  else if (existsSync(examplePath)) existing = readFileSync(examplePath, "utf8");

  const lines = existing ? existing.split("\n") : [];
  const upsert = (key, value) => {
    const line = `${key}=${value}`;
    const idx = lines.findIndex((l) => l.startsWith(`${key}=`));
    if (idx >= 0) lines[idx] = line;
    else lines.push(line);
  };

  upsert("MONOCHROME_API_BASE_URL", base);
  upsert("MONOCHROME_API_TOKEN", token);
  upsert("MONOCHROME_TURNSTILE_JWT", jwt);

  writeFileSync(varsPath, lines.filter(Boolean).join("\n") + "\n", "utf8");
  console.log(`\nWrote to ${varsPath}`);
  console.log("\nNext steps:");
  console.log("  npx wrangler secret put MONOCHROME_API_BASE_URL");
  console.log("  npx wrangler secret put MONOCHROME_API_TOKEN");
  console.log("  npx wrangler secret put MONOCHROME_TURNSTILE_JWT");
  console.log("  npx wrangler deploy");
  console.log("\nThe gateway will now try:");
  console.log("  1) SpotiFLAC community (tdl/qbz/amz-oss) — needs COMMUNITY_* session");
  console.log("  2) Monochrome unified (music-api.geeked.wtf) — needs this JWT ← you just set");
  console.log("  3) Deezer public (dzr.tabs-vs-spaces.wtf) — no auth needed");
  console.log("  4) GDStudio / community gateways — no auth needed");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
