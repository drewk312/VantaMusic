interface Station {
  id: string;
  token: string;
  userId: string | null;
  seed: ResolvedSeed;
  recommendations: AiRecommendation[];
  resolvedTracks: ResolvedTrack[];
  signals: StationSignal[];
  createdAt: string;
  lastPlayedAt: string | null;
}

interface StationSignal {
  type: 'thumbs_up' | 'thumbs_down' | 'skip' | 'complete';
  trackId: string;
  timestamp: number;
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

interface ResolvedTrack {
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

function randomId(): string {
  // Higher-entropy ID than Math.random for token safety
  const buf = new Uint8Array(12);
  globalThis.crypto?.getRandomValues?.(buf) ?? Math.random().toString(36).substring(2, 14);
  return Array.from(buf, b => b.toString(36).padStart(2, '0')).join('');
}

export class SessionStore {
  private stations = new Map<string, Station>();
  private stationsById = new Map<string, string>(); // stationId -> token
  private partnerTokens = new Map<string, { deviceModel: string; appVersion: string }>();
  private userTokens = new Map<string, { id: string; displayName: string; hasPremium: boolean }>();
  private feedback: Array<{
    userId: string;
    stationToken: string;
    trackId: string;
    trackName: string;
    artistName: string;
    isPositive: boolean;
    timestamp: string;
  }> = [];

  // ── Auth ──

  createPartnerToken(meta: { deviceModel: string; appVersion: string; ip: string }): string {
    const token = `pt_${randomId()}`;
    this.partnerTokens.set(token, {
      deviceModel: meta.deviceModel,
      appVersion: meta.appVersion,
    });
    return token;
  }

  validatePartnerToken(token: string): boolean {
    return this.partnerTokens.has(token);
  }

  async authenticate(username: string, _password: string): Promise<{
    token: string;
    id: string;
    displayName: string;
    hasPremium: boolean;
  } | null> {
    // Demo user for local development
    if (username === 'demo') {
      const existing = [...this.userTokens.values()].find(u => u.id === 'user_demo');
      if (existing) return { token: this.getTokenByUserId('user_demo')!, ...existing };
      const token = `ut_${randomId()}`;
      const user = { id: 'user_demo', displayName: 'Demo User', hasPremium: true };
      this.userTokens.set(token, user);
      return { token, ...user };
    }
    return null;
  }

  private getTokenByUserId(userId: string): string | undefined {
    for (const [tk, u] of this.userTokens) {
      if (u.id === userId) return tk;
    }
    return undefined;
  }

  validateUserToken(token: string): string | null {
    return this.userTokens.get(token)?.id ?? null;
  }

  // ── Stations ──

  async createStation(opts: {
    userId: string | null;
    seed: ResolvedSeed;
    recommendations: AiRecommendation[];
    resolvedTracks: ResolvedTrack[];
    trackCount: number;
  }): Promise<Station> {
    const id = `st_${randomId()}`;
    const token = `stok_${randomId()}`;
    const station: Station = {
      id,
      token,
      userId: opts.userId,
      seed: opts.seed,
      recommendations: opts.recommendations,
      resolvedTracks: opts.resolvedTracks,
      signals: [],
      createdAt: new Date().toISOString(),
      lastPlayedAt: null,
    };
    this.stations.set(token, station);
    this.stationsById.set(id, token);
    return station;
  }

  async getStation(token: string): Promise<Station | undefined> {
    return this.stations.get(token);
  }

  async appendTracks(
    stationId: string,
    recommendations: AiRecommendation[],
    resolvedTracks: ResolvedTrack[]
  ): Promise<void> {
    for (const station of this.stations.values()) {
      if (station.id === stationId) {
        station.recommendations.push(...recommendations);
        station.resolvedTracks.push(...resolvedTracks);
        return;
      }
    }
  }

  async listStations(userId: string): Promise<Station[]> {
    return [...this.stations.values()].filter(s => s.userId === userId);
  }

  async deleteStation(stationId: string): Promise<void> {
    const token = this.stationsById.get(stationId);
    if (token) {
      this.stations.delete(token);
      this.stationsById.delete(stationId);
    }
  }

  async updateLastPlayed(stationId: string, timestamp: string): Promise<void> {
    const token = this.stationsById.get(stationId);
    if (token) {
      const station = this.stations.get(token);
      if (station) station.lastPlayedAt = timestamp;
    }
  }

  // ── Feedback ──

  async addFeedback(opts: {
    userId: string;
    stationToken: string;
    trackId: string;
    isPositive: boolean;
  }): Promise<void> {
    const station = this.stations.get(opts.stationToken);
    const track = station?.resolvedTracks.find(t => t.id === opts.trackId) ??
                  station?.resolvedTracks.find(t => t.title === opts.trackId);
    this.feedback.push({
      userId: opts.userId,
      stationToken: opts.stationToken,
      trackId: opts.trackId,
      trackName: track?.title || 'Unknown',
      artistName: track?.artist || 'Unknown',
      isPositive: opts.isPositive,
      timestamp: new Date().toISOString(),
    });
  }

  async getFeedback(opts: {
    userId: string;
    stationToken: string;
    includePositive: boolean;
    includeNegative: boolean;
  }) {
    return this.feedback.filter(f => {
      if (f.userId !== opts.userId) return false;
      if (f.stationToken !== opts.stationToken) return false;
      if (f.isPositive && !opts.includePositive) return false;
      if (!f.isPositive && !opts.includeNegative) return false;
      return true;
    });
  }

  async addStationSignal(stationToken: string, signal: StationSignal): Promise<void> {
    const station = this.stations.get(stationToken);
    if (station) {
      station.signals.push(signal);
    }
  }
}
