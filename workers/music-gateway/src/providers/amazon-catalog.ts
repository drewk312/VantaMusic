import type { GatewayTrack } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";

const ORIGIN = "https://music.amazon.com";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
type ObjectValue = Record<string, any>;
async function readJson(url: string, init: RequestInit = {}): Promise<ObjectValue | null> {
  try {
    const r = await fetch(url, { ...init, redirect: "manual", signal: AbortSignal.timeout(4000) });
    if (!r.ok || !r.body) { await r.body?.cancel(); return null; }
    const reader = r.body.getReader(); let text = "", size = 0; const decoder = new TextDecoder();
    try { while (true) { const next = await reader.read(); if (next.done) break; size += next.value.length;
      if (size > 2_000_000) { await reader.cancel(); return null; } text += decoder.decode(next.value, { stream: true });
    } } finally { reader.releaseLock(); }
    return JSON.parse(text + decoder.decode());
  } catch { return null; }
}

function label(value: unknown, depth = 0): string {
  if (depth > 3) return "";
  if (typeof value === "string") return value.trim();
  if (value && typeof value === "object") {
    const v = value as ObjectValue;
    return typeof v.text === "string" ? v.text.trim() : typeof v.value === "string" ? v.value.trim() : label(v.defaultValue ?? v.observer?.defaultValue, depth + 1);
  }
  return "";
}

export function amazonCatalogTracks(data: unknown, limit = 15): GatewayTrack[] {
  const found = new Map<string, GatewayTrack>(); let visited = 0;
  const walk = (value: unknown, depth = 0) => {
    if (!value || typeof value !== "object" || depth > 24 || ++visited > 20000 || found.size >= limit) return;
    const item = value as ObjectValue;
    const link = item.primaryTextLink?.deeplink || item.primaryLink?.deeplink;
    if (typeof link === "string") {
      let url: URL | undefined;
      try { url = new URL(link, ORIGIN); } catch { /* Ignore malformed catalog links. */ }
      const id = url?.searchParams.get("trackAsin") || url?.pathname.match(/^\/tracks\/(B[A-Z0-9]{9})$/i)?.[1];
      const title = label(item.primaryText), artist = label(item.secondaryText1) || label(item.secondaryText);
      if (url?.origin === ORIGIN && id && /^B[A-Z0-9]{9}$/i.test(id) && title && artist) {
        const duration = (label(item.secondaryText3) || label(item.duration)).match(/^(\d+):(\d{2})$/);
        found.set(id, { id, amazon_id: id, provider: "amazon", title, artist,
          duration: duration ? Number(duration[1]) * 60 + Number(duration[2]) : undefined,
          artworkURL: normalizePublicHttpsUrl(label(item.image)) ?? undefined,
          audioQuality: "Quality checked at playback", isHiRes: false, isDolbyAtmos: false });
      }
    }
    for (const child of Object.values(value)) walk(child, depth + 1);
  };
  walk(data); return [...found.values()];
}

/** Public catalog session used by the supplied Amazon extension; no user login. */
export async function searchAmazon(query: string, limit = 15): Promise<GatewayTrack[]> {
  const config = await readJson(`${ORIGIN}/config.json`, { headers: { "User-Agent": UA, Accept: "application/json" } });
  if (!config?.deviceId || !config.sessionId || !config.csrf?.token) return [];
  const page = `${ORIGIN}/search/${encodeURIComponent(query)}`;
  const headers = JSON.stringify({
    "x-amzn-authentication": JSON.stringify({ interface: "ClientAuthenticationInterface.v1_0.ClientTokenElement", accessToken: "" }),
    "x-amzn-csrf": JSON.stringify({ interface: "CSRFInterface.v1_0.CSRFHeaderElement", token: config.csrf.token, timestamp: String(config.csrf.ts), rndNonce: String(config.csrf.rnd) }),
    "x-amzn-device-model": "WEBPLAYER", "x-amzn-device-family": "WebPlayer", "x-amzn-device-id": config.deviceId,
    "x-amzn-user-agent": UA, "x-amzn-session-id": config.sessionId, "x-amzn-request-id": crypto.randomUUID(),
    "x-amzn-device-width": "1920", "x-amzn-device-height": "1080", "x-amzn-device-language": config.displayLanguage || "en_US",
    "x-amzn-currency-of-preference": "USD", "x-amzn-os-version": "1.0", "x-amzn-application-version": config.version || "1.0.9678.0",
    "x-amzn-device-time-zone": "UTC", "x-amzn-timestamp": String(Date.now()), "x-amzn-music-domain": "music.amazon.com",
    "x-amzn-page-url": page, "x-amzn-referer": "", "x-amzn-affiliate-tags": "", "x-amzn-ref-marker": "",
    "x-amzn-weblab-id-overrides": "", "x-amzn-video-player-token": "", "x-amzn-feature-flags": "", "x-amzn-has-profile-id": "", "x-amzn-age-band": "",
  });
  const data = await readJson("https://na.mesk.skill.music.a2z.com/api/showSearch", {
    method: "POST", headers: { "Content-Type": "text/plain;charset=UTF-8", "User-Agent": UA, Origin: ORIGIN, Referer: page },
    body: JSON.stringify({ filter: JSON.stringify({ IsLibrary: ["false"] }),
      keyword: JSON.stringify({ interface: "Web.TemplatesInterface.v1_0.Touch.SearchTemplateInterface.SearchKeywordClientInformation", keyword: query }),
      suggestedKeyword: query, userHash: JSON.stringify({ level: "LIBRARY_MEMBER" }), headers }),
  });
  return amazonCatalogTracks(data, limit);
}
