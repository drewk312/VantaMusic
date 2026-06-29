import type { Env, GatewayTrack, ResolveResult, ProviderId } from "../types";
import { enrichLimit } from "../types";
import { resolveTrack } from "./resolve";
import { fetchJson } from "./shared";

async function fetchMissingArtwork(track: GatewayTrack): Promise<string | undefined> {
  if (track.artworkURL?.trim()) return track.artworkURL;

  const deezerId = track.deezer_id ?? track.id;
  if (deezerId) {
    try {
      const deezerTrackUrl = `https://api.deezer.com/track/${deezerId}`;
      const deezerResponse = (await fetchJson(deezerTrackUrl)) as {
        album?: { cover_medium?: string; cover_big?: string };
      } | null;
      const cover = deezerResponse?.album?.cover_medium ?? deezerResponse?.album?.cover_big;
      if (cover) return cover;
    } catch {
      // fall through
    }
  }

  const appleId = track.apple_id;
  if (appleId && /^\d+$/.test(appleId)) {
    try {
      const itunesUrl = `https://itunes.apple.com/lookup?id=${appleId}&entity=song`;
      const itunesResponse = (await fetchJson(itunesUrl)) as {
        results?: Array<{ artworkUrl100?: string }>;
      } | null;
      const art = itunesResponse?.results?.[0]?.artworkUrl100?.replace("100x100bb", "600x600bb");
      if (art) return art;
    } catch {
      // fall through
    }
  }

  return undefined;
}

export async function enrichTracks(tracks: GatewayTrack[], env: Env): Promise<GatewayTrack[]> {
  const limit = enrichLimit(env);
  const head = tracks.slice(0, limit);
  const tail = tracks.slice(limit);

  const enrichedHead = await Promise.all(
    head.map(async (track) => {
      try {
        const links = await resolveTrack({
          trackId: track.deezer_id ?? track.qobuz_id ?? track.id,
          provider: track.provider,
          env,
        });
        const merged = mergeTrackLinks(track, links);
        const artwork = await fetchMissingArtwork(merged);
        if (artwork) merged.artworkURL = artwork;
        return merged;
      } catch {
        const artwork = await fetchMissingArtwork(track);
        if (artwork) track.artworkURL = artwork;
        return track;
      }
    })
  );

  return [...enrichedHead, ...tail];
}

function mergeTrackLinks(track: GatewayTrack, links: ResolveResult): GatewayTrack {
  return {
    ...track,
    isrc: links.isrc ?? track.isrc,
    tidal_id: links.tidal_id ?? track.tidal_id,
    qobuz_id: links.qobuz_id ?? track.qobuz_id,
    deezer_id: links.deezer_id ?? track.deezer_id,
    amazon_id: links.amazon_id ?? track.amazon_id,
    spotify_id: links.spotify_id ?? track.spotify_id,
    apple_id: links.apple_id ?? track.apple_id,
  };
}
