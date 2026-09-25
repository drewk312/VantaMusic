import type { Env, GatewayTrack } from "../types";
import { importSpotifyPlaylist, getSpotifyRecommendations, searchSpotify } from "./spotify-web";
import { audioMuseBase } from "./audiomuse";

/**
 * AI Radio — Spotify seeds + AudioMuse LLM = best of both.
 *
 * Flow (like Spotify AI DJ + AudioMuse chatPlaylist):
 *  1) Seeds: from Spotify recently-played / playlist / search, or from VANTA's own history
 *  2) LLM: POST {AUDIOMUSE_BASE_URL}/chat/api/chatPlaylist {userInput, ai_provider, seeds}
 *  3) Fallback: Spotify recommendations + ISRC → Qobuz/Tidal resolve
 *
 * This gives VANTA a true AI Radio that works even when AudioMuse is offline
 * (degrades to Spotify recommendations + gateway search).
 */

export interface AiRadioRequest {
  prompt?: string;
  seedTrackIds?: string[];
  seedArtists?: string[];
  seedPlaylistId?: string;
  limit?: number;
  aiProvider?: "OLLAMA" | "GEMINI" | "MISTRAL" | "OPENAI";
}

export async function generateAiRadio(request: AiRadioRequest, env: Env): Promise<GatewayTrack[]> {
  const limit = Math.min(request.limit ?? 20, 50);
  const prompt = request.prompt?.trim() ?? "Create a continuous AI radio mix";
  const aiProvider = request.aiProvider ?? "OLLAMA";

  // Try AudioMuse chatPlaylist first (best — sonic LLM tool-calling)
  const base = audioMuseBase(env);
  if (base) {
    try {
      const res = await fetch(`${base}/chat/api/chatPlaylist`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          userInput: prompt,
          ai_provider: aiProvider,
          ai_model: undefined,
          seeds: request.seedTrackIds ?? [],
          artists: request.seedArtists ?? [],
          limit,
        }),
        cf: { cacheTtl: 0 } as never,
      });
      if (res.ok) {
        const data = (await res.json()) as { tracks?: unknown[]; playlist?: unknown[]; error?: string };
        const items = (data.tracks ?? data.playlist ?? []) as Record<string, unknown>[];
        if (items.length > 0) {
          return items
            .map((t) => ({
              id: String(t.id ?? (t as { item_id?: string }).item_id ?? ""),
              title: String(t.title ?? t.name ?? "Unknown"),
              artist: String(t.artist ?? (t as { artistName?: string }).artistName ?? "Unknown"),
              album: (t.album as string | undefined) ?? undefined,
              provider: "qobuz" as const,
              isrc: (t.isrc as string | undefined) ?? undefined,
            }))
            .filter((t) => t.id && t.title !== "Unknown") as GatewayTrack[];
        }
      }
    } catch {
      // fall through to Spotify
    }
  }

  // Fallback: Spotify recommendations
  if (request.seedPlaylistId) {
    const imported = await importSpotifyPlaylist(request.seedPlaylistId, env);
    if (imported.length > 0) return imported.slice(0, limit);
  }

  if (request.seedTrackIds && request.seedTrackIds.length > 0) {
    const recs = await getSpotifyRecommendations(request.seedTrackIds, env, limit);
    if (recs.length > 0) return recs;
  }

  // Last resort: text search via Spotify
  if (prompt) {
    const searched = await searchSpotify(prompt, env, limit);
    if (searched.length > 0) return searched;
  }

  return [];
}

/**
 * Smart Playlists — Most Played / Never Played / Forgotten Gems etc.
 * When AudioMuse is configured, these are sonic (MusiCNN). Otherwise, fallback to search seeds.
 */
export async function getSmartPlaylist(kind: string, env: Env): Promise<GatewayTrack[]> {
  const base = audioMuseBase(env);
  const k = kind.toLowerCase();

  if (base) {
    const endpointMap: Record<string, string> = {
      "most-played": "/api/sonic_fingerprint/generate?n=20",
      "forgotten-gems": "/api/clustering/start",
      similar: "/api/similar_tracks",
      fingerprint: "/api/sonic_fingerprint/generate",
    };
    const endpoint = endpointMap[k];
    if (endpoint) {
      try {
        const res = await fetch(`${base}${endpoint}`, {
          headers: { Accept: "application/json" },
          cf: { cacheTtl: 60 } as never,
        });
        if (res.ok) {
          const data = (await res.json()) as { tracks?: unknown[] };
          if (data.tracks?.length) {
            return (data.tracks as Record<string, unknown>[])
              .slice(0, 20)
              .map((t) => ({
                id: String(t.id ?? ""),
                title: String(t.title ?? "Unknown"),
                artist: String(t.artist ?? "Unknown"),
                provider: "qobuz" as const,
              }))
              .filter((t) => t.title !== "Unknown") as GatewayTrack[];
          }
        }
      } catch {
        // fallback
      }
    }
  }

  // Fallback: curated seeds that are known high-quality/atmos
  const fallbackQueries: Record<string, string> = {
    "most-played": "The Weeknd Blinding Lights",
    "never-played": "Tame Impala The Less I Know The Better",
    "forgotten-gems": "Billie Eilish birds of a feather",
    fingerprint: "Daft Punk Get Lucky",
    similar: "Adele Hello",
  };
  const q = fallbackQueries[k] ?? "Daft Punk";
  try {
    const { searchAll } = await import("./search");
    const result = await searchAll(q, env);
    return result.tracks.slice(0, 12);
  } catch {
    return [];
  }
}
