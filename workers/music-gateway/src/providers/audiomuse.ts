import type { Env } from "../types";

/**
 * AudioMuse-AI integration — use THEIR servers for AI sonic analysis.
 *
 * AudioMuse-AI (NeptuneHub/AudioMuse-AI) is a self-hosted Flask+Worker+Postgres
 * stack that does MusiCNN/CLAP/Whisper/GTE sonic analysis and generates:
 *  - Clustering (genre-defying playlists)
 *  - Similar songs (sonic fingerprint IVF)
 *  - Song paths (bridge two tracks)
 *  - CLAP text search ("calm piano songs", mood/instrument/genre)
 *  - Lyrics semantic search (GTE embeddings, 72 languages)
 *  - Sonic fingerprint (from listening history)
 *  - Music map, artist similarity, hyperbolic, etc.
 *
 * It supports MULTIPLE music servers on one deployment (Navidrome, Jellyfin,
 * Plex, Emby, LMS, Lyrion) — duplicate detection shares analysis across servers.
 * Deployment: `deployment/docker-compose.yaml` (postgres + flask:8000 + worker).
 *
 * This module ties it into VANTA by proxying its REST API through the Workers
 * gateway. No paid account needed — you host it yourself (or use Elestio managed).
 * VANTA becomes the Android front-end for AudioMuse's intelligence.
 *
 * All routes are proxied under /api/audiomuse/* → {AUDIOMUSE_BASE_URL}/*.
 * Example: GET /api/audiomuse/api/clap/search?q=calm+piano → {base}/api/clap/search
 */

export function isAudioMuseEnabled(env: Env): boolean {
  return Boolean(env.AUDIOMUSE_BASE_URL?.trim());
}

export function audioMuseBase(env: Env): string | null {
  const raw = env.AUDIOMUSE_BASE_URL?.trim().replace(/\/+$/, "");
  if (!raw) return null;
  try {
    const u = new URL(raw);
    if (u.protocol !== "http:" && u.protocol !== "https:") return null;
    return u.toString().replace(/\/+$/, "");
  } catch {
    return null;
  }
}

/**
 * Proxy any /api/audiomuse/* request to the AudioMuse Flask instance.
 * Forwards method, headers (except host), query, and body. Strips the
 * /api/audiomuse prefix and forwards to AUDIOMUSE_BASE_URL.
 */
export async function proxyAudioMuse(request: Request, env: Env, pathname: string): Promise<Response> {
  const base = audioMuseBase(env);
  if (!base) {
    return Response.json(
      {
        error: "audiomuse_not_configured",
        message: "AudioMuse-AI is not configured. Set AUDIOMUSE_BASE_URL (e.g. http://localhost:8000) and optionally AUDIOMUSE_API_KEY.",
        hint: "Self-host via: cd deployment && docker compose up -d (see NeptuneHub/AudioMuse-AI deployment/docker-compose.yaml) or use Elestio managed.",
      },
      { status: 503 }
    );
  }

  // pathname is /api/audiomuse/<rest> — strip prefix
  const suffix = pathname.replace(/^\/api\/audiomuse\/?/, "") || "";
  const url = new URL(request.url);
  const targetUrl = new URL(`${base}/${suffix}${url.search}`);

  const headers = new Headers();
  // Forward relevant headers, but not host/connection
  for (const [k, v] of request.headers.entries()) {
    const lk = k.toLowerCase();
    if (lk === "host" || lk === "connection" || lk === "content-length") continue;
    // Don't leak Cloudflare creds
    if (lk.startsWith("cf-") || lk.startsWith("x-forwarded")) continue;
    headers.set(k, v);
  }
  headers.set("Accept", headers.get("Accept") ?? "application/json");
  // AudioMuse auth: optional basic/JWT via env
  if (env.AUDIOMUSE_API_KEY?.trim()) {
    headers.set("Authorization", `Bearer ${env.AUDIOMUSE_API_KEY.trim()}`);
  }
  if (env.AUDIOMUSE_USERNAME?.trim() && env.AUDIOMUSE_PASSWORD?.trim()) {
    const creds = btoa(`${env.AUDIOMUSE_USERNAME.trim()}:${env.AUDIOMUSE_PASSWORD.trim()}`);
    headers.set("Authorization", `Basic ${creds}`);
  }

  const method = request.method;
  const hasBody = method !== "GET" && method !== "HEAD";
  const body = hasBody ? await request.arrayBuffer() : undefined;

  let res: Response;
  try {
    res = await fetch(targetUrl.toString(), {
      method,
      headers,
      body: hasBody ? body : undefined,
      cf: { cacheTtl: 0 } as never,
    });
  } catch (err) {
    return Response.json(
      {
        error: "audiomuse_unreachable",
        message: `Could not reach AudioMuse at ${base}: ${err instanceof Error ? err.message : String(err)}`,
        base,
        path: `/${suffix}`,
      },
      { status: 502 }
    );
  }

  // Stream back response with CORS
  const outHeaders = new Headers(res.headers);
  outHeaders.set("Access-Control-Allow-Origin", "*");
  outHeaders.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
  outHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
  outHeaders.delete("content-security-policy");

  return new Response(res.body, {
    status: res.status,
    headers: outHeaders,
  });
}

/**
 * Health probe for AudioMuse — used by /health and /status.
 */
export async function checkAudioMuseHealth(env: Env): Promise<{ healthy: boolean; latencyMs?: number; error?: string }> {
  const base = audioMuseBase(env);
  if (!base) return { healthy: false, error: "not_configured" };
  const start = Date.now();
  try {
    const res = await fetch(`${base}/api/health`, {
      method: "GET",
      headers: { Accept: "application/json" },
      cf: { cacheTtl: 0 } as never,
    });
    const latencyMs = Date.now() - start;
    if (res.ok) return { healthy: true, latencyMs };
    return { healthy: false, latencyMs, error: `http_${res.status}` };
  } catch (err) {
    return { healthy: false, error: err instanceof Error ? err.message : String(err) };
  }
}

/**
 * Known AudioMuse feature endpoints — documented for gateway discovery.
 * These are all proxied via /api/audiomuse/*.
 */
export const AUDIOMUSE_FEATURES = [
  { path: "/api/clap/search", method: "POST", description: "Natural language sonic search (e.g. 'calm piano songs', steering: more/less term weight)" },
  { path: "/api/lyrics/search/text", method: "POST", description: "Lyrics semantic search by theme/story (72 languages, GTE embeddings)" },
  { path: "/api/lyrics/search/axes", method: "POST", description: "5-axis lyrics search (Setting/Social/Valence/Temporality/Weight)" },
  { path: "/api/similar_tracks?item_id=&n=", method: "GET", description: "Find sonically similar tracks (200-d MusiCNN IVF)" },
  { path: "/api/similar_tracks?title=&artist=&n=", method: "GET", description: "Similar by title/artist fallback" },
  { path: "/api/find_path?start_song_id=&end_song_id=&max_steps=", method: "GET", description: "Song path — bridge two tracks sonically" },
  { path: "/api/hyperbolic/similar", method: "POST", description: "Hyperbolic similar" },
  { path: "/api/hyperbolic/journey", method: "POST", description: "Hyperbolic journey" },
  { path: "/api/sonic_fingerprint/generate", method: "POST", description: "Taste profile from listening history (top 20 → nearest 100)" },
  { path: "/api/clustering/start", method: "POST", description: "Auto-cluster library into genre-defying playlists (evolutionary search)" },
  { path: "/api/servers", method: "GET", description: "List connected music servers (Navidrome/Jellyfin/Plex/Emby/LMS/Lyrion)" },
  { path: "/api/servers", method: "POST", description: "Add music server (multi-server support, duplicate detection)" },
  { path: "/api/alchemy", method: "POST", description: "Song alchemy — ADD/SUBTRACT seeds to mix a vibe" },
  { path: "/chat/api/chatPlaylist", method: "POST", description: "Instant playlist via LLM (OLLAMA/GEMINI/MISTRAL/OPENAI) tool-calling" },
  { path: "/api/map?percent=&p=&n=", method: "GET", description: "2D music map (genre-based)" },
  { path: "/api/search_tracks", method: "GET", description: "Browse/search with sonic index" },
] as const;
