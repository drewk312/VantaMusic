#!/usr/bin/env node
// Official SpotiFLAC session setup/renewal for the VANTA gateway.
//
// Current Qobuz and Tidal extensions use separately scoped sessions at
// api.zarz.moe/v2. A session is obtained through human verification. This script:
//
//   1. loads/creates an install_id
//   2. hits /bootstrap to get a challenge_url
//   3. opens it in your browser (you complete the CAPTCHA once)
//   4. receives the grant on a localhost callback
//   5. exchanges the grant for session_id + session_secret + expires_at
//   6. writes provider-scoped vars into .dev.vars and a gitignored session file
//
// Usage: node scripts/community-session.mjs qobuz
//        node scripts/community-session.mjs tidal
//        node scripts/community-session.mjs desktop

import { randomBytes } from "node:crypto";
import { spawn } from "node:child_process";
import * as http from "node:http";
import { readFileSync, existsSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, "..");

const TARGETS = {
  desktop: { appVersion: "unknown", envPrefix: "COMMUNITY" },
  qobuz: { appVersion: "qobuz-web@1.2.10", envPrefix: "ZARZ_QOBUZ" },
  tidal: { appVersion: "tidal-web@1.2.2", envPrefix: "ZARZ_TIDAL" },
};

const args = process.argv.slice(2);
const flags = new Set(args.filter((a) => a.startsWith("--")));
const positionals = args.filter((a) => !a.startsWith("--"));
const checkOnly = flags.has("--check") || positionals[0] === "check";
const warnHoursArg = args.find((a) => a.startsWith("--if-expiring="))?.split("=")[1]
  ?? (args.includes("--if-expiring") ? args[args.indexOf("--if-expiring") + 1] : null);
const warnHours = Number(warnHoursArg ?? 12);

let targetName;
if (checkOnly) {
  targetName = String((positionals[0] === "check" ? positionals[1] : positionals[0]) || "desktop").trim().toLowerCase();
} else {
  targetName = String(positionals[0] || "desktop").trim().toLowerCase();
}
const target = TARGETS[targetName];
if (!target) {
  throw new Error(
    "usage: node scripts/community-session.mjs <desktop|qobuz|tidal> [--check] [--if-expiring=12]\n" +
      "       node scripts/community-session.mjs check [desktop]"
  );
}

const isDesktop = targetName === "desktop";
const VERIFY_BASE = isDesktop
  ? process.env.COMMUNITY_VERIFY_URL ?? "https://verify.spotbye.qzz.io"
  : process.env.ZARZ_VERIFY_URL ?? "https://api.zarz.moe/v2";
const APP_VERSION = (isDesktop ? process.env.COMMUNITY_APP_VERSION : process.env.ZARZ_APP_VERSION) ?? target.appVersion;
const PLATFORM = isDesktop ? "desktop" : "extension";
const INSTALL_ID_KEY = isDesktop ? "COMMUNITY_INSTALL_ID" : "ZARZ_INSTALL_ID";
const VERIFY_TIMEOUT_MS = 5 * 60 * 1000;

const devVarsPath = process.env.COMMUNITY_DEV_VARS
  ? resolve(process.env.COMMUNITY_DEV_VARS)
  : resolve(ROOT, ".dev.vars");
const sessionFile = resolve(ROOT, isDesktop ? "community_session.json" : `zarz_${targetName}_session.json`);

function parseEnvFile(content) {
  const values = {};
  for (const line of content.split(/\r?\n/)) {
    const match = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line);
    if (match) values[match[1]] = match[2];
  }
  return values;
}

function randomHex(size) {
  return randomBytes(size).toString("hex");
}

function openBrowser(url) {
  const targetUrl = new URL(url);
  if (targetUrl.protocol !== "https:") throw new Error("verification URL must use HTTPS");
  const platform = process.platform;
  const cmd = platform === "win32" ? "rundll32.exe" : platform === "darwin" ? "open" : "xdg-open";
  const args = platform === "win32" ? ["url.dll,FileProtocolHandler", targetUrl.href] : [targetUrl.href];
  try {
    const child = spawn(cmd, args, { shell: false, stdio: "ignore", detached: true, windowsHide: true });
    child.on("error", () => { /* The printed link remains available. */ });
    child.unref();
  } catch {
    // ignore — user can open the URL manually
  }
}

async function bootstrap(installId) {
  const qs = new URLSearchParams({ install_id: installId, app_version: APP_VERSION, platform: PLATFORM });
  const res = await fetch(`${VERIFY_BASE}/bootstrap?${qs.toString()}`);
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`bootstrap failed (HTTP ${res.status}): ${body}`);
  }
  const data = await res.json();
  if (!data.challenge_url && !data.challenge_id && !(data.session_id && data.session_secret)) {
    throw new Error("bootstrap response missing a session or verification challenge");
  }
  return data;
}

async function exchangeGrant(installId, grant) {
  const payload = JSON.stringify({ grant, install_id: installId, app_version: APP_VERSION, platform: PLATFORM });
  const res = await fetch(`${VERIFY_BASE}/session/exchange`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: payload,
  });
  const body = await res.text();
  if (!res.ok) {
    throw new Error(`session exchange returned HTTP ${res.status}: ${body}`);
  }
  const parsed = JSON.parse(body);
  if (!parsed.session_id || !parsed.session_secret || !parsed.expires_at) {
    throw new Error(`session exchange response incomplete: ${body}`);
  }
  return parsed;
}

function startCallbackServer() {
  return new Promise((resolvePromise, reject) => {
    const state = randomHex(16);
    let resolveGrant;
    const grantPromise = new Promise((res) => {
      resolveGrant = res;
    });
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, `http://${req.headers.host}`);
      if (url.pathname === "/session-grant") {
        if (url.searchParams.get("state") !== state) {
          res.writeHead(400, { "Content-Type": "text/html" });
          res.end("<h1>Invalid verification callback state</h1>");
          return;
        }
        const grant = url.searchParams.get("grant")?.trim() ?? "";
        res.writeHead(200, {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store",
        });
        res.end(
          "<!doctype html><html><body style='font-family:sans-serif;background:#000;color:#fff;display:grid;place-items:center;height:100vh'><div style='text-align:center'><h1>&#10003; Verified</h1><p>Returning to VANTA setup...</p></div></body></html>"
        );
        server.close();
        resolveGrant(grant || null);
      } else {
        res.writeHead(404);
        res.end("Not found");
      }
    });
    let grantPromiseReject;
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      resolvePromise({ server, state, port, grantPromise });
    });
    server.on("error", reject);
  });
}

function upsertEnv(content, key, value) {
  const linePattern = new RegExp(`^${key}=.*$`, "m");
  if (linePattern.test(content)) return content.replace(linePattern, `${key}=${value}`);
  return `${content.replace(/\s*$/u, "")}\n${key}=${value}\n`;
}

async function main() {
  const existingDevVars = existsSync(devVarsPath) ? readFileSync(devVarsPath, "utf8") : "";
  const existingValues = parseEnvFile(existingDevVars);
  const expiresKey = `${target.envPrefix}_SESSION_EXPIRES`;
  const sessionIdKey = `${target.envPrefix}_SESSION_ID`;
  const sessionSecretKey = `${target.envPrefix}_SESSION_SECRET`;
  const expiresAt = process.env[expiresKey]?.trim() || existingValues[expiresKey]?.trim() || "";
  const hasIds = Boolean(
    (process.env[sessionIdKey]?.trim() || existingValues[sessionIdKey]?.trim()) &&
      (process.env[sessionSecretKey]?.trim() || existingValues[sessionSecretKey]?.trim())
  );
  const remainingMs = expiresAt ? Date.parse(expiresAt) - Date.now() : NaN;
  const warnMs = (Number.isFinite(warnHours) ? warnHours : 12) * 60 * 60 * 1000;
  const needsRenew =
    !hasIds ||
    !Number.isFinite(remainingMs) ||
    remainingMs < Math.max(5 * 60 * 1000, warnMs);

  if (checkOnly || flags.has("--if-expiring") || args.some((a) => a.startsWith("--if-expiring="))) {
    const hoursLeft = Number.isFinite(remainingMs) ? (remainingMs / 3600000).toFixed(2) : "n/a";
    console.log(`[community-session] ${targetName} expires_at=${expiresAt || "missing"} hours_left=${hoursLeft} needs_renew=${needsRenew}`);
    if (isDesktop) {
      console.log("[community-session] note: verify.spotbye has no silent /session/refresh — CAPTCHA is required to renew.");
    }
    if (checkOnly) process.exit(needsRenew ? 2 : 0);
    if (!needsRenew) {
      console.log("[community-session] still fresh; skipping CAPTCHA renew.");
      return;
    }
  }

  let installId = process.env[INSTALL_ID_KEY]?.trim() || existingValues[INSTALL_ID_KEY]?.trim();
  if (!installId && existsSync(sessionFile)) {
    try {
      const existing = JSON.parse(readFileSync(sessionFile, "utf8"));
      if (existing.installId) installId = existing.installId;
    } catch {
      /* ignore */
    }
  }
  if (!installId) installId = randomHex(16);

  console.log(`[community-session] bootstrapping ${targetName} session`);
  const bootstrapResult = await bootstrap(installId);
  let session = bootstrapResult.session_id && bootstrapResult.session_secret
    ? bootstrapResult
    : null;

  if (!session) {
    const { port, state, grantPromise } = await startCallbackServer();
    const callbackURL = `http://127.0.0.1:${port}/session-grant?state=${state}`;
    const finalURL = bootstrapResult.challenge_url
      ? new URL(bootstrapResult.challenge_url)
      : new URL(`${VERIFY_BASE.replace(/\/$/, "")}/challenge`);
    if (bootstrapResult.challenge_id) finalURL.searchParams.set("id", bootstrapResult.challenge_id);
    finalURL.searchParams.set("cb", callbackURL);

    console.log(`\nComplete provider verification (5 minute window; the session can expire):`);
    console.log(`  ${finalURL.toString()}\n`);
    openBrowser(finalURL.toString());

    const grant = await Promise.race([
      grantPromise,
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error("verification timed out after 5 minutes")), VERIFY_TIMEOUT_MS)
      ),
    ]);
    if (!grant) throw new Error("verification completed without a grant");

    console.log(`[community-session] grant received, exchanging for session...`);
    session = await exchangeGrant(installId, grant);
  }

  let content = existingDevVars;
  content = upsertEnv(content, INSTALL_ID_KEY, installId);
  if (isDesktop) {
    content = upsertEnv(content, "COMMUNITY_APP_VERSION", APP_VERSION);
    content = upsertEnv(content, "COMMUNITY_PLATFORM", PLATFORM);
  }
  content = upsertEnv(content, `${target.envPrefix}_SESSION_ID`, session.session_id);
  content = upsertEnv(content, `${target.envPrefix}_SESSION_SECRET`, session.session_secret);
  content = upsertEnv(content, `${target.envPrefix}_SESSION_EXPIRES`, session.expires_at);
  mkdirSync(dirname(devVarsPath), { recursive: true });
  writeFileSync(devVarsPath, content, { mode: 0o600 });

  writeFileSync(
    sessionFile,
    JSON.stringify(
      {
        installId,
        sessionId: session.session_id,
        sessionSecret: session.session_secret,
        expiresAt: session.expires_at,
        appVersion: APP_VERSION,
        platform: PLATFORM,
      },
      null,
      2
    ),
    { mode: 0o600 }
  );

  console.log(`\n[community-session] session saved to ${devVarsPath}`);
  console.log(`  provider: ${targetName}`);
  console.log(`  expires_at: ${session.expires_at}`);
  console.log(`\nRun this script again before expiry to refresh this provider session.`);
}

main().catch((err) => {
  console.error(`\n[community-session] FAILED: ${err.message}`);
  process.exit(1);
});
