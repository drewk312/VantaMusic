/** Amazon Next/classic relays often return CloudFront CENC without a content key.
 * Those URLs 302 into ExoPlayer and show up as "Source unavailable".
 *
 * Next payloads may send either a bare 32-hex AES key or `kid:key` (each half
 * 32 hex). Decrypt only needs the AES key half.
 */

export function amazonDecryptionKey(key: unknown): string | null {
  if (typeof key === "string") {
    const trimmed = key.trim();
    if (/^[a-f\d]{32}$/i.test(trimmed)) return trimmed.toLowerCase();
    const kidKey = /^([a-f\d]{32}):([a-f\d]{32})$/i.exec(trimmed);
    if (kidKey) return kidKey[2].toLowerCase();
    return null;
  }
  if (Array.isArray(key)) {
    for (const entry of key) {
      const parsed = amazonDecryptionKey(entry);
      if (parsed) return parsed;
    }
  }
  return null;
}

/** Prefer `key`, then `key_specs` entries from Amazon Next/classic payloads. */
export function amazonPayloadDecryptionKey(payload: Record<string, unknown> | null | undefined): string | null {
  if (!payload) return null;
  return amazonDecryptionKey(payload.key) ?? amazonDecryptionKey(payload.key_specs);
}

export function isGatewayDecryptUrl(url: string): boolean {
  return /\/api\/decrypt|\/audio\//i.test(url);
}

function looksLikeAmazonCdn(url: string): boolean {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return host.includes("cloudfront.net") ||
      host.includes("amazonaavn") ||
      host.includes("aiv-cdn.net") ||
      host.endsWith("amazon.com");
  } catch {
    return false;
  }
}

/**
 * Raw Amazon CDN URLs are Widevine/CENC. Keep gateway proxy / MPD URLs, and
 * keep CDN URLs only when we have a 32-char hex key to wrap through decrypt.
 */
export function amazonDirectUrlIsLocked(
  provider: string | undefined,
  url: string,
  key?: unknown
): boolean {
  if ((provider ?? "").toLowerCase() !== "amazon") return false;
  if (!url.trim()) return true;
  if (isGatewayDecryptUrl(url) || /\/manifest\/mpd/i.test(url)) return false;
  if (amazonDecryptionKey(key) != null) return false;
  return looksLikeAmazonCdn(url);
}
