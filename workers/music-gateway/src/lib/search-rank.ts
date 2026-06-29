import type { GatewayTrack } from "../types";
import { variantPenalty } from "./vocal-recording";

function normalize(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]+/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}

/** Boost official studio vocal masters; demote covers, variants, and wrong artists. */
export function rankTracks(query: string, tracks: GatewayTrack[]): GatewayTrack[] {
  const q = normalize(query);
  const tokens = q.split(" ").filter(Boolean);
  const likelyArtist = tokens.length >= 2 ? tokens.slice(-2).join(" ") : tokens[tokens.length - 1] ?? "";

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

    if (track.isrc) score += 8;

    const variant = variantPenalty(track, query);
    score -= variant;

    // Quality boost only when no variant penalty
    if (variant < 40) {
      if (track.provider === "qobuz") score += 12;
      if (track.provider === "deezer" && track.qobuz_id) score += 8;
      if (track.audioQuality?.includes("24-bit")) score += 14;
      if (track.audioQuality?.includes("48 kHz")) score += 8;
      if (track.audioQuality?.includes("96 kHz")) score += 10;
      if (track.format?.toLowerCase() === "flac") score += 6;
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
