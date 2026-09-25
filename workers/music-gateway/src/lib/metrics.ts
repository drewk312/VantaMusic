import type { Env } from "../types";

export interface MetricsSnapshot {
  requests: number;
  search: number;
  new_releases: number;
  apple_editorial: number;
  resolve: number;
  stream: number;
  play: number;
  download: number;
  drm_license: number;
  sync: number;
  audiomuse: number;
  errors: number;
  rateLimited: number;
  startedAt: number;
}

let memoryMetrics: MetricsSnapshot = {
  requests: 0,
  search: 0,
  new_releases: 0,
  apple_editorial: 0,
  resolve: 0,
  stream: 0,
  play: 0,
  download: 0,
  drm_license: 0,
  sync: 0,
  audiomuse: 0,
  errors: 0,
  rateLimited: 0,
  startedAt: Date.now(),
};

export function incrementRequests(): void {
  memoryMetrics.requests++;
}

export function incrementRoute(route: "search" | "new_releases" | "apple_editorial" | "resolve" | "stream" | "play" | "download" | "drm_license" | "sync" | "audiomuse"): void {
  memoryMetrics[route]++;
}

export function incrementErrors(): void {
  memoryMetrics.errors++;
}

export function incrementRateLimited(): void {
  memoryMetrics.rateLimited++;
}

export function getMetrics(): MetricsSnapshot {
  return { ...memoryMetrics };
}

export function resetMetrics(): MetricsSnapshot {
  memoryMetrics = {
    requests: 0,
    search: 0,
    new_releases: 0,
    apple_editorial: 0,
    resolve: 0,
    stream: 0,
    play: 0,
    download: 0,
    drm_license: 0,
    sync: 0,
    audiomuse: 0,
    errors: 0,
    rateLimited: 0,
    startedAt: Date.now(),
  };
  return memoryMetrics;
}
