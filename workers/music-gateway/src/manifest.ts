import type { Env } from "./types";

export function buildManifest(env: Env) {
  const searchProviders = env.ENABLED_SEARCH_PROVIDERS.split(",").map((p) => p.trim()).filter(Boolean);
  const streamProviders = env.ENABLED_STREAM_PROVIDERS.split(",").map((p) => p.trim()).filter(Boolean);

  return {
    id: env.GATEWAY_ID,
    name: env.GATEWAY_NAME,
    version: env.GATEWAY_VERSION,
    description: env.GATEWAY_DESCRIPTION,
    icon: null,
    resources: ["search", "stream", "catalog", "resolve"],
    types: ["track", "album", "artist", "playlist"],
    supportedTypes: ["track", "album", "artist", "playlist"],
    canSearch: searchProviders.length > 0,
    canStream: streamProviders.length > 0,
    canBrowse: true,
    capabilities: [...new Set([...searchProviders, ...streamProviders, "resolve"])],
  };
}
