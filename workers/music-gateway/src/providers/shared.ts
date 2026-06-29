import type { Env, ProviderId } from "../types";

const UA = "VANTA-MusicGateway/2.0";

export async function fetchJson(url: string, init?: RequestInit): Promise<unknown | null> {
  const response = await fetch(url, {
    ...init,
    headers: {
      Accept: "application/json",
      "User-Agent": UA,
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) return null;
  try {
    return await response.json();
  } catch {
    const text = await response.text();
    const trimmed = text.trim();
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return { url: trimmed.replace(/^"|"$/g, "") };
    }
    return null;
  }
}

export async function fetchText(url: string, init?: RequestInit): Promise<string | null> {
  const response = await fetch(url, {
    ...init,
    headers: {
      Accept: "*/*",
      "User-Agent": UA,
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) return null;
  return response.text();
}

export function providerStatus(env: Env): Record<string, { search: boolean; stream: boolean; notes: string[] }> {
  const notes = (items: string[]) => items;
  return {
    deezer: {
      search: true,
      stream: true,
      notes: notes(["Search via public Deezer API", "Streams via auto-resolve to Qobuz/Tidal + public fallbacks"]),
    },
    qobuz: {
      search: true,
      stream: true,
      notes: notes([
        "Search via auto-scraped Qobuz API creds",
        "Streams via community + GDStudio + WJHE + MusicDL (no keys needed)",
      ]),
    },
    tidal: {
      search: false,
      stream: true,
      notes: notes(["IDs from /resolve", "Streams via community gateway + GDStudio + encrypted relays"]),
    },
    amazon: {
      search: false,
      stream: true,
      notes: notes(["IDs from /resolve", "Streams via spotbye + encrypted community endpoints"]),
    },
    pandora: {
      search: false,
      stream: true,
      notes: notes(["Streams via community gateway fallback"]),
    },
    apple: {
      search: true,
      stream: false,
      notes: notes(["Search via iTunes API (metadata only)", "Use resolve to map into lossless providers"]),
    },
    resolve: {
      search: false,
      stream: false,
      notes: notes(["Odesli + IDHS + optional ZARZ_RESOLVE_URL always available"]),
    },
  };
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
};

export function streamProvidersInOrder(env: Env, preferred?: string): ProviderId[] {
  const configured = parseProviderList(env.ENABLED_STREAM_PROVIDERS);
  if (!preferred) return configured;
  const normalized = preferred.trim().toLowerCase() as ProviderId;
  if (!configured.includes(normalized)) return [normalized, ...configured];
  return [normalized, ...configured.filter((p) => p !== normalized)];
}

function parseProviderList(raw: string): ProviderId[] {
  return raw
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter((value): value is ProviderId =>
      ["qobuz", "tidal", "deezer", "amazon", "pandora", "apple"].includes(value)
    );
}
