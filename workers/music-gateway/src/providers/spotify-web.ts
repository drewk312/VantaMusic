import type { Env, GatewayTrack } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { importSpotifyPlaylistExtension, searchSpotifyExtension } from "./spotify-extension";

/**
 * Spotify Web provider — for AI Radio seeding, playlist import, and recommendations.
 *
 * Spotify has NO Atmos (stereo only), but has the best social graph for AI radio
 * and playlist import. We use:
 *  1) Client Credentials (server) — search, recommendations, public playlist metadata — no user login
 *  2) TOTP web-player token (sp_dc) — same as SpotiFLAC's spotfetch.go, no app registration needed
 *
 * Env: SPOTIFY_CLIENT_ID + SPOTIFY_CLIENT_SECRET (for Client Credentials)
 *      or SPOTIFY_SP_DC (for web-player token, from browser cookies)
 */

const SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token";
const SPOTIFY_API = "https://api.spotify.com/v1";

let cachedClientToken: { token: string; expiresAt: number } | null = null;

async function getSpotifyClientToken(env: Env): Promise<string | null> {
  const id = env.SPOTIFY_CLIENT_ID?.trim();
  const secret = env.SPOTIFY_CLIENT_SECRET?.trim();
  if (!id || !secret) return null;

  const now = Date.now();
  if (cachedClientToken && cachedClientToken.expiresAt > now + 60_000) {
    return cachedClientToken.token;
  }

  const creds = btoa(`${id}:${secret}`);
  try {
    const res = await fetch(SPOTIFY_TOKEN_URL, {
      method: "POST",
      headers: {
        Authorization: `Basic ${creds}`,
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: "grant_type=client_credentials",
      cf: { cacheTtl: 0 } as never,
    });
    if (!res.ok) return null;
    const data = (await res.json()) as { access_token?: string; expires_in?: number };
    if (!data.access_token) return null;
    cachedClientToken = {
      token: data.access_token,
      expiresAt: now + (data.expires_in ?? 3600) * 1000,
    };
    return data.access_token;
  } catch {
    return null;
  }
}

function spotifyTrackToGatewayTrack(item: Record<string, unknown>): GatewayTrack | null {
  const id = String(item.id ?? "");
  const name = String(item.name ?? "");
  const artists = ((item.artists as unknown[]) ?? []) as Record<string, unknown>[];
  const artist = artists
    .map((entry) => String(entry.name ?? "").trim())
    .filter((name) => name.length > 0)
    .join(", ") || String((item as Record<string, unknown>).artist ?? "");
  if (!name || !artist || !id) return null;

  const album = ((item.album as Record<string, unknown> | undefined)?.name as string | undefined) ?? undefined;
  const images = ((item.album as Record<string, unknown> | undefined)?.images as unknown[] | undefined) as
    | Record<string, string>[]
    | undefined;
  const artworkURL = images?.[0]?.url ? normalizePublicHttpsUrl(images[0].url) ?? undefined : undefined;
  const durationMs = typeof item.duration_ms === "number" ? item.duration_ms : undefined;
  const isrc = ((item.external_ids as Record<string, string> | undefined)?.isrc as string | undefined) ?? undefined;

  return {
    id,
    title: name,
    artist,
    album,
    artworkURL,
    duration: durationMs ? Math.round(durationMs / 1000) : undefined,
    isrc,
    provider: "spotify",
    spotify_id: id,
  };
}

export async function searchSpotify(query: string, env: Env, limit = 10): Promise<GatewayTrack[]> {
  const token = await getSpotifyClientToken(env);
  if (!token) return searchSpotifyExtension(query, limit);

  const params = new URLSearchParams({
    q: query,
    type: "track",
    limit: String(Math.min(limit, 10)),
    market: "US",
  });

  try {
    const res = await fetch(`${SPOTIFY_API}/search?${params}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
      cf: { cacheTtl: 300 } as never,
    });
    if (!res.ok) return [];
    const data = (await res.json()) as { tracks?: { items?: unknown[] } };
    const items = data.tracks?.items ?? [];
    return (items as Record<string, unknown>[]).map(spotifyTrackToGatewayTrack).filter(Boolean) as GatewayTrack[];
  } catch {
    return [];
  }
}

export async function getSpotifyRecommendations(seedTrackIds: string[], env: Env, limit = 20): Promise<GatewayTrack[]> {
  const token = await getSpotifyClientToken(env);
  if (!token || seedTrackIds.length === 0) return [];

  const params = new URLSearchParams({
    seed_tracks: seedTrackIds.slice(0, 5).join(","),
    limit: String(Math.min(limit, 20)),
    market: "US",
  });

  try {
    const res = await fetch(`${SPOTIFY_API}/recommendations?${params}`, {
      headers: { Authorization: `Bearer ${token}` },
      cf: { cacheTtl: 300 } as never,
    });
    if (!res.ok) return [];
    const data = (await res.json()) as { tracks?: unknown[] };
    const items = data.tracks ?? [];
    return (items as Record<string, unknown>[]).map(spotifyTrackToGatewayTrack).filter(Boolean) as GatewayTrack[];
  } catch {
    return [];
  }
}

function cleanSpotifyPlaylistId(playlistId: string): string {
  return playlistId
    .replace(/^spotify:playlist:/i, "")
    .replace(/^https?:\/\/open\.spotify\.com\/playlist\//i, "")
    .split("?")[0]
    .trim();
}

async function importSpotifyPlaylistWithToken(
  cleanId: string,
  token: string,
  maxTracks = 1000,
): Promise<GatewayTrack[]> {
  const tracks: GatewayTrack[] = [];
  let offset = 0;
  const pageSize = 100;
  const cap = Math.min(Math.max(maxTracks, 1), 2000);

  while (tracks.length < cap) {
    const limit = Math.min(pageSize, cap - tracks.length);
    const res = await fetch(
      `${SPOTIFY_API}/playlists/${encodeURIComponent(cleanId)}/tracks?limit=${limit}&offset=${offset}&market=US&fields=items(track(id,name,artists,album(name,images),duration_ms,external_ids)),next`,
      {
        headers: { Authorization: `Bearer ${token}` },
        cf: { cacheTtl: 300 } as never,
      },
    );
    if (!res.ok) break;
    const data = (await res.json()) as { items?: { track?: unknown }[]; next?: string | null };
    const items = data.items ?? [];
    for (const row of items) {
      const mapped = row.track ? spotifyTrackToGatewayTrack(row.track as Record<string, unknown>) : null;
      if (mapped) tracks.push(mapped);
      if (tracks.length >= cap) break;
    }
    if (!data.next || items.length === 0) break;
    offset += items.length;
    if (offset > 5000) break;
  }
  return tracks;
}

/**
 * Public playlist import. Prefers Client Credentials when configured; otherwise
 * uses the embed-page scrape (no user login). Web-player token is a last resort.
 */
export async function importSpotifyPlaylist(playlistId: string, env: Env, maxTracks = 1000): Promise<GatewayTrack[]> {
  const cleanId = cleanSpotifyPlaylistId(playlistId);
  if (!cleanId) return [];

  const token = await getSpotifyClientToken(env);
  if (token) {
    try {
      const tracks = await importSpotifyPlaylistWithToken(cleanId, token, maxTracks);
      if (tracks.length > 0) return tracks;
    } catch {
      // fall through
    }
  }

  try {
    const fallback = await importSpotifyPlaylistExtension(cleanId, maxTracks);
    return fallback.tracks;
  } catch {
    return [];
  }
}

export interface SpotifyEditorialPlaylist {
  id: string;
  name: string;
  curator: string;
  description: string;
  artworkURL?: string;
  source: "spotify";
}

/** Well-known, stable Spotify editorial playlist IDs used when client creds are absent. */
const SPOTIFY_EDITORIAL_SEED: Array<{ id: string; name: string; description: string }> = [
  { id: "37i9dQZF1DXcBWIGoYBM5M", name: "Today's Top Hits", description: "The biggest hits of tomorrow. The sounds of the week." },
  { id: "37i9dQZF1DX0XUsuxWHRQd", name: "RapCaviar", description: "The best of hip-hop today." },
  { id: "37i9dQZF1DWXRqgorJj26U", name: "Rock Classics", description: "Rock legends & epic songs." },
  { id: "37i9dQZF1DX0kbJZpiYdZl", name: "Hot Hits USA", description: "The hottest tracks in the United States." },
  { id: "37i9dQZF1DXaXB8fQg7xif", name: "Dance Hits", description: "The biggest tracks in dance music." },
  { id: "37i9dQZF1DXc5e2bJhV6Tz", name: "mint", description: "Dance, electronic & club music." },
  { id: "37i9dQZF1DX0r6oooRlCjX", name: "Jazz in the Background", description: "Soft instrumental jazz for all your activities." },
  { id: "37i9dQZF1DX4sWSpwq3uzO", name: "Peaceful Piano", description: "Peaceful piano to help you relax." },
  { id: "37i9dQZF1DX4WYpdgoIcn6", name: "Chill Hits", description: "Kick back to the best new and recent chill hits." },
  { id: "37i9dQZF1DX10zKzsJ2jva", name: "Viva Latino", description: "Today's top Latin hits." },
];

function spotifyEditorialCard(item: Record<string, unknown>): SpotifyEditorialPlaylist | null {
  const id = String(item.id ?? "");
  const name = typeof item.name === "string" ? item.name : "";
  if (!id || !name) return null;
  const images = ((item.images as unknown[]) ?? []) as Record<string, string>[];
  const owner = (item.owner as Record<string, unknown> | undefined)?.display_name;
  return {
    id,
    name,
    curator: typeof owner === "string" && owner.trim() ? owner : "Spotify",
    description: typeof item.description === "string" ? item.description : "",
    artworkURL: images?.[0]?.url ? normalizePublicHttpsUrl(images[0].url) ?? undefined : undefined,
    source: "spotify",
  };
}

/**
 * Curated Spotify editorial playlists for the home feed, alongside Apple's.
 * Uses Client Credentials (featured playlists) when configured, otherwise a
 * stable public seed enriched via Spotify's oEmbed (no auth, artwork only).
 */
export async function spotifyEditorialPlaylists(env?: Env, limit = 8): Promise<SpotifyEditorialPlaylist[]> {
  const token = env ? await getSpotifyClientToken(env) : null;
  if (token) {
    try {
      const params = new URLSearchParams({ country: "US", limit: String(Math.min(limit, 50)) });
      const res = await fetch(`${SPOTIFY_API}/browse/featured-playlists?${params}`, {
        headers: { Authorization: `Bearer ${token}` },
        cf: { cacheTtl: 1800 } as never,
      });
      if (res.ok) {
        const data = (await res.json()) as { playlists?: { items?: unknown[] } };
        const items = data.playlists?.items ?? [];
        const cards = items
          .map((i) => spotifyEditorialCard(i as Record<string, unknown>))
          .filter(Boolean) as SpotifyEditorialPlaylist[];
        if (cards.length > 0) return cards.slice(0, limit);
      }
    } catch {
      // fall back to oEmbed seed below
    }
  }

  const seeded = SPOTIFY_EDITORIAL_SEED.slice(0, limit);
  const cards: SpotifyEditorialPlaylist[] = [];
  await Promise.all(
    seeded.map(async (entry) => {
      try {
        const url = `https://open.spotify.com/oembed?url=${encodeURIComponent(`https://open.spotify.com/playlist/${entry.id}`)}`;
        const res = await fetch(url, { cf: { cacheTtl: 86_400 } as never });
        if (!res.ok) return;
        const data = (await res.json()) as { thumbnail_url?: string; title?: string };
        cards.push({
          id: entry.id,
          name: typeof data.title === "string" && data.title.trim() ? data.title : entry.name,
          curator: "Spotify",
          description: entry.description,
          artworkURL: data.thumbnail_url ? normalizePublicHttpsUrl(data.thumbnail_url) ?? undefined : undefined,
          source: "spotify",
        });
      } catch {
        // skip a dead seed; keep the rest
      }
    })
  );
  return cards;
}
