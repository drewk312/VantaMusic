import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { randomBytes } from "node:crypto";
import { dirname } from "node:path";

interface Station {
  id: string;
  token: string;
  userId: string;
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

interface PartnerTokenRecord {
  deviceModel: string;
  appVersion: string;
  expiresAt: number;
}

interface UserTokenRecord {
  id: string;
  displayName: string;
  hasPremium: boolean;
  expiresAt: number;
}

interface ModelRequestWindow {
  startedAt: number;
  count: number;
}

interface FeedbackRecord {
  userId: string;
  stationToken: string;
  trackId: string;
  trackName: string;
  artistName: string;
  isPositive: boolean;
  timestamp: string;
}

interface StoreSnapshot {
  version: 1;
  stations: Station[];
  partnerTokens: Array<[string, PartnerTokenRecord]>;
  userTokens: Array<[string, UserTokenRecord]>;
  modelRequestWindows: Array<[string, ModelRequestWindow]>;
  feedback: FeedbackRecord[];
}

function randomId(): string {
  return randomBytes(18).toString("base64url");
}

export class SessionStore {
  private stations = new Map<string, Station>();
  private stationsById = new Map<string, string>(); // stationId -> token
  private partnerTokens = new Map<string, PartnerTokenRecord>();
  private userTokens = new Map<string, UserTokenRecord>();
  private modelRequestWindows = new Map<string, ModelRequestWindow>();
  private partnerLoginWindows = new Map<string, ModelRequestWindow>();
  private feedback: FeedbackRecord[] = [];

  constructor(
    private readonly options: {
      environment?: string;
      demoPassword?: string;
      partnerTokenTtlMs?: number;
      userTokenTtlMs?: number;
      partnerLoginsPerMinute?: number;
      maxPartnerTokens?: number;
      modelRequestsPerMinute?: number;
      persistencePath?: string;
    } = {}
  ) {
    this.loadSnapshot();
  }

  // ── Auth ──

  createPartnerToken(meta: { deviceModel: string; appVersion: string; ip: string }): string | null {
    const now = Date.now();
    if (!this.consumePartnerLoginQuota(meta.ip, now)) return null;
    this.compactAuthRecords(now);
    const token = `pt_${randomId()}`;
    this.partnerTokens.set(token, {
      deviceModel: meta.deviceModel,
      appVersion: meta.appVersion,
      expiresAt: now + (this.options.partnerTokenTtlMs ?? 60 * 60 * 1000),
    });
    this.enforcePartnerTokenCap();
    this.persist();
    return token;
  }

  validatePartnerToken(token: string): boolean {
    const record = this.partnerTokens.get(token);
    if (!record) return false;
    if (record.expiresAt <= Date.now()) {
      this.partnerTokens.delete(token);
      this.persist();
      return false;
    }
    return true;
  }

  async authenticate(username: string, password: string): Promise<{
    token: string;
    id: string;
    displayName: string;
    hasPremium: boolean;
  } | null> {
    const isDevelopment = this.options.environment === 'development';
    const demoPassword = this.options.demoPassword;
    if (isDevelopment && demoPassword && username === 'demo' && password === demoPassword) {
      const existing = [...this.userTokens.values()].find(u => u.id === 'user_demo');
      if (existing && existing.expiresAt > Date.now()) {
        const token = this.getTokenByUserId('user_demo');
        if (token) return { token, id: existing.id, displayName: existing.displayName, hasPremium: existing.hasPremium };
      }
      const token = `ut_${randomId()}`;
      const user = {
        id: 'user_demo',
        displayName: 'Demo User',
        hasPremium: true,
        expiresAt: Date.now() + (this.options.userTokenTtlMs ?? 8 * 60 * 60 * 1000),
      };
      this.userTokens.set(token, user);
      this.persist();
      return { token, id: user.id, displayName: user.displayName, hasPremium: user.hasPremium };
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
    const record = this.userTokens.get(token);
    if (!record) return null;
    if (record.expiresAt <= Date.now()) {
      this.userTokens.delete(token);
      this.persist();
      return null;
    }
    return record.id;
  }

  consumeModelQuota(userId: string, now = Date.now()): boolean {
    const limit = this.options.modelRequestsPerMinute ?? 5;
    const prior = this.modelRequestWindows.get(userId);
    if (!prior || now - prior.startedAt >= 60_000) {
      this.modelRequestWindows.set(userId, { startedAt: now, count: 1 });
      this.persist();
      return true;
    }
    if (prior.count >= limit) return false;
    prior.count += 1;
    this.persist();
    return true;
  }

  // ── Stations ──

  async createStation(opts: {
    userId: string;
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
    this.persist();
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
        this.persist();
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
      this.persist();
    }
  }

  async updateLastPlayed(stationId: string, timestamp: string): Promise<void> {
    const token = this.stationsById.get(stationId);
    if (token) {
      const station = this.stations.get(token);
      if (station) {
        station.lastPlayedAt = timestamp;
        this.persist();
      }
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
    this.persist();
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
      this.persist();
    }
  }

  private loadSnapshot(): void {
    const path = this.options.persistencePath?.trim();
    if (!path || !existsSync(path)) return;
    const snapshot = JSON.parse(readFileSync(path, "utf8")) as StoreSnapshot;
    if (snapshot.version !== 1 || !Array.isArray(snapshot.stations)) {
      throw new Error("Unsupported or corrupt session store snapshot");
    }
    this.stations = new Map(snapshot.stations.map(station => [station.token, station]));
    this.stationsById = new Map(snapshot.stations.map(station => [station.id, station.token]));
    this.partnerTokens = new Map(snapshot.partnerTokens ?? []);
    this.userTokens = new Map(snapshot.userTokens ?? []);
    this.modelRequestWindows = new Map(snapshot.modelRequestWindows ?? []);
    this.feedback = Array.isArray(snapshot.feedback) ? snapshot.feedback : [];
    if (this.compactAuthRecords(Date.now())) this.persist();
  }

  private consumePartnerLoginQuota(ip: string, now: number): boolean {
    const key = ip.trim() || "unknown";
    const limit = Math.max(1, this.options.partnerLoginsPerMinute ?? 10);
    const prior = this.partnerLoginWindows.get(key);
    if (!prior || now - prior.startedAt >= 60_000) {
      this.partnerLoginWindows.set(key, { startedAt: now, count: 1 });
    } else {
      if (prior.count >= limit) return false;
      prior.count += 1;
    }
    if (this.partnerLoginWindows.size > 2_000) {
      for (const [storedKey, window] of this.partnerLoginWindows) {
        if (now - window.startedAt >= 60_000) this.partnerLoginWindows.delete(storedKey);
      }
    }
    return true;
  }

  private compactAuthRecords(now: number): boolean {
    let changed = false;
    for (const [token, record] of this.partnerTokens) {
      if (record.expiresAt <= now) {
        this.partnerTokens.delete(token);
        changed = true;
      }
    }
    for (const [token, record] of this.userTokens) {
      if (record.expiresAt <= now) {
        this.userTokens.delete(token);
        changed = true;
      }
    }
    return this.enforcePartnerTokenCap() || changed;
  }

  private enforcePartnerTokenCap(): boolean {
    const limit = Math.max(1, this.options.maxPartnerTokens ?? 5_000);
    let changed = false;
    while (this.partnerTokens.size > limit) {
      let oldestToken: string | undefined;
      let oldestExpiry = Number.POSITIVE_INFINITY;
      for (const [token, record] of this.partnerTokens) {
        if (record.expiresAt < oldestExpiry) {
          oldestToken = token;
          oldestExpiry = record.expiresAt;
        }
      }
      if (!oldestToken) break;
      this.partnerTokens.delete(oldestToken);
      changed = true;
    }
    return changed;
  }

  private persist(): void {
    const path = this.options.persistencePath?.trim();
    if (!path) return;
    mkdirSync(dirname(path), { recursive: true });
    const snapshot: StoreSnapshot = {
      version: 1,
      stations: [...this.stations.values()],
      partnerTokens: [...this.partnerTokens.entries()],
      userTokens: [...this.userTokens.entries()],
      modelRequestWindows: [...this.modelRequestWindows.entries()],
      feedback: this.feedback,
    };
    const temporaryPath = `${path}.${process.pid}.tmp`;
    writeFileSync(temporaryPath, JSON.stringify(snapshot), { encoding: "utf8", mode: 0o600 });
    renameSync(temporaryPath, path);
  }
}
