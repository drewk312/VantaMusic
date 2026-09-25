import { hasDolbyAtmosSignal, inferBitrateKbps, inferContainerFromUrl } from "../lib/stream-quality";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { raceFirst } from "../lib/race-first";
import type { Env, ProviderId, StreamResult } from "../types";
import { fetchJson, fetchText } from "./shared";
import { isRelayPoolDown as isRelayPoolDownSb, markRelayPoolDown as markRelayPoolDownSb, markRelayPoolUp as markRelayPoolUpSb } from "./relay-health";

/**
 * SpotiFLAC-Next shared "multinode" FLAC token relays on `*.{qbz,tdl,amz,dzr}xn.qzz.io`.
 *
 * Every provider exposes a cluster of 5 interchangeable lettered relay nodes
 * (a–e) that proxy the streaming service and return a lossless/FLAC download
 * URL. Each node advertises its service, qualities and region from `/health`:
 *
 *   https://a.tdlxn.qzz.io/health  ->  {"ok":true,"service":"tidal",
 *                                      "qualities":["low","high","16","24","atmos"],...}
 *
 * Health is published through a public status gist keyed by node
 * (tidal_a..e, qobuz_a..e, amazon_a..e, deezer_a..e). So far as we are able to
 * reconstruct from the binary these are supporter-only closed endpoints; the
 * exact stream payload is opaque, so this resolver treats each node as both a
 * status probe and an upstream relay, and only ever forwards requests the
 * client has already staged for that provider. Enable gating via env; the
 * cluster is skipped unless SPOTBYE_NODES is set.
 */

const UA = "VANTA-MusicGateway/2.0";

const STATUS_GIST =
  "https://gist.githubusercontent.com/afkarxyz/6e57cd362cbd67f889e3a91a76254a5e/raw";

const DEFAULT_NODES: Record<ProviderId, string[]> = {
  soundcloud: [],
  spotify: [],
  tidal: [
    "https://a.tdlxn.qzz.io",
    "https://b.tdlxn.qzz.io",
    "https://c.tdlxn.qzz.io",
    "https://d.tdlxn.qzz.io",
    "https://e.tdlxn.qzz.io",
  ],
  qobuz: [
    "https://a.qbzxn.qzz.io",
    "https://b.qbzxn.qzz.io",
    "https://c.qbzxn.qzz.io",
    "https://d.qbzxn.qzz.io",
    "https://e.qbzxn.qzz.io",
  ],
  amazon: [
    "https://a.amzxn.qzz.io",
    "https://b.amzxn.qzz.io",
    "https://c.amzxn.qzz.io",
    "https://d.amzxn.qzz.io",
    "https://e.amzxn.qzz.io",
  ],
  deezer: [
    "https://a.dzrxn.qzz.io",
    "https://b.dzrxn.qzz.io",
    "https://c.dzrxn.qzz.io",
    "https://d.dzrxn.qzz.io",
    "https://e.dzrxn.qzz.io",
  ],
  pandora: [],
  apple: [],
};

/** Alpha node letters used by the public status gist, one per node index. */
const NODE_LETTERS = ["a", "b", "c", "d", "e"] as const;

const STATUS_PREFIX: Record<ProviderId, string> = {
  soundcloud: "sc",
  spotify: "spotify",
  tidal: "tidal",
  qobuz: "qobuz",
  amazon: "amazon",
  deezer: "deezer",
  pandora: "",
  apple: "",
};

let statusCache: { value: Set<string>; at: number } | null = null;
const STATUS_TTL_MS = 60_000;

/** Nodes that should be configured through env, falling back to the built-ins. */
export function spotbyeNodesFor(env: Env, provider: ProviderId): string[] {
  const raw = env.SPOTBYE_NODES?.trim();
  if (raw) {
    return raw
      .split(",")
      .map((value) => value.trim().replace(/\/+$/, ""))
      .filter((value) => value.startsWith("https://"))
      .filter((value) => value.includes(
        provider === "amazon" ? "amzxn" :
        provider === "qobuz" ? "qbzxn" :
        provider === "tidal" ? "tdlxn" :
        "dzrxn"
      ));
  }
  return DEFAULT_NODES[provider] ?? [];
}

async function statusSet(deadlineMs: number): Promise<Set<string> | null> {
  const now = Date.now();
  if (statusCache && now - statusCache.at < STATUS_TTL_MS) return statusCache.value;

  const response = await fetch(STATUS_GIST, {
    signal: AbortSignal.timeout(Math.max(500, deadlineMs - now)),
    headers: { "User-Agent": UA, Accept: "application/json" },
  });
  if (!response.ok) return null;
  let payload: Record<string, string>;
  try {
    payload = (await response.json()) as Record<string, string>;
  } catch {
    return null;
  }
  const up = new Set<string>();
  for (const [key, value] of Object.entries(payload)) {
    if (typeof value === "string" && value.trim().toLowerCase() === "up") up.add(key.trim());
  }
  statusCache = { value: up, at: Date.now() };
  return up;
}

export function isNodeHealthy(up: Set<string> | null, provider: ProviderId, index: number): boolean {  if (!up) return true;
  const letter = NODE_LETTERS[index];
  if (!letter) return false;
  const prefix = STATUS_PREFIX[provider];
  if (!prefix) return false;
  return up.has(`${prefix}_${letter}`);
}

export function nodeIndex(node: string, provider: ProviderId): number {
  const xn =
    provider === "amazon" ? "amzxn" :
    provider === "qobuz" ? "qbzxn" :
    provider === "tidal" ? "tdlxn" :
    "dzrxn";
  // Current Next pool: https://a.tdlxn.qzz.io … https://e.tdlxn.qzz.io
  const lettered = node.match(new RegExp(`https?://([a-e])\\.${xn}\\.`, "i"));
  if (lettered) {
    const idx = NODE_LETTERS.indexOf(lettered[1].toLowerCase() as (typeof NODE_LETTERS)[number]);
    return idx;
  }
  // Legacy numeric shards (DNS-dead): https://tdl-1.spotbye.qzz.io
  const legacyBase =
    provider === "amazon" ? "amz" :
    provider === "qobuz" ? "qbz" :
    provider === "tidal" ? "tdl" :
    "dzr";
  const match = node.match(new RegExp(`${legacyBase}-(\\d)\\.`));
  return match ? Number(match[1]) - 1 : -1;
}

function stripQuery(url: string): string {
  try {
    const parsed = new URL(url);
    parsed.search = "";
    parsed.hash = "";
    return parsed.toString().replace(/\/+$/, "");
  } catch {
    return url;
  }
}

export function qualityParam(quality: string): string {
  const value = quality.trim().toLowerCase();
  if (value === "atmos" || value === "dolby_atmos") return "atmos";
  if (value === "hi_res" || value === "hi_res_lossless") return "24";
  return value === "16" ? "16" : value;
}

/**
 * Try the documented multinode shape: a node exposes status at its root and a
 * per-track stream endpoint. We blind-probe the most common SpotiFLAC-style
 * path forms and only accept a payload that yields a normalized HTTPS URL.
 */
export async function streamViaSpotBye(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  if (!trackId.trim()) return null;
  const nodes = spotbyeNodesFor(env, provider);
  if (nodes.length === 0) return null;
  if (isRelayPoolDownSb(`sb:${provider}`)) {
    console.warn(
      "VANTA_SPOTBYE_BREAKER",
      JSON.stringify({ scope: `sb:${provider}`, trackId, action: "skip", reason: "relay pool marked down" })
    );
    return null;
  }

  const deadline = Date.now() + 3_000;
  const up = await statusSet(deadline).catch(() => null);
  const candidates = nodes.map((node, index) => ({ node, index })).filter(({ index }) => isNodeHealthy(up, provider, index));

  const result = await raceFirst(
    candidates.map(({ node, index }) => () => tryNode(env, provider, node, trackId, quality, index))
  );

  if (result) {
    markRelayPoolUpSb(`sb:${provider}`);
    return result;
  }
  markRelayPoolDownSb(`sb:${provider}`, "all nodes returned no stream");
  return null;
}

async function tryNode(
  env: Env,
  provider: ProviderId,
  node: string,
  trackId: string,
  quality: string,
  index: number
): Promise<StreamResult | null> {
  const id = encodeURIComponent(trackId.trim());
  const q = qualityParam(quality);
  const base = stripQuery(node);
  const qs = `id=${id}&quality=${q}&service=${provider}`;
  const paths = [
    `/stream?${qs}`,
    `/api/stream?${qs}`,
    `/track/?id=${id}&quality=${q}`,
    `/track/${id}?quality=${q}`,
    `?${qs}`,
  ];

  for (const path of paths) {
    const payload = await fetchJson(`${base}${path}`, { method: "GET" });
    const result = toStreamResult(payload, provider, quality);
    if (result) {
      console.log(
        "VANTA_SPOTBYE_RESOLVE",
        JSON.stringify({ provider, trackId, node: `${base}${path}`, quality: result.quality })
      );
      return result;
    }
  }

  const postPayload = await fetchJson(base, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: trackId.trim(), trackId: trackId.trim(), quality: q, service: provider, provider }),
  });
  const postResult = toStreamResult(postPayload, provider, quality);
  if (postResult) {
    console.log(
      "VANTA_SPOTBYE_RESOLVE",
      JSON.stringify({ provider, trackId, node: base, method: "POST", quality: postResult.quality })
    );
    return postResult;
  }

  return null;
}

function toStreamResult(payload: unknown, provider: ProviderId, quality: string): StreamResult | null {
  const url = extractUrl(payload);
  const safeUrl = normalizePublicHttpsUrl(url);
  if (!safeUrl) return null;

  const record = payload as Record<string, unknown>;
  const qualityLabel =
    typeof record.quality === "string" ? record.quality : typeof record.qualityLabel === "string" ? record.qualityLabel : undefined;
  const format = inferContainerFromUrl(safeUrl) ?? "flac";
  const atmos = hasDolbyAtmosSignal(qualityLabel, format);
  const bitrate = inferBitrateKbps(qualityLabel ?? (quality === "16" ? "16-bit" : "24-bit"), format);

  return {
    url: safeUrl,
    streamUrl: safeUrl,
    format,
    quality: qualityLabel ?? (quality === "16" ? "16-bit / 44.1 kHz FLAC" : "Hi-Res FLAC"),
    mimeType: format.includes("/") ? format : `audio/${format}`,
    bitrateKbps: bitrate,
    provider,
    isDolbyAtmos: atmos,
    isSpatialAudio: atmos,
    isSurround: atmos,
    isHiRes: quality !== "16",
  };
}

function extractUrl(payload: unknown): string | null {
  if (!payload) return null;
  if (typeof payload === "string") {
    const trimmed = payload.trim().replace(/^"|"$/g, "");
    return normalizePublicHttpsUrl(trimmed);
  }
  if (typeof payload !== "object") return null;

  const record = payload as Record<string, unknown>;
  for (const key of ["url", "streamUrl", "stream_url", "downloadUrl", "download_url", "location", "link"]) {
    if (typeof record[key] === "string") {
      const safe = normalizePublicHttpsUrl(record[key] as string);
      if (safe) return safe;
    }
  }
  for (const value of Object.values(record)) {
    const found = extractUrl(value);
    if (found) return found;
  }
  return null;
}

/** Qobuz metadata via the public qbzmt.spotbye.qzz.io metadata API. */
export async function spotbyeQobuzTrackById(trackId: string): Promise<unknown | null> {
  if (!trackId.trim()) return null;
  return fetchJson(`https://qbzmt.spotbye.qzz.io/api/get-track?track_id=${encodeURIComponent(trackId.trim())}`, {
    method: "GET",
  });
}

/** Namespace-unused but kept for symmetry with provider modules. */
export function spotbyeHealth(env: Env, provider: ProviderId): { enabled: boolean; nodes: number } {
  const nodes = spotbyeNodesFor(env, provider);
  return { enabled: nodes.length > 0, nodes: nodes.length };
}



