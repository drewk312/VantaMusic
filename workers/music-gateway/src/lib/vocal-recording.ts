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
  "tribute of honor",
  "sound-a-like",
  "cover hits",
  "sing along",
  "karaoke",
  "the backing tracks",
  "backing tracks",
  "backing track",
  "tabata",
  "workout hits",
  "fitness beats",
  "style pack",
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
  "originally performed",
  "as made famous",
  "backing track",
  "tribute to",
  "tabata",
  "hiit mix",
  "workout mix",
  "live at",
  "live from",
  "live in",
  "live on",
  "live session",
  "live performance",
  "concert recording",
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
  "originally performed",
  "as made famous",
  "backing track",
  "in the style of",
  "type beat",
  "dj mix",
  "tabata",
  "lullaby",
  "orchestra performs",
  "symphony orchestra",
  "live at",
  "live from",
  "live in",
  "live on",
  "live session",
  "live performance",
];

export function userRequestedVariant(query: string): boolean {
  const q = query.toLowerCase();
  return ["instrumental", "karaoke", "piano", "lullaby", "acoustic", "live", "remix", "cover"].some((term) =>
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

const impersonationMarkers = [
  "originally performed by",
  "originally performed",
  "as made famous",
  "made famous by",
  "in the style of",
  "backing track",
  "backing tracks",
  "the backing tracks",
  "minus one",
  "karaoke",
  "tribute of honor",
  "tribute to",
  "sound-a-like",
  "sound alike",
  "tabata",
  "hiit mix",
  "hiit version",
  "workout mix",
  "type beat",
  "type beats",
  "dj mix",
  "pound mix",
  "party tyme",
  "sing along",
  "singalong",
];

const impersonationPatterns = [
  /\b\d+\s+years?\s+\d+\s+songs?\b/i,
  /\b(?:top|best)\s+\d+\s+(?:songs|hits|tracks)\b/i,
  /\bmix\s+20\d{2}\b/i,
  /\btype\s+(?:beat|beats|instrumental)\b/i,
];

/** Karaoke, workout packs, type-beats, and "originally performed by" impersonators. */
export function isCatalogImpersonation(track: GatewayTrack, query = ""): boolean {
  if (userRequestedVariant(query)) return false;
  if (isTributeOrNonVocalArtist(track.artist)) return true;
  const stack = haystack(track.title, track.artist, track.album);
  if (!stack) return false;
  if (impersonationMarkers.some((marker) => stack.includes(marker))) return true;
  if (impersonationPatterns.some((pattern) => pattern.test(stack))) return true;
  const title = track.title ?? "";
  if ((title.match(/,/g) ?? []).length >= 2 && /\btype\b/i.test(title)) return true;
  return false;
}

export function hasVariantSignals(track: GatewayTrack): boolean {
  const stack = haystack(track.title, track.artist, track.album);
  if (!stack) return false;
  if (isTributeOrNonVocalArtist(track.artist)) return true;
  if (hasNonVocalAlbumSignals(track.album)) return true;
  if (isCatalogImpersonation(track)) return true;
  if ((track.title ?? "").toLowerCase().includes("piano")) return true;
  if (/\bcover\b/.test(stack)) return true;
  return variantMarkers.some((marker) => stack.includes(marker));
}

/** Hard reject for catalog search — mirrors Android VocalRecordingClassifier. */
export function isAllowedVocalTrack(track: GatewayTrack, query = ""): boolean {
  if (userRequestedVariant(query)) return true;
  if (isCatalogImpersonation(track, query)) return false;
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
  if (isCatalogImpersonation(track, query)) penalty += 280;
  if (hasNonVocalAlbumSignals(track.album)) penalty += 180;
  if (title.includes("piano")) penalty += 160;
  if (/\bcover\b/.test(stack)) penalty += 220;
  if (stack.includes("remix")) penalty += 90;
  if (/\blive\s+(at|from|in|on|session|performance)\b/.test(stack) || title.includes("(live)")) penalty += 180;
  if (stack.includes("acoustic rendition") || stack.includes("acoustic cover") || stack.includes("acoustic version")) {
    penalty += 70;
  }
  if (stack.includes("orchestra") || stack.includes("symphony")) penalty += 200;
  if (stack.includes("lullaby")) penalty += 200;

  return penalty;
}
