/** Run all attempts in parallel; return the first non-null result (fastest mirror wins). */
export async function raceFirst<T>(
  attempts: Array<() => Promise<T | null>>
): Promise<T | null> {
  if (attempts.length === 0) return null;

  return new Promise((resolve) => {
    let pending = attempts.length;
    let settled = false;

    for (const attempt of attempts) {
      attempt()
        .then((result) => {
          if (!settled && result != null) {
            settled = true;
            resolve(result);
          }
        })
        .catch(() => undefined)
        .finally(() => {
          pending -= 1;
          if (!settled && pending === 0) resolve(null);
        });
    }
  });
}

export interface RankedAttempt<T> {
  priority: number;
  run: () => Promise<T | null>;
}

/** Run all attempts in parallel; pick highest bitrate, then lowest priority index. */
export async function raceBest<T extends { bitrateKbps?: number }>(
  attempts: RankedAttempt<T>[]
): Promise<T | null> {
  if (attempts.length === 0) return null;

  const results = await Promise.all(
    attempts.map(async (attempt) => {
      try {
        return { priority: attempt.priority, value: await attempt.run() };
      } catch {
        return { priority: attempt.priority, value: null };
      }
    })
  );

  const successes = results.filter((entry) => entry.value != null) as Array<{
    priority: number;
    value: T;
  }>;
  if (successes.length === 0) return null;

  successes.sort((a, b) => {
    const bitrateDiff = (b.value.bitrateKbps ?? 0) - (a.value.bitrateKbps ?? 0);
    if (bitrateDiff !== 0) return bitrateDiff;
    return a.priority - b.priority;
  });

  return successes[0].value;
}
