import type { GatewayTrack } from "../types";
import { getAppleDevToken, normalizeAppleStorefront, appleSongToGatewayTrack } from "./apple-music";

/**
 * Apple Music editorial rows for the VANTA home feed.
 *
 * Upstream is the public Apple Music Web API catalog with the minted dev
 * token (same source as search): charts for editorial playlists and popular
 * songs, plus a "new music" playlist when Apple publishes one for the
 * storefront. Track entries carry ISRC + apple_id so the app resolves actual
 * playback through the audio providers — Apple nodes are metadata-only.
 */

const APPLE_BASE = "https://api.music.apple.com";

export interface AppleEditorialPlaylist {
  id: string;
  name: string;
  curator: string;
  description: string;
  artworkURL?: string;
}

export interface AppleEditorialRows {
  storefront: string;
  playlists: AppleEditorialPlaylist[];
  freshDrops: GatewayTrack[];
  popularTracks: GatewayTrack[];
}

const CACHE_TTL_MS = 60 * 60 * 1000;
let cache: { rows: AppleEditorialRows; expiresAt: number } | null = null;
let cachedNewMusicPlaylistId: string | null = null;

interface ChartsPayload {
  results?: Record<string, unknown>;
}

async function appleCatalogCharts(storefront: string, types: string, limit: number): Promise<Record<string, Array<Record<string, unknown>>> | null> {
  const mint = await getAppleDevToken();
  if (!mint) return null;
  const url = `${APPLE_BASE}/v1/catalog/${storefront}/charts?${new URLSearchParams({
    types,
    limit: String(Math.min(Math.max(limit, 1), 100)),
    "extend[playlists]": "curatorName",
  })}`;
  let res: Response;
  try {
    res = await fetch(url, { headers: { Authorization: `Bearer ${mint.token}`, Origin: "https://monochrome.tf", Accept: "application/json" }, cf: { cacheTtl: 1800 } as never });
  } catch {
    return null;
  }
  if (!res.ok) {
    console.log("VANTA_APPLE_CHARTS", JSON.stringify({ status: res.status, storefront, types }));
    return null;
  }
  try {
    const payload = (await res.json()) as ChartsPayload;
    const out: Record<string, Array<Record<string, unknown>>> = {};
    for (const [type, group] of Object.entries(payload.results ?? {})) {
      // Charts responses wrap per-chart segments: {chart: string, data: [...]}.
      const segments = Array.isArray(group) ? group : [group];
      for (const segment of segments) {
        const entry = segment as { chart?: string; data?: Array<Record<string, unknown>> } | null;
        const items = Array.isArray(entry?.data) ? entry.data : [];
        if (items.length > 0) {
          const existing = out[type] ?? [];
          out[type] = [...existing, ...items];
        }
      }
    }
    return out;
  } catch (err) {
    console.log("VANTA_APPLE_CHARTS", JSON.stringify({ storefront, types, status: "parse_error", error: err instanceof Error ? err.message : String(err) }));
    return null;
  }
}

export function playlistToEditorialCard(item: Record<string, unknown>): AppleEditorialPlaylist | null {
  const attrs = item.attributes as Record<string, unknown> | undefined;
  const name = typeof attrs?.playlistName === "string" ? attrs.playlistName : typeof attrs?.name === "string" ? attrs.name : "";
  if (!name) return null;
  const artwork = attrs?.artwork as Record<string, unknown> | undefined;
  const rawArt = typeof artwork?.url === "string" ? artwork.url : undefined;
  if (!rawArt) return null;
  return {
    id: String(item.id ?? ""),
    name,
    curator: typeof attrs?.curatorName === "string" ? attrs.curatorName : "Apple Music",
    description: typeof attrs?.description === "string" ? attrs.description.replace(/<[^>]*>/g, "").trim() : "",
    artworkURL: rawArt.replace("{w}", "600").replace("{h}", "600").replace("{f}", "jpg"),
  };
}

/** Exact exhibition name match for the rolling new-music playlist. */
function listEditorial(item: Record<string, unknown>): boolean {
  const attrs = item.attributes as Record<string, unknown> | undefined;
  const name = (attrs?.playlistName ?? attrs?.name ?? "").toString().toLowerCase();
  return name.includes("new music") && !name.includes("presents") && name !== "electronic daily";
}

/** Find Apple's rolling "new music" playlist for the storefront, if charted. */
export function findNewMusicPlaylist(playlists: Record<string, unknown>[]): Record<string, unknown> | null {
  for (const item of playlists) {
    const attrs = item.attributes as Record<string, unknown> | undefined;
    const name = (attrs?.playlistName ?? attrs?.name ?? "").toString().toLowerCase();
    if (name.includes("new music") || name.includes("new releases") || name.includes("fresh")) return item;
  }
  return null;
}

/** Tracks of any Apple editorial playlist by id (playback resolved via ISRC elsewhere). */
export async function applePlaylistTracks(storefront: string, playlistId: string, limit = 100): Promise<GatewayTrack[]> {
  const mint = await getAppleDevToken();
  if (!mint) return [];
  const cap = Math.min(Math.max(limit, 1), 300);
  const tracks: GatewayTrack[] = [];
  let offset = 0;
  const pageSize = 100;

  while (tracks.length < cap) {
    const pageLimit = Math.min(pageSize, cap - tracks.length);
    const url =
      `${APPLE_BASE}/v1/catalog/${storefront}/playlists/${encodeURIComponent(playlistId)}/tracks` +
      `?limit=${pageLimit}&offset=${offset}`;
    try {
      const res = await fetch(url, {
        headers: { Authorization: `Bearer ${mint.token}`, Origin: "https://monochrome.tf", Accept: "application/json" },
        cf: { cacheTtl: 3600 } as never,
      });
      if (!res.ok) {
        console.log("VANTA_APPLE_PLAYLIST", JSON.stringify({ status: res.status, playlistId, storefront, offset }));
        break;
      }
      const payload = (await res.json()) as {
        data?: Array<{ id?: string; attributes?: Record<string, unknown> }>;
        next?: string | null;
      };
      const page = (payload.data ?? [])
        .map((t) => appleSongToGatewayTrack({ id: t.id, attributes: t.attributes }, storefront))
        .filter((track): track is GatewayTrack => !!track);
      if (page.length === 0) break;
      tracks.push(...page);
      if (!payload.next || page.length < pageLimit) break;
      offset += page.length;
      if (offset > 500) break;
    } catch {
      break;
    }
  }
  return tracks.slice(0, cap);
}

export async function appleEditorialRows(requestedStorefront?: string, limit = 12): Promise<AppleEditorialRows | null> {
  const now = Date.now();
  if (cache && cache.expiresAt > now && !requestedStorefront) return cache.rows;

  const mint = await getAppleDevToken();
  const storefront = normalizeAppleStorefront(requestedStorefront) ?? mint?.storefront ?? "us";
  if (!mint) {
    console.log("VANTA_APPLE_CHARTS", JSON.stringify({ status: "no_mint_token", storefront }));
    return null;
  }

  const charted = await appleCatalogCharts(storefront, "playlists,songs", limit);
  const playlistEntries = charted?.playlists ?? [];
  const songEntries = charted?.songs ?? [];

  const playlists = playlistEntries
    .map(playlistToEditorialCard)
    .filter((card): card is AppleEditorialPlaylist => !!card)
    .slice(0, limit);

  const tracksFrom = (entries: Array<Record<string, unknown>>): GatewayTrack[] =>
    entries
      .map((entry) => appleSongToGatewayTrack(entry, storefront))
      .filter((track): track is GatewayTrack => !!track);

  const popularTracks = tracksFrom(songEntries).slice(0, limit);

  let freshDrops: GatewayTrack[] = [];
  const newMusic = findNewMusicPlaylist(playlistEntries);
  if (newMusic) {
    const id = String(newMusic.id ?? "");
    cachedNewMusicPlaylistId = id || null;
  }
  if (!newMusic && !cachedNewMusicPlaylistId) {
    // The most-played chart does not always feature Apple's rolling "New
    // Music Daily" editorial playlist; resolve it by catalog search once.
    const mintSearch = await getAppleDevToken();
    if (mintSearch) {
      try {
        const res = await fetch(`${APPLE_BASE}/v1/catalog/${storefront}/search?${new URLSearchParams({ term: "new music daily", types: "playlists", limit: "5" })}`, {
          headers: { Authorization: `Bearer ${mintSearch.token}`, Origin: "https://monochrome.tf", Accept: "application/json" },
          cf: { cacheTtl: 86400 } as never,
        });
        if (res.ok) {
          const payload = (await res.json()) as { results?: { playlists?: { data?: Array<Record<string, unknown>> } } };
          const items = (payload.results?.playlists?.data ?? []).filter(listEditorial);
          if (items.length > 0) cachedNewMusicPlaylistId = String(items[0].id ?? "") || null;
          console.log("VANTA_APPLE_NEWMUSIC", JSON.stringify({ status: res.status, items: items.length, id: cachedNewMusicPlaylistId }));
        } else {
          console.log("VANTA_APPLE_NEWMUSIC", JSON.stringify({ status: res.status }));
        }
      } catch {
        // keep null: Fresh Drops stays empty this round
      }
    }
  }
  const newMusicId = newMusic ? String(newMusic.id ?? "") : cachedNewMusicPlaylistId;
  if (newMusicId) {
    freshDrops = (await applePlaylistTracks(storefront, newMusicId, limit)).slice(0, limit);
  }

  const rows: AppleEditorialRows = { storefront, playlists, freshDrops, popularTracks };
  console.log("VANTA_APPLE_CHARTS", JSON.stringify({
    storefront,
    playlists: playlists.length,
    playlistChartEntries: playlistEntries.length,
    songEntries: songEntries.length,
    popularTracks: popularTracks.length,
    freshDrops: freshDrops.length,
  }));
  if (!requestedStorefront) {
    cache = { rows, expiresAt: now + CACHE_TTL_MS };
  }
  return rows;
}
