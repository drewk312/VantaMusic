import type { GatewayTrack } from "../types";
import { variantPenalty } from "./vocal-recording";

export type SearchIntentKind = "track" | "artist" | "open";

/**
 * Catalog-derived intent. This is deliberately data driven: no song or artist
 * names are embedded in the search engine.
 */
export interface SearchIntent {
  rawQuery: string;
  kind: SearchIntentKind;
  title?: string;
  artist?: string;
  retrievalQuery: string;
  confidence: number;
  corrected: boolean;
}

function normalize(value: string): string {
  return value
    .toLowerCase()
    .replace(/&/g, " and ")
    .replace(/[^\p{L}\p{N}\s]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** Accent-folded identity so "beyonce" matches "Beyoncé". */
export function foldIdentity(value: string): string {
  return normalize(value)
    .normalize("NFD")
    .replace(/\p{M}+/gu, "");
}

function compact(value: string): string {
  return foldIdentity(value).replace(/\s+/g, "");
}

import { splitArtistCredits } from "./artist-credits";

/** First billed name, ignoring featured credits and Qobuz role dumps. */
export function primaryArtistName(artist: string): string {
  const split = splitArtistCredits(artist);
  if (split.primary) return split.primary;
  return artist
    .split(",")[0]
    .replace(/\s+(?:feat\.?|featuring|ft\.?)\b.*$/i, "")
    .trim();
}

function foldedPrimaryArtist(artist: string): string {
  return foldIdentity(primaryArtistName(artist));
}

function levenshtein(left: string, right: string): number {
  if (left === right) return 0;
  if (!left.length) return right.length;
  if (!right.length) return left.length;

  const previous = Array.from({ length: right.length + 1 }, (_, index) => index);
  for (let row = 1; row <= left.length; row += 1) {
    const current = [row];
    for (let column = 1; column <= right.length; column += 1) {
      const substitution = previous[column - 1] + (left[row - 1] === right[column - 1] ? 0 : 1);
      current[column] = Math.min(previous[column] + 1, current[column - 1] + 1, substitution);
    }
    previous.splice(0, previous.length, ...current);
  }
  return previous[right.length];
}

export function textSimilarity(left: string, right: string): number {
  const a = compact(left);
  const b = compact(right);
  if (!a || !b) return 0;
  if (a === b) return 1;

  const longest = Math.max(a.length, b.length);
  const editSimilarity = 1 - levenshtein(a, b) / longest;
  const containmentSimilarity = a.includes(b) || b.includes(a)
    ? Math.min(a.length, b.length) / longest
    : 0;
  return Math.max(editSimilarity, containmentSimilarity);
}

const VERSION_WORDS = new Set([
  "remaster", "remastered", "deluxe", "edition", "version", "edit", "mix",
  "mono", "stereo", "explicit", "clean", "audio", "video", "official",
]);

const VARIANT_WORDS = [
  "live", "acoustic", "remix", "instrumental", "karaoke", "cover", "piano",
  "slowed", "sped up", "nightcore", "reverb", "unplugged",
];

function requestedVariants(query: string): string[] {
  const normalized = normalize(query);
  return VARIANT_WORDS.filter((variant) => normalized.includes(variant));
}

/** Remove release/SEO decoration while preserving a variant the user requested. */
export function identityTitle(value: string, query = ""): string {
  const requested = requestedVariants(query);
  let title = value;
  title = title.replace(/\s*[\[(]([^\])]+)[\])]/g, (whole, inner: string) => {
    const normalizedInner = normalize(inner);
    return requested.some((variant) => normalizedInner.includes(variant)) ? ` ${inner}` : " ";
  });
  title = normalize(title)
    .split(" ")
    .filter((word) => !VERSION_WORDS.has(word))
    .join(" ")
    .replace(/\b(feat|featuring|ft)\b.*$/, "")
    .trim();
  return title || normalize(value);
}

function isCreditLineArtist(artist: string): boolean {
  const value = foldIdentity(artist).replace(/\s+/g, "");
  return [
    "composerlyricist",
    "musicpublisher",
    "executiveproducer",
    "associatedperformer",
    "mainartist",
    "songwriter",
  ].some((marker) => value.includes(marker));
}

function isLowAuthorityArtist(artist: string): boolean {
  const value = foldIdentity(artist);
  if (isCreditLineArtist(artist)) return true;
  return [
    "karaoke", "tribute", "cover band", "covers", "kids bop", "kidz bop",
    "instrumental", "rain sounds", "rainforest sounds", "music box", "lyrics",
    "backing track", "the backing tracks", "originally performed",
    "tabata", "workout hits", "fitness beats", "style pack", "party tyme",
    "sound a like", "sound-a-like", "sing along",
  ].some((marker) => value.includes(foldIdentity(marker)));
}

function emptyIntent(query: string): SearchIntent {
  const rawQuery = query.trim();
  return {
    rawQuery,
    kind: "open",
    retrievalQuery: rawQuery,
    confidence: 0,
    corrected: false,
  };
}

function looksLikeSoundtrackPackaging(title: string, album: string): boolean {
  const blob = `${title} ${album}`;
  return /\b(from|soundtrack|motion picture|original score|\bost\b)\b/.test(blob);
}

function artistCatalogPattern(query: string, tracks: GatewayTrack[]): boolean {
  const q = foldIdentity(query);
  if (!q) return false;
  const titleHits = tracks.filter((track) => foldIdentity(identityTitle(track.title ?? "", query)) === q);
  const artistHits = tracks.filter((track) => {
    if (isLowAuthorityArtist(track.artist ?? "")) return false;
    const primary = foldedPrimaryArtist(track.artist ?? "");
    return primary === q || foldIdentity(track.artist ?? "") === q;
  });
  if (titleHits.length >= 2) return false;
  const distinctTitles = new Set(artistHits.map((track) => foldIdentity(identityTitle(track.title ?? "", query))));
  return artistHits.length >= 2 && distinctTitles.size >= 2;
}

/**
 * Resolve a misspelled or incomplete query from a relevance-ordered catalog
 * response (Apple Search is used by the gateway). The same logic works for any
 * catalog and never depends on a hand-maintained list of songs.
 */
export function inferSearchIntent(query: string, discoveryTracks: GatewayTrack[]): SearchIntent {
  const fallback = emptyIntent(query);
  const normalizedQuery = normalize(query);
  const compactQuery = compact(query);
  if (!normalizedQuery || compactQuery.length < 3 || discoveryTracks.length === 0) return fallback;

  const discoverySlice = discoveryTracks.slice(0, 50);
  if (artistCatalogPattern(query, discoverySlice)) {
    const q = foldIdentity(query);
    const artistHit = discoverySlice.find((track) => {
      if (isLowAuthorityArtist(track.artist ?? "")) return false;
      return foldedPrimaryArtist(track.artist ?? "") === q;
    });
    const artist = artistHit ? primaryArtistName(artistHit.artist ?? "") : undefined;
    if (artist) {
      return {
        rawQuery: query.trim(),
        kind: "artist",
        artist,
        retrievalQuery: artist,
        confidence: 1,
        corrected: normalize(artist) !== normalizedQuery,
      };
    }
  }

  const explicitBy = query.match(/^(.+?)\s+by\s+(.+)$/i);
  if (explicitBy) {
    const title = explicitBy[1].trim();
    const artist = explicitBy[2].trim();
    return {
      rawQuery: query.trim(),
      kind: "track",
      title,
      artist,
      retrievalQuery: `${title} ${artist}`,
      confidence: 1,
      corrected: false,
    };
  }

  const titleCounts = new Map<string, number>();
  const identityCounts = new Map<string, number>();
  const earliestTitleYears = new Map<string, number>();
  for (const track of discoverySlice) {
    const title = identityTitle(track.title ?? "", query);
    if (title) titleCounts.set(title, (titleCounts.get(title) ?? 0) + 1);
    const artist = normalize(track.artist ?? "");
    if (title && artist) {
      const identity = `${title}|${artist}`;
      identityCounts.set(identity, (identityCounts.get(identity) ?? 0) + 1);
    }
    if (title && typeof track.releaseYear === "number") {
      earliestTitleYears.set(title, Math.min(earliestTitleYears.get(title) ?? track.releaseYear, track.releaseYear));
    }
  }

  const candidates = discoverySlice.map((track, index) => {
    const title = identityTitle(track.title ?? "", query);
    const artist = track.artist?.trim() ?? "";
    const titleSimilarity = textSimilarity(query, title);
    const titleArtistSimilarity = textSimilarity(query, `${title} ${artist}`);
    const artistTitleSimilarity = textSimilarity(query, `${artist} ${title}`);
    const similarity = Math.max(titleSimilarity, titleArtistSimilarity, artistTitleSimilarity);
    // Upstream order helps distinguish a popular near-spelling from an obscure
    // exact-text collision, but the bonus drops quickly so an exact
    // title+artist identity can still beat a provider's first cover result.
    const relevanceOrderBonus = Math.max(0, 35 - index * 20);
    const exactTitleBonus = normalize(title) === normalizedQuery ? 4 : 0;
    const titleConsensusBonus = Math.min(30, Math.max(0, (titleCounts.get(title) ?? 1) - 1) * 15);
    const identityConsensusBonus = Math.min(
      40,
      Math.max(0, (identityCounts.get(`${title}|${normalize(artist)}`) ?? 1) - 1) * 20
    );
    const originalReleaseBonus = track.releaseYear && track.releaseYear === earliestTitleYears.get(title) ? 45 : 0;
    const authorityPenalty = isLowAuthorityArtist(artist) ? 20 : 0;
    const soundtrackPenalty = looksLikeSoundtrackPackaging(normalize(track.title ?? ""), normalize(track.album ?? "")) ? 18 : 0;
    const swappedIdentityPenalty =
      foldedPrimaryArtist(artist) === foldIdentity(query) &&
      foldIdentity(title) !== foldIdentity(query) ? 35 : 0;
    const selfTitledCollisionPenalty =
      compact(artist) === compact(title) && compact(title) === compactQuery && compactQuery.length <= 12 ? 28 : 0;
    const variantCost = Math.min(120, variantPenalty(track, query) / 2);
    const metadataBonus = (track.isrc ? 3 : 0) + (track.album ? 2 : 0);
    return {
      track,
      title,
      artist,
      similarity,
      score: similarity * 100 + relevanceOrderBonus + exactTitleBonus + titleConsensusBonus + identityConsensusBonus + originalReleaseBonus + metadataBonus - authorityPenalty - soundtrackPenalty - swappedIdentityPenalty - selfTitledCollisionPenalty - variantCost,
    };
  }).sort((left, right) => right.score - left.score);

  const best = candidates[0];
  const minimumConfidence = compactQuery.length <= 5 ? 0.88 : 0.72;
  if (!best || !best.title || !best.artist || best.similarity < minimumConfidence) return fallback;

  const variants = requestedVariants(query);
  const retrievalParts = [best.title, best.artist];
  for (const variant of variants) {
    if (!normalize(retrievalParts.join(" ")).includes(variant)) retrievalParts.push(variant);
  }
  const retrievalQuery = retrievalParts.join(" ").trim();
  return {
    rawQuery: query.trim(),
    kind: "track",
    title: best.title,
    artist: best.artist,
    retrievalQuery,
    confidence: best.similarity,
    corrected: normalize(retrievalQuery) !== normalizedQuery,
  };
}

function userWantsSpatial(query: string): boolean {
  return /\b(atmos|dolby atmos|spatial|surround|5\.1|7\.1|360|sony 360|hi-res|hires)\b/.test(query.toLowerCase());
}

function albumLooksLikeCompilation(album: string): boolean {
  return [
    "greatest hits", "best of", "very best", "collection", "compilation",
    "anthology", "essentials", "hits", "now that s what i call",
  ].some((marker) => album.includes(marker));
}

/** Boost the intended studio master and demote covers, variants, and weak metadata. */
export function rankTracks(query: string, tracks: GatewayTrack[], resolvedIntent?: SearchIntent): GatewayTrack[] {
  const q = foldIdentity(query);
  const tokens = q.split(" ").filter(Boolean);
  const intent = resolvedIntent ?? emptyIntent(query);

  // Explicit artist suffix detection remains useful when rankTracks is called
  // independently from the gateway's catalog-intent stage.
  const suffixArtist = tracks
    .map((track) => foldedPrimaryArtist(track.artist ?? ""))
    .filter((artist) => artist && (q === artist || q.endsWith(` ${artist}`)))
    .sort((a, b) => b.length - a.length)[0];
  const intendedArtist = foldIdentity(intent.artist ?? suffixArtist ?? "");
  const intendedTitle = foldIdentity(
    intent.title ?? (suffixArtist ? q.slice(0, q.length - suffixArtist.length).trim() : "")
  );

  const exactIdentityTracks = intendedArtist && intendedTitle
    ? tracks.filter((track) =>
        foldIdentity(identityTitle(track.title ?? "", query)) === intendedTitle &&
        foldedPrimaryArtist(track.artist ?? "") === intendedArtist
      )
    : [];
  const exactDurations = exactIdentityTracks
    .map((track) => track.duration)
    .filter((duration): duration is number => typeof duration === "number" && duration > 0);
  const earliestReleaseYear = exactIdentityTracks
    .map((track) => track.releaseYear)
    .filter((year): year is number => typeof year === "number")
    .sort((a, b) => a - b)[0];

  const scored = tracks.map((track, index) => {
    const rawTitle = track.title ?? "";
    const title = foldIdentity(rawTitle);
    const baseTitle = foldIdentity(identityTitle(rawTitle, query));
    const artist = foldedPrimaryArtist(track.artist ?? "");
    const album = foldIdentity(track.album ?? "");
    const titleSimilarity = intendedTitle ? textSimilarity(intendedTitle, baseTitle) : textSimilarity(q, baseTitle);
    let score = Math.max(0, 20 - index);

    if (intendedArtist) {
      const artistSimilarity = textSimilarity(intendedArtist, artist);
      if (artist === intendedArtist) score += 440;
      else if (artistSimilarity >= 0.9) score += 260;
      else if (intent.kind === "artist") score -= 480;
      else if (intent.kind === "track" || suffixArtist) score -= 620;
    }

    if (intendedTitle) {
      if (baseTitle === intendedTitle) score += 380;
      else if (titleSimilarity >= 0.92) score += 320;
      else if (titleSimilarity >= 0.82) score += 230;
      else if (titleSimilarity >= 0.68) score += 90;
      else score -= 180;
    } else {
      if (title === q || baseTitle === q) score += 240;
      else if (intent.kind !== "artist" && (title.includes(q) || baseTitle.includes(q))) score += 150;
      if (artist === q) score += 220;
      else if (artist.includes(q)) score += 130;
    }

    if (`${baseTitle} ${artist}` === q || `${artist} ${baseTitle}` === q) score += 180;
    if (album.includes(q)) score += 45;
    if (looksLikeSoundtrackPackaging(title, album) && intendedTitle && baseTitle === intendedTitle) score -= 40;
    if (artist === q && baseTitle !== q && intent.kind !== "artist") score -= 80;

    const meaningfulTokens = tokens.filter((token) => token.length >= 3);
    for (const token of meaningfulTokens) {
      if (intent.kind !== "artist" && title.includes(token)) score += 7;
      if (artist.includes(token)) score += 9;
      if (album.includes(token)) score += 3;
    }

    const variant = variantPenalty(track, query);
    score -= variant;

    if (intendedArtist && intendedTitle && artist === intendedArtist && baseTitle === intendedTitle) {
      if (earliestReleaseYear && track.releaseYear === earliestReleaseYear) score += 100;
      if (track.duration && exactDurations.length > 1) {
        const studioClusterSize = exactDurations.filter((duration) => Math.abs(duration - track.duration!) <= 8).length;
        score += studioClusterSize * 25;
      }
      if (albumLooksLikeCompilation(album)) score -= 45;
    }

    if (track.isrc) score += 12;
    if (track.album) score += 5;

    const lowAuthority = isLowAuthorityArtist(track.artist ?? "");
    const wantsSpatial = userWantsSpatial(q);
    if (variant < 40 && !lowAuthority) {
      if (track.provider === "qobuz") score += 18;
      if (track.provider === "amazon") score += wantsSpatial ? 120 : 4;
      if (track.provider === "tidal" && wantsSpatial) score += 90;
      if (track.provider === "deezer" && track.qobuz_id) score += 10;
      if (track.audioQuality?.includes("24-bit")) score += 14;
      if (track.audioQuality?.includes("48 kHz")) score += 6;
      if (track.audioQuality?.includes("96 kHz")) score += 10;
      if (track.audioQuality?.includes("192 kHz")) score += 14;
      if (track.format?.toLowerCase() === "flac") score += 6;

      if (wantsSpatial) {
        if (track.isDolbyAtmos) score += 180;
        else if (track.isSpatialAudio) score += 140;
        else if (track.isSurround) score += 100;
        if (track.isHiRes) score += 40;
      } else if (track.isDolbyAtmos || track.isSpatialAudio || track.isSurround) {
        score += 8;
      }
    }

    if (track.provider === "soundcloud") score -= 260;
    if (lowAuthority) score -= 400;
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

export function extractArtistsAndAlbums(
  tracks: GatewayTrack[],
  intent?: SearchIntent
): { artists: GatewayArtist[]; albums: GatewayAlbum[] } {
  const intendedArtist = foldIdentity(intent?.artist ?? "");
  const identityTracks = intendedArtist
    ? tracks.filter((track) => foldedPrimaryArtist(track.artist ?? "") === intendedArtist)
    : tracks;
  const source = identityTracks.length > 0 ? identityTracks : tracks;
  const artistMap = new Map<string, GatewayArtist>();
  const albumMap = new Map<string, GatewayAlbum>();

  for (const track of source) {
    const artistName = primaryArtistName(track.artist ?? "");
    if (!artistName || isLowAuthorityArtist(artistName)) continue;
    const key = foldedPrimaryArtist(artistName);
    if (!artistMap.has(key)) {
      artistMap.set(key, {
        id: `${track.provider ?? "catalog"}:artist:${key}`,
        name: artistName,
        artworkURL: track.artworkURL,
      });
    }

    const albumTitle = track.album?.trim();
    if (albumTitle && artistName) {
      const albumKey = `${foldIdentity(albumTitle)}|${key}`;
      if (!albumMap.has(albumKey)) {
        albumMap.set(albumKey, {
          id: track.albumId ?? `${track.provider ?? "catalog"}:album:${albumKey}`,
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
