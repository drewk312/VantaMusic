const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Api-Key, User-Agent, Accept",
};

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
    },
  });
}

export function text(body: string, status = 200): Response {
  return new Response(body, {
    status,
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      ...CORS_HEADERS,
    },
  });
}

export function handleOptions(): Response {
  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

export function unauthorized(): Response {
  return json({ error: "unauthorized", hint: "Set X-Api-Key header to match GATEWAY_API_KEY secret" }, 401);
}

export function notFound(message = "not_found"): Response {
  return json({ error: message }, 404);
}

export function badRequest(message: string): Response {
  return json({ error: message }, 400);
}

export function serviceUnavailable(provider: string, hint: string): Response {
  return json(
    {
      error: "provider_not_configured",
      provider,
      hint,
    },
    503
  );
}

export async function readJson<T>(request: Request): Promise<T | null> {
  try {
    return (await request.json()) as T;
  } catch {
    return null;
  }
}

export function extractTrackId(pathname: string): string | null {
  const patterns = [
    /^\/(?:api\/)?stream\/([^/]+)$/,
    /^\/(?:api\/)?resolve\/([^/]+)$/,
    /^\/(?:api\/)?download\/([^/]+)$/,
  ];
  for (const pattern of patterns) {
    const match = pathname.match(pattern);
    if (match?.[1]) return decodeURIComponent(match[1]);
  }
  return null;
}

export function normalizeQuality(raw: string | null | undefined, fallback: string): string {
  const value = (raw ?? fallback).trim();
  return value === "16" || value === "24" ? value : fallback;
}

export function providerFromQuery(url: URL, bodyService?: string): string | undefined {
  return (
    bodyService?.trim().toLowerCase() ||
    url.searchParams.get("provider")?.trim().toLowerCase() ||
    url.searchParams.get("service")?.trim().toLowerCase() ||
    undefined
  );
}
