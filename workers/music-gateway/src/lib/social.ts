import type {
  ActivityEventDto,
  ActivityFeedDto,
  FriendsListDto,
  LibrarySnapshotDto,
} from "../sync/types";
import type { Env } from "../types";
import { kvWritesEnabled } from "./cache";

const ACTIVITY_TTL_SECONDS = 86_400; // 1 day
const ACTIVITY_MIN_INTERVAL_MS = 30_000;
const activityLastWrite = new Map<string, number>();

function friendsKey(userId: string): string {
  return `friends:${userId.trim().toLowerCase()}`;
}

function activityKey(userId: string): string {
  return `activity:${userId.trim().toLowerCase()}`;
}

function libraryKey(userId: string): string {
  return `library:${userId.trim().toLowerCase()}`;
}

function normalizeUserId(userId: string): string {
  return userId.trim().toLowerCase();
}

async function safeGet(kv: KVNamespace | undefined, key: string): Promise<string | null> {
  if (!kv) return null;
  try {
    return await kv.get(key);
  } catch (err) {
    console.warn("VANTA_SOCIAL_KV_READ_ERROR", JSON.stringify({ key, error: err instanceof Error ? err.message : String(err) }));
    return null;
  }
}

async function safePut(
  kv: KVNamespace | undefined,
  key: string,
  value: string,
  options?: KVNamespacePutOptions,
  writesEnabled = true
): Promise<boolean> {
  if (!kv || !writesEnabled) return false;
  try {
    await kv.put(key, value, options);
    return true;
  } catch (err) {
    console.warn("VANTA_SOCIAL_KV_WRITE_ERROR", JSON.stringify({ key, error: err instanceof Error ? err.message : String(err) }));
    return false;
  }
}

export function aggregateFriendActivity(
  events: (ActivityEventDto | null)[],
  limit: number
): ActivityEventDto[] {
  const safeLimit = Math.max(1, Math.min(limit, 100));
  return events
    .filter((event): event is ActivityEventDto => event != null)
    .sort((a, b) => b.startedAtMs - a.startedAtMs)
    .slice(0, safeLimit);
}

export async function getFriendIds(env: Env, userId: string): Promise<string[]> {
  const raw = await safeGet(env.SOCIAL_KV, friendsKey(userId));
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((value): value is string => typeof value === "string")
      .map((value) => normalizeUserId(value))
      .filter((value, index, all) => value.length > 0 && all.indexOf(value) === index);
  } catch {
    return [];
  }
}

export async function addFriend(env: Env, userId: string, friendId: string): Promise<FriendsListDto> {
  const ownerId = normalizeUserId(userId);
  const targetId = normalizeUserId(friendId);
  if (!ownerId || !targetId || ownerId === targetId) {
    return { friendIds: await getFriendIds(env, ownerId) };
  }
  if (!env.SOCIAL_KV) {
    return { friendIds: [] };
  }

  const friendIds = await getFriendIds(env, ownerId);
  if (!friendIds.includes(targetId)) {
    friendIds.push(targetId);
    await safePut(env.SOCIAL_KV, friendsKey(ownerId), JSON.stringify(friendIds), undefined, kvWritesEnabled(env));
  }

  // Symmetric friendship for MVP cross-device feeds.
  const reverseIds = await getFriendIds(env, targetId);
  if (!reverseIds.includes(ownerId)) {
    reverseIds.push(ownerId);
    await safePut(env.SOCIAL_KV, friendsKey(targetId), JSON.stringify(reverseIds), undefined, kvWritesEnabled(env));
  }

  return { friendIds };
}

export async function getActivity(env: Env, userId: string): Promise<ActivityEventDto | null> {
  const raw = await safeGet(env.SOCIAL_KV, activityKey(userId));
  if (!raw) return null;
  try {
    return JSON.parse(raw) as ActivityEventDto;
  } catch {
    return null;
  }
}

export async function putActivity(env: Env, userId: string, event: ActivityEventDto): Promise<void> {
  const ownerId = normalizeUserId(userId);
  const lastWrite = activityLastWrite.get(ownerId) ?? 0;
  const now = Date.now();
  if (now - lastWrite < ACTIVITY_MIN_INTERVAL_MS) {
    console.log("VANTA_SOCIAL_RATE_LIMIT", JSON.stringify({ userId: ownerId, deltaMs: now - lastWrite }));
    return;
  }
  activityLastWrite.set(ownerId, now);
  const normalized = {
    ...event,
    userId: ownerId,
  };
  await safePut(env.SOCIAL_KV, activityKey(normalized.userId), JSON.stringify(normalized), {
    expirationTtl: ACTIVITY_TTL_SECONDS,
  }, kvWritesEnabled(env));
}

export async function getFriendActivityFeed(
  env: Env,
  userId: string,
  limit: number
): Promise<ActivityFeedDto> {
  const friendIds = await getFriendIds(env, userId);
  const events = await Promise.all(friendIds.map((friendId) => getActivity(env, friendId)));
  const aggregated = aggregateFriendActivity(events, limit);
  return {
    events: aggregated,
    updatedAtMs: Date.now(),
  };
}

export async function getLibrarySnapshot(
  env: Env,
  userId: string,
  sinceMs?: number
): Promise<LibrarySnapshotDto | null> {
  const raw = await safeGet(env.SOCIAL_KV, libraryKey(userId));
  if (!raw) return null;
  try {
    const snapshot = JSON.parse(raw) as LibrarySnapshotDto;
    if (sinceMs != null && Number.isFinite(sinceMs) && snapshot.generatedAtMs <= sinceMs) {
      return {
        vantaUserId: snapshot.vantaUserId,
        deviceName: snapshot.deviceName,
        version: snapshot.version ?? 1,
        generatedAtMs: snapshot.generatedAtMs,
        tracks: [],
        likedTrackIds: [],
        recentPlayedTrackIds: [],
      };
    }
    return snapshot;
  } catch {
    return null;
  }
}

export async function putLibrarySnapshot(
  env: Env,
  userId: string,
  snapshot: LibrarySnapshotDto
): Promise<{ snapshotId: string; serverTracksMerged: number }> {
  const ownerId = normalizeUserId(userId);
  const snapshotId = `${ownerId}:${snapshot.generatedAtMs}`;
  if (!env.SOCIAL_KV) {
    return { snapshotId, serverTracksMerged: snapshot.tracks?.length ?? 0 };
  }

  const existing = await getLibrarySnapshot(env, ownerId);
  const mergedTracks = mergeTracks(existing?.tracks ?? [], snapshot.tracks ?? []);
  const merged: LibrarySnapshotDto = {
    ...snapshot,
    vantaUserId: ownerId,
    tracks: mergedTracks,
    likedTrackIds: mergeUnique(existing?.likedTrackIds ?? [], snapshot.likedTrackIds ?? []),
    recentPlayedTrackIds: mergeUnique(
      snapshot.recentPlayedTrackIds ?? [],
      existing?.recentPlayedTrackIds ?? []
    ).slice(0, 50),
  };

  await safePut(env.SOCIAL_KV, libraryKey(ownerId), JSON.stringify(merged), undefined, kvWritesEnabled(env));
  return { snapshotId, serverTracksMerged: mergedTracks.length };
}

function mergeUnique(...lists: string[][]): string[] {
  const seen = new Set<string>();
  const merged: string[] = [];
  for (const list of lists) {
    for (const value of list) {
      const normalized = value.trim();
      if (!normalized || seen.has(normalized)) continue;
      seen.add(normalized);
      merged.push(normalized);
    }
  }
  return merged;
}

function mergeTracks(
  existing: LibrarySnapshotDto["tracks"],
  incoming: LibrarySnapshotDto["tracks"]
): NonNullable<LibrarySnapshotDto["tracks"]> {
  const byId = new Map<string, NonNullable<LibrarySnapshotDto["tracks"]>[number]>();
  for (const track of existing ?? []) {
    byId.set(track.vantaTrackId, track);
  }
  for (const track of incoming ?? []) {
    const prior = byId.get(track.vantaTrackId);
    byId.set(track.vantaTrackId, prior ? { ...prior, ...track } : track);
  }
  return Array.from(byId.values());
}
