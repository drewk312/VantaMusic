import type { Env, ProviderId, StreamResult } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import {
  hasDolbyAtmosSignal,
  hasImmersiveContainerSignal,
  hasSony360Signal,
  inferBitrateKbps,
  inferContainerFromUrl,
  isHiResSignal,
} from "../lib/stream-quality";
import {
  communitySessionFromEnv,
  signCommunityRequest,
} from "./community-session";
import { isRelayPoolDown as isRelayPoolDownScope, markRelayPoolDown as markRelayPoolDownScope, markRelayPoolUp as markRelayPoolUpScope } from "./relay-health";
import { durationFromMpd } from "./tidal-api";
import { extensionAudioUrl } from "../extensions/audio-proxy";
import { amazonPayloadDecryptionKey, amazonDirectUrlIsLocked } from "../lib/playable-stream";

export type CommunityKind = "qobuz" | "tidal" | "amazon" | "deezer";

// SpotiFLAC community relays. The `*-oss` hosts are the CLASSIC open pool:
// they accept a plain JSON body {"id","quality":"16"|"24"} signed with our
// HMAC community session (X-Sig-*) and return either a direct full-length
// CDN URL (Qobuz/Amazon) or a `MANIFEST:<base64 MPD>` (Tidal). The lettered
// `*.{qbz,tdl,amz,dzr}xn.qzz.io` shards speak the closed "SpotiFLAC-Next"
// encrypted protocol, implemented in community-next.ts and used from the
// stream.ts cascade (step 7). Keep this classic list free of Next hosts so
// we never burn Worker subrequests on `400 Encrypted request required`.
const NEXT_HOSTS: Record<CommunityKind, string[]> = {
  qobuz: [
    "https://qbz-oss.spotbye.qzz.io", // classic open pool — flaky, retry budget low
    "https://qbdlx.spotbye.qzz.io", // up (200)
    "https://qbzmt.spotbye.qzz.io", // up (200)
  ],
  tidal: [
    "https://tdl-oss.spotbye.qzz.io", // alive (403 root, /api/dl expected)
  ],
  amazon: [
    "https://amz-oss.spotbye.qzz.io", // alive (200)
  ],
  // dzr-oss died (DNS NXDOMAIN, CF 530/1016 verified). The Deezer community
  // fallback is the public mirror path, not a relay: never burn requests here.
  deezer: [],
};

function isCommunityKind(provider: ProviderId): provider is CommunityKind {
  return provider === "qobuz" || provider === "tidal" || provider === "amazon" || provider === "deezer";
}

function mapCommunityQuality(quality: string): string {
  const value = quality.trim().toLowerCase();
  if (value === "atmos" || value === "dolby_atmos" || value.includes("atmos") || value === "eac3_joc") {
    return "atmos";
  }
  if (value === "360" || value === "360ra" || value === "sony360" || value === "sony_360" || value === "360_reality_audio") {
    return "360";
  }
  if (value === "hi_res" || value === "hi_res_lossless" || value === "24") return "24";
  return "16";
}

function perKindRelaySecret(env: Env, kind: CommunityKind): string | undefined {
  switch (kind) {
    case "qobuz":
      return env.COMMUNITY_RELAY_QOBUZ;
    case "tidal":
      return env.COMMUNITY_RELAY_TIDAL;
    case "amazon":
      return env.COMMUNITY_RELAY_AMAZON;
    default:
      return undefined;
  }
}

function communityBases(env: Env, kind: CommunityKind): string[] {
  // Operator-provided relay host list (comma-separated origins) wins. In
  // production this is the ONLY source of relay hosts — the builtin list
  // below is dev/test-only so a copy of this repo cannot run a working
  // gateway without this account's Worker secrets.
  const perKind = perKindRelaySecret(env, kind)?.trim();
  if (perKind) {
    const hosts = perKind
      .split(",")
      .map((value) => value.trim().replace(/\/+$/, ""))
      .filter((value) => value.startsWith("http"));
    if (hosts.length > 0) return hosts;
  }
  const configured = env.COMMUNITY_RELAY_BASE?.trim().replace(/\/+$/, "");
  if (configured && configured.includes(kind)) {
    return [configured];
  }
  if (configured && env.COMMUNITY_FORCE_BASE === "true") {
    return [configured];
  }
  if (env.ENVIRONMENT === "production") {
    // Production deployments must provide relay hosts via Worker secrets.
    return [];
  }
  // Classic open pool (`*-oss`) ONLY — the sole protocol our plain-JSON HMAC
  // body works with. When present, take up to two candidates (qbz-oss is
  // flaky, so its fresh mirrors follow). The `*-N` shards reject plain JSON
  // and waste the Worker subrequest budget, so they are never attempted.
  const dlist = NEXT_HOSTS[kind];
  const attemptable = dlist.slice(0, kind === "qobuz" ? 3 : 1);
  return attemptable.length > 0 ? attemptable : [];
}

/**
 * POST /api/dl to the live SpotiFLAC community relay with the HMAC-signed
 * session headers. No paid account required — only a valid community session
 * (one-time verification) stored in env. Returns a StreamResult honoring the
 * requested Atmos quality when the relay returns a real E-AC-3 JOC stream.
 */
export async function streamViaSignedCommunity(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  if (!isCommunityKind(provider)) return null;
  if (!trackId.trim()) return null;

  const session = communitySessionFromEnv(env);
  if (!session) return null;

  const bases = communityBases(env, provider as CommunityKind);
  if (bases.length === 0) return null;

  if (isRelayPoolDownScope(`sc:${provider}`)) {
    console.warn(
      "VANTA_COMMUNITY_BREAKER",
      JSON.stringify({ scope: `sc:${provider}`, trackId, action: "skip", reason: "relay pool marked down" })
    );
    return null;
  }

  const bodyText = JSON.stringify({
    id: trackId.trim(),
    quality: mapCommunityQuality(quality),
  });
  const body = new TextEncoder().encode(bodyText);

  let has503 = false;
  // Try Next sharded pool in order — if one shard is on 503 break, next shard likely isn't
  for (const base of bases) {
    const endpoint = `${base}/api/dl`;
    const url = new URL(endpoint);
    const headers = await signCommunityRequest(
      session,
      "POST",
      url.pathname,
      url.search,
      body
    );
    headers["Content-Type"] = "application/json";
    headers["Accept"] = "application/json";

    let response: Response;
    try {
      response = await fetch(endpoint, {
        method: "POST",
        headers,
        body: bodyText,
      });
    } catch {
      continue;
    }

    if (!response.ok) {
      const bodyHint = await response.text().catch(() => "");
      console.warn(
        "VANTA_COMMUNITY_SHARD",
        JSON.stringify({ provider, trackId, base, status: response.status, body: bodyHint.slice(0, 120) })
      );
      if (response.status === 400 && bodyHint.includes("fmt=6") && quality !== "16") {
        const retryBody = { id: trackId, quality: "16" };
        const retryBodyText = JSON.stringify(retryBody);
        const retryHeaders = await signCommunityRequest(
          session,
          "POST",
          url.pathname,
          url.search,
          new TextEncoder().encode(retryBodyText)
        );
        retryHeaders["Content-Type"] = "application/json";
        retryHeaders["Accept"] = "application/json";
        try {
          const retryResp = await fetch(endpoint, {
            method: "POST",
            headers: retryHeaders,
            body: retryBodyText,
          });
          if (retryResp.ok) {
            const retryPayload = (await retryResp.json().catch(() => null)) as Record<string, unknown> | null;
            if (retryPayload && typeof retryPayload.url === "string") {
              const streamUrl = normalizePublicHttpsUrl(retryPayload.url);
              if (streamUrl) {
                return {
                  url: streamUrl,
                  streamUrl,
                  format: "flac",
                  quality: "16",
                  mimeType: "audio/flac",
                  bitrateKbps: 1411,
                  provider,
                  isDolbyAtmos: false,
                  isSpatialAudio: false,
                  isSurround: false,
                };
              }
            }
          }
        } catch {
          // continue
        }
      }
      if (response.status === 503) {
        has503 = true;
      }
      if (response.status === 401) {
        // The status alone does not distinguish expiry, signing errors, or
        // network binding. Do not report an IP mismatch without evidence.
        console.warn(
          "VANTA_COMMUNITY_401",
          JSON.stringify({ provider, trackId, base, reason: "session or signature rejected" })
        );
        markRelayPoolDownScope(`sc:${provider}`, "401 signed request validation failed", 60_000);
        return null;
      }
      if (response.status === 428) {
        // Verification session required/invalid — try next shard (same session) or fallback
        continue;
      }
      if (response.status === 429 || response.status === 503) {
        // This shard on break/rate-limit — try next shard immediately (Next has no global break)
        continue;
      }
      continue;
    }

    markRelayPoolUpScope(`sc:${provider}`);

    const payload = (await response.json().catch(() => null)) as Record<string, unknown> | null;
    if (!payload) {
      console.warn("VANTA_COMMUNITY_SHARD", JSON.stringify({ provider, trackId, base, reason: "non-json body" }));
      continue;
    }

    const rawUrl = typeof payload.url === "string" ? payload.url : null;
    let streamUrl = rawUrl ? normalizePublicHttpsUrl(rawUrl) : null;
    let isMpdManifest = false;
    let mpdIsAtmos = false;
    let mpdIs360 = false;

    if (rawUrl && rawUrl.startsWith("MANIFEST:")) {
      const b64 = rawUrl.slice("MANIFEST:".length).trim();
      let xml = "";
      try {
        xml = atob(b64);
      } catch {
        // invalid base64
      }
      if (xml) {
        const duration = durationFromMpd(xml);
        if (duration != null && duration <= 45) {
          console.warn(
            "VANTA_TIDAL_PREVIEW_REJECTED",
            JSON.stringify({ trackId, duration, reason: `mpd duration ${duration}s` })
          );
          continue;
        }
        isMpdManifest = true;
        mpdIsAtmos = /EAC3_JOC|ec-3|eac3/i.test(xml);
        mpdIs360 = /mha1|mhm1|mpeg-h|360.?ra|360 reality/i.test(xml);
        const safeB64 = b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
        const gatewayOrigin = env.GATEWAY_BASE_URL?.trim() || "https://vanta-music-gateway.16drewk.workers.dev";
        streamUrl = `${gatewayOrigin.replace(/\/+$/, "")}/manifest/mpd?data=${safeB64}`;
      }
    }

    if (!streamUrl) {
      console.warn(
        "VANTA_COMMUNITY_SHARD",
        JSON.stringify({ provider, trackId, base, reason: "no usable https url", urlPrefix: rawUrl?.slice(0, 40) })
      );
      continue;
    }

    const payloadKey = amazonPayloadDecryptionKey(payload);
    if (amazonDirectUrlIsLocked(provider, streamUrl, payloadKey)) {
      console.warn(
        "VANTA_COMMUNITY_SHARD",
        JSON.stringify({ provider, trackId, base, reason: "amazon cenc without key" })
      );
      continue;
    }
    if (provider === "amazon" && payloadKey) {
      const proxied = await extensionAudioUrl(env, {
        provider: "amazon",
        id: trackId,
        url: streamUrl,
        key: payloadKey,
        format: "mp4",
        expires: Date.now() + 30 * 60_000,
      });
      if (!proxied) {
        console.warn(
          "VANTA_COMMUNITY_SHARD",
          JSON.stringify({ provider, trackId, base, reason: "amazon decrypt proxy unavailable" })
        );
        continue;
      }
      streamUrl = proxied;
    }

    const resolvedQuality = typeof payload.quality === "string"
      ? payload.quality
      : (quality === "atmos" || quality.includes("atmos") || mpdIsAtmos ? "Dolby Atmos" : mapCommunityQuality(quality));
    const payloadFormat = typeof payload.format === "string" ? payload.format
      : typeof payload.codec === "string" ? payload.codec
      : undefined;
    const format = payloadFormat
      ?? (isMpdManifest ? (mpdIsAtmos ? "eac3-joc" : mpdIs360 ? "mha1" : "flac") : (inferContainerFromUrl(streamUrl) ?? (hasDolbyAtmosSignal(resolvedQuality) ? "m4a" : "flac")));
    const atmos = mpdIsAtmos || hasDolbyAtmosSignal(resolvedQuality, format);
    const bitrateKbps = inferBitrateKbps(String(resolvedQuality), format);
    const mimeType = isMpdManifest ? "application/dash+xml" : (format.includes("/") ? format : `audio/${format}`);
    const sony360 = mpdIs360 || hasSony360Signal(String(resolvedQuality), format, mimeType) || hasImmersiveContainerSignal(format, mimeType);
    const spatial = atmos || sony360;

    return {
      url: streamUrl,
      streamUrl,
      format,
      quality: sony360 && !atmos ? "360 Reality Audio" : String(resolvedQuality),
      mimeType,
      bitrateKbps,
      provider,
      isDolbyAtmos: atmos,
      isSpatialAudio: spatial,
      isSurround: spatial,
      spatialFormat: atmos ? "DOLBY_ATMOS" : (sony360 ? "SONY_360_REALITY_AUDIO" : undefined),
      isHiRes: isHiResSignal(String(resolvedQuality), format),
    };
  }

  if (has503) {
    markRelayPoolDownScope(`sc:${provider}`, "all signed community shards returned 503 maintenance break", 15_000);
  }
  return null;
}


