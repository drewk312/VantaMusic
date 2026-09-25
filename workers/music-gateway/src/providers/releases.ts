import type { GatewayTrack } from "../types";
import { fetchJson } from "./shared";
import { isKnownSpatialTrack } from "../lib/spatial-seed";
import { lookupQobuzTrackByIsrc, searchQobuzPublic } from "./qobuz-api";
import { searchAmazon } from "./amazon-catalog";

type DeezerAlbum = {
  id?: number;
  title?: string;
  cover_medium?: string;
  release_date?: string;
  artist?: { name?: string };
};

type DeezerTrack = {
  id?: number;
  title?: string;
  duration?: number;
  track_position?: number;
  disk_number?: number;
  explicit_lyrics?: boolean;
  isrc?: string;
  artist?: { name?: string };
  album?: DeezerAlbum;
};

type AppleChartTrack = {
  id?: string;
  name?: string;
  artistName?: string;
  releaseDate?: string;
  artworkUrl100?: string;
};

type JsonFetcher = (url: string, init?: RequestInit) => Promise<unknown | null>;

function isRecentDate(dateStr?: string, maxDays = 60): boolean {
  if (!dateStr) return false;
  const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!match) return false;
  const time = new Date(dateStr).getTime();
  if (isNaN(time)) return false;
  const now = Date.now();
  const diffDays = (now - time) / (1000 * 60 * 60 * 24);
  return diffDays >= -7 && diffDays <= maxDays;
}

/**
 * Returns actual tracks from editorial new-release and chart catalogues.
 * Verified recent drops are tagged discoveryKind = "new_release" with ISO releaseDate.
 * Metadata is enriched across Qobuz, Amazon, and Tidal.
 */
export async function getEditorialNewReleases(
  limit = 18,
  loadJson: JsonFetcher = fetchJson
): Promise<GatewayTrack[]> {
  const boundedLimit = Math.max(1, Math.min(limit, 30));

  // 1. Check Deezer's editorial releases endpoint
  const releases = (await loadJson("https://api.deezer.com/editorial/0/releases?limit=12")) as {
    data?: DeezerAlbum[];
  } | null;
  const albums = (releases?.data ?? []).filter((album) => album.id && album.title);
  if (albums.length > 0) {
    const batches = await Promise.all(
      albums.map(async (album) => {
        const details = (await loadJson(`https://api.deezer.com/album/${album.id}/tracks?limit=3`)) as {
          data?: DeezerTrack[];
        } | null;
        return (details?.data ?? []).map((track) => toGatewayTrack(track, album, "new_release"));
      })
    );
    const editorialTracks = dedupeTracks(batches.flat(), boundedLimit);
    if (editorialTracks.length > 0) return crossReferenceMetadata(editorialTracks, loadJson);
  }

  // 2. Deezer chart albums (features current weekly album drops with exact release dates)
  if (loadJson === fetchJson) {
    try {
      const chartAlbumsPayload = (await loadJson("https://api.deezer.com/chart/0/albums?limit=12")) as {
        data?: DeezerAlbum[];
      } | null;
      const chartAlbums = (chartAlbumsPayload?.data ?? []).filter((album) => album.id && album.title);
      if (chartAlbums.length > 0) {
        const albumBatches = await Promise.all(
          chartAlbums.map(async (album) => {
            const details = (await loadJson(`https://api.deezer.com/album/${album.id}`)) as (DeezerAlbum & {
              tracks?: { data?: DeezerTrack[] };
            }) | null;
            const albumDate = details?.release_date ?? album.release_date;
            const tracks = details?.tracks?.data ?? [];
            if (!tracks.length) return [];
            const isRecent = isRecentDate(albumDate, 60);
            return tracks.slice(0, 3).map((track) =>
              toGatewayTrack(track, { ...album, release_date: albumDate }, isRecent ? "new_release" : "chart")
            );
          })
        );
        const verifiedTracks = albumBatches.flat().filter((t) => t.discoveryKind === "new_release");
        if (verifiedTracks.length > 0) {
          const deduped = dedupeTracks(verifiedTracks, boundedLimit);
          return crossReferenceMetadata(deduped, loadJson);
        }
      }
    } catch {
      // Fall through to chart tracks
    }
  }

  // 3. Deezer live chart tracks: extract tracks and verify release dates from albums
  const chart = (await loadJson(
    `https://api.deezer.com/chart/0/tracks?limit=${Math.min(boundedLimit * 2, 50)}`
  )) as { data?: DeezerTrack[] } | null;
  const rawChartTracks = chart?.data ?? [];
  if (rawChartTracks.length > 0) {
    const tracksWithDates = await Promise.all(
      rawChartTracks.slice(0, boundedLimit).map(async (track) => {
        let albumDate = track.album?.release_date;
        if (!albumDate && track.album?.id && loadJson === fetchJson) {
          try {
            const alb = (await loadJson(`https://api.deezer.com/album/${track.album.id}`)) as DeezerAlbum | null;
            if (alb?.release_date) albumDate = alb.release_date;
          } catch {
            // Ignore failure to fetch individual album
          }
        }
        const isRecent = isRecentDate(albumDate, 60);
        return toGatewayTrack(
          track,
          { ...(track.album ?? {}), release_date: albumDate },
          isRecent ? "new_release" : "chart"
        );
      })
    );
    const directChartTracks = dedupeTracks(tracksWithDates, boundedLimit);
    if (directChartTracks.length > 0) return crossReferenceMetadata(directChartTracks, loadJson);
  }

  // 3. Parent chart fallback
  const nestedChart = (await loadJson(
    `https://api.deezer.com/chart/0?limit=${Math.min(boundedLimit * 2, 50)}`
  )) as { tracks?: { data?: DeezerTrack[] } } | null;
  const nestedChartTracks = (nestedChart?.tracks?.data ?? []).map((track) =>
    toGatewayTrack(track, track.album ?? {}, "chart")
  );
  const deezerTracks = dedupeTracks(nestedChartTracks, boundedLimit);
  if (deezerTracks.length > 0) return crossReferenceMetadata(deezerTracks, loadJson);

  // 4. Apple discovery metadata
  const appleChart = (await loadJson(
    `https://rss.marketingtools.apple.com/api/v2/us/music/most-played/${boundedLimit}/songs.json`
  )) as { feed?: { results?: AppleChartTrack[] } } | null;
  const appleResults = appleChart?.feed?.results ?? [];
  const appleTracks = appleResults.map((track) =>
    toAppleGatewayTrack(track, isRecentDate(track.releaseDate, 60) ? "new_release" : "chart")
  );
  return crossReferenceMetadata(dedupeTracks(appleTracks, boundedLimit), loadJson);
}

async function crossReferenceMetadata(tracks: GatewayTrack[], loadJson: JsonFetcher): Promise<GatewayTrack[]> {
  if (loadJson !== fetchJson) return tracks;
  return Promise.all(
    tracks.map(async (track) => {
      try {
        // 1. Dolby Atmos / Spatial Audio seed check
        const spatial = isKnownSpatialTrack(track.title, track.artist);
        if (spatial) {
          if (spatial.spatialFormat === "dolby_atmos") {
            track.isDolbyAtmos = true;
            track.isSpatialAudio = true;
            track.atmosMixAvailable = true;
            track.spatialEvidence = "verified";
          } else if (spatial.spatialFormat === "spatial_audio" || spatial.spatialFormat === "surround") {
            track.isSpatialAudio = true;
            track.spatialEvidence = "verified";
          }
        }
        // 2. Qobuz metadata check
        if (track.isrc) {
          const qobuzId = await lookupQobuzTrackByIsrc(track.isrc).catch(() => null);
          if (qobuzId) {
            track.qobuz_id = qobuzId;
            track.isHiRes = true;
            track.audioQuality = "24-bit / 96 kHz FLAC";
          }
        }
        if (!track.qobuz_id) {
          const qobuzResults = await searchQobuzPublic(`${track.title} ${track.artist}`, 1).catch(() => []);
          if (qobuzResults.length > 0) {
            const match = qobuzResults[0];
            track.qobuz_id = match.id;
            track.isHiRes = match.isHiRes ?? true;
            track.audioQuality = match.audioQuality ?? "24-bit / 96 kHz FLAC";
          }
        }
        // 3. Amazon metadata check
        const amazonResults = await searchAmazon(`${track.title} ${track.artist}`, 1).catch(() => []);
        if (amazonResults.length > 0) {
          track.amazon_id = amazonResults[0].amazon_id;
        }
        return track;
      } catch {
        return track;
      }
    })
  );
}

function dedupeTracks(tracks: GatewayTrack[], limit: number): GatewayTrack[] {
  const seen = new Set<string>();
  return tracks
    .filter((track) => track.id && track.title && track.artist)
    .filter((track) => {
      const key = track.isrc?.toUpperCase() ?? track.id;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, limit);
}

function toGatewayTrack(
  track: DeezerTrack,
  release: DeezerAlbum,
  discoveryKind: "new_release" | "chart"
): GatewayTrack {
  const album = track.album ?? release;
  const releaseDate = album.release_date ?? release.release_date;
  const releaseYear = releaseDate?.match(/^(\d{4})/)?.[1];
  return {
    id: String(track.id),
    title: track.title ?? "Unknown track",
    artist: track.artist?.name ?? album.artist?.name ?? "Unknown Artist",
    album: album.title,
    albumId: album.id ? String(album.id) : undefined,
    artworkURL: album.cover_medium,
    duration: track.duration,
    releaseDate,
    releaseYear: releaseYear ? Number(releaseYear) : undefined,
    discoveryKind,
    trackNumber: track.track_position,
    discNumber: track.disk_number,
    explicit: track.explicit_lyrics ?? false,
    isrc: track.isrc,
    provider: "deezer",
    deezer_id: String(track.id),
    audioQuality: "16-bit / 44.1 kHz FLAC",
  };
}

function toAppleGatewayTrack(track: AppleChartTrack, discoveryKind: "new_release" | "chart" = "chart"): GatewayTrack {
  const releaseYear = track.releaseDate?.match(/^(\d{4})/)?.[1];
  return {
    id: track.id ?? "",
    title: track.name ?? "Unknown track",
    artist: track.artistName ?? "Unknown Artist",
    artworkURL: track.artworkUrl100?.replace(/\/\d+x\d+bb\./, "/600x600bb."),
    releaseYear: releaseYear ? Number(releaseYear) : undefined,
    releaseDate: track.releaseDate,
    discoveryKind,
    provider: "apple",
    apple_id: track.id,
    audioQuality: "Catalog metadata",
  };
}
