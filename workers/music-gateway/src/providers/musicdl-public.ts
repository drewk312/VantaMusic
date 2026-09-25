import type { Env, StreamResult } from "../types";
import { inferBitrateKbps, qualityLabelFromBitrate } from "../lib/stream-quality";
import { fetchJson } from "./shared";

const MUSICDL_QOBUZ_URL = "https://www.musicdl.me/api/qobuz/download";

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

/** Public MusicDL Qobuz relay — requires the MUSICDL_DEBUG_KEY operator secret (no user account). */
export async function streamViaMusicDlPublic(env: Env, trackId: string, quality: string): Promise<StreamResult | null> {
  const debugKey = env.MUSICDL_DEBUG_KEY?.trim();
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
