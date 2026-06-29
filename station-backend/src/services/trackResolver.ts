import { generateStationTracks } from './gemini';
import { Logger } from '../utils/logger';

const log = new Logger('trackResolver');

interface ResolveSeedInput {
  rawText: string;
  overrideType?: string;
  eraStart?: number;
  eraEnd?: number;
  artistName?: string;
  trackTitle?: string;
  hintKeywords?: string[];
}

interface ResolvedSeed {
  kind: string;
  displayName: string;
  eraStart?: number;
  eraEnd?: number;
  artistName?: string;
  trackTitle?: string;
  keywords?: string[];
}

interface AiRecommendation {
  id: string;
  title: string;
  artist: string;
  album?: string;
  reasoning?: string;
}

interface ResolvedTrackResponse {
  id: string;
  title: string;
  artist: string;
  album: string;
  artworkUrl: string;
  streamUrl: string;
  durationSec: number;
  quality: string;
  source: string;
}

export class TrackResolver {
  /**
   * Resolve a raw text seed + optional structured fields into a normalized seed.
   */
  async resolveSeed(input: ResolveSeedInput): Promise<ResolvedSeed> {
    if (input.overrideType) {
      return {
        kind: input.overrideType,
        displayName: input.rawText,
        eraStart: input.eraStart,
        eraEnd: input.eraEnd,
        artistName: input.artistName,
        trackTitle: input.trackTitle,
        keywords: input.hintKeywords,
      };
    }

    // Auto-detect seed kind from text
    const kind = this.detectSeedKind(input.rawText);
    return {
      kind,
      displayName: input.rawText,
      eraStart: input.eraStart,
      eraEnd: input.eraEnd,
      artistName: input.artistName,
      trackTitle: input.trackTitle,
      keywords: input.hintKeywords,
    };
  }

  private detectSeedKind(rawText: string): string {
    const decadePattern = /\b(19[0-9]0s?|20[0-2]0s?)\b/;
    const moodPattern = /\b(upbeat|chill|relaxing|energetic|melancholy|happy|sad|angry|peaceful|focus)\b/i;
    const activityPattern = /\b(workout|study|sleep|party|cooking|driving|running|gym)\b/i;
    const genreKeywords = [
      'rock', 'pop', 'jazz', 'blues', 'hip hop', 'rap', 'rnb', 'r&b',
      'electronic', 'dance', 'country', 'folk', 'classical', 'metal',
      'indie', 'alternative', 'soul', 'funk', 'reggae', 'latin',
    ];

    if (decadePattern.test(rawText)) return 'ERA';
    if (activityPattern.test(rawText)) return 'ACTIVITY';
    if (moodPattern.test(rawText)) return 'MOOD';
    if (genreKeywords.some(k => rawText.toLowerCase().includes(k))) return 'GENRE';

    // If it looks like "artist - song" or "song by artist"
    if (rawText.includes(' - ') || rawText.toLowerCase().includes(' by ')) return 'TRACK';

    return 'GENRE'; // Default to genre for free text
  }

  /**
   * Generate a batch of track recommendations from Gemini, then resolve them
   * to playable tracks (with stream URLs).
   */
  async resolveBatch(recommendations: AiRecommendation[]): Promise<ResolvedTrackResponse[]> {
    // In development mode, return tracks without stream resolution
    // (the app will resolve streams locally via its SourceRegistry)
    const tracks: ResolvedTrackResponse[] = recommendations.map((rec, i) => ({
      id: rec.id || `gemini_${Date.now()}_${i}`,
      title: rec.title,
      artist: rec.artist,
      album: rec.album || 'Unknown Album',
      artworkUrl: '',
      streamUrl: '', // Client will resolve locally
      durationSec: 0,
      quality: 'ai_recommended',
      source: 'gemini_ai',
    }));

    log.info('Resolved %d AI recommendations to track stubs', tracks.length);
    return tracks;
  }

  /**
   * Search for tracks/artists/genres.
   * Delegates to Gemini for AI-powered interpretation, returns track results.
   */
  async search(opts: {
    query: string;
    type: string;
    hintType?: string;
    limit: number;
  }): Promise<SearchResultItem[]> {
    const seed = opts.hintType || this.detectSeedKind(opts.query);
    const geminiReq = {
      seed: opts.query,
      seedKind: seed,
      recentHistory: [],
      tasteProfile: { likes: [], dislikes: [] },
      count: Math.min(opts.limit, 15),
    };

    try {
      const geminiResult = await generateStationTracks(geminiReq as any);
      return (geminiResult.tracks || []).slice(0, opts.limit).map((t, i) => ({
        id: `search_${Date.now()}_${i}`,
        title: t.title,
        artist: t.artist,
        album: t.album || null,
        artworkUrl: null,
        durationSec: 0,
        quality: 'ai_suggested',
        matchReason: t.reason || 'gemini_match',
      }));
    } catch (err) {
      log.error('Gemini search failed for "%s": %O', opts.query, err);
      return [];
    }
  }
}

interface SearchResultItem {
  id: string;
  title: string;
  artist: string;
  album: string | null;
  artworkUrl: string | null;
  durationSec: number;
  quality: string;
  matchReason: string;
}
