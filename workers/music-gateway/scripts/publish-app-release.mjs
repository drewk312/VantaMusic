#!/usr/bin/env node
/**
 * Publish a sideload APK so VANTA clients can show Update / Download.
 *
 *   node scripts/publish-app-release.mjs --apk ../../app/build/outputs/apk/debug/vanta.apk --version-code 2 --version-name 1.1
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const args = process.argv.slice(2);
const readArg = (name, fallback = "") => {
  const index = args.indexOf(name);
  if (index < 0 || index === args.length - 1) return fallback;
  return args[index + 1];
};

const apk = resolve(readArg("--apk", "../../app/build/outputs/apk/debug/vanta.apk"));
const versionCode = Number.parseInt(readArg("--version-code", "2"), 10);
const versionName = readArg("--version-name", "1.1");
const donateUrl = readArg("--donate-url", "");
const changelog = readArg("--changelog", "Latest VANTA build.");
const bucket = readArg("--bucket", "vanta-releases");

if (!existsSync(apk)) {
  console.error(`APK not found: ${apk}`);
  process.exit(1);
}

const wrangler = (wranglerArgs) => {
  const result = spawnSync("npx", ["wrangler", ...wranglerArgs], {
    stdio: "inherit",
    shell: true,
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
};

wrangler(["r2", "bucket", "create", bucket]);
wrangler(["r2", "object", "put", `${bucket}/vanta.apk`, "--file", apk, "--remote"]);

const dir = mkdtempSync(join(tmpdir(), "vanta-release-"));
const manifestPath = join(dir, "update.json");
writeFileSync(
  manifestPath,
  JSON.stringify(
    {
      versionCode,
      versionName,
      apkUrl: "https://vanta-music-gateway.16drewk.workers.dev/app/download",
      donateUrl,
      changelog,
      publishedAt: new Date().toISOString(),
    },
    null,
    2
  )
);
wrangler(["r2", "object", "put", `${bucket}/update.json`, "--file", manifestPath, "--content-type", "application/json", "--remote"]);
console.log(`Published VANTA ${versionName} (${versionCode}) to R2 ${bucket}`);
