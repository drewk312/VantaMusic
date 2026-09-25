import type { Env, ProviderId, StreamResult } from "../types";
import {
  hasAtmosCodecSignal,
  hasDolbyAtmosSignal,
  hasImmersiveContainerSignal,
  hasSony360Signal,
  inferBitrateKbps,
  inferContainerFromUrl,
  is360Quality,
  isAtmosQuality,
  qualityLabelFromBitrate,
} from "../lib/stream-quality";
import { streamWithPublicFallbacks, type StreamCrossIds } from "./public-stream";
import { streamTidalNative } from "./tidal-api";
import { streamTidalHifiApi } from "./hifi-api";
import { streamFromOperatorBackend } from "./operator-backend";
import { lookupQobuzTrackByIsrc, streamQobuzAuthenticated, streamQobuzPublic, isQobuzSampleUrl } from "./qobuz-api";
import { streamViaNextCommunity } from "./community-next";
import { streamViaZarzSigned, zarzCanPlay } from "./zarz-signed";
import { streamViaMonochromeUnified } from "./monochrome-unified";
import { resolveTrack } from "./resolve";
import { fetchJson, streamProvidersInOrder, UPSTREAM_BY_PROVIDER } from "./shared";
import { splitCatalogTrackId } from "../http";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { familyForProvider } from "../types";
import { raceFirst } from "../lib/race-first";
import { streamSoundCloud, streamSoundCloudExact } from "./soundcloud";
import { streamViaGdstudioExact } from "./gdstudio-catalog";
import { amazonDirectUrlIsLocked } from "../lib/playable-stream";

export const streamTiming = {
  globalTimeoutMs: 24_000,
  fastPathMs: 800,
  expandBudgetMs: 800,
  spatialTimeoutMs: 12_000,
};

export function configureStreamTimingForTests(partial: Partial<typeof streamTiming>): () => void {
  const previous = { ...streamTiming };
  Object.assign(streamTiming, partial);
  return () => {
    streamTiming.globalTimeoutMs = previous.globalTimeoutMs;
    streamTiming.fastPathMs = previous.fastPathMs;
    streamTiming.expandBudgetMs = previous.expandBudgetMs;
    streamTiming.spatialTimeoutMs = previous.spatialTimeoutMs;
  };
}

export async function streamWithFallback(
  env: Env,
  trackId: string,
  quality: string,
  preferredProvider?: string,
  hint?: { provider?: string },
  request?: Request
): Promise<StreamResult | null> {
  const deadline = Date.now() + streamTiming.globalTimeoutMs;
  if (quality === "auto") {
    const stereoP = streamAtQuality(env, trackId, "24", preferredProvider, hint, deadline, request);
    const spatialP = raceFirst([
      () => streamAtQuality(env, trackId, "atmos", preferredProvider, hint, Date.now() + streamTiming.spatialTimeoutMs, request),
      () => streamAtQuality(env, trackId, "360", preferredProvider, hint, Date.now() + streamTiming.spatialTimeoutMs, request),
    ]);
    const spatialWaitMs = Math.min(streamTiming.spatialTimeoutMs, 3_500);
    const spatial = await raceWithDeadline(Date.now() + spatialWaitMs, () => spatialP);
    if (spatial && !amazonDirectUrlIsLocked(spatial.provider, spatial.url ?? spatial.streamUrl ?? "")) {
      return spatial;
    }
    return stereoP;
  }
  if (isAtmosQuality(quality) || is360Quality(quality)) {
    return streamAtQuality(env, trackId, quality, preferredProvider, hint, Date.now() + streamTiming.spatialTimeoutMs, request);
  }
  return streamAtQuality(env, trackId, quality, preferredProvider, hint, deadline, request);
}

async function streamAtQuality(
  env: Env,
  trackId: string,
  quality: string,
  preferredProvider: string | undefined,
  hint: { provider?: string } | undefined,
  deadline: number,
  request?: Request
): Promise<StreamResult | null> {
  const order = streamProvidersInOrder(env, preferredProvider, quality);
  if ((isAtmosQuality(quality) || is360Quality(quality)) && order.length === 0) {
    return null;
  }

  const split = splitCatalogTrackId(trackId);
  const sourceHint = split.provider ?? hint?.provider ?? preferredProvider;
  const directProvider = (split.provider ?? sourceHint ?? (/^\d{4,}$/.test(trackId) ? "qobuz" : undefined)) as ProviderId | undefined;

  // 1. FAST PATH: If the exact provider and track ID are known, try the exact provider IMMEDIATELY!
  const directAttempt = directProvider && order.includes(directProvider)
    ? streamFromProvider(env, directProvider, split.id, quality, {}, request).catch(() => null)
    : null;
  if (directAttempt) {
    const directStream = await raceWithDeadline(Math.min(deadline, Date.now() + streamTiming.fastPathMs), () => directAttempt);
    if (directStream) return directStream;
  }

  // Spatial mixes usually require a cross-catalog Amazon/Tidal ID. Give
  // Odesli enough time before we race providers that cannot serve Atmos/360.
  const spatialRequest = isAtmosQuality(quality) || is360Quality(quality);
  const expandBudget = Math.min(
    spatialRequest ? Math.max(streamTiming.expandBudgetMs, 3_500) : streamTiming.expandBudgetMs,
    Math.max(0, deadline - Date.now())
  );
  const ids: Record<string, string> = {};
  const recording: { title?: string; artist?: string; durationSec?: number } = {};
  let expanded = false;
  const expansion = expandTrackIdsInto(env, trackId, sourceHint ?? directProvider, ids, recording, quality)
    .finally(() => { expanded = true; });
  await raceWithDeadline(Date.now() + expandBudget, () => expansion);

  // For 360/Atmos, never probe with a foreign catalog id (e.g. Qobuz digits on
  // Amazon). Wait out the expand window when the target provider id is still
  // missing so the late-expansion race below can use the resolved ASIN/TIDAL id.
  if (spatialRequest && !expanded) {
    await raceWithDeadline(deadline, () => expansion);
  }
  const crossIds = buildCrossIds(ids, recording);

  // Keep the original request in the race: a slow success is still playable.
  // Reusing its promise also avoids issuing duplicate upstream requests.
  if (Date.now() < deadline) {
    const attempts = order
      .map((provider) => {
        if (provider === directProvider && directAttempt && (!ids[provider] || ids[provider] === split.id)) return () => directAttempt;
        const providerTrackId = pickIdForProvider(ids, provider, ids.fallback ?? trackId, sourceHint);
        if (!providerTrackId) return null;
        return () => streamFromProvider(env, provider, providerTrackId, quality, crossIds, request);
      })
      .filter((fn): fn is () => Promise<StreamResult | null> => fn != null);

    // A slow catalog lookup can still discover an exact recording while audio
    // requests are pending. Include those newly resolved IDs in the same race.
    if (!expanded) attempts.push(async () => {
      await expansion;
      if (Date.now() >= deadline) return null;
      const late = order.filter((provider) => (provider !== directProvider || ids[provider] !== split.id) && ids[provider])
        .map((provider) => () => streamFromProvider(env, provider, ids[provider], quality, buildCrossIds(ids, recording), request));
      return late.length ? raceFirst(late) : null;
    });

    if (attempts.length > 0) {
      const stream = await raceWithDeadline(deadline, () => raceFirst(attempts));
      if (stream) return stream;
    }
  }
  return null;
}

export async function raceWithDeadline<T>(deadlineMs: number, fn: () => Promise<T | null>): Promise<T | null> {
  const remaining = deadlineMs - Date.now();
  if (remaining <= 0) return null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      fn(),
      new Promise<T | null>((resolve) => { timer = setTimeout(() => resolve(null), remaining); }),
    ]);
  } catch {
    return null;
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}

async function expandTrackIdsInto(
  env: Env,
  trackId: string,
  providerHint: string | undefined,
  ids: Record<string, string>,
  recording: { title?: string; artist?: string; durationSec?: number },
  quality?: string
): Promise<Record<string, string>> {
  const split = splitCatalogTrackId(trackId);
  const catalogId = split.id;
  ids.fallback = catalogId;
  if (providerHint) ids[providerHint] = catalogId;
  if (split.provider) ids[split.provider] = catalogId;

  const resolved = await resolveTrack({
    trackId: catalogId,
    provider: providerHint ?? split.provider,
    env,
  }).catch(() => null);

  if (resolved?.tidal_id) ids.tidal = resolved.tidal_id;
  if (resolved?.tidal_atmos_id) {
    ids.tidal_atmos = resolved.tidal_atmos_id;
    if (isAtmosQuality(quality ?? "") || quality === "auto") {
      ids.tidal = resolved.tidal_atmos_id;
    }
  }
  if (resolved?.qobuz_id) ids.qobuz = resolved.qobuz_id;
  if (resolved?.deezer_id) ids.deezer = resolved.deezer_id;
  if (resolved?.amazon_id) ids.amazon = resolved.amazon_id;
  if (resolved?.amazon_atmos_id) {
    ids.amazon_atmos = resolved.amazon_atmos_id;
    if (isAtmosQuality(quality ?? "") || is360Quality(quality ?? "") || quality === "auto") {
      ids.amazon = resolved.amazon_atmos_id;
    }
  }
  if (resolved?.isrc) ids.isrc = resolved.isrc;
  if (resolved?.title) recording.title = resolved.title;
  if (resolved?.artist) recording.artist = resolved.artist;
  if (resolved?.durationSec) recording.durationSec = resolved.durationSec;

  if (!ids.qobuz && resolved?.isrc) {
    const qobuzFromIsrc = await lookupQobuzTrackByIsrc(resolved.isrc).catch(() => null);
    if (qobuzFromIsrc) ids.qobuz = qobuzFromIsrc;
  }

  console.log(
    "VANTA_PLAY_TRACK_REQUEST",
    JSON.stringify({
      phase: "expand_track_ids",
      trackId,
      providerHint: providerHint ?? null,
      tidal_id: ids.tidal ?? null,
      tidal_atmos_id: ids.tidal_atmos ?? null,
      qobuz_id: ids.qobuz ?? null,
      deezer_id: ids.deezer ?? null,
      isrc: resolved?.isrc ?? null,
    })
  );

  return ids;
}

function buildCrossIds(
  ids: Record<string, string>,
  recording: { title?: string; artist?: string; durationSec?: number }
): StreamCrossIds {
  return {
    qobuz: ids.qobuz,
    tidal: ids.tidal,
    isrc: ids.isrc,
    deezer: ids.deezer,
    amazon: ids.amazon,
    title: recording.title,
    artist: recording.artist,
    durationSec: recording.durationSec,
  };
}

export function pickIdForProvider(
  ids: Record<string, string>,
  provider: ProviderId,
  fallback: string,
  sourceHint?: string
): string {
  if (ids[provider]) return ids[provider];
  const normalizedHint = sourceHint?.trim().toLowerCase();
  if (normalizedHint === provider) return ids[normalizedHint] ?? fallback;
  // Bare numeric IDs from /search are Qobuz track IDs on this gateway.
  if (!normalizedHint && provider === "qobuz" && /^\d{4,}$/.test(fallback)) return fallback;
  return "";
}

/**
 * Try to get a stream from a single provider. Subrequest-optimized:
 * each provider path uses at most 1-2 fetches (was 10-30).
 */
export async function streamFromProvider(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string,
  crossIds: StreamCrossIds = {},
  request?: Request
): Promise<StreamResult | null> {
  if (!trackId) return null;
  if (provider === "soundcloud") {
    if (isAtmosQuality(quality) || is360Quality(quality)) return null;
    return acceptStreamForRequestedQuality(await streamSoundCloud(trackId, env), quality, "PUBLIC");
  }
  // Metadata catalogs resolve to an exact recording on an audio provider.
  if (provider === "spotify" || provider === "apple") return null;

  // 1. Operator backend (1 fetch)
  const operator = await streamFromOperatorBackend(env, provider, trackId, quality);
  const acceptedOperator = acceptStreamForRequestedQuality(operator, quality, "OPERATOR");
  if (acceptedOperator) {
    logResolvedStream(provider, trackId, quality, acceptedOperator);
    return acceptedOperator;
  }

  const wantsSpatial = isAtmosQuality(quality) || is360Quality(quality);
  const nextProviders = provider === "qobuz" || provider === "tidal" || provider === "amazon" || provider === "deezer";

  // 1b. SpotiFLAC Next FIRST for Atmos / Sony 360 — sessionless lettered shards
  // are live even when Zarz extension sessions are expired. Stereo FLAC still
  // prefers Zarz/native below so BYOA stays primary for hi-res stereo.
  if (wantsSpatial && nextProviders) {
    const nextSpatial = acceptStreamForRequestedQuality(
      await streamViaNextCommunity(env, provider, trackId, quality),
      quality,
      "COMMUNITY"
    );
    if (nextSpatial) {
      logResolvedStream(provider, trackId, quality, nextSpatial);
      return nextSpatial;
    }
  }

  // 2. Current official SpotiFLAC extension contract: signed ticket + provider request.
  if (provider === "qobuz" || provider === "tidal" || provider === "deezer" || provider === "amazon") {
    if (await zarzCanPlay(env, provider)) {
      const zarz = await streamViaZarzSigned(env, provider, trackId, quality);
      const acceptedZarz = acceptStreamForRequestedQuality(zarz, quality, "ZARZ");
      if (acceptedZarz) {
        logResolvedStream(provider, trackId, quality, acceptedZarz);
        return acceptedZarz;
      }
    }
  }

  // 3. Tidal native (1 fetch)
  if (provider === "tidal") {
    const nativeAtmos = await streamTidalNative(env, trackId, quality, request);
    const acceptedNative = acceptStreamForRequestedQuality(nativeAtmos, quality, "TIDAL");
    if (acceptedNative) {
      logResolvedStream(provider, trackId, quality, acceptedNative);
      return acceptedNative;
    }
  }

  // 4. Tidal hifi-api (1 fetch)
  if (provider === "tidal") {
    const hifi = await streamTidalHifiApi(env, trackId, quality);
    const acceptedHifi = acceptStreamForRequestedQuality(hifi, quality, "TIDAL");
    if (acceptedHifi) {
      logResolvedStream(provider, trackId, quality, acceptedHifi);
      return acceptedHifi;
    }
  }

  // 5. Qobuz authenticated (1-3 fetches for format_id cascade)
  if (provider === "qobuz") {
    const official = await streamQobuzAuthenticated(env, trackId, quality, request);
    const acceptedOfficial = acceptStreamForRequestedQuality(official, quality, "QOBUZ");
    if (acceptedOfficial) {
      logResolvedStream(provider, trackId, quality, acceptedOfficial);
      return acceptedOfficial;
    }

    // 6. Qobuz public/community token (1-3 fetches)
    const pub = await streamQobuzPublic(env, trackId, quality);
      const acceptedPub = acceptStreamForRequestedQuality(pub, quality, "QOBUZ");
    if (acceptedPub) {
      logResolvedStream(provider, trackId, quality, acceptedPub);
      return acceptedPub;
    }
  }

  // 7. SpotiFLAC Next for stereo / remaining qualities (spatial already tried in 1b).
  // Never the classic HI-RES `*-oss` pool (CAPTCHA cliffs + overload breaks).
  if (!wantsSpatial && nextProviders) {
    const next = acceptStreamForRequestedQuality(
      await streamViaNextCommunity(env, provider, trackId, quality),
      quality,
      "COMMUNITY"
    );
    if (next) {
      logResolvedStream(provider, trackId, quality, next);
      return next;
    }
  }

  // 8. Monochrome Unified (1 fetch — only tries if Turnstile JWT is configured)
  if (provider === "tidal" || provider === "amazon" || provider === "qobuz" || provider === "deezer") {
    const monochrome = await streamViaMonochromeUnified(env, provider, trackId, quality, {
      isrc: crossIds.isrc,
      title: crossIds.title,
      artist: crossIds.artist,
      durationSec: crossIds.durationSec,
    });
    const acceptedMono = acceptStreamForRequestedQuality(monochrome, quality, "COMMUNITY");
    if (acceptedMono) {
      logResolvedStream(provider, trackId, quality, acceptedMono);
      return acceptedMono;
    }
  }

  // 9. Upstream relay — STREAMLINED: 1 POST only (was 5 URLs × GET+POST = 10 fetches)
  const upstream = await streamViaUpstream(env, provider, trackId, quality);
  const acceptedUpstream = acceptStreamForRequestedQuality(upstream, quality, "COMMUNITY");
  if (acceptedUpstream) {
    logResolvedStream(provider, trackId, quality, acceptedUpstream);
    return acceptedUpstream;
  }

  if (isAtmosQuality(quality) || is360Quality(quality)) return null;

  // 10. Public FLAC mirrors, then verified FLAC identity match, then MP3 last.
  const publicFallback = await streamWithPublicFallbacks(env, provider, trackId, quality, crossIds);
  const acceptedPublic = acceptStreamForRequestedQuality(publicFallback, quality, "PUBLIC");
  if (acceptedPublic) {
    logResolvedStream(provider, trackId, quality, acceptedPublic);
    return acceptedPublic;
  }

  const gdstudioFlac = await streamViaGdstudioExact(env, crossIds.title, crossIds.artist, quality, provider, "flac");
  const acceptedGdstudioFlac = acceptStreamForRequestedQuality(gdstudioFlac, quality, "PUBLIC");
  if (acceptedGdstudioFlac) {
    logResolvedStream(provider, trackId, quality, acceptedGdstudioFlac);
    return acceptedGdstudioFlac;
  }

  const soundcloud = await streamSoundCloudExact(env, crossIds.title, crossIds.artist, crossIds.durationSec);
    const acceptedSoundCloud = acceptStreamForRequestedQuality(soundcloud, quality, "PUBLIC");
  if (acceptedSoundCloud) {
    logResolvedStream("soundcloud", trackId, quality, acceptedSoundCloud);
    return acceptedSoundCloud;
  }

  const gdstudioMp3 = await streamViaGdstudioExact(env, crossIds.title, crossIds.artist, quality, provider, "mp3");
  const acceptedGdstudioMp3 = acceptStreamForRequestedQuality(gdstudioMp3, quality, "PUBLIC");
  if (acceptedGdstudioMp3) logResolvedStream(provider, trackId, quality, acceptedGdstudioMp3);
  return acceptedGdstudioMp3;
}

export function isSampleOrPreviewUrl(url: string): boolean {
  if (!url) return false;
  if (isQobuzSampleUrl(url)) return true;
  const lower = url.toLowerCase();
  if (lower.includes("audio-ssl.itunes.apple.com") || lower.includes("itunes.apple.com")) return true;
  if (lower.includes("cdns-preview") || lower.includes(".dzcdn.net/stream/")) return true;
  if (lower.includes("/preview/") || lower.includes("preview.mpd") || lower.includes("preview.m4a")) return true;
  return false;
}

/** Goal H: logs may carry the host of a stream URL, never the URL itself. */
function safeUrlHost(url: string): string | null {
  try {
    return new URL(url).host || null;
  } catch {
    return null;
  }
}

export function acceptStreamForRequestedQuality(
  result: StreamResult | null,
  quality: string,
  family?: StreamResult["family"]
): StreamResult | null {
  if (!result) return null;
  const streamUrl = result.url ?? result.streamUrl ?? "";
  if (isSampleOrPreviewUrl(streamUrl)) {
    // Goal H: never log full stream URLs; host + shape is enough to diagnose.
    console.warn(
      "VANTA_STREAM_SAMPLE_REJECTED",
      JSON.stringify({
        provider: result.provider,
        family: family ?? result.family ?? null,
        urlHost: safeUrlHost(streamUrl),
        reason: "sample or preview stream rejected",
      })
    );
    return null;
  }
  if (family) result.family = family;
  // A FLAC/stereo request must never be silently upgraded to a codec the
  // caller may be unable to decode (observed on Pixel Dolby decoders).
  if (!isAtmosQuality(quality)) {
    if (is360Quality(quality)) {
      const sony = hasSony360Signal(result.format, result.quality, result.mimeType) ||
        hasImmersiveContainerSignal(result.format, result.mimeType);
      if (sony) {
        result.isSpatialAudio = true;
        result.isSurround = true;
        result.spatialFormat = "SONY_360_REALITY_AUDIO";
        if (!hasSony360Signal(result.quality)) result.quality = "360 Reality Audio";
        return result;
      }
      // A verified Atmos stream is still immersive — accept as a substitute.
      if (result.isDolbyAtmos && hasAtmosCodecSignal(result.format, result.quality, result.mimeType)) return result;
      return null;
    }
    return result.isDolbyAtmos ||
      hasAtmosCodecSignal(result.format, result.quality, result.mimeType) ? null : result;
  }
  if (result.isDolbyAtmos && hasAtmosCodecSignal(result.format, result.quality, result.mimeType)) {
    return result;
  }
  return null;
}

/**
 * Upstream relay — OPTIMIZED: Only try 1 POST request (was 5 URLs × GET+POST = 10 fetches).
 * The POST body is the most universal format accepted by relay APIs.
 */
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

  // Single POST — the most universal relay API format
  const postPayload = await fetchJson(trimmedBase, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: trackId, quality, service: provider, provider }),
  });
  const postResult = normalizeStreamPayload(postPayload, provider);
  if (postResult) return postResult;

  // Single GET fallback with query params
  const id = encodeURIComponent(trackId);
  const getPayload = await fetchJson(
    `${trimmedBase}?id=${id}&quality=${encodeURIComponent(quality)}&service=${provider}`,
    { method: "GET" }
  );
  return normalizeStreamPayload(getPayload, provider);
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
  const safeUrl = normalizePublicHttpsUrl(url);
  if (!safeUrl) return null;
  if (isQobuzSampleUrl(safeUrl)) {
    console.warn(
      "VANTA_QOBUZ_SAMPLE_REJECTED",
      JSON.stringify({ urlHost: safeUrlHost(safeUrl ?? ""), reason: "range-limited preview from upstream relay" })
    );
    return null;
  }

  const expiresAt = firstNumber(record, ["expiresAt", "expires_at", "exp", "expiration", "etsp"]);
  const quality = firstString(record, ["quality", "qualityLabel", "audioQuality"]);
  const format = firstString(record, ["format", "codec", "mimeType"]) ?? inferContainerFromUrl(safeUrl);
  const bitrate =
    firstNumber(record, ["bitrateKbps", "bitrate_kbps", "bitrate", "bit_rate", "br"]) ??
    inferBitrateKbps(quality, format);
  const atmos = hasDolbyAtmosSignal(quality, format);
  const spatial = atmos;
  const surround = atmos;

  return {
    url: safeUrl,
    streamUrl: safeUrl,
    format,
    quality: quality ?? qualityLabelFromBitrate(bitrate, format),
    mimeType: format?.includes("/") ? format : (format ? `audio/${format}` : undefined),
    bitrateKbps: bitrate,
    expiresAt: expiresAt ?? undefined,
    provider,
    isDolbyAtmos: atmos,
    isSpatialAudio: spatial,
    isSurround: surround,
  };
}

function logResolvedStream(provider: ProviderId, trackId: string, quality: string, result: StreamResult): void {
  console.log(
    "VANTA_STREAM_RESOLVE",
    JSON.stringify({
      provider,
      family: result.family ?? null,
      trackId,
      quality,
      format: result.format,
      bitrateKbps: result.bitrateKbps,
      isDolbyAtmos: result.isDolbyAtmos,
      isSpatialAudio: result.isSpatialAudio,
      isSurround: result.isSurround,
      spatialFormat: result.spatialFormat ?? null,
      expiresAt: result.expiresAt ? new Date(result.expiresAt * 1000).toISOString() : null,
    })
  );
  // Goal H: source truth with only validated values. Never log URLs, signatures,
  // tokens, cookies, grants, or session ids.
  console.log(
    "VANTA_SOURCE",
    JSON.stringify({
      provider,
      family: result.family ?? familyForProvider(provider),
      canonical: trackId,
      requested: quality,
      actualFormat: result.format ?? null,
      bitDepth: result.bitDepth ?? null,
      sampleRateHz: result.sampleRateHz ?? null,
      channels: result.channelCount ?? null,
      spatial: result.spatialFormat ?? null,
      validated: !isSampleOrPreviewUrl(result.url ?? result.streamUrl ?? ""),
    })
  );
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
