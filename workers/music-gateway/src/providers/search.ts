import type { Env, GatewayTrack, GatewayPlaylist, ProviderId } from "../types";
import { filterTracks } from "../lib/content-purity";
import { extractArtistsAndAlbums, foldIdentity, identityTitle, inferSearchIntent, primaryArtistName, rankTracks } from "../lib/search-rank";
import { mergeArtistCredits, splitArtistCredits } from "../lib/artist-credits";
import { isAllowedVocalTrack } from "../lib/vocal-recording";
import { searchQobuzPublic } from "./qobuz-api";
import { qobuzArtistLine } from "../lib/qobuz-artists";
import { fetchJson } from "./shared";
import { hasDolbyAtmosSignal, hasSpatialAudioSignal, hasSurroundSignal, isHiResSignal } from "../lib/stream-quality";
import { enrichSpatialFromSeed, isKnownSpatialTrackWithFormat, SPATIAL_SEED } from "../lib/spatial-seed";
import type { SpatialSeedEntry } from "../lib/spatial-seed";
import { searchCacheTtl } from "../lib/cache";
import { searchSoundCloud } from "./soundcloud";
import { searchAmazon } from "./amazon-catalog";
import { searchSpotify } from "./spotify-web";
import { getAppleDevToken } from "./apple-music";
import { playlistToEditorialCard } from "./apple-editorial";

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

  return (payload?.data ?? []).map((track) => {
    const credits = splitArtistCredits(track.artist?.name ?? "Unknown Artist");
    return {
      id: String(track.id),
      title: track.title,
      artist: credits.primary || track.artist?.name || "Unknown Artist",
      featuredArtists: credits.featured.length > 0 ? credits.featured : undefined,
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
    };
  });
}

export function mapDeezerPlaylistItem(item: {
  id?: number | string;
  title?: string;
  picture_xl?: string;
  picture_medium?: string;
  nb_tracks?: number;
  user?: { name?: string };
}): GatewayPlaylist | null {
  const id = String(item.id ?? "").trim();
  const name = (item.title ?? "").trim();
  if (!id || !name) return null;
  return {
    id,
    name,
    curator: item.user?.name?.trim() || "Deezer",
    artworkURL: item.picture_xl ?? item.picture_medium,
    trackCount: typeof item.nb_tracks === "number" ? item.nb_tracks : undefined,
    source: "deezer",
  };
}

export function playlistNameMatchesQuery(name: string, query: string): boolean {
  const tokens = query
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .split(/\s+/)
    .filter((token) => token.length >= 3);
  if (tokens.length === 0) return true;
  const haystack = name.toLowerCase();
  return tokens.every((token) => haystack.includes(token));
}

export function dedupeSearchPlaylists(playlists: GatewayPlaylist[]): GatewayPlaylist[] {
  const seenIds = new Set<string>();
  const seenNames = new Set<string>();
  const out: GatewayPlaylist[] = [];
  for (const playlist of playlists) {
    const idKey = `${playlist.source}:${playlist.id}`.toLowerCase();
    const nameKey = playlist.name.toLowerCase().replace(/\s+/g, " ").trim();
    if (seenIds.has(idKey) || (nameKey && seenNames.has(nameKey))) continue;
    seenIds.add(idKey);
    if (nameKey) seenNames.add(nameKey);
    out.push(playlist);
  }
  return out;
}

export async function searchDeezerPlaylists(query: string, limit = 12): Promise<GatewayPlaylist[]> {
  const url = `https://api.deezer.com/search/playlist?q=${encodeURIComponent(query)}&limit=${limit}`;
  const payload = (await fetchJson(url)) as {
    data?: Array<{
      id?: number | string;
      title?: string;
      picture_xl?: string;
      picture_medium?: string;
      nb_tracks?: number;
      user?: { name?: string };
    }>;
  } | null;
  return (payload?.data ?? []).map(mapDeezerPlaylistItem).filter((item): item is GatewayPlaylist => item != null);
}

export async function searchApplePlaylists(query: string, limit = 8): Promise<GatewayPlaylist[]> {
  const mint = await getAppleDevToken();
  if (!mint) return [];
  const params = new URLSearchParams({
    term: query,
    types: "playlists",
    limit: String(Math.min(Math.max(limit, 1), 10)),
    l: "en-US",
  });
  const url = `https://api.music.apple.com/v1/catalog/${mint.storefront}/search?${params}`;
  const payload = (await fetchJson(url, {
    headers: { Authorization: `Bearer ${mint.token}`, Origin: "https://monochrome.tf" },
  })) as { results?: { playlists?: { data?: Array<Record<string, unknown>> } } } | null;
  return (payload?.results?.playlists?.data ?? [])
    .map((item) => playlistToEditorialCard(item))
    .filter((card): card is NonNullable<typeof card> => card != null)
    .map((card) => ({
      id: card.id,
      name: card.name,
      curator: card.curator,
      artworkURL: card.artworkURL,
      description: card.description,
      source: "apple",
    }));
}

export async function searchCatalogPlaylists(query: string): Promise<GatewayPlaylist[]> {
  const trimmed = query.trim();
  if (trimmed.length < 2) return [];
  const [deezer, apple] = await Promise.all([
    searchDeezerPlaylists(trimmed).catch(() => [] as GatewayPlaylist[]),
    searchApplePlaylists(trimmed).catch(() => [] as GatewayPlaylist[]),
  ]);
  return dedupeSearchPlaylists(
    [...deezer, ...apple].filter((playlist) => playlistNameMatchesQuery(playlist.name, trimmed))
  ).slice(0, 12);
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
  const album = item.album as { title?: string; id?: string | number; image?: { large?: string }; release_date_original?: string; release_date_stream?: string } | undefined;
  const maximumBitDepth = typeof item.maximum_bit_depth === "number" ? item.maximum_bit_depth : 16;
  const maximumSamplingRate = typeof item.maximum_sampling_rate === "number" ? item.maximum_sampling_rate : 44.1;
  const id = String(item.id ?? "");
  const credits = splitArtistCredits(qobuzArtistLine(item));

  return {
    id,
    title: String(item.title ?? item.name ?? "Unknown"),
    artist: credits.primary || qobuzArtistLine(item),
    featuredArtists: credits.featured.length > 0 ? credits.featured : undefined,
    album: album?.title ? String(album.title) : undefined,
    albumId: album?.id != null ? String(album.id) : undefined,
    artworkURL: album?.image?.large,
    duration: typeof item.duration === "number" ? item.duration : undefined,
    releaseYear: releaseYear(album?.release_date_original ?? album?.release_date_stream),
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
    `&entity=song&limit=${limit}&media=music&country=US&lang=en_us`;

  const payload = (await fetchJson(url)) as {
    results?: Array<{
      trackId: number;
      trackName: string;
      artistName: string;
      collectionArtistName?: string;
      collectionName?: string;
      collectionId?: number;
      artworkUrl100?: string;
      trackTimeMillis?: number;
      trackNumber?: number;
      discNumber?: number;
      trackExplicitness?: string;
      releaseDate?: string;
    }>;
  } | null;

  return (payload?.results ?? []).map((track) => {
    const credits = splitArtistCredits(track.artistName, track.collectionArtistName);
    return {
      id: String(track.trackId),
      title: track.trackName,
      artist: credits.primary || track.collectionArtistName || track.artistName,
      featuredArtists: credits.featured.length > 0 ? credits.featured : undefined,
      album: track.collectionName,
      albumId: track.collectionId ? String(track.collectionId) : undefined,
      artworkURL: track.artworkUrl100?.replace("100x100bb", "600x600bb"),
      duration: track.trackTimeMillis ? Math.round(track.trackTimeMillis / 1000) : undefined,
      releaseYear: releaseYear(track.releaseDate),
      trackNumber: track.trackNumber,
      discNumber: track.discNumber,
      audioQuality: "AAC / Apple Music",
      format: "aac",
      explicit: track.trackExplicitness === "explicit",
      provider: "apple",
      apple_id: String(track.trackId),
    };
  });
}

async function searchDeezerIdentity(title: string, artist: string, limit = 25): Promise<GatewayTrack[]> {
  const escapedTitle = title.replace(/"/g, "").trim();
  const escapedArtist = artist.replace(/"/g, "").trim();
  return searchDeezer(`track:"${escapedTitle}" artist:"${escapedArtist}"`, limit);
}

async function searchMusicSuggestions(query: string): Promise<string[]> {
  const url =
    `https://suggestqueries.google.com/complete/search?client=firefox&ds=yt` +
    `&q=${encodeURIComponent(query)}`;
  const payload = (await fetchJson(url)) as [string, string[]] | null;
  return Array.isArray(payload?.[1])
    ? payload[1].filter((value): value is string => typeof value === "string" && value.trim().length > 0)
    : [];
}

const SUGGESTION_NOISE = new Set([
  "lyrics", "karaoke", "piano", "movie", "guitar", "slowed", "instrumental",
  "dance", "song", "cover", "live", "remix", "official", "audio", "video",
  "chapters", "scenes", "tabs", "chords", "reaction", "tutorial", "solo",
  "lesson", "lessons", "scene", "performance", "flash", "mob", "clean",
  "extended", "edit", "meme", "4k", "8d", "hd", "acoustic", "unplugged",
  "rehearsal", "super", "bowl", "halftime", "backing", "track", "bass",
  "drums", "transcription", "choir",
]);

function normalizeSuggestion(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

interface CatalogSuggestion {
  query: string;
  titleHint?: string;
  artistHint?: string;
}

/** Select a consensus correction, optionally retaining a structured artist suffix. */
function resolveCatalogSuggestion(query: string, suggestions: string[]): CatalogSuggestion {
  const normalized = suggestions.map(normalizeSuggestion).filter(Boolean);
  const base = normalized[0];
  if (!base) return { query: query.trim() };
  const supporting = normalized.filter((candidate) => candidate === base || candidate.startsWith(`${base} `));
  if (supporting.length < 2) return { query: query.trim() };

  const artistCandidates = supporting.slice(0, 4).filter((candidate) => {
    if (candidate === base) return false;
    const suffixWords = candidate.slice(base.length).trim().split(/\s+/).filter(Boolean);
    return suffixWords.length <= 4 && !suffixWords.some((word) => SUGGESTION_NOISE.has(word));
  });
  // Autocomplete ordering is relevance evidence. The first clean suffix is
  // much more likely to be the primary artist than a later, longer guest or
  // alternate performer (for example Adele before Lil Wayne).
  const selected = artistCandidates[0];
  if (!selected) return { query: base };
  return {
    query: selected,
    titleHint: base,
    artistHint: selected.slice(base.length).trim(),
  };
}

/** Backward-compatible string form used by tests and callers. */
export function chooseCatalogSuggestion(query: string, suggestions: string[]): string {
  return resolveCatalogSuggestion(query, suggestions).query;
}

function releaseYear(value: string | undefined): number | undefined {
  const match = value?.match(/^(\d{4})/);
  if (!match) return undefined;
  const year = Number(match[1]);
  return year >= 1900 && year <= new Date().getUTCFullYear() + 1 ? year : undefined;
}

export async function searchByProvider(
  provider: ProviderId,
  query: string,
  env: Env
): Promise<GatewayTrack[]> {
  switch (provider) {
    case "soundcloud":
      return searchSoundCloud(query, env);
    case "amazon":
      return searchAmazon(query);
    case "spotify":
      return searchSpotify(query, env);
    case "deezer":
      return searchDeezer(query);
    case "qobuz":
      return searchQobuz(query, env);
    case "apple": {
      // Apple catalog via am-mint (atmos traits + video covers) + iTunes fallback
      // Tie in THEIR servers — Apple Music Atmos detection via audioTraits
      const { searchAppleMusic } = await import("./apple-music");
      const [appleMusic, itunes] = await Promise.all([
        searchAppleMusic(query, 8).catch(() => []),
        searchAppleItunes(query, 8).catch(() => []),
      ]);
      // Merge, prefer Apple Music (has atmos traits)
      const seen = new Set<string>();
      const merged: GatewayTrack[] = [];
      for (const t of [...appleMusic, ...itunes]) {
        const key = `${t.title.toLowerCase()}|${t.artist.toLowerCase()}`;
        if (!seen.has(key)) {
          seen.add(key);
          merged.push(t);
        }
      }
      return merged;
    }
    case "tidal": {
      if (!env.HIFI_API_URL?.trim()) return [];
      const { searchTidalHifiApi } = await import("./hifi-api");
      return searchTidalHifiApi(query, env);
    }
    default:
      return [];
  }
}

export function dedupeTracks(tracks: GatewayTrack[], preferredArtist = ""): GatewayTrack[] {
  const byKey = new Map<string, GatewayTrack>();

  const normalizeIdentity = (value: string) => foldIdentity(value);
  const normalizedPreferredArtist = foldIdentity(primaryArtistName(preferredArtist));
  const providerWeight = (track: GatewayTrack) => {
    if (track.provider === "qobuz") return 40;
    if (track.provider === "deezer") return 30;
    if (track.provider === "apple") return 20;
    return 10;
  };
  const richness = (track: GatewayTrack) =>
    providerWeight(track) +
    (normalizedPreferredArtist && foldIdentity(primaryArtistName(track.artist ?? "")) === normalizedPreferredArtist ? 100 : 0) +
    (track.isrc ? 8 : 0) +
    (track.album ? 4 : 0) +
    (track.artworkURL ? 2 : 0) +
    (track.releaseYear ? 2 : 0) +
    (track.qobuz_id ? 6 : 0) +
    ((track.featuredArtists?.length ?? 0) > 0 ? 3 : 0);

  const adoptCredits = (winner: GatewayTrack, other: GatewayTrack): GatewayTrack => {
    const credits = mergeArtistCredits(winner, other);
    if (!credits.primary && credits.featured.length === 0) return winner;
    return {
      ...winner,
      artist: credits.primary || winner.artist,
      featuredArtists: credits.featured.length > 0 ? credits.featured : undefined,
    };
  };

  const sanitizeTrackCredits = (track: GatewayTrack): GatewayTrack => {
    const credits = mergeArtistCredits(track, track);
    return {
      ...track,
      artist: credits.primary || track.artist,
      featuredArtists: credits.featured.length > 0 ? credits.featured : undefined,
    };
  };

  for (const track of tracks) {
    const title = normalizeIdentity(identityTitle(track.title ?? ""));
    const artist = foldIdentity(primaryArtistName(track.artist ?? ""));
    const durationBucket = track.duration && track.duration > 0 ? Math.round(track.duration / 5) : "unknown";
    const identityKey = `${title}|${artist}|${durationBucket}`;
    const isrcKey = track.isrc?.trim() ? `isrc:${track.isrc.trim().toUpperCase()}` : null;
    const existing = (isrcKey && byKey.get(isrcKey)) || byKey.get(identityKey);

    if (!existing) {
      byKey.set(identityKey, track);
      if (isrcKey) byKey.set(isrcKey, track);
      continue;
    }

    const keepNew = richness(track) > richness(existing);
    const merged = keepNew ? adoptCredits(track, existing) : adoptCredits(existing, track);
    for (const [key, value] of byKey.entries()) {
      if (value === existing) byKey.delete(key);
    }
    byKey.set(identityKey, merged);
    if (isrcKey) byKey.set(isrcKey, merged);
  }

  return [...new Set(byKey.values())].map(sanitizeTrackCredits);
}

import { isFormatOnlyQuery } from "../lib/content-purity";
import { kvWritesEnabled } from "../lib/cache";

const SEARCH_CACHE_VERSION = "v20-featured-artists-clean";
const FORMAT_SEARCH_SUBREQUEST_BUDGET = 40;
const MAX_SIMULTANEOUS_PROVIDER_SEARCHES = 6;

function estimatedProviderSubrequests(provider: ProviderId): number {
  // Qobuz may need two credential-discovery requests, a catalog request, and
  // one authenticated fallback. Other configured search providers use one.
  return provider === "soundcloud" ? 6 : provider === "qobuz" || provider === "spotify" ? 4 : provider === "amazon" ? 2 : 1;
}

/** Maximum curated seeds that fit beneath the per-request fetch budget. */
export function formatSeedLimit(providers: ProviderId[]): number {
  if (providers.length === 0) return 0;
  const costPerSeed = providers.reduce(
    (total, provider) => total + estimatedProviderSubrequests(provider),
    0
  );
  return Math.max(1, Math.floor(FORMAT_SEARCH_SUBREQUEST_BUDGET / costPerSeed));
}

async function searchFormatSeeds(
  seedQueries: string[],
  providers: ProviderId[],
  env: Env
): Promise<GatewayTrack[]> {
  if (providers.length === 0 || seedQueries.length === 0) return [];
  const concurrentSeeds = Math.max(
    1,
    Math.floor(MAX_SIMULTANEOUS_PROVIDER_SEARCHES / providers.length)
  );
  const tracks: GatewayTrack[] = [];
  for (let offset = 0; offset < seedQueries.length; offset += concurrentSeeds) {
    const chunk = seedQueries.slice(offset, offset + concurrentSeeds);
    const chunkResults = await Promise.all(
      chunk.map(async (seedQuery) => {
        const batch = await Promise.all(
          providers.map((provider) => searchByProvider(provider, seedQuery, env))
        );
        return batch.flat();
      })
    );
    tracks.push(...chunkResults.flat());
  }
  return tracks;
}

export async function searchAll(
  query: string,
  env: Env
): Promise<{
  tracks: GatewayTrack[];
  albums: unknown[];
  artists: unknown[];
  playlists: GatewayPlaylist[];
}> {
  try {
    const cacheKey = `search:${SEARCH_CACHE_VERSION}:${env.ENABLED_SEARCH_PROVIDERS}:${query.trim().toLowerCase()}`;
    if (env.CACHE && kvWritesEnabled(env)) {
      try {
        const cached = await env.CACHE.get(cacheKey, "json") as {
          tracks: GatewayTrack[];
          albums: unknown[];
          artists: unknown[];
          playlists: GatewayPlaylist[];
        } | null;
        if (cached?.tracks?.length) {
          return cached;
        }
      } catch (err) {
        console.warn("VANTA_SEARCH_CACHE_READ_ERROR", JSON.stringify({ cacheKey, error: err instanceof Error ? err.message : String(err) }));
      }
    }

    const providers = env.ENABLED_SEARCH_PROVIDERS.split(",")
      .map((value) => value.trim().toLowerCase())
      .filter(Boolean) as ProviderId[];

    let tracks: GatewayTrack[] = [];
    const playlistsTask = isFormatOnlyQuery(query) ? Promise.resolve([] as GatewayPlaylist[]) : searchCatalogPlaylists(query);
    if (isFormatOnlyQuery(query)) {
      // Format-only queries ("Dolby Atmos", "spatial", "5.1", etc.) hit free APIs as metadata spam.
      // Fall back to curated seed list to surface real songs known to have spatial mixes.
      
      const normalized = query.toLowerCase().trim().replace(/-/g, " ");
      const matchesFormat = (entry: SpatialSeedEntry) => {
        if (normalized.includes("dolby atmos") || normalized === "atmos") return entry.spatialFormat === "dolby_atmos";
        if (normalized.includes("spatial audio") || normalized === "spatial") return ["dolby_atmos", "spatial_audio"].includes(entry.spatialFormat);
        if (normalized.includes("surround") || normalized === "5.1" || normalized === "7.1") return ["dolby_atmos", "spatial_audio", "surround"].includes(entry.spatialFormat);
        if (normalized.includes("hi res") || normalized.includes("hires") || normalized.includes("hi-res")) return entry.spatialFormat === "hi_res";
        if (normalized.includes("lossless") || normalized.includes("flac") || normalized.includes("dsd")) return entry.spatialFormat === "hi_res";
        return true;
      };
      const seedQueries = SPATIAL_SEED
        .filter(matchesFormat)
        .slice(0, formatSeedLimit(providers))
        .map((entry) => `${entry.title} ${entry.artist}`);
      const seedTracks = await searchFormatSeeds(seedQueries, providers, env);
      tracks = dedupeTracks(seedTracks).map(enrichSpatialFromSeed);
      // Keep only tracks that match the requested format according to the seed list.
      tracks = tracks.filter((track) => {
        const title = track.title ?? "";
        const artist = track.artist ?? "";
        if (normalized.includes("hi res") || normalized.includes("hires") || normalized.includes("lossless") || normalized.includes("flac")) {
          return isKnownSpatialTrackWithFormat(title, artist, "hi_res");
        }
        if (normalized.includes("dolby atmos") || normalized === "atmos") {
          return isKnownSpatialTrackWithFormat(title, artist, "dolby_atmos");
        }
        if (normalized.includes("spatial audio") || normalized === "spatial") {
          return isKnownSpatialTrackWithFormat(title, artist, ["dolby_atmos", "spatial_audio"]);
        }
        if (normalized.includes("surround") || normalized === "5.1" || normalized === "7.1") {
          return isKnownSpatialTrackWithFormat(title, artist, ["dolby_atmos", "spatial_audio", "surround"]);
        }
        return track.isDolbyAtmos || track.isSpatialAudio || track.isSurround || track.isHiRes;
      });
    } else {
      // Apple Search is a catalog-wide, typo-tolerant intent resolver. Query it
      // first, infer a title/artist identity from its relevance-ordered results,
      // then ask the lossless catalogs for that identity. This fixes arbitrary
      // misspellings and word-boundary errors without a hard-coded song list.
      const appleEnabled = providers.includes("apple");
      let discoveryTracks = appleEnabled ? await searchAppleItunes(query, 15).catch(() => []) : [];
      discoveryTracks = discoveryTracks.filter((track) => isAllowedVocalTrack(track, query));
      let intent = inferSearchIntent(query, discoveryTracks);
      let catalogTracks: GatewayTrack[] = [];
      const audioProviders = providers.filter(
        (provider) => provider !== "apple" && provider !== "soundcloud"
      );

      if (intent.kind === "open") {
        const suggestions = await searchMusicSuggestions(query).catch(() => []);
        const suggestion = resolveCatalogSuggestion(query, suggestions);
        const suggestedQuery = suggestion.query;
        const suggestionChanged = suggestedQuery.toLowerCase() !== query.trim().toLowerCase();
        // Preserve configured discovery order. Deezer/Apple relevance is more
        // useful for identity inference; Qobuz metadata is still preferred by
        // deduplication after the intended title/artist has been resolved.
        const firstPassProviders = audioProviders;
        // A raw typo can legitimately produce no Apple results. Retry Apple
        // once with the consensus correction and put its popularity-ordered
        // metadata ahead of noisier lossless catalogs for identity inference.
        // Playback still resolves through the lossless providers below.
        const [correctedAppleTracks, initialAudioBatches] = await Promise.all([
          suggestionChanged && appleEnabled ? searchAppleItunes(suggestedQuery, 15) : Promise.resolve([]),
          Promise.all(firstPassProviders.map((provider) =>
            provider === "deezer" && suggestion.titleHint && suggestion.artistHint
              ? searchDeezerIdentity(suggestion.titleHint, suggestion.artistHint)
              : searchByProvider(provider, suggestedQuery, env)
          )),
        ]);
        let firstPassBatches = initialAudioBatches;
        const inferredQuery = suggestionChanged ? suggestedQuery : query;
        discoveryTracks = [...correctedAppleTracks, ...firstPassBatches.flat()]
          .filter((track) => isAllowedVocalTrack(track, inferredQuery));
        const inferred = suggestion.titleHint && suggestion.artistHint
          ? {
              rawQuery: query,
              kind: "track" as const,
              title: suggestion.titleHint,
              artist: suggestion.artistHint,
              retrievalQuery: `${suggestion.titleHint} ${suggestion.artistHint}`,
              confidence: 1,
              corrected: true,
            }
          : inferSearchIntent(inferredQuery, discoveryTracks);
        intent = {
          ...inferred,
          rawQuery: query,
          corrected: inferred.corrected || suggestionChanged,
        };

        // If raw catalog results revealed a better title/artist (for example a
        // split-word typo), run one bounded refinement for the lossless sources.
        if (!suggestionChanged && intent.kind === "track" && intent.corrected) {
          const refinedProviders = audioProviders;
          firstPassBatches = await Promise.all(
            refinedProviders.map((provider) => searchByProvider(provider, intent.retrievalQuery, env))
          );
          discoveryTracks = firstPassBatches.flat().filter((track) => isAllowedVocalTrack(track, query));
          intent = inferSearchIntent(query, discoveryTracks);
        }
        catalogTracks = discoveryTracks;
      } else {
        const retrievalQuery = intent.retrievalQuery || query;
        const batches = await Promise.all(
          audioProviders.map((provider) => searchByProvider(provider, retrievalQuery, env))
        );
        catalogTracks = batches.flat();
      }

      console.log("VANTA_SEARCH_INTENT", JSON.stringify({
        query,
        discoveryCount: discoveryTracks.length,
        discoveryTop: discoveryTracks.slice(0, 3).map((track) => `${track.title} — ${track.artist}`),
        kind: intent.kind,
        title: intent.title ?? null,
        artist: intent.artist ?? null,
        retrievalQuery: intent.retrievalQuery,
        confidence: intent.confidence,
      }));
      tracks = dedupeTracks([...catalogTracks, ...discoveryTracks], intent.artist);

      if (truthy(env.ENRICH_SEARCH_RESULTS) && tracks.length > 0) {
        const { enrichTracks } = await import("./enrich");
        tracks = await enrichTracks(tracks, env);
      }

      tracks = tracks.map((track) => enrichSpatialFromSeed(track));
      tracks = filterTracks(tracks, query);
      tracks = rankTracks(query, tracks, intent).slice(0, 30);

      const { artists, albums } = extractArtistsAndAlbums(tracks, intent);
      const payload = { tracks, albums, artists, playlists: await playlistsTask.catch(() => [] as GatewayPlaylist[]) };

      if (env.CACHE) {
        try {
          await env.CACHE.put(cacheKey, JSON.stringify(payload), { expirationTtl: searchCacheTtl(env, payload.tracks.length) });
        } catch (err) {
          console.warn("VANTA_SEARCH_CACHE_WRITE_ERROR", JSON.stringify({ cacheKey, error: err instanceof Error ? err.message : String(err) }));
        }
      }

      return payload;
    }

    if (truthy(env.ENRICH_SEARCH_RESULTS) && tracks.length > 0) {
      const { enrichTracks } = await import("./enrich");
      tracks = await enrichTracks(tracks, env);
    }

    tracks = tracks.map((track) => enrichSpatialFromSeed(track));
    tracks = filterTracks(tracks, query);
    tracks = rankTracks(query, tracks).slice(0, 30);

    const { artists, albums } = extractArtistsAndAlbums(tracks);
    const payload = { tracks, albums, artists, playlists: await playlistsTask.catch(() => [] as GatewayPlaylist[]) };

    if (env.CACHE) {
      try {
        await env.CACHE.put(cacheKey, JSON.stringify(payload), { expirationTtl: searchCacheTtl(env, payload.tracks.length) });
      } catch (err) {
        console.warn("VANTA_SEARCH_CACHE_WRITE_ERROR", JSON.stringify({ cacheKey, error: err instanceof Error ? err.message : String(err) }));
      }
    }

    return payload;
  } catch (err) {
    console.error("VANTA_SEARCH_ERROR", JSON.stringify({ query, error: err instanceof Error ? err.message : String(err) }));
    return { tracks: [], albums: [], artists: [], playlists: [] };
  }
}

function truthy(value: string | undefined): boolean {
  if (!value) return false;
  const normalized = value.trim().toLowerCase();
  return normalized === "1" || normalized === "true" || normalized === "yes" || normalized === "on";
}








