import { is360Quality, isAtmosQuality } from "../lib/stream-quality";
import type { Env, ProviderId } from "../types";

const UA = "VANTA-MusicGateway/2.0";

const FETCH_TIMEOUT_MS = 4_000;

export async function fetchJson(url: string, init?: RequestInit): Promise<unknown | null> {
  try {
    const response = await fetch(url, {
      ...init,
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
      headers: {
        Accept: "application/json",
        "User-Agent": UA,
        ...(init?.headers ?? {}),
      },
    });
    if (!response.ok) return null;
    const text = await response.text();
    try {
      return JSON.parse(text);
    } catch {
      const trimmed = text.trim();
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return { url: trimmed.replace(/^"|"$/g, "") };
      }
      return null;
    }
  } catch (err) {
    console.warn("VANTA_FETCH_ERROR", JSON.stringify({ url, error: err instanceof Error ? err.message : String(err) }));
    return null;
  }
}

export async function fetchText(url: string, init?: RequestInit): Promise<string | null> {
  try {
    const response = await fetch(url, {
      ...init,
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
      headers: {
        Accept: "*/*",
        "User-Agent": UA,
        ...(init?.headers ?? {}),
      },
    });
    if (!response.ok) return null;
    return response.text();
  } catch (err) {
    console.warn("VANTA_FETCH_ERROR", JSON.stringify({ url, error: err instanceof Error ? err.message : String(err) }));
    return null;
  }
}

export function hasLicensedTidalSession(env: Env): boolean {
  return Boolean(
    env.TIDAL_STREAM_UPSTREAM?.trim() ||
    env.TIDAL_API_URL?.trim() ||
    env.HIFI_API_URL?.trim() ||
    hasOperatorProvider(env, "tidal")
  );
}

export function providerStatus(env: Env): Record<string, { search: boolean; stream: boolean; licensed: boolean; notes: string[] }> {
  const notes = (items: string[]) => items;
  const qobuzLicensed = Boolean(env.QOBUZ_AUTH_TOKEN?.trim() || hasOperatorProvider(env, "qobuz"));
  const qobuzRelayConfigured = Boolean(env.QOBUZ_STREAM_UPSTREAM?.trim());
  const tidalLicensed = hasLicensedTidalSession(env);
  return {
    deezer: {
      search: true,
      stream: true,
      licensed: Boolean(env.DEEZER_STREAM_UPSTREAM?.trim() || hasOperatorProvider(env, "deezer")),
      notes: notes(["Search via public Deezer API", "SpotiFLAC extension audio streams through the gateway with byte-range seeking"]),
    },
    qobuz: {
      search: true,
      stream: true,
      licensed: qobuzLicensed,
      notes: notes([
        "Search via public Qobuz catalog",
        qobuzLicensed
          ? "Hi-res FLAC via the configured Qobuz session"
          : qobuzRelayConfigured
            ? "A Qobuz relay is configured; licensing and maximum quality depend on that relay"
            : "Licensed hi-res FLAC needs QOBUZ_AUTH_TOKEN or an authenticated operator backend",
      ]),
    },
    tidal: {
      search: false,
      stream: true,
      licensed: tidalLicensed,
      notes: notes(
        tidalLicensed
          ? ["Tidal IDs from /resolve", "Atmos is accepted only with E-AC-3 JOC or another verified spatial codec"]
          : [
              "Search is off — results come from Qobuz, Deezer, and Apple, not Tidal",
              "Atmos is accepted only when a source returns E-AC-3 JOC or another verified spatial codec. Stereo FLAC is not Atmos.",
            ]
      ),
    },
    amazon: {
      search: true,
      stream: true,
      licensed: Boolean(env.AMAZON_STREAM_UPSTREAM?.trim() || hasOperatorProvider(env, "amazon")),
      notes: notes(["Search via the Amazon public web catalog", "FLAC and E-AC-3 audio are decoded while streaming, with byte-range seeking", "Atmos requires an EC-3 extension type A signal in the media"]),
    },
    pandora: {
      search: false,
      stream: true,
      licensed: Boolean(env.PANDORA_STREAM_UPSTREAM?.trim() || hasOperatorProvider(env, "pandora")),
      notes: notes(["Pandora radio IDs from /resolve"]),
    },
    apple: {
      search: true,
      stream: false,
      licensed: false,
      notes: notes(["Search via iTunes API (metadata only)", "Apple Music is not a VANTA stream source"]),
    },
    soundcloud: {
      search: true, stream: true, licensed: false,
      notes: ["Public progressive MP3/Opus and plain HLS only; previews and protected streams are excluded", "Playback availability depends on the public formats supplied for each track"],
    },
    spotify: {
      search: true,
      stream: false, licensed: false,
      notes: ["Metadata catalog; playback maps to the same recording on an audio provider"],
    },
    resolve: {
      search: false,
      stream: false,
      licensed: false,
      notes: notes(["Odesli + IDHS map catalog IDs across services"]),
    },
  };
}

function hasOperatorProvider(env: Env, provider: ProviderId): boolean {
  if (!env.OPERATOR_BACKEND_URL?.trim() || !env.OPERATOR_BACKEND_API_KEY?.trim()) return false;
  return (env.OPERATOR_BACKEND_PROVIDERS ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .includes(provider);
}

export type UpstreamKey =
  | "QOBUZ_STREAM_UPSTREAM"
  | "TIDAL_STREAM_UPSTREAM"
  | "DEEZER_STREAM_UPSTREAM"
  | "AMAZON_STREAM_UPSTREAM"
  | "PANDORA_STREAM_UPSTREAM";

export const UPSTREAM_BY_PROVIDER: Record<ProviderId, UpstreamKey | null> = {
  qobuz: "QOBUZ_STREAM_UPSTREAM",
  tidal: "TIDAL_STREAM_UPSTREAM",
  deezer: "DEEZER_STREAM_UPSTREAM",
  amazon: "AMAZON_STREAM_UPSTREAM",
  pandora: "PANDORA_STREAM_UPSTREAM",
  apple: null,
  soundcloud: null,
  spotify: null,
};

export function streamProvidersInOrder(env: Env, preferred?: string, quality?: string): ProviderId[] {
  const configured = parseProviderList(env.ENABLED_STREAM_PROVIDERS);
  const normalized = preferred?.trim().toLowerCase() as ProviderId | undefined;

  if (isAtmosQuality(quality)) {
    const atmosCapable = configured.filter((provider) => provider === "tidal" || provider === "amazon");
    if (normalized === "tidal" || normalized === "amazon") {
      return [...new Set([normalized, ...atmosCapable])];
    }
    return atmosCapable;
  }

  if (is360Quality(quality)) {
    // Sony 360 Reality Audio lives on Amazon Music only.
    const spatial360 = configured.filter((provider) => provider === "amazon");
    if (normalized === "amazon") {
      return [...new Set([normalized, ...spatial360])];
    }
    return spatial360;
  }

  const immersiveFirst = quality?.trim().toLowerCase() === "hi_res";
  const ranked = immersiveFirst
    ? (["tidal", "amazon", ...configured.filter((p) => p !== "tidal" && p !== "amazon")] as ProviderId[])
    : configured;
  if (!normalized) return [...new Set(ranked)];
  if (immersiveFirst && (normalized === "qobuz" || normalized === "deezer")) {
    return [...new Set(ranked)];
  }
  if (!ranked.includes(normalized)) return [normalized, ...ranked];
  return [normalized, ...ranked.filter((p) => p !== normalized)];
}

function parseProviderList(raw: string | undefined): ProviderId[] {
  return (raw ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter((value): value is ProviderId =>
      ["qobuz", "tidal", "deezer", "amazon", "pandora", "apple", "soundcloud", "spotify"].includes(value)
    );
}
