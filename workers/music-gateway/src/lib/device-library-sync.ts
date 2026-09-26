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

const inMemorySyncMap = new Map<string, DeviceLibraryPayload>();

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

  // 1. Keep in memory for instant local retrieval
  inMemorySyncMap.set(code, body);

  // 2. Primary: Durable Object SQLite storage (unlimited writes, no daily KV quota)
  if (env.EXTENSION_SESSIONS) {
    try {
      const id = env.EXTENSION_SESSIONS.idFromName("device-sync");
      const stub = env.EXTENSION_SESSIONS.get(id);
      await (stub as any).saveDeviceLibrary(body);
    } catch (e) {
      console.warn("DO device-sync save failed", e);
    }
  }

  // 3. Secondary: Cloudflare KV (swallow daily write quota error safely)
  if (env.SOCIAL_KV && kvWritesEnabled(env)) {
    try {
      await env.SOCIAL_KV.put(keyFor(code), JSON.stringify(body), { expirationTtl: TTL_SECONDS });
    } catch (e) {
      console.warn("KV device-sync save failed (likely quota limit)", e);
    }
  }

  return body;
}

export async function getDeviceLibrary(env: Env, pairCode: string): Promise<DeviceLibraryPayload | null> {
  if (!isValidPairCode(pairCode)) return null;
  const code = normalizeCode(pairCode);

  // 1. Primary: Durable Object SQLite storage
  if (env.EXTENSION_SESSIONS) {
    try {
      const id = env.EXTENSION_SESSIONS.idFromName("device-sync");
      const stub = env.EXTENSION_SESSIONS.get(id);
      const res = await (stub as any).getDeviceLibrary(code);
      if (res && Array.isArray((res as any).tracks)) {
        return res as DeviceLibraryPayload;
      }
    } catch (e) {
      console.warn("DO device-sync get failed", e);
    }
  }

  // 2. Secondary: Cloudflare KV
  if (env.SOCIAL_KV) {
    try {
      const raw = await env.SOCIAL_KV.get(keyFor(code));
      if (raw) return JSON.parse(raw) as DeviceLibraryPayload;
    } catch {}
  }

  // 3. Fallback: in-memory
  if (inMemorySyncMap.has(code)) {
    return inMemorySyncMap.get(code)!;
  }

  return null;
}

