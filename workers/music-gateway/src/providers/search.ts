import type { Env, GatewayTrack, ProviderId } from "../types";
import { filterTracks } from "../lib/content-purity";
import { extractArtistsAndAlbums, rankTracks } from "../lib/search-rank";
import { searchQobuzPublic } from "./qobuz-api";
import { fetchJson } from "./shared";

export async function searchDeezer(query: string, limit = 25): Promise<GatewayTrack[]> {
  const url = `https://api.deezer.com/search?q=${encodeURIComponent(query)}&limit=${limit}`;
  const payload = (await fetchJson(url)) as {
    data?: Array<{
      id: number;
      title: string;
      link?: string;
      artist?: { name?: string };
      album?: { title?: string; id?: number; cover_medium?: string };
      duration?: number;
      track_position?: number;
      disk_number?: number;
      explicit_lyrics?: boolean;
      isrc?: string;
    }>;
  } | null;

  return (payload?.data ?? []).map((track) => ({
    id: String(track.id),
    title: track.title,
    artist: track.artist?.name ?? "Unknown Artist",
    album: track.album?.title,
    albumId: track.album?.id ? String(track.album.id) : undefined,
    artworkURL: track.album?.cover_medium,
    duration: track.duration,
    trackNumber: track.track_position,
    discNumber: track.disk_number,
    audioQuality: "16-bit / 44.1 kHz FLAC",
    isrc: track.isrc,
    format: "flac",
    explicit: track.explicit_lyrics ?? false,
    provider: "deezer" as ProviderId,
    deezer_id: String(track.id),
  }));
}

export async function searchQobuz(query: string, env: Env, limit = 25): Promise<GatewayTrack[]> {
  // Zero-config first: auto-scraped public Qobuz API creds (no user secrets).
  const publicResults = await searchQobuzPublic(query, limit);
  if (publicResults.length > 0) return publicResults;

  if (env.QOBUZ_APP_ID?.trim() && env.QOBUZ_AUTH_TOKEN?.trim()) {
    const url =
      `https://www.qobuz.com/api.json/0.2/catalog/search?query=${encodeURIComponent(query)}` +
      `&limit=${limit}&app_id=${encodeURIComponent(env.QOBUZ_APP_ID)}&user_auth_token=${encodeURIComponent(env.QOBUZ_AUTH_TOKEN)}`;
    const { fetchJson } = await import("./shared");
    const payload = (await fetchJson(url)) as { tracks?: { items?: Array<Record<string, unknown>> } } | null;
    if (payload?.tracks?.items?.length) {
      return payload.tracks.items.map((item) => mapQobuzTrack(item));
    }
  }
  return [];
}

function mapQobuzTrack(item: Record<string, unknown>): GatewayTrack {
  const performer = item.performer as { name?: string } | undefined;
  const album = item.album as { title?: string; id?: string | number; image?: { large?: string } } | undefined;
  const maximumBitDepth = typeof item.maximum_bit_depth === "number" ? item.maximum_bit_depth : 16;
  const maximumSamplingRate = typeof item.maximum_sampling_rate === "number" ? item.maximum_sampling_rate : 44.1;
  const id = String(item.id ?? "");

  const artistName =
    performer?.name ??
    (typeof item.artist === "object" && item.artist && "name" in item.artist
      ? String((item.artist as { name?: string }).name ?? "")
      : "");

  return {
    id,
    title: String(item.title ?? item.name ?? "Unknown"),
    artist: artistName || "Unknown Artist",
    album: album?.title ? String(album.title) : undefined,
    albumId: album?.id != null ? String(album.id) : undefined,
    artworkURL: album?.image?.large,
    duration: typeof item.duration === "number" ? item.duration : undefined,
    trackNumber: typeof item.track_number === "number" ? item.track_number : undefined,
    discNumber: typeof item.media_number === "number" ? item.media_number : undefined,
    audioQuality: `${maximumBitDepth}-bit / ${maximumSamplingRate} kHz FLAC`,
    isrc: typeof item.isrc === "string" ? item.isrc : undefined,
    format: "flac",
    explicit: Boolean(item.parental_warning),
    provider: "qobuz",
    qobuz_id: id,
  };
}

export async function searchAppleItunes(query: string, limit = 25): Promise<GatewayTrack[]> {
  const url =
    `https://itunes.apple.com/search?term=${encodeURIComponent(query)}` +
    `&entity=song&limit=${limit}&media=music`;

  const payload = (await fetchJson(url)) as {
    results?: Array<{
      trackId: number;
      trackName: string;
      artistName: string;
      collectionName?: string;
      collectionId?: number;
      artworkUrl100?: string;
      trackTimeMillis?: number;
      trackNumber?: number;
      discNumber?: number;
      trackExplicitness?: string;
    }>;
  } | null;

  return (payload?.results ?? []).map((track) => ({
    id: String(track.trackId),
    title: track.trackName,
    artist: track.artistName,
    album: track.collectionName,
    albumId: track.collectionId ? String(track.collectionId) : undefined,
    artworkURL: track.artworkUrl100?.replace("100x100bb", "600x600bb"),
    duration: track.trackTimeMillis ? Math.round(track.trackTimeMillis / 1000) : undefined,
    trackNumber: track.trackNumber,
    discNumber: track.discNumber,
    audioQuality: "AAC / Apple Music",
    format: "aac",
    explicit: track.trackExplicitness === "explicit",
    provider: "apple",
    apple_id: String(track.trackId),
  }));
}

export async function searchByProvider(
  provider: ProviderId,
  query: string,
  env: Env
): Promise<GatewayTrack[]> {
  switch (provider) {
    case "deezer":
      return searchDeezer(query);
    case "qobuz":
      return searchQobuz(query, env);
    case "apple":
      return searchAppleItunes(query);
    default:
      return [];
  }
}

export function dedupeTracks(tracks: GatewayTrack[]): GatewayTrack[] {
  const seen = new Set<string>();
  const result: GatewayTrack[] = [];

  for (const track of tracks) {
    const key = track.isrc?.toUpperCase() || `${track.provider ?? "x"}:${track.id}`;
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(track);
  }

  return result;
}

export async function searchAll(
  query: string,
  env: Env
): Promise<{
  tracks: GatewayTrack[];
  albums: unknown[];
  artists: unknown[];
  playlists: unknown[];
}> {
  const cacheKey = `search:${query.trim().toLowerCase()}`;
  if (env.CACHE) {
    const cached = await env.CACHE.get(cacheKey, "json") as {
      tracks: GatewayTrack[];
      albums: unknown[];
      artists: unknown[];
      playlists: unknown[];
    } | null;
    if (cached?.tracks?.length) {
      return cached;
    }
  }

  const providers = env.ENABLED_SEARCH_PROVIDERS.split(",")
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean) as ProviderId[];

  const batches = await Promise.all(providers.map((provider) => searchByProvider(provider, query, env)));
  let tracks = dedupeTracks(batches.flat());

  if (truthy(env.ENRICH_SEARCH_RESULTS) && tracks.length > 0) {
    const { enrichTracks } = await import("./enrich");
    tracks = await enrichTracks(tracks, env);
  }

  tracks = filterTracks(tracks, query);
  tracks = rankTracks(query, tracks).slice(0, 30);

  const { artists, albums } = extractArtistsAndAlbums(tracks);
  const payload = { tracks, albums, artists, playlists: [] as unknown[] };

  if (env.CACHE) {
    await env.CACHE.put(cacheKey, JSON.stringify(payload), { expirationTtl: 300 });
  }

  return payload;
}

function truthy(value: string | undefined): boolean {
  if (!value) return false;
  const normalized = value.trim().toLowerCase();
  return normalized === "1" || normalized === "true" || normalized === "yes" || normalized === "on";
}
