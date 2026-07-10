import { badRequest, json, readJson } from "../http";
import { authenticateSyncRequest, type SyncIdentity } from "../auth";
import {
  addFriend,
  getFriendActivityFeed,
  getFriendIds,
  getLibrarySnapshot,
  putActivity,
  putLibrarySnapshot,
} from "../lib/social";
import type { Env } from "../types";
import type {
  ActivityEventDto,
  AddFriendBody,
  FriendsListDto,
  LibrarySnapshotDto,
} from "./types";

const SYNC_PATH = /^\/sync\/(activity|library|friends)\/([^/]+)$/;

export async function handleSyncRoute(
  request: Request,
  env: Env,
  pathname: string,
  rateHeaders: Record<string, string>,
  identity?: SyncIdentity
): Promise<Response | null> {
  const match = pathname.match(SYNC_PATH);
  if (!match) return null;

  const authentication = identity
    ? { ok: true as const, identity }
    : await authenticateSyncRequest(request, env);
  if (!authentication.ok) {
    return json({ error: authentication.error }, authentication.status, rateHeaders);
  }

  const resource = match[1];
  let userId: string;
  try {
    userId = decodeURIComponent(match[2]);
  } catch (err) {
    return withHeaders(badRequest("invalid userId encoding"), rateHeaders);
  }

  if (!userId.trim()) {
    return withHeaders(badRequest("missing userId"), rateHeaders);
  }
  if (userId.trim() !== authentication.identity.userId) {
    return json({ error: "sync_identity_mismatch" }, 403, rateHeaders);
  }

  const canonicalUserId = authentication.identity.userId;

  if (resource === "activity") {
    return handleActivityRoute(request, env, canonicalUserId, rateHeaders);
  }
  if (resource === "library") {
    return handleLibraryRoute(request, env, canonicalUserId, rateHeaders);
  }
  return handleFriendsRoute(request, env, canonicalUserId, rateHeaders);
}

async function handleActivityRoute(
  request: Request,
  env: Env,
  userId: string,
  rateHeaders: Record<string, string>
): Promise<Response> {
  if (request.method === "POST") {
    const body = await readJson<ActivityEventDto>(request);
    if (!body?.trackId?.trim() || !body.title?.trim() || !body.artist?.trim()) {
      return withHeaders(badRequest("invalid activity payload"), rateHeaders);
    }
    if (body.userId?.trim() !== userId) {
      return json({ error: "sync_identity_mismatch" }, 403, rateHeaders);
    }
    await putActivity(env, userId, body);
    return emptyOk(rateHeaders);
  }

  if (request.method === "GET") {
    const url = new URL(request.url);
    const limitRaw = url.searchParams.get("limit");
    const parsedLimit = limitRaw ? Number.parseInt(limitRaw, 10) : 50;
    const limit = Number.isFinite(parsedLimit) ? parsedLimit : 50;
    const feed = await getFriendActivityFeed(env, userId, limit);
    return json(feed, 200, rateHeaders);
  }

  return withHeaders(badRequest("method_not_allowed"), rateHeaders);
}

async function handleLibraryRoute(
  request: Request,
  env: Env,
  userId: string,
  rateHeaders: Record<string, string>
): Promise<Response> {
  if (request.method === "POST") {
    const body = await readJson<LibrarySnapshotDto>(request);
    if (!body?.vantaUserId?.trim() || !body.deviceName?.trim()) {
      return withHeaders(badRequest("invalid library snapshot"), rateHeaders);
    }
    if (body.vantaUserId.trim() !== userId) {
      return json({ error: "sync_identity_mismatch" }, 403, rateHeaders);
    }
    const result = await putLibrarySnapshot(env, userId, body);
    return json(
      {
        snapshotId: result.snapshotId,
        serverTracksMerged: result.serverTracksMerged,
        conflicts: 0,
        nextSyncAtMs: Date.now() + 3_600_000,
      },
      200,
      rateHeaders
    );
  }

  if (request.method === "GET") {
    const url = new URL(request.url);
    const sinceRaw = url.searchParams.get("since");
    const sinceMs = sinceRaw ? Number.parseInt(sinceRaw, 10) : undefined;
    const snapshot =
      (await getLibrarySnapshot(env, userId, sinceMs)) ??
      ({
        vantaUserId: userId,
        deviceName: "unknown",
        version: 1,
        generatedAtMs: 0,
        tracks: [],
        likedTrackIds: [],
        recentPlayedTrackIds: [],
      } satisfies LibrarySnapshotDto);
    return json(snapshot, 200, rateHeaders);
  }

  return withHeaders(badRequest("method_not_allowed"), rateHeaders);
}

async function handleFriendsRoute(
  request: Request,
  env: Env,
  userId: string,
  rateHeaders: Record<string, string>
): Promise<Response> {
  if (request.method === "POST") {
    const body = await readJson<AddFriendBody>(request);
    if (!body?.friendId?.trim()) {
      return withHeaders(badRequest("missing friendId"), rateHeaders);
    }
    const result = await addFriend(env, userId, body.friendId);
    return json(result satisfies FriendsListDto, 200, rateHeaders);
  }

  if (request.method === "GET") {
    const friendIds = await getFriendIds(env, userId);
    return json({ friendIds } satisfies FriendsListDto, 200, rateHeaders);
  }

  return withHeaders(badRequest("method_not_allowed"), rateHeaders);
}

function withHeaders(response: Response, headers: Record<string, string>): Response {
  for (const [key, value] of Object.entries(headers)) {
    response.headers.set(key, value);
  }
  return response;
}

function emptyOk(extraHeaders: Record<string, string> = {}): Response {
  return new Response(null, {
    status: 200,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, X-Api-Key, User-Agent, Accept",
      ...extraHeaders,
    },
  });
}
