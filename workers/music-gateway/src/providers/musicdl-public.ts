import type { StreamResult } from "../types";
import { inferBitrateKbps, qualityLabelFromBitrate } from "../lib/stream-quality";
import { fetchJson } from "./shared";

const MUSICDL_QOBUZ_URL = "https://www.musicdl.me/api/qobuz/download";

const SEED_PARTS = [
  new Uint8Array([0x73, 0x70, 0x6f, 0x74, 0x69, 0x66]),
  new Uint8Array([0x6c, 0x61, 0x63, 0x3a, 0x71, 0x6f]),
  new Uint8Array([0x62, 0x75, 0x7a, 0x3a, 0x6d, 0x75, 0x73, 0x69, 0x63, 0x64, 0x6c, 0x3a, 0x76, 0x31]),
];
const AAD = new Uint8Array([
  0x71, 0x6f, 0x62, 0x75, 0x7a, 0x7c, 0x6d, 0x75, 0x73, 0x69, 0x63, 0x64, 0x6c, 0x7c, 0x64, 0x65,
  0x62, 0x75, 0x67, 0x7c, 0x76, 0x31,
]);
const NONCE = new Uint8Array([0x91, 0x2a, 0x5c, 0x77, 0x0f, 0x33, 0xa8, 0x14, 0x62, 0x9d, 0xce, 0x41]);
const CIPHERTEXT = new Uint8Array([
  0xf3, 0x4a, 0x83, 0x45, 0x24, 0xb6, 0x22, 0xaf, 0xd6, 0xc3, 0x6e, 0x2d, 0x56, 0xd1, 0xbb, 0x0b,
  0xe9, 0x1b, 0x4f, 0x1c, 0x5f, 0x41, 0x55, 0xc2, 0xc6, 0xdf, 0xad, 0x21, 0x58, 0xfe, 0xd5, 0xb8,
  0x2d, 0x29, 0xf9, 0x9e, 0x6f, 0xd6,
]);
const TAG = new Uint8Array([
  0x69, 0x0c, 0x42, 0x70, 0x14, 0x83, 0xff, 0x14, 0xc8, 0xbe, 0x17, 0x00, 0x69, 0xb1, 0xfe, 0xbb,
]);

let keyPromise: Promise<CryptoKey> | null = null;
let cachedDebugKey: string | null | undefined;

async function musicDlKey(): Promise<CryptoKey> {
  if (!keyPromise) {
    keyPromise = (async () => {
      const total = SEED_PARTS.reduce((n, p) => n + p.length, 0);
      const merged = new Uint8Array(total);
      let offset = 0;
      for (const part of SEED_PARTS) {
        merged.set(part, offset);
        offset += part.length;
      }
      const hash = await crypto.subtle.digest("SHA-256", merged);
      return crypto.subtle.importKey("raw", hash, { name: "AES-GCM" }, false, ["decrypt"]);
    })();
  }
  return keyPromise;
}

async function getMusicDlDebugKey(): Promise<string | null> {
  if (cachedDebugKey !== undefined) return cachedDebugKey;
  try {
    const key = await musicDlKey();
    const sealed = new Uint8Array(CIPHERTEXT.length + TAG.length);
    sealed.set(CIPHERTEXT);
    sealed.set(TAG, CIPHERTEXT.length);
    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: NONCE, additionalData: AAD }, key, sealed);
    cachedDebugKey = new TextDecoder().decode(plain);
    return cachedDebugKey;
  } catch {
    cachedDebugKey = null;
    return null;
  }
}

function mapMusicDlQuality(quality: string): string {
  return quality === "16" ? "6" : "27";
}

function extractUrl(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  for (const key of ["url", "downloadUrl", "download_url", "streamUrl", "stream_url", "link"]) {
    const value = record[key];
    if (typeof value === "string" && value.startsWith("http")) return value;
  }
  return null;
}

/** Public MusicDL Qobuz relay — uses embedded debug key (no user account). */
export async function streamViaMusicDlPublic(trackId: string, quality: string): Promise<StreamResult | null> {
  const debugKey = await getMusicDlDebugKey();
  if (!debugKey) return null;

  const qobuzQuality = mapMusicDlQuality(quality);
  const payload = await fetchJson(MUSICDL_QOBUZ_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Debug-Key": debugKey,
    },
    body: JSON.stringify({
      url: `https://open.qobuz.com/track/${trackId}`,
      quality: qobuzQuality,
    }),
  });

  const url = extractUrl(payload);
  if (!url) return null;

  const bitrate = inferBitrateKbps(qobuzQuality === "27" ? "24-bit" : "16-bit", "flac");
  return {
    url,
    streamUrl: url,
    format: "flac",
    quality: qualityLabelFromBitrate(bitrate, "flac"),
    mimeType: "audio/flac",
    bitrateKbps: bitrate,
    provider: "qobuz",
  };
}
