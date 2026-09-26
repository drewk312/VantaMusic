// Run from workers/music-gateway. Verification grants are exchanged locally;
// provider credentials are uploaded directly to this Worker's secret store.
import { createServer } from "node:http";
import { randomBytes, timingSafeEqual } from "node:crypto";
import { mkdtemp, writeFile, unlink, rmdir } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { spawn } from "node:child_process";

const base = "https://api.zarz.moe/v2";
const versions = { amazon: "amzn@2.3.3", deezer: "deezer@1.3.5", qobuz: "qobuz-web@1.2.10", tidal: "tidal-web@1.2.2" };
const selected = process.argv.slice(2);
const providers = selected.length ? selected : ["amazon", "deezer"];
if (providers.some((name) => !Object.hasOwn(versions, name))) throw new Error("Unknown extension provider");
const installId = randomBytes(16).toString("hex");
const setupToken = randomBytes(32).toString("hex");
const pending = new Map();
const completed = new Set();
let exchanging = false;
let localOrigin;

async function readJson(response) {
  if (!response.ok) throw new Error(`Provider returned HTTP ${response.status}`);
  const text = await response.text();
  if (text.length > 65536) throw new Error("Provider response too large");
  return JSON.parse(text);
}

async function saveSession(provider, session) {
  if (!session.session_id || !session.session_secret || !session.expires_at ||
      !Number.isFinite(Date.parse(session.expires_at)) || Date.parse(session.expires_at) <= Date.now()) throw new Error("Incomplete or expired provider session");
  const prefix = `ZARZ_${provider.toUpperCase()}`;
  const dir = await mkdtemp(join(tmpdir(), "vanta-extension-session-"));
  const file = join(dir, "secrets.json");
  try {
    await writeFile(file, JSON.stringify({
      [`${prefix}_INSTALL_ID`]: installId,
      [`${prefix}_SESSION_ID`]: session.session_id,
      [`${prefix}_SESSION_SECRET`]: session.session_secret,
      [`${prefix}_SESSION_EXPIRES`]: session.expires_at,
    }), { mode: 0o600 });
    await new Promise((resolveJob, reject) => {
      const child = spawn(process.execPath, [resolve("node_modules/wrangler/bin/wrangler.js"),
        "secret", "bulk", file, "--name", "vanta-music-gateway"], { stdio: ["ignore", "pipe", "pipe"], windowsHide: true });
      child.on("error", reject);
      child.on("exit", (code) => code === 0 ? resolveJob() : reject(new Error(`Could not save gateway session (Wrangler exit ${code})`)));
      // Consume output without printing provider credentials or command diagnostics.
      child.stdout.resume(); child.stderr.resume();
    });
    completed.add(provider);
    console.log(`${provider}: session saved to VANTA gateway; expires ${session.expires_at}`);
  } finally { await unlink(file).catch(() => undefined); await rmdir(dir); }
}

const page = `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>VANTA source setup</title>
<style>body{background:#101014;color:#f4eee5;font:17px system-ui;max-width:620px;margin:8vh auto;padding:24px}button{background:#d7ad72;color:#101014;border:0;border-radius:8px;padding:14px 22px;font:inherit;cursor:pointer}p{line-height:1.6}#status{color:#c7c2ca}</style></head>
<body><h1>Connect VANTA music sources</h1><p>Verify each extension with its provider. VANTA saves the resulting session to your gateway automatically.</p><button id="connect">Connect next source</button><p id="status">Ready to connect the selected sources.</p>
<script>
const token=${JSON.stringify(setupToken)}, providers=${JSON.stringify(providers)};
let active=null, popup=null;
const status=document.getElementById('status'), button=document.getElementById('connect');
async function request(path,body){const response=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json','X-Setup-Token':token},body:JSON.stringify(body)});const result=await response.json();if(!response.ok)throw Error(result.error);return result}
button.onclick=async()=>{if(!providers.length)return;active=providers[0];popup=window.open('about:blank','vanta-source-verification','width=640,height=800');if(!popup){status.textContent='Allow the verification popup, then try again.';return}button.disabled=true;status.textContent='Preparing '+active+' verification…';try{const result=await request('/bootstrap',{provider:active});if(result.connected){popup.close();finish()}else{popup.location=result.url;status.textContent='Complete '+active+' verification in the provider window.'}}catch(e){status.textContent=e.message;button.disabled=false;popup.close()}};
function finish(){providers.shift();status.textContent=active+' is connected.';button.disabled=false;if(!providers.length){status.textContent='Selected source sessions are saved. You can close this page.';button.hidden=true}}
window.addEventListener('message',async event=>{if(event.origin!=='https://api.zarz.moe'||event.source!==popup||event.data?.type!=='zarz_grant'||typeof event.data.grant!=='string')return;status.textContent='Saving '+active+' session…';try{await request('/grant',{provider:active,grant:event.data.grant});popup.close();finish()}catch(e){status.textContent=e.message;button.disabled=false}});
</script></body></html>`;

const server = createServer(async (request, response) => {
  const send = (status, value) => { response.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" }); response.end(JSON.stringify(value)); };
  try {
    const url = new URL(request.url, localOrigin);
    if (request.method === "GET" && url.pathname === "/" && url.searchParams.get("token") === setupToken) {
      response.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Frame-Options": "DENY" });
      response.end(page); return;
    }
    const supplied = Buffer.from(String(request.headers["x-setup-token"] ?? ""));
    if (request.method !== "POST" || request.headers.origin !== localOrigin ||
      supplied.length !== setupToken.length || !timingSafeEqual(supplied, Buffer.from(setupToken))) { send(403, { error: "Setup authorization required" }); return; }
    let text = "";
    for await (const chunk of request) { text += chunk; if (text.length > 32768) throw new Error("Request too large"); }
    const body = JSON.parse(text);
    const provider = body.provider;
    if (!providers.includes(provider)) throw new Error("Unknown provider");
    if (url.pathname === "/bootstrap") {
      if (completed.has(provider)) { send(200, { connected: true }); return; }
      const bootstrapUrl = new URL(`${base}/bootstrap`);
      bootstrapUrl.searchParams.set("install_id", installId);
      bootstrapUrl.searchParams.set("app_version", versions[provider]);
      const result = await readJson(await fetch(bootstrapUrl, { headers: { Accept: "application/json", "User-Agent": `SpotiFLAC-Mobile/${versions[provider]}` }, signal: AbortSignal.timeout(10000) }));
      if (result.session_id && result.session_secret) { await saveSession(provider, result); send(200, { connected: true }); return; }
      if (!result.challenge_id) throw new Error("Provider did not return a session or challenge");
      pending.set(provider, Date.now() + 10 * 60000);
      const challenge = new URL(`${base}/challenge`);
      challenge.searchParams.set("id", result.challenge_id);
      send(200, { url: challenge.toString() }); return;
    }
    if (url.pathname === "/grant") {
      if (exchanging || (pending.get(provider) ?? 0) < Date.now() || typeof body.grant !== "string" || !body.grant) throw new Error("Restart source verification");
      exchanging = true;
      try {
        const session = await readJson(await fetch(`${base}/session/exchange`, { method: "POST",
          headers: { "Content-Type": "application/json", "User-Agent": `SpotiFLAC-Mobile/${versions[provider]}` },
          body: JSON.stringify({ grant: body.grant, install_id: installId, app_version: versions[provider], platform: "extension" }), signal: AbortSignal.timeout(15000) }));
        await saveSession(provider, session); pending.delete(provider); send(200, { connected: true });
      } finally { exchanging = false; }
      return;
    }
    send(404, { error: "Not found" });
  } catch (error) { send(400, { error: error.message }); }
});
server.listen(0, "127.0.0.1", () => {
  localOrigin = `http://127.0.0.1:${server.address().port}`;
  console.log(`VANTA_SETUP_URL=${localOrigin}/?token=${setupToken}`);
});
setTimeout(() => { server.closeAllConnections(); server.close(); }, 15 * 60000).unref();
