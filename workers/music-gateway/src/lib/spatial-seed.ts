/**
 * Known tracks / albums / ISRCs widely released in Dolby Atmos / Spatial Audio.
 *
 * Free search providers don't expose a spatial flag, so this seed list is used
 * to surface real songs when the user searches for Atmos/spatial/surround formats.
 * It is intentionally conservative: only well-known commercial releases.
 */
import type { GatewayTrack } from "../types";

export interface SpatialSeedEntry {
  title?: string;
  artist?: string;
  album?: string;
  isrc?: string;
  spatialFormat: "dolby_atmos" | "spatial_audio" | "surround" | "hi_res";
}

export const SPATIAL_SEED: SpatialSeedEntry[] = [
  { title: "Blinding Lights", artist: "The Weeknd", spatialFormat: "dolby_atmos" },
  { title: "Save Your Tears", artist: "The Weeknd", spatialFormat: "dolby_atmos" },
  { title: "Starboy", artist: "The Weeknd", spatialFormat: "dolby_atmos" },
  { title: "After Hours", artist: "The Weeknd", spatialFormat: "dolby_atmos" },
  { title: "Die For You", artist: "The Weeknd", spatialFormat: "dolby_atmos" },
  { title: "Bad Guy", artist: "Billie Eilish", spatialFormat: "dolby_atmos" },
  { title: "Happier Than Ever", artist: "Billie Eilish", spatialFormat: "dolby_atmos" },
  { title: "Bury a Friend", artist: "Billie Eilish", spatialFormat: "dolby_atmos" },
  { title: "Therefore I Am", artist: "Billie Eilish", spatialFormat: "dolby_atmos" },
  { title: "Bohemian Rhapsody", artist: "Queen", spatialFormat: "dolby_atmos" },
  { title: "Don't Stop Me Now", artist: "Queen", spatialFormat: "dolby_atmos" },
  { title: "We Will Rock You", artist: "Queen", spatialFormat: "dolby_atmos" },
  { title: "Killer Queen", artist: "Queen", spatialFormat: "dolby_atmos" },
  { title: "Let It Be", artist: "The Beatles", spatialFormat: "dolby_atmos" },
  { title: "Come Together", artist: "The Beatles", spatialFormat: "dolby_atmos" },
  { title: "Here Comes The Sun", artist: "The Beatles", spatialFormat: "dolby_atmos" },
  { title: "Something", artist: "The Beatles", spatialFormat: "dolby_atmos" },
  { title: "Mr. Blue Sky", artist: "Electric Light Orchestra", spatialFormat: "dolby_atmos" },
  { title: "Don't Bring Me Down", artist: "Electric Light Orchestra", spatialFormat: "dolby_atmos" },
  { title: "Sweet Child O' Mine", artist: "Guns N' Roses", spatialFormat: "dolby_atmos" },
  { title: "Welcome to the Jungle", artist: "Guns N' Roses", spatialFormat: "dolby_atmos" },
  { title: "November Rain", artist: "Guns N' Roses", spatialFormat: "dolby_atmos" },
  { title: "Hotel California", artist: "Eagles", spatialFormat: "dolby_atmos" },
  { title: "Take It Easy", artist: "Eagles", spatialFormat: "dolby_atmos" },
  { title: "Life in the Fast Lane", artist: "Eagles", spatialFormat: "dolby_atmos" },
  { title: "Rocket Man", artist: "Elton John", spatialFormat: "dolby_atmos" },
  { title: "Tiny Dancer", artist: "Elton John", spatialFormat: "dolby_atmos" },
  { title: "Your Song", artist: "Elton John", spatialFormat: "dolby_atmos" },
  { title: "Dreams", artist: "Fleetwood Mac", spatialFormat: "dolby_atmos" },
  { title: "The Chain", artist: "Fleetwood Mac", spatialFormat: "dolby_atmos" },
  { title: "Go Your Own Way", artist: "Fleetwood Mac", spatialFormat: "dolby_atmos" },
  { title: "Rhiannon", artist: "Fleetwood Mac", spatialFormat: "dolby_atmos" },
  { title: "Levitating", artist: "Dua Lipa", spatialFormat: "dolby_atmos" },
  { title: "Don't Start Now", artist: "Dua Lipa", spatialFormat: "dolby_atmos" },
  { title: "New Rules", artist: "Dua Lipa", spatialFormat: "dolby_atmos" },
  { title: "As It Was", artist: "Harry Styles", spatialFormat: "dolby_atmos" },
  { title: "Watermelon Sugar", artist: "Harry Styles", spatialFormat: "dolby_atmos" },
  { title: "Adore You", artist: "Harry Styles", spatialFormat: "dolby_atmos" },
  { title: "Anti-Hero", artist: "Taylor Swift", spatialFormat: "dolby_atmos" },
  { title: "Cruel Summer", artist: "Taylor Swift", spatialFormat: "dolby_atmos" },
  { title: "Karma", artist: "Taylor Swift", spatialFormat: "dolby_atmos" },
  { title: "Lover", artist: "Taylor Swift", spatialFormat: "dolby_atmos" },
  { title: "Midnight Rain", artist: "Taylor Swift", spatialFormat: "dolby_atmos" },
  { title: "Uptown Funk", artist: "Mark Ronson", spatialFormat: "dolby_atmos" },
  { title: "24K Magic", artist: "Bruno Mars", spatialFormat: "dolby_atmos" },
  { title: "Locked Out of Heaven", artist: "Bruno Mars", spatialFormat: "dolby_atmos" },
  { title: "That's What I Like", artist: "Bruno Mars", spatialFormat: "dolby_atmos" },
  { title: "Shape of You", artist: "Ed Sheeran", spatialFormat: "dolby_atmos" },
  { title: "Perfect", artist: "Ed Sheeran", spatialFormat: "dolby_atmos" },
  { title: "Thinking Out Loud", artist: "Ed Sheeran", spatialFormat: "dolby_atmos" },
  { title: "Bad Romance", artist: "Lady Gaga", spatialFormat: "dolby_atmos" },
  { title: "Poker Face", artist: "Lady Gaga", spatialFormat: "dolby_atmos" },
  { title: "Shallow", artist: "Lady Gaga", spatialFormat: "dolby_atmos" },
  { title: "Just Dance", artist: "Lady Gaga", spatialFormat: "dolby_atmos" },
  { title: "Rolling in the Deep", artist: "Adele", spatialFormat: "dolby_atmos" },
  { title: "Someone Like You", artist: "Adele", spatialFormat: "dolby_atmos" },
  { title: "Easy On Me", artist: "Adele", spatialFormat: "dolby_atmos" },
  { title: "Set Fire to the Rain", artist: "Adele", spatialFormat: "dolby_atmos" },
  { title: "Smells Like Teen Spirit", artist: "Nirvana", spatialFormat: "dolby_atmos" },
  { title: "Come As You Are", artist: "Nirvana", spatialFormat: "dolby_atmos" },
  { title: "Lithium", artist: "Nirvana", spatialFormat: "dolby_atmos" },
  { title: "Heart-Shaped Box", artist: "Nirvana", spatialFormat: "dolby_atmos" },
  { title: "Everlong", artist: "Foo Fighters", spatialFormat: "dolby_atmos" },
  { title: "The Pretender", artist: "Foo Fighters", spatialFormat: "dolby_atmos" },
  { title: "Best of You", artist: "Foo Fighters", spatialFormat: "dolby_atmos" },
  { title: "Learn to Fly", artist: "Foo Fighters", spatialFormat: "dolby_atmos" },
  { title: "Black Hole Sun", artist: "Soundgarden", spatialFormat: "dolby_atmos" },
  { title: "Superunknown", artist: "Soundgarden", spatialFormat: "dolby_atmos" },
  { title: "Spoonman", artist: "Soundgarden", spatialFormat: "dolby_atmos" },
  { title: "Californication", artist: "Red Hot Chili Peppers", spatialFormat: "dolby_atmos" },
  { title: "Under the Bridge", artist: "Red Hot Chili Peppers", spatialFormat: "dolby_atmos" },
  { title: "Scar Tissue", artist: "Red Hot Chili Peppers", spatialFormat: "dolby_atmos" },
  { title: "Otherside", artist: "Red Hot Chili Peppers", spatialFormat: "dolby_atmos" },
  { title: "Wonderwall", artist: "Oasis", spatialFormat: "dolby_atmos" },
  { title: "Don't Look Back in Anger", artist: "Oasis", spatialFormat: "dolby_atmos" },
  { title: "Champagne Supernova", artist: "Oasis", spatialFormat: "dolby_atmos" },
  { title: "Creep", artist: "Radiohead", spatialFormat: "dolby_atmos" },
  { title: "Karma Police", artist: "Radiohead", spatialFormat: "dolby_atmos" },
  { title: "Paranoid Android", artist: "Radiohead", spatialFormat: "dolby_atmos" },
  { title: "No Surprises", artist: "Radiohead", spatialFormat: "dolby_atmos" },
  { title: "Do I Wanna Know?", artist: "Arctic Monkeys", spatialFormat: "dolby_atmos" },
  { title: "R U Mine?", artist: "Arctic Monkeys", spatialFormat: "dolby_atmos" },
  { title: "505", artist: "Arctic Monkeys", spatialFormat: "dolby_atmos" },
  { title: "I Wanna Be Yours", artist: "Arctic Monkeys", spatialFormat: "dolby_atmos" },
  { title: "Take Five", artist: "Dave Brubeck", spatialFormat: "dolby_atmos" },
  { title: "Blue in Green", artist: "Miles Davis", spatialFormat: "dolby_atmos" },
  { title: "So What", artist: "Miles Davis", spatialFormat: "dolby_atmos" },
  { title: "Fly Me to the Moon", artist: "Frank Sinatra", spatialFormat: "dolby_atmos" },
  { title: "My Way", artist: "Frank Sinatra", spatialFormat: "dolby_atmos" },
  { title: "That's Life", artist: "Frank Sinatra", spatialFormat: "dolby_atmos" },
  { title: "New York, New York", artist: "Frank Sinatra", spatialFormat: "dolby_atmos" },
  // Hi-Res seed entries
  { title: "Hotel California", artist: "Eagles", spatialFormat: "hi_res" },
  { title: "Stairway to Heaven", artist: "Led Zeppelin", spatialFormat: "hi_res" },
  { title: "Money", artist: "Pink Floyd", spatialFormat: "hi_res" },
  { title: "Time", artist: "Pink Floyd", spatialFormat: "hi_res" },
  { title: "Wish You Were Here", artist: "Pink Floyd", spatialFormat: "hi_res" },
  { title: "Blackbird", artist: "The Beatles", spatialFormat: "hi_res" },
  { title: "Here Comes the Sun", artist: "The Beatles", spatialFormat: "hi_res" },
  { title: "Let It Be", artist: "The Beatles", spatialFormat: "hi_res" },
  { title: "Aja", artist: "Steely Dan", spatialFormat: "hi_res" },
  { title: "Peg", artist: "Steely Dan", spatialFormat: "hi_res" },
  { title: "Deacon Blues", artist: "Steely Dan", spatialFormat: "hi_res" },
  { title: "Take Five", artist: "Dave Brubeck", spatialFormat: "hi_res" },
];

function normalize(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function enrichSpatialFromSeed(track: GatewayTrack): GatewayTrack {
  const seed = isKnownSpatialTrack(track.title, track.artist);
  if (!seed) return track;
  return {
    ...track,
    isDolbyAtmos: seed.spatialFormat === "dolby_atmos" || track.isDolbyAtmos,
    isSpatialAudio: ["dolby_atmos", "spatial_audio"].includes(seed.spatialFormat) || track.isSpatialAudio,
    isSurround: ["dolby_atmos", "spatial_audio", "surround"].includes(seed.spatialFormat) || track.isSurround,
    isHiRes: seed.spatialFormat === "hi_res" || track.isHiRes,
  };
}

export function isKnownSpatialTrack(title: string, artist: string): SpatialSeedEntry | undefined {
  const nTitle = normalize(title);
  const nArtist = normalize(artist);
  return SPATIAL_SEED.find((entry) => {
    const eTitle = normalize(entry.title ?? "");
    const eArtist = normalize(entry.artist ?? "");
    if (!eTitle || !eArtist) return false;
    return nTitle === eTitle && nArtist === eArtist;
  });
}

export function isKnownSpatialTrackWithFormat(
  title: string,
  artist: string,
  format: SpatialSeedEntry["spatialFormat"] | SpatialSeedEntry["spatialFormat"][]
): boolean {
  const nTitle = normalize(title);
  const nArtist = normalize(artist);
  const formats = Array.isArray(format) ? format : [format];
  return SPATIAL_SEED.some((entry) => {
    const eTitle = normalize(entry.title ?? "");
    const eArtist = normalize(entry.artist ?? "");
    if (!eTitle || !eArtist) return false;
    return nTitle === eTitle && nArtist === eArtist && formats.includes(entry.spatialFormat);
  });
}

