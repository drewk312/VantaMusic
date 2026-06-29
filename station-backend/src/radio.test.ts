import { describe, it, expect } from 'vitest';

// Test radio station seed validation logic
interface StationSeed {
  kind: 'artist' | 'genre' | 'mood' | 'era';
  displayName: string;
  seedArtist?: string;
  seedGenre?: string;
  queryPhrases: string[];
}

function validateSeed(seed: Partial<StationSeed>): { valid: boolean; reason?: string } {
  if (!seed.kind) return { valid: false, reason: 'Missing kind' };
  if (!seed.displayName || seed.displayName.trim().length === 0) {
    return { valid: false, reason: 'Missing displayName' };
  }
  if (seed.kind === 'artist' && !seed.seedArtist) {
    return { valid: false, reason: 'Artist seed missing seedArtist' };
  }
  if (seed.kind === 'genre' && !seed.seedGenre) {
    return { valid: false, reason: 'Genre seed missing seedGenre' };
  }
  return { valid: true };
}

function buildStationName(seed: StationSeed): string {
  switch (seed.kind) {
    case 'artist': return `${seed.seedArtist} Radio`;
    case 'genre': return `${seed.seedGenre} Mix`;
    case 'mood': return `${seed.displayName} Vibes`;
    case 'era': return `${seed.displayName} Classics`;
    default: return seed.displayName;
  }
}

describe('validateSeed', () => {
  it('accepts valid artist seed', () => {
    const result = validateSeed({ kind: 'artist', displayName: 'Drake Radio', seedArtist: 'Drake', queryPhrases: [] });
    expect(result.valid).toBe(true);
  });

  it('accepts valid genre seed', () => {
    const result = validateSeed({ kind: 'genre', displayName: 'Hip-Hop Mix', seedGenre: 'hip-hop', queryPhrases: [] });
    expect(result.valid).toBe(true);
  });

  it('rejects missing kind', () => {
    const result = validateSeed({ displayName: 'Something' });
    expect(result.valid).toBe(false);
    expect(result.reason).toContain('kind');
  });

  it('rejects missing displayName', () => {
    const result = validateSeed({ kind: 'mood', displayName: '' });
    expect(result.valid).toBe(false);
  });

  it('rejects artist seed without seedArtist', () => {
    const result = validateSeed({ kind: 'artist', displayName: 'Radio', queryPhrases: [] });
    expect(result.valid).toBe(false);
    expect(result.reason).toContain('seedArtist');
  });
});

describe('buildStationName', () => {
  it('builds artist radio name', () => {
    const seed: StationSeed = { kind: 'artist', displayName: 'Drake Radio', seedArtist: 'Drake', queryPhrases: [] };
    expect(buildStationName(seed)).toBe('Drake Radio');
  });

  it('builds genre mix name', () => {
    const seed: StationSeed = { kind: 'genre', displayName: 'Pop Mix', seedGenre: 'pop', queryPhrases: [] };
    expect(buildStationName(seed)).toBe('pop Mix');
  });

  it('builds mood vibes name', () => {
    const seed: StationSeed = { kind: 'mood', displayName: 'Chill', queryPhrases: [] };
    expect(buildStationName(seed)).toBe('Chill Vibes');
  });
});
