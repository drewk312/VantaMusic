import type { Env, ProviderId, StreamResult } from "../types";
import { inferBitrateKbps, qualityLabelFromBitrate } from "../lib/stream-quality";
import { raceBest } from "../lib/race-first";
import { streamWithPublicFallbacks } from "./public-stream";
import { lookupQobuzTrackByIsrc } from "./qobuz-api";
import { resolveTrack } from "./resolve";
import { fetchJson, streamProvidersInOrder, UPSTREAM_BY_PROVIDER } from "./shared";

export async function streamWithFallback(
  env: Env,
  trackId: string,
  quality: string,
  preferredProvider?: string,
  hint?: { provider?: string }
): Promise<StreamResult | null> {
  const ids = await expandTrackIds(env, trackId, hint?.provider ?? preferredProvider);
  const crossIds = { qobuz: ids.qobuz, tidal: ids.tidal };
  const sourceHint = hint?.provider ?? preferredProvider;
  const order = streamProvidersInOrder(env, preferredProvider);

  if (sourceHint === "deezer") {
    const deezerAttempts: Array<{ priority: number; run: () => Promise<StreamResult | null> }> = [];
    let priority = 0;
    if (ids.tidal) {
      deezerAttempts.push({
        priority: priority++,
        run: () => streamFromProvider(env, "tidal", ids.tidal!, quality, crossIds),
      });
    }
    if (ids.qobuz) {
      deezerAttempts.push({
        priority: priority++,
        run: () => streamFromProvider(env, "qobuz", ids.qobuz!, quality, crossIds),
      });
    }
    deezerAttempts.push({
      priority: priority,
      run: () => streamFromProvider(env, "deezer", ids.deezer ?? trackId, quality, crossIds),
    });
    const raced = await raceBest(deezerAttempts);
    if (raced) return raced;
  }

  const attempts = order
    .map((provider, index) => {
      const providerTrackId = pickIdForProvider(ids, provider, ids.fallback ?? trackId, sourceHint);
      if (!providerTrackId) return null;
      return {
        priority: index,
        run: () => streamFromProvider(env, provider, providerTrackId, quality, crossIds),
      };
    })
    .filter((entry): entry is { priority: number; run: () => Promise<StreamResult | null> } => entry != null);

  return raceBest(attempts);
}

async function expandTrackIds(
  env: Env,
  trackId: string,
  providerHint?: string
): Promise<Record<string, string>> {
  const ids: Record<string, string> = { fallback: trackId };
  if (providerHint) ids[providerHint] = trackId;

  const resolved = await resolveTrack({
    trackId,
    provider: providerHint,
    env,
  }).catch(() => null);

  if (resolved?.tidal_id) ids.tidal = resolved.tidal_id;
  if (resolved?.qobuz_id) ids.qobuz = resolved.qobuz_id;
  if (resolved?.deezer_id) ids.deezer = resolved.deezer_id;
  if (resolved?.amazon_id) ids.amazon = resolved.amazon_id;

  if (!ids.qobuz && resolved?.isrc) {
    const qobuzFromIsrc = await lookupQobuzTrackByIsrc(resolved.isrc);
    if (qobuzFromIsrc) ids.qobuz = qobuzFromIsrc;
  }

  console.log(
    "VANTA_PLAY_TRACK_REQUEST",
    JSON.stringify({
      phase: "expand_track_ids",
      trackId,
      providerHint: providerHint ?? null,
      tidal_id: ids.tidal ?? null,
      qobuz_id: ids.qobuz ?? null,
      deezer_id: ids.deezer ?? null,
      isrc: resolved?.isrc ?? null,
    })
  );

  return ids;
}

function pickIdForProvider(
  ids: Record<string, string>,
  provider: ProviderId,
  fallback: string,
  sourceHint?: string
): string {
  if (ids[provider]) return ids[provider];
  const normalizedHint = sourceHint?.trim().toLowerCase();
  if (normalizedHint === provider) return ids[normalizedHint] ?? fallback;
  return "";
}

export async function streamFromProvider(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string,
  crossIds: { qobuz?: string; tidal?: string } = {}
): Promise<StreamResult | null> {
  if (!trackId) return null;

  const upstream = await streamViaUpstream(env, provider, trackId, quality);
  if (upstream) return upstream;

  if (provider === "qobuz" || provider === "deezer" || provider === "tidal" || provider === "amazon") {
    const musicDl = await streamViaMusicDl(env, provider, trackId, quality);
    if (musicDl) return musicDl;
  }

  return streamWithPublicFallbacks(env, provider, trackId, quality, crossIds);
}

async function streamViaUpstream(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  const key = UPSTREAM_BY_PROVIDER[provider];
  if (!key) return null;

  const base = env[key]?.trim();
  if (!base) return null;

  const trimmedBase = base.replace(/\/$/, "");
  const id = encodeURIComponent(trackId);
  const candidates = [
    `${trimmedBase}/${id}?quality=${encodeURIComponent(quality)}&provider=${provider}`,
    `${trimmedBase}/${id}?quality=${encodeURIComponent(quality)}`,
    `${trimmedBase}/${id}`,
    `${trimmedBase}?id=${id}&quality=${encodeURIComponent(quality)}&service=${provider}`,
    `${trimmedBase}?trackId=${id}&quality=${encodeURIComponent(quality)}&provider=${provider}`,
  ];

  for (const endpoint of candidates) {
    const getPayload = await fetchJson(endpoint, { method: "GET" });
    const getResult = normalizeStreamPayload(getPayload, provider);
    if (getResult) return getResult;

    const postPayload = await fetchJson(trimmedBase, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id: trackId, quality, service: provider, provider }),
    });
    const postResult = normalizeStreamPayload(postPayload, provider);
    if (postResult) return postResult;
  }

  return null;
}

async function streamViaMusicDl(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  const base = env.MUSICDL_BASE_URL?.trim();
  if (!base) return null;

  const trimmedBase = base.replace(/\/$/, "");
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (env.MUSICDL_API_KEY?.trim()) {
    headers.Authorization = `Bearer ${env.MUSICDL_API_KEY.trim()}`;
    headers["X-Api-Key"] = env.MUSICDL_API_KEY.trim();
  }

  const servicePath = provider;
  const endpoints = [
    `${trimmedBase}/api/${servicePath}/stream`,
    `${trimmedBase}/api/stream/${servicePath}`,
    `${trimmedBase}/${servicePath}/stream`,
    `${trimmedBase}/api/dl`,
    `${trimmedBase}/download`,
  ];

  const bodies = [
    { id: trackId, track_id: trackId, quality, service: provider },
    { id: trackId, quality, provider },
    { trackId, quality, service: provider },
  ];

  for (const endpoint of endpoints) {
    for (const body of bodies) {
      const payload = await fetchJson(endpoint, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });
      const result = normalizeStreamPayload(payload, provider);
      if (result) return result;
    }
  }

  return null;
}

function normalizeStreamPayload(payload: unknown, provider: ProviderId): StreamResult | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;

  const url = firstString(record, [
    "url",
    "streamUrl",
    "stream_url",
    "downloadUrl",
    "download_url",
    "download_url_flac",
    "link",
    "location",
  ]);
  if (!url) return null;

  const expiresAt = firstNumber(record, ["expiresAt", "expires_at", "exp", "expiration", "etsp"]);
  const quality = firstString(record, ["quality", "qualityLabel", "audioQuality"]);
  const format = firstString(record, ["format", "codec", "mimeType"]) ?? "flac";
  const bitrate =
    firstNumber(record, ["bitrateKbps", "bitrate_kbps", "bitrate", "bit_rate", "br"]) ??
    inferBitrateKbps(quality, format);

  return {
    url,
    streamUrl: url,
    format,
    quality: quality ?? qualityLabelFromBitrate(bitrate, format),
    mimeType: format.includes("/") ? format : `audio/${format}`,
    bitrateKbps: bitrate,
    expiresAt: expiresAt ?? undefined,
    provider,
  };
}

function firstString(record: Record<string, unknown>, keys: string[]): string | null {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return null;
}

function firstNumber(record: Record<string, unknown>, keys: string[]): number | null {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && /^\d+$/.test(value)) return Number(value);
  }
  return null;
}
