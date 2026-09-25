import type { Env, GatewayTrack } from "../types";
import { appleEditorialRows, type AppleEditorialPlaylist } from "./apple-editorial";
import { spotifyEditorialPlaylists } from "./spotify-web";

/**
 * Aggregated Home feed for the VANTA app: one request assembles every free,
 * no-auth source we verified. Rows that can be personalized by the client are
 * computed there; everything upstream here is server-cached.
 */

export interface HomeRow {
  id: string;
  title: string;
  subtitle: string;
  kind: "editorial_text" | "playlist_cards" | "tracks";
  entries: Array<Record<string, unknown>>;
  tracks?: GatewayTrack[];
}

let cache: { payload: Record<string, unknown>; expiresAt: number } | null = null;
const CACHE_TTL_MS = 10 * 60 * 1000;

function deezerChartToTracks(payload: unknown, limit: number): GatewayTrack[] {
  const data = (payload as { data?: unknown[] })?.data;
  if (!Array.isArray(data)) return [];
  const tracks: GatewayTrack[] = [];
  for (const item of data.slice(0, limit)) {
    const entry = item as Record<string, unknown>;
    const id = entry.id != null ? String(entry.id) : "";
    const title = typeof entry.title_short === "string" && entry.title_short ? entry.title_short : (typeof entry.title === "string" ? entry.title : "");
    const artist = tostring((entry.artist as Record<string, unknown> | undefined)?.name);
    if (!id || !title || !artist) continue;
    const album = entry.album as Record<string, unknown> | undefined;
    tracks.push({
      id: `deezer:${id}`,
      title,
      artist,
      album: typeof album?.title === "string" ? album.title : undefined,
      artworkURL: typeof album?.cover_medium === "string" ? album.cover_medium : typeof album?.cover_big === "string" ? album.cover_big : undefined,
      duration: typeof entry.duration === "number" ? entry.duration : undefined,
      provider: "deezer",
      deezer_id: id,
    });
  }
  return tracks;
}

function tostring(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

async function deezerChartTracks(limit: number): Promise<GatewayTrack[]> {
  try {
    const res = await fetch(`https://api.deezer.com/chart/0/tracks?limit=${Math.min(Math.max(limit, 1), 50)}`, { cf: { cacheTtl: 1800 } as never });
    if (res.ok) return deezerChartToTracks(await res.json(), limit);
  } catch {
    // fall through
  }
  return [];
}

export async function buildHomeFeed(limit = 12, env?: Env): Promise<Record<string, unknown>> {
  const now = Date.now();
  if (cache && cache.expiresAt > now) return cache.payload;

  const [appleRows, spotifyCards, deezerTracks] = await Promise.all([
    appleEditorialRows(undefined, limit).catch(() => null),
    spotifyEditorialPlaylists(env, Math.min(limit, 10)).catch(() => []),
    deezerChartTracks(limit),
  ]);

  const playlistRows: AppleEditorialPlaylist[] = [
    ...(appleRows?.playlists ?? []),
    ...spotifyCards,
  ];
  const payload: Record<string, unknown> = {
    updatedAt: new Date(now).toISOString(),
    storefront: appleRows?.storefront ?? "us",
    playlists: playlistRows,
    freshDrops: appleRows?.freshDrops ?? [],
    popularTracks: appleRows?.popularTracks ?? [],
    trendingNow: deezerTracks,
  };
  cache = { payload, expiresAt: now + CACHE_TTL_MS };
  return payload;
}

