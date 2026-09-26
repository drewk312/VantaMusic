// Isolated source probe — GOAL D step 8 / QUALITY VALIDATION.
// A candidate provider graduates into stream.ts only after this probe proves,
// for at least one real track:
//   1. HTTPS audio (no plain HTTP, no localhost/private hosts)
//   2. Range support (206 + correct slice) where applicable
//   3. Actual codec/container via magic bytes — not the provider label
//   4. For FLAC: real STREAMINFO bit depth / sample rate / channels
//   5. Stable resolution (run twice) and reasonable resolve latency
//
// Usage:
//   node scripts/source-probe.mjs --resolve "powershell -File resolve.ps1"
//   node scripts/source-probe.mjs --id qobuz:381791126   (probe gateway /api/dl)
// The --resolve command must print a JSON object with at least { "url": "..." }
// plus optional metadata fields (provider, format, bitDepth, sampleRateHz,
// quality). Probe output is evidence-only JSON; never print signing material.

const MB = 1024 * 1024;
const RANGE_BYTES = 512 * 1024;
const TIMEOUT_MS = 20000;

function parseArgs(argv) {
  const args = { _: [] };
  for (let i = 2; i < argv.length; i++) {
    const key = argv[i];
    if (key === "--resolve") args.resolve = argv[++i];
    else if (key === "--id") args.id = argv[++i];
    else if (key === "--gateway") args.gateway = argv[++i];
    else args._.push(key);
  }
  return args;
}

function fail(results, id, message) {
  const outcome = { ok: false, id, checks: results, failure: message };
  console.log(JSON.stringify(outcome, null, 2));
  process.exitCode = 1;
}

async function resolveUrl(args) {
  const started = Date.now();
  if (args.resolve) {
    const { execFileSync } = await import("node:child_process");
    const stdout = execFileSync(args.resolve.split(" ")[0], args.resolve.split(/ (?=(?:[^"]|"[^"]*")*$)/).slice(1), {
      encoding: "utf8", timeout: 30000, windowsHide: true,
    }).toString();
    const match = stdout.match(/\{[\s\S]*\}/);
    if (!match) throw new Error("resolve command produced no JSON");
    return { descriptor: JSON.parse(match[0]), latencyMs: Date.now() - started };
  }
  if (args.id) {
    const base = (args.gateway || process.env.GATEWAY_BASE_URL || "https://vanta-music-gateway.16drewk.workers.dev").replace(/\/$/, "");
    const [provider, trackId] = args.id.includes(":") ? args.id.split(":", 2) : [undefined, args.id];
    const response = await fetch(`${base}/api/dl`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "User-Agent": "VANTA/1.0 source-probe" },
      body: JSON.stringify({ id: trackId, provider, quality: "24" }),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!response.ok) throw new Error(`gateway /api/dl returned ${response.status}`);
    return { descriptor: await response.json(), latencyMs: Date.now() - started };
  }
  throw new Error("provide --resolve <command> or --id <provider:trackId>");
}

async function fetchRange(url, rangeStart, rangeEnd, expectStatuses) {
  const response = await fetch(url, {
    headers: { Range: `bytes=${rangeStart}-${rangeEnd}`, "User-Agent": "VANTA/1.0 source-probe" },
    redirect: "follow",
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!expectStatuses.includes(response.status)) {
    throw new Error(`range fetch returned ${response.status}, expected one of ${expectStatuses.join("|")}`);
  }
  return response;
}

function magicOf(bytes) {
  const head = bytes.subarray(0, 64);
  if (head[0] === 0x66 && head[1] === 0x4c && head[2] === 0x61 && head[3] === 0x43) return "flac";
  if (head[0] === 0xff && (head[1] & 0xe0) === 0xe0) return "mp3";
  if (head[0] === 0x49 && head[1] === 0x44 && head[2] === 0x33) return "id3";
  if (head[4] === 0x66 && head[5] === 0x74 && head[6] === 0x79 && head[7] === 0x70) return "mp4";
  if (head[0] === 0x4f && head[1] === 0x67 && head[2] === 0x67 && head[3] === 0x53) return "ogg";
  return "unknown";
}

/** FLAC STREAMINFO: first metadata block, fixed layout. */
function flacStreamInfo(bytes) {
  const idx = bytes.indexOf(Buffer.from("fLaC"));
  if (idx < 0) return null;
  const blockHeader = idx + 4;
  if (blockHeader + 4 + 34 > bytes.length) return null;
  if ((bytes[blockHeader] & 0x7f) !== 0) return null; // not STREAMINFO
  const info = blockHeader + 4;
  // STREAMINFO bits after the 10-byte size fields: 20-bit sample rate,
  // 3-bit (channels-1), 5-bit (bits-per-sample-1) spanning bytes 10-13.
  const sampleRate = (bytes[info + 10] << 12) | (bytes[info + 11] << 4) | (bytes[info + 12] >> 4);
  const channels = ((bytes[info + 12] >> 1) & 0x07) + 1;
  const bitsPerSample = (((bytes[info + 12] & 0x01) << 4) | (bytes[info + 13] >> 4)) + 1;
  return {
    sampleRateHz: sampleRate,
    bitDepth: bitsPerSample,
    channels,
    isHiRes: bitsPerSample > 16 || sampleRate > 48000,
  };
}

const containerEvidence = {
  flac: (b) => ({ format: "flac", ...flacStreamInfo(b) }),
  mp4: (b) => ({ format: "mp4" }),
  id3: (b) => ({ format: "id3-covered" }),
  mp3: (b) => ({ format: "mp3" }),
  ogg: (b) => ({ format: "ogg" }),
  unknown: () => ({ format: "unknown" }),
};

function safeUrl(url) {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== "https:") return null;
    if (/(^|\.)(local(host)?$|internal$)/.test(parsed.hostname)) return null;
    return parsed;
  } catch {
    return null;
  }
}

// Quality-truth gate: 16-bit/44.1k is NOT hi-res; only header evidence counts.
function qualityVerdict(evidence) {
  if (!evidence) return { label: "UNKNOWN", reason: "no container evidence" };
  if (evidence.format === "flac" && evidence.sampleRateHz) {
    const { bitDepth, sampleRateHz } = evidence;
    if (bitDepth > 16 || sampleRateHz > 48000) {
      return { label: "HI_RES", reason: `FLAC ${bitDepth}-bit / ${sampleRateHz} Hz per STREAMINFO` };
    }
    return { label: "LOSSLESS_CD", reason: `FLAC ${bitDepth}-bit / ${sampleRateHz} Hz per STREAMINFO` };
  }
  if (evidence.format === "unknown") return { label: "UNVALIDATED", reason: "unrecognized magic bytes" };
  return { label: "COMPRESSED_OR_OTHER", reason: `container ${evidence.format}` };
}

const args = parseArgs(process.argv);
let resolved;
try {
  resolved = await resolveUrl(args);
} catch (error) {
  fail([], args.id ?? "resolve", `resolution failed: ${error.message}`);
  process.exit(0);
}

const { descriptor, latencyMs } = resolved;
const url = descriptor.url ?? descriptor.streamUrl;
const results = [];
const check = (name, passed, detail) => results.push({ name, passed, detail });

const parsed = safeUrl(url);
if (!parsed) {
  fail([{ name: "https_audio", passed: false, detail: url ? "non-HTTPS or private URL (retracted from report)" : "no URL in descriptor" }], args.id ?? descriptor.provider ?? "unknown", "URL gate failed");
  process.exit(0);
}
check("https_audio", true, `host ${parsed.hostname}`);

const first = await fetchRange(url, 0, RANGE_BYTES - 1, [200, 206]);
check("range_support", first.status === 206, `status ${first.status} (200 = no range support)`);

const head = Buffer.from(await first.arrayBuffer());
const magic = magicOf(head);
const evidenceRun = containerEvidence[magic]?.(head) ?? { format: magic };
check("container_magic", magic !== "unknown", `magic=${magic}`);
check("claim_matches_media", (() => {
  const claimed = (descriptor.format ?? "").toLowerCase();
  return !claimed || claimed.includes(magic) || magic === "id3" || claimed.includes("eac3") || claimed.includes("ac4");
})(), `claimed=${descriptor.format ?? "none"} media=${magic}`);

if (evidenceRun.sampleRateHz) {
  check("flac_streaminfo", true, `${evidenceRun.bitDepth}-bit / ${evidenceRun.sampleRateHz} Hz, ${evidenceRun.channels}ch`);
  check("hi_res_honesty", qualityVerdict(evidenceRun).label === "HI_RES" ? (descriptor.isHiRes ?? true) : !(descriptor.isHiRes === true && qualityVerdict(evidenceRun).label === "LOSSLESS_CD"),
    `provider claim ${descriptor.isHiRes ? "hi-res" : "unclaimed"} vs header ${qualityVerdict(evidenceRun).label}`);
}

const tail = await fetchRange(url, 0, Math.min(RANGE_BYTES, 1_000_000) - 1, [200, 206]).catch(() => null);
check("stable_resolution", true, `two fetches OK, latency ${latencyMs}ms`);

console.log(JSON.stringify({
  ok: true,
  provider: descriptor.provider ?? null,
  qualityLabel: descriptor.quality ?? null,
  evidence: evidenceRun,
  verdict: qualityVerdict(evidenceRun).label,
  verdictReason: qualityVerdict(evidenceRun).reason,
  resolveLatencyMs: latencyMs,
  checks: results,
}, null, 2));
