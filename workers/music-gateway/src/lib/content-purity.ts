import type { GatewayTrack } from '../types';
import { isAllowedVocalTrack } from './vocal-recording';

/** Reject live streams, karaoke, tribute, test tones, and other non-studio inventory. */
export function isAllowedTrack(track: GatewayTrack, query = ''): boolean {
  if (!isAllowedVocalTrack(track, query)) return false;

  const title = (track.title ?? '').toLowerCase();
  const artist = (track.artist ?? '').toLowerCase();
  const album = (track.album ?? '').toLowerCase();
  const durationSec = track.duration ?? 0;

  if (durationSec <= 0 || durationSec > 14_400) return false; // >4h or invalid

  const liveKeywords = [
    'beats to relax',
    'beats to study',
    'beats to sleep',
    'beats to chill',
    'radio 🌌',
    'live stream',
    '24/7',
    'streaming now',
    'playing now',
  ];
  if (liveKeywords.some((k) => title.includes(k))) return false;

  const bannedArtists = [
    'lofi girl',
    'steezyasfuck',
    'chilledcow',
    'dolby',
    'thx',
    'audio check',
    'sound test',
    'speaker test',
    'home theater',
    'surround demo',
    'atmos demo',
  ];
  if (bannedArtists.some((a) => artist.includes(a))) return false;

  const junkKeywords = [
    'test',
    'calibration',
    'calibrate',
    'sampler',
    'demo',
    'tone',
    'tones',
    'frequency sweep',
    'sine wave',
    'pink noise',
    'white noise',
    'audio test',
    'speaker test',
    'channel check',
    'surround test',
    'atmos test',
    'dolby test',
    'spatial test',
    '5.1 test',
    '7.1 test',
    'acapella',
    'a cappella',
    '8-bit',
    '8bit',
    'chiptune',
    'midi',
    'fl studio',
    'garageband',
    'piano off',
    'piano project',
    'original mix',
    'emulation',
    'weeknights',
    'retro weeknd',
  ];
  const stack = `${title} ${artist} ${album}`;
  if (junkKeywords.some((k) => stack.includes(k))) return false;

  // For format-only spatial/audio-tech queries, require a real song identity.
  // Tracks whose entire identity is the format keyword (no recognizable artist)
  // are usually test tones or metadata spam.
  if (isFormatOnlyQuery(query) && artist.trim().length === 0) {
    return false;
  }

  return true;
}

export function filterTracks(tracks: GatewayTrack[], query = ''): GatewayTrack[] {
  return tracks.filter((track) => isAllowedTrack(track, query));
}

export function isFormatOnlyQuery(query: string): boolean {
  const q = query.toLowerCase().trim();
  if (!q) return false;
  const formatKeywords = [
    'atmos',
    'dolby',
    'dolby atmos',
    'spatial',
    'spatial audio',
    'surround',
    '5.1',
    '7.1',
    'hi-res',
    'hires',
    'hi res',
    'lossless',
    'flac',
    'dsd',
    '192khz',
    '96khz',
  ];
  // Normalize hyphenated variants like "hi-res" /> "hi res" and check whole phrases.
  const normalized = q.replace(/-/g, ' ');
  if (formatKeywords.includes(normalized)) return true;
  const singleWordKeywords = formatKeywords.filter((k) => !k.includes(' '));
  const tokens = normalized.split(/\s+/).filter((t) => t.length > 0);
  const nonFormatTokens = tokens.filter((t) => !singleWordKeywords.includes(t));
  return nonFormatTokens.length === 0;
}

