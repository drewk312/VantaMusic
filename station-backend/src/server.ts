import express, { Request, Response } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { generateStationTracks } from './services/gemini';
import { TrackResolver } from './services/trackResolver';
import { SessionStore } from './services/sessionStore';
import { generateDjScript } from './services/gemini';
import { generateSpeechBase64 } from './services/elevenlabs';
import { Logger } from './utils/logger';
import * as dotenv from 'dotenv';
import { verifyFirebaseIdToken } from './services/firebaseAuth';

dotenv.config();

// ── Types ──

interface JsonRpcRequest {
  jsonrpc: '2.0';
  method: string;
  params?: Record<string, unknown>;
  id?: number | string;
  userToken?: string;
  partnerToken?: string;
}

interface JsonRpcResponse {
  jsonrpc: '2.0';
  result?: Record<string, unknown>;
  error?: JsonRpcError | null;
  id: number | string | null;
}

interface JsonRpcError {
  code: number;
  message: string;
  data?: unknown;
}

interface RequestContext {
  userId: string | null;
  partnerToken: string | null;
  firebaseAuthenticated: boolean;
  ip: string;
  userAgent: string;
}

interface MethodDefinition {
  description: string;
  params: Record<string, { type: string; required: boolean; description: string }>;
  auth: 'none' | 'optional' | 'required';
  requiresPartner?: boolean;
  handler: (params: Record<string, unknown>, context: RequestContext) => Promise<Record<string, unknown>>;
}

// ── Error codes ──

const ERROR_CODES = {
  INVALID_METHOD:       { code: 1001, message: 'Method not found' },
  INVALID_PARAMS:       { code: 1002, message: 'Invalid parameters' },
  MISSING_AUTH:         { code: 1003, message: 'Authentication required' },
  INVALID_AUTH:         { code: 1004, message: 'Invalid or expired token' },
  INVALID_SEED:         { code: 1005, message: 'Seed could not be resolved' },
  STATION_NOT_FOUND:    { code: 1006, message: 'Station does not exist' },
  RATE_LIMITED:         { code: 1007, message: 'Rate limit exceeded' },
  GEMINI_ERROR:         { code: 2001, message: 'AI generation failed' },
  RESOLVER_ERROR:       { code: 2002, message: 'Track resolution failed' },
  INTERNAL_ERROR:       { code: 2999, message: 'Internal server error' },
} as const;

// ── Method registry ──

const methods: Record<string, MethodDefinition> = {};

function registerMethod(name: string, def: MethodDefinition) {
  methods[name] = {
    ...def,
    requiresPartner: def.requiresPartner ?? !PUBLIC_METHODS.has(name),
  };
}

const PUBLIC_METHODS = new Set(['auth.partnerLogin', 'system.getCapabilities', 'system.ping']);
const MIN_STATION_TRACK_COUNT = 5;
const MAX_STATION_TRACK_COUNT = 30;
const MIN_REFILL_TRACK_COUNT = 5;
const MAX_REFILL_TRACK_COUNT = 20;

export function normalizeRequestedCount(
  value: unknown,
  defaultValue: number,
  min: number,
  max: number
): { value: number; clamped: boolean } | null {
  if (value == null) return { value: defaultValue, clamped: false };
  if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value)) return null;
  const normalized = Math.min(max, Math.max(min, value));
  return { value: normalized, clamped: normalized !== value };
}

// ── App bootstrap ──

const app = express();
const PORT = process.env.PORT || 3456;
const MAX_RPC_BATCH_SIZE = 20;
const MAX_STRING_PARAM_LENGTH = 8_192;
const configuredOrigins = (process.env.ALLOWED_ORIGINS ?? '')
  .split(',')
  .map(origin => origin.trim())
  .filter(Boolean);
const isProduction = process.env.NODE_ENV === 'production';

if (isProduction && configuredOrigins.length === 0) {
  throw new Error('ALLOWED_ORIGINS is required in production');
}
if (isProduction && !process.env.SESSION_STORE_PATH?.trim()) {
  throw new Error('SESSION_STORE_PATH is required in production');
}
if (isProduction && !process.env.FIREBASE_PROJECT_ID?.trim()) {
  throw new Error('FIREBASE_PROJECT_ID is required in production');
}
if (isProduction && !process.env.GEMINI_API_KEY?.trim()) {
  throw new Error('GEMINI_API_KEY is required in production');
}

// Middleware
app.use(helmet());
app.use(cors({
  origin: isProduction ? configuredOrigins : true,
  credentials: true,
}));
app.use(express.json({ limit: '256kb' }));

// Rate limiting
const limiter = rateLimit({
  windowMs: 60_000,
  max: 120,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (_req, res) => {
    res.status(429).json(makeError(null, ERROR_CODES.RATE_LIMITED));
  },
});
app.use(limiter);

// Services
const sessions = new SessionStore({
  environment: process.env.NODE_ENV,
  demoPassword: process.env.DEMO_PASSWORD,
  persistencePath: process.env.SESSION_STORE_PATH,
});
const resolver = new TrackResolver();
const log = new Logger('server');

// ── METHOD: auth.partnerLogin ──

registerMethod('auth.partnerLogin', {
  description: 'Exchange app credentials for a partner token. Call once on app start.',
  params: {
    deviceModel:  { type: 'string', required: true,  description: 'e.g. "android-generic"' },
    appVersion:   { type: 'string', required: true,  description: 'e.g. "1.4.0"' },
  },
  auth: 'none',
  handler: async (params, ctx) => {
    const partnerToken = sessions.createPartnerToken({
      deviceModel: params.deviceModel as string,
      appVersion: params.appVersion as string,
      ip: ctx.ip,
    });
    if (!partnerToken) throw ERROR_CODES.RATE_LIMITED;

    return {
      partnerToken,
      syncTime: Date.now(),
      serverTime: new Date().toISOString(),
    };
  },
});

// ── METHOD: auth.userLogin ──

registerMethod('auth.userLogin', {
  description: 'Authenticate a user. Returns a user token for subsequent requests.',
  params: {
    username: { type: 'string', required: true, description: 'Username or email' },
    password: { type: 'string', required: true, description: 'Password' },
  },
  auth: 'none',
  handler: async (params) => {
    const user = await sessions.authenticate(
      params.username as string,
      params.password as string,
    );
    if (!user) throw ERROR_CODES.INVALID_AUTH;
    return {
      userToken: user.token,
      userId: user.id,
      displayName: user.displayName,
      hasPremium: user.hasPremium,
    };
  },
});

// ── METHOD: station.createStation ──
// Core: seed text + optional structured fields -> Gemini curation
// -> track resolution -> persisted station

registerMethod('station.createStation', {
  description: 'Create a new station from a seed (text, artist, track, era, genre, mood, activity).',
  params: {
    seedText:     { type: 'string',  required: true,  description: 'Raw input: "90s", "trip hop", "workout music"' },
    seedType:     { type: 'string',  required: false, description: 'Resolved type: ERA|GENRE|ARTIST|TRACK|MOOD|ACTIVITY' },
    seedEraStart: { type: 'number',  required: false, description: 'Era start year (if ERA)' },
    seedEraEnd:   { type: 'number',  required: false, description: 'Era end year (if ERA)' },
    seedArtist:   { type: 'string',  required: false, description: 'Artist name (if ARTIST seed)' },
    seedTitle:    { type: 'string',  required: false, description: 'Track title (if TRACK seed)' },
    hintKeywords: { type: 'string[]', required: false, description: 'Extra keywords to guide generation' },
    trackCount:   { type: 'number',  required: false, description: 'Number of tracks to generate (default 30)' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    const seedText = params.seedText as string;
    if (!seedText) throw ERROR_CODES.INVALID_SEED;

    // Step 1: Resolve the seed into a structured query
    const seed = await resolver.resolveSeed({
      rawText: seedText,
      overrideType: params.seedType as string | undefined,
      eraStart: params.seedEraStart as number | undefined,
      eraEnd: params.seedEraEnd as number | undefined,
      artistName: params.seedArtist as string | undefined,
      trackTitle: params.seedTitle as string | undefined,
      hintKeywords: params.hintKeywords as string[] | undefined,
    });

    // Step 2: Generate curated track list from Gemini
    const normalizedCount = normalizeRequestedCount(
      params.trackCount,
      30,
      MIN_STATION_TRACK_COUNT,
      MAX_STATION_TRACK_COUNT
    );
    if (!normalizedCount) throw ERROR_CODES.INVALID_PARAMS;
    if (normalizedCount.clamped) log.warn('Clamped station trackCount for user=%s', ctx.userId);
    if (!sessions.consumeModelQuota(ctx.userId)) throw ERROR_CODES.RATE_LIMITED;
    const trackCount = normalizedCount.value;
    let recommendations: Array<{ id: string; title: string; artist: string; album?: string; reason?: string }> = [];

    try {
      const geminiResult = await generateStationTracks({
        seed: seedText,
        seedKind: seed.kind,
        seedEraStart: seed.eraStart,
        seedEraEnd: seed.eraEnd,
        seedArtist: seed.artistName,
        seedTitle: seed.trackTitle,
        hintKeywords: seed.keywords,
        recentHistory: [],
        tasteProfile: { likes: [], dislikes: [] },
        count: trackCount,
      });

      recommendations = (geminiResult.tracks || []).slice(0, trackCount).map((t: any, i: number) => ({
        id: `gemini_${i}`,
        title: t.title,
        artist: t.artist,
        album: t.album,
        reasoning: t.reason,
      }));

      if (!geminiResult.stationName && seed.displayName) {
        (geminiResult as any).stationName = seed.displayName;
      }
    } catch (err) {
      log.error('Gemini station generation failed: %O', err);
      throw ERROR_CODES.GEMINI_ERROR;
    }

    // Step 3: Resolve each recommendation to playable track stubs
    const resolvedTracks = await resolver.resolveBatch(recommendations);

    // Step 4: Persist the station
    const station = await sessions.createStation({
      userId: ctx.userId,
      seed,
      recommendations,
      resolvedTracks,
      trackCount: resolvedTracks.length,
    });

    return {
      stationId: station.id,
      stationToken: station.token,
      seed: {
        kind: seed.kind,
        displayName: seed.displayName,
        eraStart: seed.eraStart,
        eraEnd: seed.eraEnd,
        artistName: seed.artistName,
        trackTitle: seed.trackTitle,
      },
      tracks: resolvedTracks.map(t => ({
        trackToken: t.id,
        artistName: t.artist,
        trackName: t.title,
        albumName: t.album,
        artworkUrl: t.artworkUrl || null,
        durationSec: t.durationSec > 0 ? t.durationSec : null,
        sourceQuality: t.quality || null,
      })),
      trackCount: resolvedTracks.length,
    };
  },
});

// ── METHOD: station.getPlaylist ──

registerMethod('station.getPlaylist', {
  description: 'Get the playlist for an existing station. Use for initial load.',
  params: {
    stationToken: { type: 'string', required: true, description: 'Station token from createStation' },
    offset:       { type: 'number', required: false, description: 'Start index (default 0)' },
    limit:        { type: 'number', required: false, description: 'Max tracks to return (default 30)' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    const station = await sessions.getStation(params.stationToken as string);
    if (!station) throw ERROR_CODES.STATION_NOT_FOUND;
    if (station.userId !== ctx.userId) throw ERROR_CODES.INVALID_AUTH;

    const offset = (params.offset as number) || 0;
    const limit = (params.limit as number) || 30;
    const tracks = station.resolvedTracks.slice(offset, offset + limit);

    return {
      stationId: station.id,
      tracks: tracks.map(t => ({
        trackToken: t.id,
        artistName: t.artist,
        trackName: t.title,
        albumName: t.album,
        artworkUrl: t.artworkUrl || null,
        durationSec: t.durationSec > 0 ? t.durationSec : null,
        sourceQuality: t.quality || null,
      })),
      offset,
      limit,
      totalTracks: station.resolvedTracks.length,
      hasMore: offset + limit < station.resolvedTracks.length,
    };
  },
});

// ── METHOD: station.getPlaylistRefill ──

registerMethod('station.getPlaylistRefill', {
  description: 'Generate additional tracks for a station, avoiding already-played tracks.',
  params: {
    stationToken:   { type: 'string',   required: true,  description: 'Station token' },
    playedTrackIds: { type: 'string[]', required: true,  description: 'Track IDs already played (for dedup)' },
    count:          { type: 'number',   required: false, description: 'Tracks to generate (default 20)' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    const station = await sessions.getStation(params.stationToken as string);
    if (!station) throw ERROR_CODES.STATION_NOT_FOUND;
    if (station.userId !== ctx.userId) throw ERROR_CODES.INVALID_AUTH;

    const normalizedCount = normalizeRequestedCount(
      params.count,
      20,
      MIN_REFILL_TRACK_COUNT,
      MAX_REFILL_TRACK_COUNT
    );
    if (!normalizedCount) throw ERROR_CODES.INVALID_PARAMS;
    if (normalizedCount.clamped) log.warn('Clamped station refill count for user=%s', ctx.userId);
    if (!sessions.consumeModelQuota(ctx.userId)) throw ERROR_CODES.RATE_LIMITED;
    const count = normalizedCount.value;

    // Use Gemini refill (reuses generate with extra dedup context)
    try {
      const geminiResult = await generateStationTracks({
        seed: station.seed.displayName,
        seedKind: station.seed.kind,
        seedEraStart: station.seed.eraStart,
        seedEraEnd: station.seed.eraEnd,
        seedArtist: station.seed.artistName,
        seedTitle: station.seed.trackTitle,
        hintKeywords: station.seed.keywords,
        recentHistory: station.resolvedTracks.slice(-10).map(t => ({ title: t.title, artist: t.artist })),
        tasteProfile: { likes: [], dislikes: [] },
        count,
      });

      const recommendations = (geminiResult.tracks || []).slice(0, count).map((t: any, i: number) => ({
        id: `gemini_refill_${Date.now()}_${i}`,
        title: t.title,
        artist: t.artist,
        album: t.album,
        reasoning: t.reason || 'refill',
      }));

      const resolvedTracks = await resolver.resolveBatch(recommendations);

      await sessions.appendTracks(station.id, recommendations, resolvedTracks);

      return {
        tracks: resolvedTracks.map(t => ({
          trackToken: t.id,
          artistName: t.artist,
          trackName: t.title,
          albumName: t.album,
          artworkUrl: t.artworkUrl || null,
          durationSec: t.durationSec > 0 ? t.durationSec : null,
          sourceQuality: t.quality || null,
        })),
        trackCount: resolvedTracks.length,
        totalStationTracks: station.resolvedTracks.length + resolvedTracks.length,
      };
    } catch (err) {
      log.error('Gemini refill failed: %O', err);
      throw ERROR_CODES.GEMINI_ERROR;
    }
  },
});

// ── METHOD: station.listStations ──

registerMethod('station.listStations', {
  description: 'List all stations for the authenticated user.',
  params: {},
  auth: 'required',
  handler: async (_params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    const stations = await sessions.listStations(ctx.userId);

    return {
      stations: stations.map(s => ({
        stationId: s.id,
        stationToken: s.token,
        name: s.seed.displayName,
        seedKind: s.seed.kind,
        trackCount: s.resolvedTracks.length,
        createdAt: s.createdAt,
        lastPlayedAt: s.lastPlayedAt,
      })),
    };
  },
});

// ── METHOD: station.deleteStation ──

registerMethod('station.deleteStation', {
  description: 'Delete a station and all its data.',
  params: {
    stationToken: { type: 'string', required: true, description: 'Station token from createStation' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;

    const station = await sessions.getStation(params.stationToken as string);
    if (!station) throw ERROR_CODES.STATION_NOT_FOUND;
    if (station.userId && station.userId !== ctx.userId) throw ERROR_CODES.INVALID_AUTH;

    await sessions.deleteStation(station.id);
    return { success: true };
  },
});

// ── METHOD: music.search ──

registerMethod('music.search', {
  description: 'Search for tracks, artists, or genres. Returns structured results with type hints.',
  params: {
    query:      { type: 'string', required: true,  description: 'Search query' },
    searchType: { type: 'string', required: false, description: 'ARTIST|TRACK|GENRE|ALL (default ALL)' },
    limit:      { type: 'number', required: false, description: 'Max results (default 20)' },
  },
  auth: 'optional',
  handler: async (params, _ctx) => {
    const query = params.query as string;
    if (!query || query.trim().length === 0) throw ERROR_CODES.INVALID_PARAMS;

    const searchType = (params.searchType as string) || 'ALL';
    const limit = (params.limit as number) || 20;

    const results = await resolver.search({
      query,
      type: searchType,
      limit,
    });

    return {
      nearMatches: results.map(r => ({
        trackToken: r.id,
        artistName: r.artist,
        trackName: r.title,
        albumName: r.album,
        artworkUrl: r.artworkUrl,
        durationSec: r.durationSec,
        sourceQuality: r.quality,
        matchReason: r.matchReason,
      })),
      interpretedQuery: {
        original: query,
        detectedType: searchType,
        normalizedQuery: query,
      },
      resultCount: results.length,
    };
  },
});

// ── METHOD: music.getGenreStations ──

registerMethod('music.getGenreStations', {
  description: 'Get pre-defined genre categories for quick station creation.',
  params: {
    level:    { type: 'number', required: false, description: 'Category depth (default 0 = top level)' },
    parentId: { type: 'string', required: false, description: 'Parent category for sub-genres' },
  },
  auth: 'none',
  handler: async () => {
    const categories = [
      { id: 'pop',             name: 'Pop',             hasChildren: true,  quickMixToken: 'qm:pop' },
      { id: 'rock',            name: 'Rock',            hasChildren: true,  quickMixToken: 'qm:rock' },
      { id: 'hip-hop',         name: 'Hip-Hop',         hasChildren: true,  quickMixToken: 'qm:hip-hop' },
      { id: 'rnb',             name: 'R&B',             hasChildren: true,  quickMixToken: 'qm:rnb' },
      { id: 'electronic',      name: 'Electronic',      hasChildren: true,  quickMixToken: 'qm:electronic' },
      { id: 'jazz',            name: 'Jazz',            hasChildren: true,  quickMixToken: 'qm:jazz' },
      { id: 'classical',       name: 'Classical',       hasChildren: true,  quickMixToken: 'qm:classical' },
      { id: 'country',         name: 'Country',         hasChildren: true,  quickMixToken: 'qm:country' },
      { id: 'indie',           name: 'Indie',           hasChildren: true,  quickMixToken: 'qm:indie' },
      { id: 'metal',           name: 'Metal',           hasChildren: true,  quickMixToken: 'qm:metal' },
      { id: 'folk',            name: 'Folk',            hasChildren: true,  quickMixToken: 'qm:folk' },
      { id: 'latin',           name: 'Latin',           hasChildren: true,  quickMixToken: 'qm:latin' },
      { id: 'soul',            name: 'Soul',            hasChildren: true,  quickMixToken: 'qm:soul' },
      { id: 'reggae',          name: 'Reggae',          hasChildren: true,  quickMixToken: 'qm:reggae' },
      { id: 'blues',           name: 'Blues',           hasChildren: true,  quickMixToken: 'qm:blues' },
    ];

    return {
      categories: categories.map(g => ({
        stationToken: `genre:${g.id}`,
        categoryName: g.name,
        categoryToken: g.id,
        hasChildren: g.hasChildren,
        artworkUrl: null,
        quickMixToken: g.quickMixToken,
      })),
    };
  },
});

// ── METHOD: track.explain ──

registerMethod('track.explain', {
  description: 'Get the AI reasoning for why a track was included in a station.',
  params: {
    stationToken: { type: 'string', required: true, description: 'Station token from createStation' },
    trackId:      { type: 'string', required: true, description: 'Track id to explain' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    const station = await sessions.getStation(params.stationToken as string);
    if (!station) throw ERROR_CODES.STATION_NOT_FOUND;
    if (station.userId !== ctx.userId) throw ERROR_CODES.INVALID_AUTH;

    const trackId = params.trackId as string;
    const track = station.resolvedTracks.find(t => t.id === trackId);
    const recommendation = station.recommendations.find(r => r.id === trackId || r.title === trackId);

    const explanation = recommendation?.reasoning ||
      `"${track?.title || trackId}" by ${track?.artist || 'Unknown'} was included because it fits the "${station.seed.displayName}" station.`;

    return {
      trackId,
      explanation,
      seedKind: station.seed.kind,
      seedName: station.seed.displayName,
    };
  },
});

// ── METHOD: user.getFeedback ──

registerMethod('user.getFeedback', {
  description: 'Get the user\'s thumbs up/down history for a station.',
  params: {
    stationToken:    { type: 'string',  required: true, description: 'Station token from createStation' },
    includePositive: { type: 'boolean', required: false, description: 'Include thumbs up (default true)' },
    includeNegative: { type: 'boolean', required: false, description: 'Include thumbs down (default true)' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    const station = await sessions.getStation(params.stationToken as string);
    if (!station) throw ERROR_CODES.STATION_NOT_FOUND;
    if (station.userId !== ctx.userId) throw ERROR_CODES.INVALID_AUTH;

    const feedback = await sessions.getFeedback({
      userId: ctx.userId,
      stationToken: params.stationToken as string,
      includePositive: params.includePositive !== false,
      includeNegative: params.includeNegative !== false,
    });

    return {
      feedback: feedback.map(f => ({
        trackId: f.trackId,
        trackName: f.trackName,
        artistName: f.artistName,
        isPositive: f.isPositive,
        timestamp: f.timestamp,
      })),
    };
  },
});

// ── METHOD: user.addFeedback ──

registerMethod('user.addFeedback', {
  description: 'Thumb up or thumb down a track. Influences future generation for this station.',
  params: {
    stationToken: { type: 'string',  required: true, description: 'Station token from createStation' },
    trackToken:   { type: 'string',  required: true, description: 'Track token to rate' },
    isPositive:   { type: 'boolean', required: true, description: 'true = thumbs up, false = thumbs down' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    const station = await sessions.getStation(params.stationToken as string);
    if (!station) throw ERROR_CODES.STATION_NOT_FOUND;
    if (station.userId !== ctx.userId) throw ERROR_CODES.INVALID_AUTH;

    await sessions.addFeedback({
      userId: ctx.userId,
      stationToken: params.stationToken as string,
      trackId: params.trackToken as string,
      isPositive: params.isPositive as boolean,
    });

    await sessions.addStationSignal(params.stationToken as string, {
      type: params.isPositive ? 'thumbs_up' : 'thumbs_down',
      trackId: params.trackToken as string,
      timestamp: Date.now(),
    });

    return { success: true };
  },
});

// ── METHOD: dj.generateScript ──
// Kept from old API — generates DJ voiceover between tracks

registerMethod('dj.generateScript', {
  description: 'Generate a DJ voiceover script and optional TTS audio between tracks.',
  params: {
    stationName: { type: 'string', required: true, description: 'Name of the station' },
    recentTracks: { type: 'object[]', required: true, description: 'Recently played tracks [{title, artist}]' },
    nextTrack:    { type: 'object', required: true, description: 'Upcoming track {title, artist}' },
    tone:         { type: 'string', required: false, description: 'Tone hint (e.g. "energetic", "chill")' },
  },
  auth: 'required',
  handler: async (params, ctx) => {
    if (!ctx.userId) throw ERROR_CODES.MISSING_AUTH;
    if (!sessions.consumeModelQuota(ctx.userId)) throw ERROR_CODES.RATE_LIMITED;
    try {
      const script = await generateDjScript({
        stationName: params.stationName as string,
        recentTracks: params.recentTracks as Array<{ title: string; artist: string }>,
        nextTrack: params.nextTrack as { title: string; artist: string },
        tone: params.tone as string | undefined,
      });

      let audioBase64: string | undefined;
      try {
        audioBase64 = await generateSpeechBase64(script);
      } catch (ttsErr) {
        log.warn('TTS generation failed, returning script only: %O', ttsErr);
      }

      return { script, audioBase64 };
    } catch (err) {
      log.error('DJ script generation failed: %O', err);
      throw ERROR_CODES.GEMINI_ERROR;
    }
  },
});

// ── METHOD: system.getCapabilities ──

registerMethod('system.getCapabilities', {
  description: 'Query server capabilities. Call on app start to feature-gate UI.',
  params: {},
  auth: 'none',
  handler: async () => ({
    features: {
      aiCuration: true,
      trackExplain: true,
      genreBrowse: true,
      userFeedback: true,
      djVoiceover: true,
      maxTrackCount: 50,
      maxRefillCount: 30,
      supportedSeedTypes: ['ERA', 'GENRE', 'ARTIST', 'TRACK', 'MOOD', 'ACTIVITY'],
      supportedAudioFormats: ['FLAC', 'MP3_320', 'MP3_128'],
    },
    limits: {
      maxStationsPerUser: 100,
      maxTracksPerStation: 500,
      stationTtlHours: 720,
      rateLimitPerMinute: 120,
    },
    version: {
      apiVersion: '1.0.0',
      geminiModel: process.env.GEMINI_MODEL || 'gemini-2.0-flash',
      serverTime: new Date().toISOString(),
    },
  }),
});

// ── METHOD: system.ping ──

registerMethod('system.ping', {
  description: 'Health check. Returns server time and latency info.',
  params: {},
  auth: 'none',
  handler: async () => ({
    serverTime: new Date().toISOString(),
    status: 'ok',
  }),
});

// ── JSON-RPC helpers ──

function makeError(id: number | string | null, err: typeof ERROR_CODES[keyof typeof ERROR_CODES], data?: unknown): JsonRpcResponse {
  return {
    jsonrpc: '2.0',
    error: { code: err.code, message: err.message, data },
    id,
  };
}

function makeResult(id: number | string | null, result: Record<string, unknown>): JsonRpcResponse {
  return {
    jsonrpc: '2.0',
    result,
    error: null,
    id,
  };
}

export async function dispatch(req: JsonRpcRequest, context: RequestContext): Promise<JsonRpcResponse> {
  const { method, params = {}, id = null } = req;

  const methodDef = methods[method];
  if (!methodDef) {
    return makeError(id, ERROR_CODES.INVALID_METHOD, { method });
  }

  if (methodDef.requiresPartner && !context.firebaseAuthenticated && (!context.partnerToken || !sessions.validatePartnerToken(context.partnerToken))) {
    return makeError(id, ERROR_CODES.MISSING_AUTH);
  }

  if (methodDef.auth === 'required' && !context.userId) {
    return makeError(id, ERROR_CODES.MISSING_AUTH);
  }

  for (const [paramName, paramDef] of Object.entries(methodDef.params)) {
    const isPresent = Object.prototype.hasOwnProperty.call(params, paramName);
    if (paramDef.required && !isPresent) {
      return makeError(id, ERROR_CODES.INVALID_PARAMS, {
        missingParam: paramName,
        expectedType: paramDef.type,
      });
    }
    if (isPresent && !matchesParamType(params[paramName], paramDef.type)) {
      return makeError(id, ERROR_CODES.INVALID_PARAMS, {
        invalidParam: paramName,
        expectedType: paramDef.type,
      });
    }
  }

  try {
    const result = await methodDef.handler(params, context);
    return makeResult(id, result);
  } catch (err: unknown) {
    if (typeof err === 'object' && err !== null && 'code' in err) {
      return makeError(id, err as typeof ERROR_CODES[keyof typeof ERROR_CODES]);
    }
    log.error('Unhandled error in %s: %O', method, err);
    return makeError(id, ERROR_CODES.INTERNAL_ERROR);
  }
}

function matchesParamType(value: unknown, expectedType: string): boolean {
  if (expectedType.endsWith('[]')) {
    const itemType = expectedType.slice(0, -2);
    return Array.isArray(value) && value.length <= 100 && value.every(item => matchesParamType(item, itemType));
  }
  if (expectedType === 'string') {
    return typeof value === 'string' && value.length <= MAX_STRING_PARAM_LENGTH;
  }
  if (expectedType === 'number') {
    return typeof value === 'number' && Number.isFinite(value);
  }
  if (expectedType === 'boolean') {
    return typeof value === 'boolean';
  }
  if (expectedType === 'object') {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }
  return false;
}

async function requestContext(req: Request, rpcRequest: JsonRpcRequest): Promise<RequestContext> {
  const authorization = req.get('Authorization');
  const headerUserToken = authorization?.startsWith('Bearer ')
    ? authorization.slice('Bearer '.length).trim()
    : undefined;
  const userToken = rpcRequest.userToken?.trim() || headerUserToken;
  const partnerToken = rpcRequest.partnerToken?.trim() || req.get('X-Partner-Token')?.trim() || null;

  const firebaseProjectId = process.env.FIREBASE_PROJECT_ID?.trim();
  const firebaseUserId = firebaseProjectId && headerUserToken
    ? await verifyFirebaseIdToken(headerUserToken, firebaseProjectId)
    : null;
  const allowLegacyUserTokens = process.env.NODE_ENV === 'development' && !firebaseProjectId;
  return {
    userId: firebaseUserId ?? (allowLegacyUserTokens && userToken ? sessions.validateUserToken(userToken) : null),
    partnerToken,
    firebaseAuthenticated: firebaseUserId != null,
    ip: req.ip || 'unknown',
    userAgent: req.get('User-Agent') || 'unknown',
  };
}

// ── Routes ──

app.post('/jsonrpc', async (req: Request, res: Response) => {
  const isBatch = Array.isArray(req.body);
  const rawRequests: unknown[] = isBatch ? req.body : [req.body];
  if (rawRequests.length === 0 || rawRequests.length > MAX_RPC_BATCH_SIZE) {
    res.status(400).json(makeError(null, ERROR_CODES.INVALID_PARAMS, {
      reason: `Batch size must be between 1 and ${MAX_RPC_BATCH_SIZE}`,
    }));
    return;
  }

  const responses = await Promise.all(
    rawRequests.map(async (value) => {
      if (!isJsonRpcRequest(value)) {
        const invalidId = typeof value === 'object' && value !== null && 'id' in value
          ? (value as { id?: number | string }).id ?? null
          : null;
        return makeError(invalidId, ERROR_CODES.INVALID_PARAMS, {
          reason: 'Missing jsonrpc:"2.0" or method',
        });
      }

      if (isBatch && value.method === 'auth.partnerLogin') {
        return makeError(value.id ?? null, ERROR_CODES.RATE_LIMITED, {
          reason: 'auth.partnerLogin must be called as a single JSON-RPC request',
        });
      }

      const context = await requestContext(req, value);

      return dispatch(value, context);
    })
  );

  const singleResponse = responses[0];
  const status = !isBatch && singleResponse?.error &&
    (singleResponse.error.code === ERROR_CODES.MISSING_AUTH.code || singleResponse.error.code === ERROR_CODES.INVALID_AUTH.code)
    ? 401
    : 200;
  res.status(status).json(isBatch ? responses : singleResponse);
});

function isJsonRpcRequest(value: unknown): value is JsonRpcRequest {
  if (typeof value !== 'object' || value === null) return false;
  const request = value as Partial<JsonRpcRequest>;
  return request.jsonrpc === '2.0' && typeof request.method === 'string' && request.method.length > 0;
}

// Auto-generated API documentation
app.get('/methods', (_req: Request, res: Response) => {
  const docs = Object.entries(methods).map(([name, def]) => ({
    method: name,
    description: def.description,
    auth: def.auth,
    params: Object.entries(def.params).map(([paramName, paramDef]) => ({
      name: paramName,
      ...paramDef,
    })),
  }));

  res.json({
    jsonrpc: '2.0',
    endpoint: '/jsonrpc',
    methodCount: docs.length,
    methods: docs,
  });
});

// Health check
app.get('/health', (_req: Request, res: Response) => {
  res.json({ status: 'ok', uptime: process.uptime(), timestamp: new Date().toISOString() });
});

// Legacy backward-compat: keep old /api/v1/radio/generate and /api/v1/radio/dj
// redirecting through JSON-RPC internally
app.post('/api/v1/radio/generate', async (req: Request, res: Response) => {
  const body = req.body as Record<string, unknown>;
  const rpcReq: JsonRpcRequest = {
    jsonrpc: '2.0',
    method: 'station.createStation',
    params: {
      seedText: body.seed,
      seedType: body.seedKind,
      seedEraStart: body.seedEraStart,
      seedEraEnd: body.seedEraEnd,
      seedArtist: body.seedArtist,
      seedTitle: body.seedTitle,
      hintKeywords: body.hintKeywords,
      trackCount: body.count || 15,
    },
    id: 1,
  };
  const context = await requestContext(req, rpcReq);
  const result = await dispatch(rpcReq, context);
  res.json(result.error ? { error: result.error.message } : result.result);
});

app.post('/api/v1/radio/dj', async (req: Request, res: Response) => {
  const body = req.body as Record<string, unknown>;
  const rpcReq: JsonRpcRequest = {
    jsonrpc: '2.0',
    method: 'dj.generateScript',
    params: {
      stationName: body.stationName,
      recentTracks: body.recentTracks,
      nextTrack: body.nextTrack,
      tone: body.tone,
    },
    id: 1,
  };
  const context = await requestContext(req, rpcReq);
  const result = await dispatch(rpcReq, context);
  res.json(result.error ? { error: result.error.message } : result.result);
});

// ── Start ──

if (require.main === module) {
  app.listen(PORT, () => {
    log.info('Station backend listening on :%d', PORT);
    log.info('JSON-RPC endpoint: POST /jsonrpc');
    log.info('API docs:         GET  /methods');
    log.info('Health check:     GET  /health');
    log.info('Legacy REST:      POST /api/v1/radio/generate, /api/v1/radio/dj');
  });
}

export { app };
