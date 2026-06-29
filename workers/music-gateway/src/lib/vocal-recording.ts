import type { GatewayTrack } from "../types";

export function haystack(title?: string, artist?: string, album?: string): string {
  return `${title ?? ""} ${artist ?? ""} ${album ?? ""}`
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

const tributeArtistMarkers = [
  "rockabye baby",
  "symphony orchestra",
  "light orchestra",
  "guitar revival",
  "dreamy sugar",
  "ambient light",
  "acoustic guitar revival",
  "roma symphony",
  "soft serenades",
  "pancadão",
  "boanerges",
  "fredy'sam",
  "japanese jenn",
  "venkij palys",
  "tetrismouth",
  "party tyme",
  "ultimate tribute",
  "tribute stars",
  "sound-a-like",
  "cover hits",
  "sing along",
  "karaoke",
];

const nonVocalAlbumMarkers = [
  "lullaby",
  "lullabies",
  "renditions",
  "rendition",
  "translations",
  "translation",
  "performs",
  "instrumental version",
  "instrumental",
  "karaoke",
  "piano music",
  "acoustic renditions",
  "acoustic rendition",
  "sleep all",
  "ambient translation",
  "cover version",
  "cover versions",
  "in the style of",
  "made famous by",
  "tribute to",
  "live at",
  "live from",
];

const variantMarkers = [
  "instrumental",
  "karaoke",
  "remix",
  "slowed",
  "sped up",
  "sped-up",
  "nightcore",
  "acoustic version",
  "acoustic cover",
  "unplugged",
  "piano version",
  "piano cover",
  "cover version",
  "tribute to",
  "made famous by",
  "in the style of",
  "lullaby",
  "orchestra performs",
  "symphony orchestra",
  "live at",
  "live from",
];

export function userRequestedVariant(query: string): boolean {
  const q = query.toLowerCase();
  return ["instrumental", "karaoke", "piano", "lullaby", "acoustic", "live", "remix"].some((term) =>
    q.includes(term)
  );
}

export function isTributeOrNonVocalArtist(artist?: string): boolean {
  const normalized = (artist ?? "").toLowerCase().trim();
  if (!normalized) return false;
  return tributeArtistMarkers.some((marker) => normalized.includes(marker));
}

export function hasNonVocalAlbumSignals(album?: string): boolean {
  const normalized = (album ?? "").toLowerCase().trim();
  if (!normalized) return false;
  return nonVocalAlbumMarkers.some((marker) => normalized.includes(marker));
}

export function hasVariantSignals(track: GatewayTrack): boolean {
  const stack = haystack(track.title, track.artist, track.album);
  if (!stack) return false;
  if (isTributeOrNonVocalArtist(track.artist)) return true;
  if (hasNonVocalAlbumSignals(track.album)) return true;
  if ((track.title ?? "").toLowerCase().includes("piano")) return true;
  return variantMarkers.some((marker) => stack.includes(marker));
}

/** Hard reject for catalog search — mirrors Android VocalRecordingClassifier. */
export function isAllowedVocalTrack(track: GatewayTrack, query = ""): boolean {
  if (userRequestedVariant(query)) return true;
  if (isTributeOrNonVocalArtist(track.artist)) return false;
  if (hasNonVocalAlbumSignals(track.album)) return false;
  if (hasVariantSignals(track)) return false;
  return true;
}

/** Soft penalty for ranking — negative score subtracted from rank. */
export function variantPenalty(track: GatewayTrack, query = ""): number {
  if (userRequestedVariant(query)) return 0;
  let penalty = 0;
  const stack = haystack(track.title, track.artist, track.album);
  const title = (track.title ?? "").toLowerCase();

  if (isTributeOrNonVocalArtist(track.artist)) penalty += 220;
  if (hasNonVocalAlbumSignals(track.album)) penalty += 180;
  if (title.includes("piano")) penalty += 160;
  if (stack.includes("remix")) penalty += 90;
  if (stack.includes("live at") || stack.includes("live from") || title.includes("(live)")) penalty += 80;
  if (stack.includes("acoustic rendition") || stack.includes("acoustic cover") || stack.includes("acoustic version")) {
    penalty += 70;
  }
  if (stack.includes("orchestra") || stack.includes("symphony")) penalty += 200;
  if (stack.includes("lullaby")) penalty += 200;

  // SEO title stuffing: title contains extra artist tokens not in artist field
  const artist = (track.artist ?? "").toLowerCase();
  const queryTokens = query.toLowerCase().split(/\s+/).filter((t) => t.length > 2);
  const likelyArtist = queryTokens.length >= 2 ? queryTokens.slice(-2).join(" ") : "";
  if (likelyArtist && title.includes(likelyArtist) && !artist.includes(likelyArtist)) {
    penalty += 150;
  }

  return penalty;
}
