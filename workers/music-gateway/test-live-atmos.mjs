import * as fs from "node:fs";

const devVars = fs.readFileSync(".dev.vars", "utf8");
const env = Object.fromEntries(devVars.split("\n").filter(l=>l.includes("=")).map(l=>{ const [k,...v]=l.split("="); return [k,v.join("=")] }));

const installId = env.COMMUNITY_INSTALL_ID;
const sessionId = env.COMMUNITY_SESSION_ID;
const sessionSecret = env.COMMUNITY_SESSION_SECRET;
const appVersion = "unknown";
const platform = "desktop";

console.log("installId", installId.slice(0,8), "...");
console.log("sessionId", sessionId.slice(0,20), "...");
console.log("secret", sessionSecret.slice(0,8), "...");

async function sign(method, pathname, search, body) {
  const bodyHash = await crypto.subtle.digest("SHA-256", body).then(b=>Array.from(new Uint8Array(b)).map(x=>x.toString(16).padStart(2,"0")).join(""));
  const ts = new Date().toISOString().replace(/(\.\d+)?Z$/, ".000Z");
  const nonce = [...crypto.getRandomValues(new Uint8Array(12))].map(b=>b.toString(16).padStart(2,"0")).join("");
  const window = Math.floor(Date.parse(ts)/1000/300);
  // HMAC rolling key: HMAC_SHA256(secret, window:sessionId)
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(sessionSecret), {name:"HMAC", hash:"SHA-256"}, false, ["sign"]);
  const rollingInput = `${window}:${sessionId}`;
  const rollingKeyRaw = await crypto.subtle.sign("HMAC", key, enc.encode(rollingInput));
  const rollingKey = new Uint8Array(rollingKeyRaw);
  const rollingKeyImported = await crypto.subtle.importKey("raw", rollingKey, {name:"HMAC", hash:"SHA-256"}, false, ["sign"]);
  const signingInput = ["SPOTIFLAC-HMAC-V1", method, pathname, search, bodyHash, ts, nonce, sessionId, appVersion, platform].join("\n");
  const sigRaw = await crypto.subtle.sign("HMAC", rollingKeyImported, enc.encode(signingInput));
  const sig = Buffer.from(sigRaw).toString("base64url");
  return {
    "X-Sig-Session": sessionId,
    "X-Sig-Timestamp": ts,
    "X-Sig-Nonce": nonce,
    "X-Sig-Body-SHA256": bodyHash,
    "X-Sig-Signature": sig,
    "X-Sig-App-Version": appVersion,
    "X-Sig-Platform": platform,
    "Content-Type": "application/json",
    "Accept": "application/json",
  };
}

async function test(kind, base, trackId, quality) {
  const endpoint = `${base}/api/dl`;
  const bodyText = JSON.stringify({ id: trackId, quality });
  const body = new TextEncoder().encode(bodyText);
  const url = new URL(endpoint);
  const headers = await sign("POST", url.pathname, url.search, body);
  console.log(`\n=== ${kind} ${trackId} q=${quality} @ ${base} ===`);
  console.log("headers", Object.keys(headers).join(","));
  const res = await fetch(endpoint, { method:"POST", headers, body: bodyText });
  console.log("STATUS", res.status, res.statusText);
  const text = await res.text();
  console.log(text.slice(0, 800));
}

const LIVE = {
  qobuz: "https://qbz-oss.spotbye.qzz.io",
  tidal: "https://tdl-oss.spotbye.qzz.io",
  amazon: "https://amz-oss.spotbye.qzz.io",
};

// Test hi-res for qobuz id from search
await test("qobuz", LIVE.qobuz, "266725029", "24");
await test("qobuz", LIVE.qobuz, "266725029", "16");
await test("tidal", LIVE.tidal, "222282692", "24"); // try a tidal id
await test("amazon", LIVE.amazon, "B0B7N1P4VZ", "atmos"); // amazon asin example
