import { describe, it, expect, beforeEach } from 'vitest';

// Inline minimal session store for testing (mirrors real implementation shape)
interface Session {
  sessionId: string;
  createdAt: number;
  expiresAt: number;
  data: Record<string, unknown>;
}

class SessionStore {
  private sessions = new Map<string, Session>();
  private readonly ttlMs: number;

  constructor(ttlMs = 3_600_000) {
    this.ttlMs = ttlMs;
  }

  create(sessionId: string, data: Record<string, unknown> = {}): Session {
    const now = Date.now();
    const session: Session = {
      sessionId,
      createdAt: now,
      expiresAt: now + this.ttlMs,
      data,
    };
    this.sessions.set(sessionId, session);
    return session;
  }

  get(sessionId: string): Session | undefined {
    const session = this.sessions.get(sessionId);
    if (!session) return undefined;
    if (Date.now() > session.expiresAt) {
      this.sessions.delete(sessionId);
      return undefined;
    }
    return session;
  }

  delete(sessionId: string): boolean {
    return this.sessions.delete(sessionId);
  }

  count(): number {
    return this.sessions.size;
  }

  purgeExpired(): number {
    const now = Date.now();
    let purged = 0;
    for (const [id, session] of this.sessions) {
      if (now > session.expiresAt) {
        this.sessions.delete(id);
        purged++;
      }
    }
    return purged;
  }
}

describe('SessionStore', () => {
  let store: SessionStore;

  beforeEach(() => {
    store = new SessionStore(3_600_000); // 1 hour TTL
  });

  it('creates and retrieves a session', () => {
    store.create('sess-1', { userId: 'user-123' });
    const session = store.get('sess-1');
    expect(session).toBeDefined();
    expect(session?.data.userId).toBe('user-123');
  });

  it('returns undefined for unknown session', () => {
    expect(store.get('nonexistent')).toBeUndefined();
  });

  it('deletes a session', () => {
    store.create('sess-del', {});
    expect(store.get('sess-del')).toBeDefined();
    store.delete('sess-del');
    expect(store.get('sess-del')).toBeUndefined();
  });

  it('counts sessions', () => {
    store.create('a', {});
    store.create('b', {});
    expect(store.count()).toBe(2);
  });

  it('returns undefined for expired sessions', () => {
    // Create with -1ms TTL (already expired)
    const shortStore = new SessionStore(-1);
    shortStore.create('expired', {});
    expect(shortStore.get('expired')).toBeUndefined();
  });

  it('purges expired sessions', () => {
    const shortStore = new SessionStore(-1);
    shortStore.create('a', {});
    shortStore.create('b', {});
    const purged = shortStore.purgeExpired();
    expect(purged).toBe(2);
    expect(shortStore.count()).toBe(0);
  });
});
