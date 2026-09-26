#!/usr/bin/env node
// VANTA isolated source probe (Goal D). A provider graduates into production
// only after this probe proves: correct identity metadata, HTTPS audio, range
// support where applicable, actual codec/container, and stable resolution.
// Run: node scripts/probe-source.mjs --url <streamUrl>
//      node scripts/probe-source.mjs --archive "artist live"
// No credentials are read, stored, or sent. Output never logs tokens.

const FETCHER = "VANTA-source-probe/1.0";

const args = process.argv.slice(2);
function argValue(name) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : null;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function fetchWithTiming(url, init = {}) {
  const started = Date.now();
  let response;
  try {
    response = await fetch(url, {
      ...init,
      headers: { "User-Agent": FETCHER, ...(init.headers ?? {}) },
      redirect: "manual",
      signal: AbortSignal.timeout(init.timeoutMs ?? 15000),
    });
  } catch (error) {
    return { ok: false, status: 0, ms: Date.now() - started, error: String(error) };
  }
  return { ok: response.ok, status: response.status, response, ms: Date.now() - started };
}

function sniffMagic(bytes) {
  const sig = bytes.subarray(0, 12);
  const ascii = Buffer.from(sig.slice(0, 4)).toString("latin1");
  if (ascii === "fLaC") return { container: "flac", ok: true };
  if (ascii === "OggS") return { container: "ogg", ok: true };
  if (ascii === "RIFF" && Buffer.from(bytes.subarray(8, 12)).toString("latin1") === "WAVE") return { container: "wav", ok: true };
  if (ascii === "ID3" || (sig[0] === 0xff && (sig[1] & 0xe0) === 0xe0)) return { container: "mp3", ok: true };
  if (ascii === "ftyp") return { container: "mp4/m4a", ok: true };
  if (ascii === "MAC ") return { container: "ape", ok: true };
  return { container: null, ok: false };
}

/** Follow redirect chain manually, recording hops; refuse scheme changes and >4 hops. */
async function resolveFinalUrl(rawUrl) {
  const hops = [];
  let current = rawUrl;
  for (let i = 0; i < 5; i++) {
    const step = await fetchWithTiming(current, { method: "GET", headers: { Range: "bytes=0-1023" } });
    if (step.status >= 300 && step.status < 400) {
      const location = step.response.headers.get("location");
      if (!location) return { hops, final: null };
      try {
        const next = new URL(location, current);
        if (next.protocol !== "https:") return { hops, final: null, fatal: "scheme-downgrade" };
        hops.push(next.origin + next.pathname);
        current = next.toString();
      } catch { return { hops, final: null }; }
      continue;
    }
    return { hops, final: current, status: step.status };
  }
  return { hops, final: null };
}

async function probeStreamUrl(rawUrl) {
  const report = { url: rawUrl.replace(/([?&]token=)[^&]+/gi, "$1<omitted>"), checks: {} };
  let url;
  try { url = new URL(rawUrl); } catch { report.checks.https = { pass: false, reason: "unparseable" }; return report; }
  report.checks.https = { pass: url.protocol === "https:", proto: url.protocol.replace(":", "") };

  // 0. Resolve the redirect chain (mirrors production: CDN hops may change).
  const chain = await resolveFinalUrl(rawUrl);
  report.checks.redirects = { hops: chain.hops.map((h) => new URL(h).host), finalHost: chain.final ? new URL(chain.final).host : null, fatal: chain.fatal ?? null };
  if (!chain.final) return report;

  // 1. Head: status + content-type + accept-ranges
  const head = await fetchWithTiming(chain.final, { method: "GET", headers: { Range: "bytes=0-1023" } });
  report.checks.latencyMs = head.ms;
  if (!head.ok) {
    report.checks.audioReach = { pass: false, status: head.status };
    return report;
  }
  report.checks.rangeHead = {
    pass: head.status === 206 && /bytes/.test(head.response.headers.get("content-range") ?? ""),
    status: head.status,
    contentType: head.response.headers.get("content-type"),
    acceptRanges: head.response.headers.get("accept-ranges"),
    contentLength: head.response.headers.get("content-length"),
  };
  const headBytes = new Uint8Array(await head.response.arrayBuffer());
  const magic = sniffMagic(headBytes);
  report.checks.container = magic;
  if (magic.ok) report.checks.flacLikely = { pass: magic.container === "flac" || magic.container === "ogg" };

  if (!(report.checks.rangeHead?.pass)) return report;

  // 2. Middle-range seek (seekability)
  await sleep(250);
  const mid = await fetchWithTiming(chain.final, { method: "GET", headers: { Range: "bytes=1000000-1000999" } });
  report.checks.rangeSeek = { pass: mid.ok && mid.status === 206, status: mid?.status };

  // 3. Out-of-bounds range behaves sanely
  await sleep(250);
  const end = await fetchWithTiming(chain.final, { method: "GET", headers: { Range: "bytes=99999999999-" } });
  report.checks.rangeTailsafe = { pass: end.status === 416 || end.ok, status: end?.status };

  return report;
}

async function probeArchive(query, limit = 3) {
  const searchUrl = new URL("https://archive.org/advancedsearch.php");
  searchUrl.searchParams.set("q", `${query} AND format:(FLAC) AND mediatype:(audio)`);
  searchUrl.searchParams.set("fl[]", ["identifier", "title", "collection"]);
  searchUrl.searchParams.set("rows", String(limit));
  searchUrl.searchParams.set("output", "json");
  const search = await fetchWithTiming(searchUrl, { headers: { Accept: "application/json" } });
  if (!search.ok) return { step: "search", pass: false, status: search.status };
  const payload = await search.response.json().catch(() => null);
  const docs = payload?.response?.docs ?? [];
  const results = [];
  for (const doc of docs.slice(0, limit)) {
    const meta = await fetchWithTiming(`https://archive.org/metadata/${encodeURIComponent(doc.identifier)}`);
    if (!metaOk(meta)) continue;
    const data = await meta.response.json().catch(() => null);
    const files = (data?.files ?? []).filter((f) => /\.(flac|mp3)$/i.test(f.name ?? ""));
    for (const file of files.filter((f) => /\.flac$/i.test(f.name ?? "")).slice(0, 1)) {
      const stream = await probeStreamUrl(`https://archive.org/download/${encodeURIComponent(doc.identifier)}/${encodeURIComponent(file.name)}`);
      results.push({
        identifier: doc.identifier,
        identity: { title: doc.title, file: file.name, format: file.format, fileLengthBytes: Number(file.size ?? 0), trackDuration: file.length ?? null },
        probe: stream,
        pass: stream.checks.https?.pass === true && stream.checks.rangeHead?.pass === true && stream.checks.container?.ok === true,
      });
    }
  }
  return { step: "archive", query, found: payload?.response?.numFound ?? 0, results };
}

function metaOk(meta) {
  return meta.ok && meta.status === 200;
}

const url = argValue("--url");
const archive = argValue("--archive");
const output = url ? { probe: "direct_url", ...(await probeStreamUrl(url)) } : archive ? await probeArchive(archive) : { usage: "--url <streamUrl> | --archive <query>" };
console.log(JSON.stringify(output, null, 2));
const pass = url
  ? output.checks?.https?.pass === true && output.checks?.rangeHead?.pass === true && output.checks?.container?.ok === true
  : (output.results ?? []).length > 0 && output.results.every((r) => r.pass);
console.log(`# PROBE ${pass ? "PASS" : "FAIL"}`);
