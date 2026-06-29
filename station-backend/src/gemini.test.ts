import { describe, it, expect } from 'vitest';

// Test Gemini/ElevenLabs service guard logic
function requireApiKey(key: string | undefined, serviceName: string): { ok: boolean; status: number; message: string } {
  if (!key || key.trim().length === 0) {
    return { ok: false, status: 503, message: `${serviceName} API key not configured` };
  }
  return { ok: true, status: 200, message: 'ok' };
}

function sanitizePrompt(prompt: string, maxLength = 2000): string {
  return prompt.replace(/<[^>]*>/g, '').trim().slice(0, maxLength);
}

function isRateLimited(requestsThisMinute: number, maxPerMinute: number): boolean {
  return requestsThisMinute >= maxPerMinute;
}

describe('requireApiKey', () => {
  it('returns 503 when key is undefined', () => {
    const result = requireApiKey(undefined, 'Gemini');
    expect(result.ok).toBe(false);
    expect(result.status).toBe(503);
    expect(result.message).toContain('Gemini');
  });

  it('returns 503 when key is empty string', () => {
    const result = requireApiKey('', 'ElevenLabs');
    expect(result.ok).toBe(false);
    expect(result.status).toBe(503);
  });

  it('returns 503 when key is only whitespace', () => {
    const result = requireApiKey('   ', 'Gemini');
    expect(result.ok).toBe(false);
    expect(result.status).toBe(503);
  });

  it('returns 200 when key is present', () => {
    const result = requireApiKey('sk-abc123', 'Gemini');
    expect(result.ok).toBe(true);
    expect(result.status).toBe(200);
  });
});

describe('sanitizePrompt', () => {
  it('strips HTML tags', () => {
    // The regex /<[^>]*>/g removes tag delimiters only, not inner text nodes,
    // so <script>alert(1)</script>hello → alert(1)hello.
    expect(sanitizePrompt('<script>alert(1)</script>hello')).toBe('alert(1)hello');
  });

  it('trims whitespace', () => {
    expect(sanitizePrompt('  hello  ')).toBe('hello');
  });

  it('truncates to maxLength', () => {
    const long = 'a'.repeat(3000);
    expect(sanitizePrompt(long, 2000).length).toBe(2000);
  });

  it('preserves normal text', () => {
    expect(sanitizePrompt('Play some jazz')).toBe('Play some jazz');
  });
});

describe('isRateLimited', () => {
  it('returns false when under limit', () => {
    expect(isRateLimited(5, 10)).toBe(false);
  });

  it('returns true when at limit', () => {
    expect(isRateLimited(10, 10)).toBe(true);
  });

  it('returns true when over limit', () => {
    expect(isRateLimited(15, 10)).toBe(true);
  });
});
