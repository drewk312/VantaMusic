/**
 * Phone ↔ TV library share via a short pair code (no Firebase required).
 * Auth is the existing gateway API key. Stores a compact track list in SOCIAL_KV.
 */
import type { Env } from "../types";
import { kvWritesEnabled } from "./cache";

export interface DeviceSyncTrack {
  title: string;
  artist: string;
  album?: string | null;
  artworkUrl?: string | null;
  isFavorite?: boolean;
  providerId?: string | null;
  externalTrackId?: string | null;
  isrc?: string | null;
  durationMs?: number | null;
}

export interface DeviceLibraryPayload {
  pairCode: string;
  deviceName: string;
  updatedAtMs: number;
  tracks: DeviceSyncTrack[];
}

const TTL_SECONDS = 60 * 60 * 24 * 30; // 30 days

function normalizeCode(code: string): string {
  return code.trim().toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8);
}

function keyFor(code: string): string {
  return `device-lib:${normalizeCode(code)}`;
}

export function isValidPairCode(code: string): boolean {
  const normalized = normalizeCode(code);
  return normalized.length >= 4 && normalized.length <= 8;
}

export async function putDeviceLibrary(
  env: Env,
  payload: DeviceLibraryPayload
): Promise<DeviceLibraryPayload | null> {
  if (!isValidPairCode(payload.pairCode)) return null;
  const code = normalizeCode(payload.pairCode);
  const tracks = (payload.tracks ?? [])
    .filter((t) => typeof t.title === "string" && t.title.trim() && typeof t.artist === "string" && t.artist.trim())
    .slice(0, 2_000)
    .map((t) => ({
      title: t.title.trim().slice(0, 200),
      artist: t.artist.trim().slice(0, 200),
      album: t.album?.trim()?.slice(0, 200) || null,
      artworkUrl: t.artworkUrl?.trim()?.slice(0, 500) || null,
      isFavorite: Boolean(t.isFavorite),
      providerId: t.providerId?.trim()?.slice(0, 64) || null,
      externalTrackId: t.externalTrackId?.trim()?.slice(0, 128) || null,
      isrc: t.isrc?.trim()?.slice(0, 32) || null,
      durationMs: typeof t.durationMs === "number" && t.durationMs > 0 ? Math.floor(t.durationMs) : null,
    }));

  const body: DeviceLibraryPayload = {
    pairCode: code,
    deviceName: (payload.deviceName || "VANTA").trim().slice(0, 64),
    updatedAtMs: Date.now(),
    tracks,
  };

  if (!env.SOCIAL_KV || !kvWritesEnabled(env)) {
    return body;
  }
  try {
    await env.SOCIAL_KV.put(keyFor(code), JSON.stringify(body), { expirationTtl: TTL_SECONDS });
    return body;
  } catch {
    return null;
  }
}

export async function getDeviceLibrary(env: Env, pairCode: string): Promise<DeviceLibraryPayload | null> {
  if (!isValidPairCode(pairCode) || !env.SOCIAL_KV) return null;
  try {
    const raw = await env.SOCIAL_KV.get(keyFor(pairCode));
    if (!raw) return null;
    return JSON.parse(raw) as DeviceLibraryPayload;
  } catch {
    return null;
  }
}
