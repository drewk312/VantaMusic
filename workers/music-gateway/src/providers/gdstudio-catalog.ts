import type { Env, ProviderId, StreamResult } from "../types";
import { inferBitrateKbps } from "../lib/stream-quality";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { fetchJson } from "./shared";

/** GD Studio currently lists these as the open sources. Qobuz/Tidal/Deezer return 400. */
const LIVE_SOURCES = ["netease", "joox"] as const;
const DEFAULT_API = "https://music-api.gdstudio.xyz/api.php";

export function gdstudioApiUrl(env: Env): string {
  const configured = env.GDSTUDIO_API_URL?.trim();
  if (configured) return configured;
  return DEFAULT_API;
}

function normalizeName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function isLikelyAlternateRecording(title: string): boolean {
  return /\b(mix|remix|cover|karaoke|nightcore|sped up|slowed|live stream|24\/7)\b/i.test(title);
}

function namesMatch(want: string, got: string): boolean {
  const a = normalizeName(want);
  const b = normalizeName(got);
  return a.length >= 4 && b.length >= 4 && (a === b || a.includes(b) || b.includes(a));
}

function artistsMatch(want: string, got: unknown): boolean {
  const names = Array.isArray(got)
    ? got.filter((item): item is string => typeof item === "string")
    : typeof got === "string"
      ? [got]
      : [];
  return names.some((name) => namesMatch(want, name));
}

interface GdstudioSearchHit {
  id?: string;
  name?: string;
  artist?: unknown;
  source?: string;
}

interface GdstudioUrlPayload {
  url?: string;
  br?: number;
}

function looksLikeFlac(bytes: Uint8Array): boolean {
  return bytes.length >= 4 && bytes[0] === 0x66 && bytes[1] === 0x4c && bytes[2] === 0x61 && bytes[3] === 0x43;
}

function looksLikeMp3(bytes: Uint8Array): boolean {
  if (bytes.length >= 3 && bytes[0] === 0x49 && bytes[1] === 0x44 && bytes[2] === 0x33) return true;
  return bytes.length >= 2 && bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0;
}

const NETEASE_REFERER = "https://music.163.com/";
const CHROME_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

/** NetEase signed paths `/20YYMMDDHHmmss/` are issued-at (CST). Live for ~15 minutes. */
export function neteaseUrlExpiresAtSeconds(url: string): number | undefined {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return undefined;
  }
  const host = parsed.hostname.toLowerCase();
  if (!host.endsWith("126.net") && !host.endsWith("163.com") && !host.includes("music.126")) {
    return undefined;
  }
  const match = parsed.pathname.match(/\/(20\d{12})(?:\/|$)/);
  if (!match) return Math.floor(Date.now() / 1000) + 60;
  const stamp = match[1];
  const issued = Date.parse(
    `${stamp.slice(0, 4)}-${stamp.slice(4, 6)}-${stamp.slice(6, 8)}T${stamp.slice(8, 10)}:${stamp.slice(10, 12)}:${stamp.slice(12, 14)}+08:00`
  );
  if (!Number.isFinite(issued)) return Math.floor(Date.now() / 1000) + 60;
  return Math.floor((issued + 15 * 60_000) / 1000);
}

function neteaseProbeHeaders(url: string): Record<string, string> {
  const headers: Record<string, string> = {
    Range: "bytes=0-11",
    "User-Agent": CHROME_UA,
    Accept: "*/*",
  };
  try {
    const host = new URL(url).hostname.toLowerCase();
    if (host.endsWith("126.net") || host.endsWith("163.com") || host.includes("music.126")) {
      headers.Referer = NETEASE_REFERER;
      headers.Origin = "https://music.163.com";
    }
  } catch {
    // URL parse failed; probe without a Referer.
  }
  return headers;
}

async function classifyAudio(url: string): Promise<"flac" | "lossy" | null> {
  try {
    const response = await fetch(url, {
      headers: neteaseProbeHeaders(url),
      signal: AbortSignal.timeout(4_000),
    });
    if (!response.ok && response.status !== 206) return null;
    const ctype = (response.headers.get("content-type") || "").toLowerCase();
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (looksLikeFlac(bytes) || (ctype.includes("flac") && !looksLikeMp3(bytes))) return "flac";
    if (ctype.includes("mpeg") || ctype.includes("mp3") || looksLikeMp3(bytes)) return "lossy";
    if (/\.flac(\?|$)/i.test(url)) return "flac";
    return null;
  } catch {
    return null;
  }
}

function bitrates(mode: "flac" | "mp3"): string[] {
  return mode === "flac" ? ["999", "740"] : ["320"];
}

function toResult(
  url: string,
  catalogProvider: ProviderId,
  br: number | undefined,
  kind: "flac" | "lossy"
): StreamResult {
  const flac = kind === "flac";
  const bitrateKbps = flac
    ? inferBitrateKbps(br && br >= 900 ? "24-bit" : "16-bit", "flac")
    : (br && br > 0 && br <= 320 ? br : 320);
  return {
    url,
    streamUrl: url,
    format: flac ? "flac" : "mp3",
    quality: flac
      ? (br && br >= 900 ? "Hi-Res FLAC" : "Lossless FLAC")
      : `MP3 ${bitrateKbps} kbps`,
    mimeType: flac ? "audio/flac" : "audio/mpeg",
    bitrateKbps,
    expiresAt: neteaseUrlExpiresAtSeconds(url),
    provider: catalogProvider,
    isDolbyAtmos: false,
    isSpatialAudio: false,
    isSurround: false,
    isHiRes: Boolean(flac && br && br >= 900),
  };
}

function pickHit(hits: GdstudioSearchHit[], title: string, artist: string): GdstudioSearchHit | null {
  const originalAlt = isLikelyAlternateRecording(title);
  return hits.find((hit) => {
    if (!hit.id || !hit.name) return false;
    if (!originalAlt && isLikelyAlternateRecording(hit.name)) return false;
    return namesMatch(title, hit.name) && artistsMatch(artist, hit.artist);
  }) ?? null;
}

async function resolveUrl(
  api: string,
  source: string,
  id: string,
  mode: "flac" | "mp3",
  catalogProvider: ProviderId
): Promise<StreamResult | null> {
  for (const br of bitrates(mode)) {
    const payload = await fetchJson(
      `${api}?types=url&source=${encodeURIComponent(source)}&id=${encodeURIComponent(id)}&br=${br}`
    ) as GdstudioUrlPayload | null;
    const url = typeof payload?.url === "string" ? normalizePublicHttpsUrl(payload.url) : null;
    if (!url) continue;
    if (typeof payload?.br === "number" && payload.br < 0) continue;
    const expiresAt = neteaseUrlExpiresAtSeconds(url);
    if (expiresAt != null && expiresAt * 1000 <= Date.now() + 5_000) continue;
    const kind = await classifyAudio(url);
    if (!kind) continue;
    if (mode === "flac" && kind !== "flac") continue;
    if (mode === "mp3" && kind === "flac") return toResult(url, catalogProvider, payload?.br, "flac");
    return toResult(url, catalogProvider, payload?.br, kind);
  }
  return null;
}

/**
 * GD Studio Netease/JOOX identity match. `flac` only returns verified FLAC;
 * `mp3` is the absolute last resort after SoundCloud.
 */
export async function streamViaGdstudioExact(
  env: Env,
  title: string | undefined,
  artist: string | undefined,
  quality: string,
  catalogProvider: ProviderId = "qobuz",
  mode: "flac" | "mp3" = "flac"
): Promise<StreamResult | null> {
  if (!title?.trim() || !artist?.trim()) return null;
  if (mode === "flac" && quality.trim().toLowerCase().includes("atmos")) return null;
  const api = gdstudioApiUrl(env).replace(/\/+$/, "");
  const name = encodeURIComponent(`${title.trim()} ${artist.trim()}`);
  for (const source of LIVE_SOURCES) {
    const payload = await fetchJson(`${api}?types=search&source=${source}&name=${name}&count=8&pages=1`);
    const hits = Array.isArray(payload) ? payload as GdstudioSearchHit[] : [];
    const hit = pickHit(hits, title, artist);
    if (!hit?.id) continue;
    const stream = await resolveUrl(api, source, String(hit.id), mode, catalogProvider);
    if (stream) return stream;
  }
  return null;
}
