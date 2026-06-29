import { describe, it, expect } from 'vitest';

// Inline track resolver logic matching the shape expected in server.ts
interface TrackSeed {
  title: string;
  artist: string;
  durationMs?: number;
}

interface ResolvedTrack {
  title: string;
  artist: string;
  streamUrl: string;
  bitrateKbps: number;
  durationMs: number;
}

function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .replace(/\(.*?\)/g, '')
    .replace(/\[.*?\]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function artistMatches(a: string, b: string): boolean {
  const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, '');
  return norm(a).includes(norm(b)) || norm(b).includes(norm(a));
}

function isValidStreamUrl(url: string): boolean {
  if (!url.startsWith('http://') && !url.startsWith('https://')) return false;
  if (url.includes('soundhelix')) return false;
  return true;
}

describe('Track normalizeTitle', () => {
  it('lowercases title', () => {
    expect(normalizeTitle('Hello World')).toBe('hello world');
  });

  it('removes parenthetical suffixes', () => {
    expect(normalizeTitle('Song (Official Audio)')).toBe('song');
  });

  it('removes bracket suffixes', () => {
    expect(normalizeTitle('Song [Lyrics Video]')).toBe('song');
  });

  it('collapses whitespace', () => {
    expect(normalizeTitle('Song   Title')).toBe('song title');
  });
});

describe('artistMatches', () => {
  it('matches exact same artist', () => {
    expect(artistMatches('Drake', 'Drake')).toBe(true);
  });

  it('matches featured artist substring', () => {
    expect(artistMatches('Drake ft. Travis Scott', 'Drake')).toBe(true);
  });

  it('does not match completely different artists', () => {
    expect(artistMatches('Drake', 'Adele')).toBe(false);
  });
});

describe('isValidStreamUrl', () => {
  it('accepts valid https URL', () => {
    expect(isValidStreamUrl('https://cdn.example.com/track.mp3')).toBe(true);
  });

  it('rejects blank URL', () => {
    expect(isValidStreamUrl('')).toBe(false);
  });

  it('rejects soundhelix demo URL', () => {
    expect(isValidStreamUrl('https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3')).toBe(false);
  });

  it('rejects relative URL', () => {
    expect(isValidStreamUrl('/tracks/song.mp3')).toBe(false);
  });
});
