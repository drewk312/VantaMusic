/** Split catalog artist strings into primary + featured (Apple-style collabs). */

const DUO_BAND_NAMES = new Set([
  "simon & garfunkel",
  "hall & oates",
  "brooks & dunn",
  "brooks and dunn",
  "sam & dave",
  "sam and dave",
  "ike & tina turner",
  "ike and tina turner",
  "captain & tennille",
  "captain and tennille",
  "sonny & cher",
  "sonny and cher",
  "peaches & herb",
  "peaches and herb",
  "ashford & simpson",
  "ashford and simpson",
  "earth, wind & fire",
  "earth, wind, and fire",
  "blood, sweat & tears",
  "crosby, stills & nash",
  "crosby, stills, nash & young",
  "emerson, lake & palmer",
]);

const CREDIT_NOISE =
  /^(electric|acoustic|bass|pedal\s*steel|steel|project)?\s*(guitar|drums?|percussion|mandolin|piano|keys|keyboards?|vocals?|violin|cello|saxophone|trumpet|harmonica|banjo|fiddle|dobro|organ|synth|synthesizer|coordinator|producer|engineer|mixer|assistant|programming|arrangement|arranger)$/i;

const FEAT_IN_ARTIST = /\s+(?:feat\.?|featuring|ft\.?)\s+/i;
const COLLAB_SPLIT = /\s*(?:,|&|\band\b|\bvs\.?\b|\s+x\s+|\+)\s*/i;

export interface ArtistCredits {
  primary: string;
  featured: string[];
}

function cleanName(value: string): string {
  return value.replace(/\s+/g, " ").trim();
}

function looksLikePersonCredit(name: string): boolean {
  const cleaned = cleanName(name);
  if (!cleaned) return false;
  if (CREDIT_NOISE.test(cleaned)) return false;
  const words = cleaned.split(/\s+/);
  if (words.length === 1 && /^(guitar|drums?|bass|piano|vocals?|percussion|mandolin|strings?)$/i.test(words[0])) {
    return false;
  }
  return true;
}

function uniqueNames(names: string[]): string[] {
  const out: string[] = [];
  for (const name of names) {
    const cleaned = cleanName(name);
    if (!cleaned || !looksLikePersonCredit(cleaned)) continue;
    if (out.some((existing) => existing.toLowerCase() === cleaned.toLowerCase())) continue;
    out.push(cleaned);
  }
  return out;
}

function isDuoBand(artist: string): boolean {
  const normalized = artist.toLowerCase().replace(/\s+/g, " ").trim();
  return DUO_BAND_NAMES.has(normalized);
}

function capFeatured(names: string[]): string[] {
  // Collab billing is almost never more than a couple guests; longer lists are credit dumps.
  return names.slice(0, 2);
}

function preferFeatured(left: string[], right: string[]): string[] {
  const rawA = uniqueNames(left);
  const rawB = uniqueNames(right);
  if (rawA.length > 0 && rawA.length <= 2 && rawB.length > 2) return rawA;
  if (rawB.length > 0 && rawB.length <= 2 && rawA.length > 2) return rawB;
  return capFeatured(uniqueNames([...rawA, ...rawB]));
}

/**
 * Parse "Ella Langley & Morgan Wallen" / "A feat. B" into primary + guests.
 * Permanent duo act names stay intact. Qobuz credit dumps are capped.
 */
export function splitArtistCredits(rawArtist: string, albumArtist?: string): ArtistCredits {
  const artist = cleanName(rawArtist);
  if (!artist) return { primary: "", featured: [] };
  if (isDuoBand(artist)) return { primary: artist, featured: [] };

  const albumPrimary = cleanName(albumArtist ?? "");
  if (albumPrimary && artist.toLowerCase().includes(albumPrimary.toLowerCase()) && artist !== albumPrimary) {
    const remainder = cleanName(
      artist
        .replace(new RegExp(`^${escapeRegExp(albumPrimary)}\\s*(?:&|and|,|x|\\+|feat\\.?|featuring|ft\\.?)\\s*`, "i"), "")
        .replace(new RegExp(`\\s*(?:&|and|,|x|\\+|feat\\.?|featuring|ft\\.?)\\s*${escapeRegExp(albumPrimary)}$`, "i"), "")
    );
    if (remainder && remainder.toLowerCase() !== albumPrimary.toLowerCase()) {
      return { primary: albumPrimary, featured: capFeatured(uniqueNames(splitCollabTail(remainder))) };
    }
  }

  const featIdx = artist.search(FEAT_IN_ARTIST);
  if (featIdx > 0) {
    const primary = cleanName(artist.slice(0, featIdx));
    const tail = cleanName(artist.slice(featIdx).replace(FEAT_IN_ARTIST, ""));
    return {
      primary: primary || artist,
      featured: capFeatured(
        uniqueNames(splitCollabTail(tail)).filter((name) => name.toLowerCase() !== primary.toLowerCase())
      ),
    };
  }

  const parts = artist.split(COLLAB_SPLIT).map(cleanName).filter(Boolean);
  if (parts.length < 2) return { primary: artist, featured: [] };

  const allSingleWord = parts.every((part) => part.split(/\s+/).length === 1);
  if (allSingleWord && !/[&+]|\band\b|\bx\b/i.test(artist)) {
    return { primary: artist, featured: [] };
  }

  const primary = parts[0];
  const featuredRaw = uniqueNames(parts.slice(1)).filter((name) => name.toLowerCase() !== primary.toLowerCase());
  // Long comma lists are almost always liner-note dumps (Qobuz performers).
  const featured = parts.length > 3 ? featuredRaw.slice(0, 1) : capFeatured(featuredRaw);
  return { primary, featured };
}

function splitCollabTail(tail: string): string[] {
  return tail.split(COLLAB_SPLIT).map(cleanName).filter(Boolean);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Merge credits from two catalog rows so Apple "&" partners survive Deezer-winning dedupe. */
export function mergeArtistCredits(
  winner: { artist?: string; featuredArtists?: string[]; albumArtist?: string },
  other: { artist?: string; featuredArtists?: string[]; albumArtist?: string }
): ArtistCredits {
  const left = splitArtistCredits(winner.artist ?? "", winner.albumArtist);
  const right = splitArtistCredits(other.artist ?? "", other.albumArtist);
  const primary = left.primary || right.primary;
  const featured = preferFeatured(
    [...(winner.featuredArtists ?? []), ...left.featured],
    [...(other.featuredArtists ?? []), ...right.featured]
  ).filter((name) => name.toLowerCase() !== primary.toLowerCase());
  return { primary, featured };
}
