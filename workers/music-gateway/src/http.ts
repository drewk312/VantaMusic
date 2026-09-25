export const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Api-Key, User-Agent, Accept, X-Qobuz-Token, X-Tidal-Token, X-Deezer-Arl, X-Amazon-Token",
};

const SUPPORTED_TRACK_HOSTS = new Set([
  "music.apple.com",
  "itunes.apple.com",
  "open.spotify.com",
  "spotify.link",
  "tidal.com",
  "listen.tidal.com",
  "open.qobuz.com",
  "qobuz.com",
  "www.qobuz.com",
  "deezer.com",
  "www.deezer.com",
  "music.amazon.com",
  "youtube.com",
  "www.youtube.com",
  "music.youtube.com",
  "youtu.be",
  "soundcloud.com",
  "www.soundcloud.com",
  "song.link",
  "album.link",
  "odesli.co",
]);

/** Accept only known public music links before forwarding them to resolver services. */
export function normalizeSupportedTrackUrl(value: string): string | null {
  const trimmed = value.trim();
  if (/^spotify:track:[a-zA-Z0-9]+$/.test(trimmed)) return trimmed;
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol !== "https:" || parsed.username || parsed.password) return null;
    const host = parsed.hostname.toLowerCase();
    if (!SUPPORTED_TRACK_HOSTS.has(host)) return null;
    parsed.hash = "";
    return parsed.toString();
  } catch {
    return null;
  }
}

export function trackUrlHost(value: string): string {
  if (value.startsWith("spotify:")) return "spotify";
  try {
    return new URL(value).hostname.toLowerCase();
  } catch {
    return "invalid";
  }
}

export function json(data: unknown, status = 200, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
      ...extraHeaders,
    },
  });
}

export function text(body: string, status = 200): Response {
  return new Response(body, {
    status,
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      ...CORS_HEADERS,
    },
  });
}

export function handleOptions(): Response {
  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

export function unauthorized(): Response {
  return json({ error: "unauthorized", hint: "Set X-Api-Key header to match GATEWAY_API_KEY secret" }, 401);
}

export function notFound(message = "not_found"): Response {
  return json({ error: message }, 404);
}

export function badRequest(message: string): Response {
  return json({ error: message }, 400);
}

export function serviceUnavailable(provider: string, hint: string): Response {
  return json(
    {
      error: "provider_not_configured",
      provider,
      hint,
    },
    503
  );
}

export async function readJson<T>(request: Request): Promise<T | null> {
  try {
    return (await request.json()) as T;
  } catch {
    return null;
  }
}

const CATALOG_PREFIXES = new Set(["qobuz", "tidal", "deezer", "amazon", "pandora", "apple", "soundcloud", "spotify"]);

/** Split `qobuz:123` catalog ids used by the Android client. */
export function splitCatalogTrackId(trackId: string): { provider?: string; id: string } {
  const trimmed = trackId.trim();
  const separator = trimmed.indexOf(":");
  if (separator <= 0 || separator === trimmed.length - 1) return { id: trimmed };
  const provider = trimmed.slice(0, separator).toLowerCase();
  if (!CATALOG_PREFIXES.has(provider)) return { id: trimmed };
  const id = trimmed.slice(separator + 1).trim();
  return id ? { provider, id } : { id: trimmed };
}

export function extractTrackId(pathname: string): string | null {
  const patterns = [
    /^\/(?:api\/)?stream\/([^/]+)$/,
    /^\/(?:api\/)?resolve\/([^/]+)$/,
    /^\/(?:api\/)?download\/([^/]+)$/,
  ];
  for (const pattern of patterns) {
    const match = pathname.match(pattern);
    if (match?.[1]) {
      try {
        return decodeURIComponent(match[1]);
      } catch {
        return null;
      }
    }
  }
  return null;
}

export function normalizeQuality(raw: string | null | undefined, fallback: string): string {
  const safeFallback = fallback === "16" || fallback === "24" ? fallback : "24";
  const value = (raw ?? safeFallback).trim().toLowerCase().replace(/-/g, "_");
  if (value === "16" || value === "24") return value;
  if (value === "auto") return "auto";
  if (value === "atmos" || value === "dolby_atmos" || value === "eac3" || value === "eac3_joc" || value === "ac4" || value === "ac_4") return "atmos";
  if (value === "360" || value === "360ra" || value === "sony360" || value === "sony_360" || value === "360_reality_audio") return "360";
  if (value === "hi_res" || value === "hi_res_lossless") return "hi_res";
  return safeFallback;
}

export function isImmersiveQuality(quality: string): boolean {
  const value = quality.trim().toLowerCase();
  return value === "atmos" || value === "hi_res" || value === "360" || value.includes("atmos") || value.includes("360");
}

export function providerFromQuery(url: URL, bodyService?: string): string | undefined {
  return (
    bodyService?.trim().toLowerCase() ||
    url.searchParams.get("provider")?.trim().toLowerCase() ||
    url.searchParams.get("service")?.trim().toLowerCase() ||
    undefined
  );
}
