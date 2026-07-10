import type { GatewayTrack } from "../types";
import { variantPenalty } from "./vocal-recording";

function normalize(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]+/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}

const CANONICAL_ARTISTS_BY_TITLE: Record<string, string[]> = {
  "spirit in the sky": ["norman greenbaum"],
  "everybody wants to rule the world": ["tears for fears"],
  "head over heels": ["tears for fears"],
  "sowing the seeds of love": ["tears for fears"],
  "mad world": ["tears for fears"],
};

/** Boost official studio vocal masters; demote covers, variants, and wrong artists. */
function userWantsSpatial(query: string): boolean {
  return /\b(atmos|dolby atmos|spatial|surround|5\.1|7\.1|360|sony 360|hi-res|hires)\b/.test(query.toLowerCase());
}

export function rankTracks(query: string, tracks: GatewayTrack[]): GatewayTrack[] {
  const q = normalize(query);
  const tokens = q.split(" ").filter(Boolean);
  const likelyArtist = tokens.length >= 2 ? tokens.slice(-2).join(" ") : tokens[tokens.length - 1] ?? "";
  const canonicalArtists = CANONICAL_ARTISTS_BY_TITLE[q] ?? [];

  const scored = tracks.map((track) => {
    const title = normalize(track.title ?? "");
    const artist = normalize(track.artist ?? "");
    const album = normalize(track.album ?? "");
    let score = 0;

    // Strongest: full query appears as a phrase in title or artist
    if (title.includes(q)) score += 200;
    if (artist.includes(q)) score += 180;
    if (`${title} ${artist}` === q || `${artist} ${title}` === q) score += 160;
    if (title === q || artist === q) score += 140;

    // Strong: exact artist match
    if (likelyArtist && artist === likelyArtist) score += 110;
    if (artist === q) score += 100;

    // Moderate: query appears in album
    if (album.includes(q)) score += 50;

    // Weak: individual token matches (only if all tokens are meaningful length)
    const meaningfulTokens = tokens.filter(t => t.length >= 3);
    for (const token of meaningfulTokens) {
      if (title.includes(token)) score += 8;
      if (artist.includes(token)) score += 10;
      if (album.includes(token)) score += 4;
    }

    // Bonus if track has ALL query tokens in title (not just some)
    const allTokensInTitle = tokens.every(t => title.includes(t));
    if (allTokensInTitle && tokens.length > 1) score += 60;

    // Exact studio single / album track preference
    if (title === tokens[0] && likelyArtist && artist.includes(likelyArtist)) score += 60;
    if (title === q && canonicalArtists.includes(artist)) score += 260;

    if (track.isrc) score += 8;

    const variant = variantPenalty(track, query);
    score -= variant;

    // Quality / spatial boost only when no variant penalty
    if (variant < 40) {
      if (track.provider === "qobuz") score += 12;
      if (track.provider === "deezer" && track.qobuz_id) score += 8;
      if (track.audioQuality?.includes("24-bit")) score += 14;
      if (track.audioQuality?.includes("48 kHz")) score += 8;
      if (track.audioQuality?.includes("96 kHz")) score += 10;
      if (track.format?.toLowerCase() === "flac") score += 6;

      // Spatial / surround / hi-res preference when user explicitly asks for it
      if (userWantsSpatial(q)) {
        if (track.isDolbyAtmos) score += 180;
        else if (track.isSpatialAudio) score += 140;
        else if (track.isSurround) score += 100;
        if (track.isHiRes) score += 40;
      } else if (track.isDolbyAtmos || track.isSpatialAudio || track.isSurround) {
        // Slight preference for spatial-aware masters in normal queries too, but much smaller
        score += 8;
      }
    }

    // Penalize obvious non-original artist names when query names a known artist
    if (likelyArtist && artist !== likelyArtist && !artist.includes(likelyArtist)) {
      score -= 45;
    }

    return { track, score };
  });

  return scored
    .sort((a, b) => b.score - a.score || (a.track.title ?? "").localeCompare(b.track.title ?? ""))
    .map((entry) => entry.track);
}

export interface GatewayArtist {
  id: string;
  name: string;
  artworkURL?: string;
}

export interface GatewayAlbum {
  id: string;
  title: string;
  artist: string;
  artworkURL?: string;
}

export function extractArtistsAndAlbums(tracks: GatewayTrack[]): {
  artists: GatewayArtist[];
  albums: GatewayAlbum[];
} {
  const artistMap = new Map<string, GatewayArtist>();
  const albumMap = new Map<string, GatewayAlbum>();

  for (const track of tracks) {
    const artistName = track.artist?.trim();
    if (artistName) {
      const key = normalize(artistName);
      if (!artistMap.has(key)) {
        artistMap.set(key, {
          id: `${track.provider ?? "catalog"}:artist:${key}`,
          name: artistName,
          artworkURL: track.artworkURL,
        });
      }
    }

    const albumTitle = track.album?.trim();
    if (albumTitle && artistName) {
      const key = `${normalize(albumTitle)}|${normalize(artistName)}`;
      if (!albumMap.has(key)) {
        albumMap.set(key, {
          id: track.albumId ?? `${track.provider ?? "catalog"}:album:${key}`,
          title: albumTitle,
          artist: artistName,
          artworkURL: track.artworkURL,
        });
      }
    }
  }

  return {
    artists: [...artistMap.values()].slice(0, 12),
    albums: [...albumMap.values()].slice(0, 12),
  };
}


