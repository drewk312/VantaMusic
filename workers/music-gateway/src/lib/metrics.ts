import type { Env } from "../types";

export interface MetricsSnapshot {
  requests: number;
  search: number;
  resolve: number;
  stream: number;
  play: number;
  download: number;
  errors: number;
  rateLimited: number;
  startedAt: number;
}

let memoryMetrics: MetricsSnapshot = {
  requests: 0,
  search: 0,
  resolve: 0,
  stream: 0,
  play: 0,
  download: 0,
  errors: 0,
  rateLimited: 0,
  startedAt: Date.now(),
};

export function incrementRequests(): void {
  memoryMetrics.requests++;
}

export function incrementRoute(route: "search" | "resolve" | "stream" | "play" | "download"): void {
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
    resolve: 0,
    stream: 0,
    play: 0,
    download: 0,
    errors: 0,
    rateLimited: 0,
    startedAt: Date.now(),
  };
  return memoryMetrics;
}

