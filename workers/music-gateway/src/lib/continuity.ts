import type { Env } from "../types";
import { kvWritesEnabled } from "./cache";

const DEVICE_TTL_SECONDS = 45;
const SESSION_TTL_SECONDS = 86_400;
const ONLINE_WINDOW_MS = 15_000;

export type ContinuityMode = "independent" | "cast" | "follow";
export type ContinuityRole = "phone" | "tv" | "other";

export interface ContinuityDeviceDto {
  deviceId: string;
  role: ContinuityRole;
  name: string;
  lastSeenAtMs: number;
}

export interface ContinuityTrackRefDto {
  trackId?: string;
  title?: string;
  artist?: string;
  album?: string;
  artworkUrl?: string;
  providerId?: string;
  externalTrackId?: string;
  isrc?: string;
}

export interface ContinuitySessionDto {
  mode: ContinuityMode;
  leaderDeviceId: string | null;
  followerDeviceIds: string[];
  track: ContinuityTrackRefDto | null;
  positionMs: number;
  isPlaying: boolean;
  updatedAtMs: number;
  seq: number;
}

export interface ContinuitySnapshotDto {
  devices: ContinuityDeviceDto[];
  session: ContinuitySessionDto;
}

function devicesKey(userId: string): string {
  return `continuity:devices:${userId.trim().toLowerCase()}`;
}

function sessionKey(userId: string): string {
  return `continuity:session:${userId.trim().toLowerCase()}`;
}

function normalizeUserId(userId: string): string {
  return userId.trim().toLowerCase();
}

async function safeGet(kv: KVNamespace | undefined, key: string): Promise<string | null> {
  if (!kv) return null;
  try {
    return await kv.get(key);
  } catch {
    return null;
  }
}

async function safePut(
  kv: KVNamespace | undefined,
  key: string,
  value: string,
  ttlSeconds: number,
  writesEnabled: boolean
): Promise<boolean> {
  if (!kv || !writesEnabled) return false;
  try {
    await kv.put(key, value, { expirationTtl: ttlSeconds });
    return true;
  } catch {
    return false;
  }
}

function emptySession(): ContinuitySessionDto {
  return {
    mode: "independent",
    leaderDeviceId: null,
    followerDeviceIds: [],
    track: null,
    positionMs: 0,
    isPlaying: false,
    updatedAtMs: Date.now(),
    seq: 0,
  };
}

export async function getContinuitySnapshot(env: Env, userId: string): Promise<ContinuitySnapshotDto> {
  const ownerId = normalizeUserId(userId);
  const now = Date.now();
  const devicesRaw = await safeGet(env.SOCIAL_KV, devicesKey(ownerId));
  let devices: ContinuityDeviceDto[] = [];
  if (devicesRaw) {
    try {
      const parsed = JSON.parse(devicesRaw) as ContinuityDeviceDto[];
      if (Array.isArray(parsed)) {
        devices = parsed.filter(
          (d) =>
            d &&
            typeof d.deviceId === "string" &&
            d.deviceId.length > 0 &&
            now - (d.lastSeenAtMs || 0) <= ONLINE_WINDOW_MS
        );
      }
    } catch {
      devices = [];
    }
  }

  const sessionRaw = await safeGet(env.SOCIAL_KV, sessionKey(ownerId));
  let session = emptySession();
  if (sessionRaw) {
    try {
      const parsed = JSON.parse(sessionRaw) as ContinuitySessionDto;
      if (parsed && typeof parsed.mode === "string") {
        session = {
          ...emptySession(),
          ...parsed,
          followerDeviceIds: Array.isArray(parsed.followerDeviceIds) ? parsed.followerDeviceIds : [],
        };
      }
    } catch {
      session = emptySession();
    }
  }

  return { devices, session };
}

export async function putContinuityHeartbeat(
  env: Env,
  userId: string,
  device: { deviceId: string; role: ContinuityRole; name: string }
): Promise<ContinuitySnapshotDto> {
  const ownerId = normalizeUserId(userId);
  const deviceId = device.deviceId.trim();
  if (!deviceId) return getContinuitySnapshot(env, ownerId);

  const now = Date.now();
  const current = await getContinuitySnapshot(env, ownerId);
  const nextDevices = current.devices.filter((d) => d.deviceId !== deviceId && now - d.lastSeenAtMs <= ONLINE_WINDOW_MS);
  nextDevices.push({
    deviceId,
    role: device.role === "tv" || device.role === "phone" ? device.role : "other",
    name: (device.name || "VANTA").trim().slice(0, 64) || "VANTA",
    lastSeenAtMs: now,
  });

  await safePut(
    env.SOCIAL_KV,
    devicesKey(ownerId),
    JSON.stringify(nextDevices),
    DEVICE_TTL_SECONDS,
    kvWritesEnabled(env)
  );

  return { devices: nextDevices, session: current.session };
}

export async function putContinuitySession(
  env: Env,
  userId: string,
  patch: Partial<ContinuitySessionDto> & { deviceId?: string }
): Promise<ContinuitySessionDto> {
  const ownerId = normalizeUserId(userId);
  const current = await getContinuitySnapshot(env, ownerId);
  const prev = current.session;

  const mode = (patch.mode as ContinuityMode | undefined) ?? prev.mode;
  const next: ContinuitySessionDto = {
    mode: mode === "cast" || mode === "follow" || mode === "independent" ? mode : "independent",
    leaderDeviceId:
      mode === "independent"
        ? null
        : (patch.leaderDeviceId ?? prev.leaderDeviceId ?? patch.deviceId ?? null),
    followerDeviceIds:
      mode === "independent"
        ? []
        : Array.isArray(patch.followerDeviceIds)
          ? patch.followerDeviceIds
          : prev.followerDeviceIds,
    track: mode === "independent" ? null : (patch.track !== undefined ? patch.track : prev.track),
    positionMs:
      typeof patch.positionMs === "number" && Number.isFinite(patch.positionMs)
        ? Math.max(0, Math.floor(patch.positionMs))
        : prev.positionMs,
    isPlaying: typeof patch.isPlaying === "boolean" ? patch.isPlaying : prev.isPlaying,
    updatedAtMs: Date.now(),
    seq: (prev.seq || 0) + 1,
  };

  await safePut(
    env.SOCIAL_KV,
    sessionKey(ownerId),
    JSON.stringify(next),
    SESSION_TTL_SECONDS,
    kvWritesEnabled(env)
  );
  return next;
}
