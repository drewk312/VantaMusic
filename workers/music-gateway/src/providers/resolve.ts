import type { Env, ResolveResult } from "../types";
import { fetchJson } from "./shared";
import { lookupQobuzTrackByIsrc } from "./qobuz-api";

interface OdesliResponse {
  linksByPlatform?: Record<string, { url?: string }>;
  entitiesByUniqueId?: Record<string, { isrc?: string }>;
}

interface ZarzResolveResponse {
  success?: boolean;
  isrc?: string;
  songUrls?: Record<string, string | null>;
}

interface IdhsLink {
  type?: string;
  url?: string;
  notAvailable?: boolean;
}

interface IdhsResponse {
  links?: IdhsLink[];
}

export async function resolveTrackLinks(targetUrl: string, env: Env): Promise<ResolveResult> {
  const merged: ResolveResult = { external_links: {} };

  const odesli = await resolveViaOdesli(targetUrl).catch(() => null);
  mergeResolve(merged, odesli);

  if (!hasPlatformIds(merged)) {
    const idhs = await resolveViaIdhs(targetUrl).catch(() => null);
    mergeResolve(merged, idhs);
  }

  if (!merged.spotify_id && isSpotifyUrl(targetUrl)) {
    const zarz = await resolveViaZarz(targetUrl, env).catch(() => null);
    mergeResolve(merged, zarz);
  }

  return merged;
}

export async function resolveTrack(opts: {
  url?: string;
  trackId?: string;
  provider?: string;
  env?: Env;
}): Promise<ResolveResult> {
  const env = opts.env ?? ({} as Env);

  if (opts.url) {
    return resolveTrackLinks(opts.url, env);
  }

  if (opts.provider === "deezer" && opts.trackId) {
    const fromDeezer = await resolveDeezerTrack(opts.trackId, env);
    if (fromDeezer) return fromDeezer;
  }

  if (opts.provider === "qobuz" && opts.trackId) {
    return resolveTrackLinks(`https://open.qobuz.com/track/${opts.trackId}`, env);
  }

  if (opts.provider === "tidal" && opts.trackId) {
    return resolveTrackLinks(`https://listen.tidal.com/track/${opts.trackId}`, env);
  }

  if (opts.provider === "amazon" && opts.trackId) {
    return resolveTrackLinks(`https://music.amazon.com/tracks/${opts.trackId}`, env);
  }

  if (opts.provider === "apple" && opts.trackId) {
    return resolveTrackLinks(`https://music.apple.com/us/song/${opts.trackId}`, env);
  }

  if (opts.trackId) {
    return resolveTrackLinks(`https://open.qobuz.com/track/${opts.trackId}`, env);
  }

  throw new Error("resolve_requires_url_or_track_id");
}

async function resolveViaOdesli(targetUrl: string): Promise<ResolveResult> {
  const response = await fetch(
    `https://api.song.link/v1-alpha.1/links?url=${encodeURIComponent(targetUrl)}&userCountry=US`,
    { headers: { Accept: "application/json", "User-Agent": "VANTA-MusicGateway/2.0" } }
  );
  if (!response.ok) {
    console.warn("VANTA_PLAY_TRACK_REQUEST", JSON.stringify({ phase: "odesli_failed", status: response.status, url: targetUrl }));
    throw new Error(`odesli_failed:${response.status}`);
  }

  const data = (await response.json()) as OdesliResponse;
  const result: ResolveResult = { external_links: {} };

  for (const entity of Object.values(data.entitiesByUniqueId ?? {})) {
    if (entity?.isrc) {
      result.isrc = entity.isrc;
      break;
    }
  }

  applyOdesliLinks(result, data.linksByPlatform ?? {});
  return result;
}

async function resolveViaIdhs(targetUrl: string): Promise<ResolveResult> {
  const response = await fetch("https://idonthavespotify.sjdonado.com/api/search?v=1", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": "VANTA-MusicGateway/2.0",
    },
    body: JSON.stringify({
      link: targetUrl,
      adapters: ["tidal", "deezer", "qobuz", "amazonMusic", "appleMusic", "spotify"],
    }),
  });
  if (!response.ok) throw new Error(`idhs_failed:${response.status}`);

  const data = (await response.json()) as IdhsResponse;
  const result: ResolveResult = { external_links: {} };

  for (const link of data.links ?? []) {
    if (!link.url || link.notAvailable) continue;
    const type = (link.type ?? "").toLowerCase();
    switch (type) {
      case "tidal":
        result.external_links!.tidal = link.url;
        result.tidal_id = extractId(link.url, /\/track\/(\d+)/);
        break;
      case "deezer":
        result.external_links!.deezer = link.url;
        result.deezer_id = extractId(link.url, /\/track\/(\d+)/);
        break;
      case "qobuz":
        result.external_links!.qobuz = link.url;
        result.qobuz_id = extractId(link.url, /\/track\/(\d+)/);
        break;
      case "applemusic":
        result.external_links!.apple = link.url;
        result.apple_id = extractId(link.url, /(?:\?i=|\/id)(\d+)/);
        break;
      case "spotify":
        result.external_links!.spotify = link.url;
        result.spotify_id = extractId(link.url, /\/track\/([a-zA-Z0-9]+)/);
        break;
      default:
        break;
    }
  }

  return result;
}

async function resolveViaZarz(targetUrl: string, env: Env): Promise<ResolveResult> {
  const endpoint = env.ZARZ_RESOLVE_URL?.trim() || "https://api.zarz.moe/v1/resolve";
  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": "VANTA-MusicGateway/2.0",
    },
    body: JSON.stringify({ url: targetUrl }),
  });
  if (!response.ok) throw new Error(`zarz_failed:${response.status}`);

  const data = (await response.json()) as ZarzResolveResponse;
  const result: ResolveResult = { external_links: {}, isrc: data.isrc };

  for (const [platform, url] of Object.entries(data.songUrls ?? {})) {
    if (!url) continue;
    const key = platform.toLowerCase();
    if (key === "tidal") {
      result.external_links!.tidal = url;
      result.tidal_id = extractId(url, /\/track\/(\d+)/);
    } else if (key === "qobuz") {
      result.external_links!.qobuz = url;
      result.qobuz_id = extractId(url, /\/track\/(\d+)/);
    } else if (key === "deezer") {
      result.external_links!.deezer = url;
      result.deezer_id = extractId(url, /\/track\/(\d+)/);
    } else if (key === "amazonmusic") {
      result.external_links!.amazon = url;
      result.amazon_id = extractAmazonId(url);
    } else if (key === "applemusic") {
      result.external_links!.apple = url;
      result.apple_id = extractId(url, /(?:\?i=|\/id)(\d+)/);
    } else if (key === "spotify") {
      result.external_links!.spotify = url;
      result.spotify_id = extractId(url, /\/track\/([a-zA-Z0-9]+)/);
    }
  }

  return result;
}

async function resolveDeezerTrack(deezerTrackId: string, env: Env): Promise<ResolveResult | null> {
  const payload = await fetchJson(`https://api.deezer.com/track/${encodeURIComponent(deezerTrackId)}`);
  const track = payload as { isrc?: string; link?: string } | null;
  if (!track) return null;

  const merged: ResolveResult = { deezer_id: deezerTrackId, isrc: track.isrc, external_links: {} };
  if (track.link) {
    mergeResolve(merged, await resolveTrackLinks(track.link, env).catch(() => null));
  }
  if (!merged.tidal_id && track.link) {
    mergeResolve(merged, await resolveViaIdhs(track.link).catch(() => null));
  }
  await mergeResolveFromIsrc(merged);
  return merged;
}

async function mergeResolveFromIsrc(target: ResolveResult): Promise<void> {
  const isrc = target.isrc?.trim();
  if (!isrc) return;

  if (!target.qobuz_id) {
    const qobuzId = await lookupQobuzTrackByIsrc(isrc).catch(() => null);
    if (qobuzId) {
      target.qobuz_id = qobuzId;
      target.external_links = target.external_links ?? {};
      target.external_links.qobuz = `https://open.qobuz.com/track/${qobuzId}`;
    }
  }

  if (!target.tidal_id && target.deezer_id) {
    mergeResolve(
      target,
      await resolveTrackLinks(`https://www.deezer.com/track/${target.deezer_id}`, {} as Env).catch(() => null)
    );
  }

  if (!target.tidal_id && target.qobuz_id) {
    mergeResolve(target, await resolveTrackLinks(`https://open.qobuz.com/track/${target.qobuz_id}`, {} as Env).catch(() => null));
  }
}

function applyOdesliLinks(result: ResolveResult, links: Record<string, { url?: string }>) {
  if (links.spotify?.url) {
    result.external_links!.spotify = links.spotify.url;
    result.spotify_id = extractId(links.spotify.url, /\/track\/([a-zA-Z0-9]+)/);
  }
  if (links.tidal?.url) {
    result.external_links!.tidal = links.tidal.url;
    result.tidal_id = extractId(links.tidal.url, /\/track\/(\d+)/);
  }
  if (links.qobuz?.url) {
    result.external_links!.qobuz = links.qobuz.url;
    result.qobuz_id = extractId(links.qobuz.url, /\/track\/(\d+)/);
  }
  if (links.deezer?.url) {
    result.external_links!.deezer = links.deezer.url;
    result.deezer_id = extractId(links.deezer.url, /\/track\/(\d+)/);
  }
  if (links.amazonMusic?.url) {
    result.external_links!.amazon = links.amazonMusic.url;
    result.amazon_id = extractAmazonId(links.amazonMusic.url);
  }
  if (links.appleMusic?.url) {
    result.external_links!.apple = links.appleMusic.url;
    result.apple_id = extractId(links.appleMusic.url, /(?:\?i=|\/id)(\d+)/);
  }
}

function mergeResolve(target: ResolveResult, incoming: ResolveResult | null | undefined) {
  if (!incoming) return;
  target.isrc = target.isrc ?? incoming.isrc;
  target.tidal_id = target.tidal_id ?? incoming.tidal_id;
  target.qobuz_id = target.qobuz_id ?? incoming.qobuz_id;
  target.deezer_id = target.deezer_id ?? incoming.deezer_id;
  target.amazon_id = target.amazon_id ?? incoming.amazon_id;
  target.spotify_id = target.spotify_id ?? incoming.spotify_id;
  target.apple_id = target.apple_id ?? incoming.apple_id;
  target.external_links = { ...(target.external_links ?? {}), ...(incoming.external_links ?? {}) };
}

function hasPlatformIds(result: ResolveResult): boolean {
  return Boolean(result.tidal_id || result.qobuz_id || result.deezer_id || result.amazon_id);
}

function isSpotifyUrl(url: string): boolean {
  const lower = url.toLowerCase();
  return lower.includes("spotify.com/") || lower.startsWith("spotify:");
}

function extractId(url: string, pattern: RegExp): string | undefined {
  return url.match(pattern)?.[1];
}

function extractAmazonId(url: string): string | undefined {
  return (
    url.match(/[?&]trackAsin=([A-Z0-9]+)/i)?.[1] ??
    url.match(/\/tracks\/([A-Z0-9]+)/i)?.[1]
  );
}
