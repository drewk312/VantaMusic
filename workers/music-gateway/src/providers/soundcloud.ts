import type { Env, GatewayTrack, StreamResult } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";

const API = "https://api-v2.soundcloud.com";
const CLIENT_ID = /^[a-zA-Z0-9]{32}$/;

// The extension uses the public web client's ID and public, unsnipped streams.
// Bound response sizes and hosts when inspecting the web client's asset bundle.
async function readText(url: string, maxBytes = 4_000_000): Promise<string | null> {
  try {
    const response = await fetch(url, {
      signal: AbortSignal.timeout(3500), redirect: "manual",
      headers: { "User-Agent": "Mozilla/5.0", Accept: "*/*" },
    });
    if (!response.ok || !response.body) return null;
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let size = 0;
    let text = "";
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) return text + decoder.decode();
        size += value.byteLength;
        if (size > maxBytes) { await reader.cancel(); return null; }
        text += decoder.decode(value, { stream: true });
      }
    } finally { reader.releaseLock(); }
  } catch { return null; }
}

async function clientId(env: Env): Promise<string | null> {
  if (CLIENT_ID.test(env.SOUNDCLOUD_CLIENT_ID ?? "")) return env.SOUNDCLOUD_CLIENT_ID!;
  const cache = typeof caches === "undefined" ? null : caches.default;
  const key = new Request("https://soundcloud.com/vanta-public-client-id");
  const cached = await cache?.match(key).catch(() => undefined);
  const cachedId = await cached?.text();
  if (cachedId && CLIENT_ID.test(cachedId)) return cachedId;
  const html = await readText("https://soundcloud.com/");
  if (!html) return null;
  const extract = (text: string) => text.match(/client_id[:=]["']?([a-zA-Z0-9]{32})["']/)?.[1];
  let id = extract(html);
  const scripts = [...html.matchAll(/src="(https:\/\/a-v2\.sndcdn\.com\/assets\/[^"?#]+\.js)"/g)]
    .map((match) => match[1]).reverse().slice(0, 4);
  for (const script of scripts) {
    if (id) break;
    const body = await readText(script);
    if (body) id = extract(body);
  }
  if (!id) return null;
  await cache?.put(key, new Response(id, { headers: { "Cache-Control": "public, max-age=3600" } })).catch(() => undefined);
  return id;
}

interface Transcoding {
  url?: string;
  snipped?: boolean;
  format?: { protocol?: string; mime_type?: string };
}
interface SoundCloudTrack {
  id?: number;
  title?: string;
  duration?: number;
  full_duration?: number;
  streamable?: boolean;
  policy?: string;
  artwork_url?: string;
  user?: { username?: string; avatar_url?: string };
  publisher_metadata?: { artist?: string; album_title?: string; isrc?: string; explicit?: boolean };
  track_authorization?: string;
  media?: { transcodings?: Transcoding[] };
}

async function scJson<T>(url: string): Promise<T | null> {
  const text = await readText(url);
  try { return text ? JSON.parse(text) as T : null; } catch { return null; }
}

export function soundCloudPlayable(track: SoundCloudTrack): boolean {
  return track.streamable === true && ["ALLOW", "MONETIZE"].includes(track.policy ?? "") &&
    !((track.full_duration ?? 0) > (track.duration ?? 0) + 2000) &&
    Boolean(track.media?.transcodings?.some((t) => !t.snipped &&
      ["progressive", "hls"].includes(t.format?.protocol ?? "")));
}

export async function searchSoundCloud(query: string, env: Env, limit = 20): Promise<GatewayTrack[]> {
  const id = await clientId(env);
  if (!id) return [];
  const params = new URLSearchParams({ q: query, client_id: id, limit: String(Math.min(limit, 50)) });
  const payload = await scJson<{ collection?: SoundCloudTrack[] }>(`${API}/search/tracks?${params}`);
  return (payload?.collection ?? []).filter((track) => track.id && track.title && soundCloudPlayable(track)).map((track) => ({
    id: String(track.id), provider: "soundcloud", title: track.title!,
    artist: track.publisher_metadata?.artist || track.user?.username || "Unknown Artist",
    album: track.publisher_metadata?.album_title,
    duration: Math.round((track.full_duration ?? track.duration ?? 0) / 1000),
    artworkURL: (track.artwork_url || track.user?.avatar_url)?.replace("-large.", "-t500x500."),
    isrc: track.publisher_metadata?.isrc,
    explicit: track.publisher_metadata?.explicit ?? false,
    audioQuality: "SoundCloud audio", isHiRes: false, isDolbyAtmos: false,
  }));
}

function normalizeTitle(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function isLikelyAlternateRecording(title: string): boolean {
  return /\b(mix|remix|cover|karaoke|nightcore|sped up|slowed|live stream|24\/7)\b/i.test(title);
}

/** Last-resort exact recording: duration-matched public SoundCloud, never YouTube mixes. */
export async function streamSoundCloudExact(
  env: Env,
  title: string | undefined,
  artist: string | undefined,
  durationSec: number | undefined
): Promise<StreamResult | null> {
  if (!title?.trim() || !artist?.trim() || !durationSec || durationSec < 30) return null;
  const matches = await searchSoundCloud(`${title} ${artist}`, env, 8);
  const originalAlt = isLikelyAlternateRecording(title);
  const hit = matches.find((track) => {
    if (!track.duration || Math.abs(track.duration - durationSec) > 3) return false;
    if (!originalAlt && isLikelyAlternateRecording(track.title)) return false;
    const want = normalizeTitle(title);
    const got = normalizeTitle(track.title);
    return want.length >= 4 && (got.includes(want) || want.includes(got));
  });
  return hit ? streamSoundCloud(hit.id, env) : null;
}

export async function streamSoundCloud(trackId: string, env: Env): Promise<StreamResult | null> {
  if (!/^\d{1,20}$/.test(trackId)) return null;
  const id = await clientId(env);
  if (!id) return null;
  const track = await scJson<SoundCloudTrack>(`${API}/tracks/${trackId}?client_id=${id}`);
  if (!track || String(track.id) !== trackId || !soundCloudPlayable(track) || !track.track_authorization) return null;
  const candidates = [...(track.media?.transcodings ?? [])]
    .filter((t) => !t.snipped && /audio\/(mpeg|ogg)/.test(t.format?.mime_type ?? "") &&
      ["progressive", "hls"].includes(t.format?.protocol ?? ""))
    .sort((a, b) => Number(b.format?.protocol === "progressive") - Number(a.format?.protocol === "progressive"));
  for (const candidate of candidates.slice(0, 2)) {
    const safe = normalizePublicHttpsUrl(candidate.url);
    if (!safe) continue;
    const endpoint = new URL(safe);
    if (endpoint.origin !== API) continue;
    endpoint.searchParams.set("client_id", id);
    endpoint.searchParams.set("track_authorization", track.track_authorization);
    const payload = await scJson<{ url?: string }>(endpoint.toString());
    const url = normalizePublicHttpsUrl(payload?.url);
    if (!url || !new URL(url).hostname.endsWith(".sndcdn.com")) continue;
    const hls = candidate.format?.protocol === "hls";
    const opus = candidate.format?.mime_type?.includes("ogg") ?? false;
    return {
      url, streamUrl: url, provider: "soundcloud", format: opus ? "opus" : "mp3",
      mimeType: hls ? "application/vnd.apple.mpegurl" : candidate.format?.mime_type,
      quality: opus ? "SoundCloud Opus" : "SoundCloud MP3 128 kbps",
      bitrateKbps: opus ? undefined : 128,
      isHiRes: false, isDolbyAtmos: false, isSpatialAudio: false, isSurround: false,
    };
  }
  return null;
}
