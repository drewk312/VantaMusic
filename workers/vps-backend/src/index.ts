const UA = "VANTA-QobuzStream/1.0";
const QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2";

function md5Hex(str: string): string {
  return str;
}

async function md5(str: string): Promise<string> {
  const data = new TextEncoder().encode(str);
  const hashBuffer = await crypto.subtle.digest("MD5", data);
  return Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function fetchJson(url: string, init?: RequestInit): Promise<any> {
  try {
    const resp = await fetch(url, {
      ...init,
      signal: AbortSignal.timeout(6000),
      headers: {
        Accept: "application/json",
        "User-Agent": UA,
        ...(init?.headers ?? {}),
      },
    });
    if (!resp.ok) return null;
    return await resp.json();
  } catch {
    return null;
  }
}

async function qobuzSignedGet(
  path: string,
  params: Record<string, string>,
  env: Env
): Promise<any> {
  const appId = env.QOBUZ_APP_ID;
  const appSecret = env.QOBUZ_APP_SECRET;
  if (!appId || !appSecret) return null;

  const timestamp = String(Math.floor(Date.now() / 1000));

  const normalizedPath = path.replace(/^\/+|\/+$/g, "").replace(/\//g, "");
  const keys = Object.keys(params).sort();
  let payload = normalizedPath;
  for (const key of keys) {
    payload += key + params[key];
  }
  payload += timestamp + appSecret;
  const sig = await md5(payload);

  const query = new URLSearchParams({
    ...params,
    app_id: appId,
    request_ts: timestamp,
    request_sig: sig,
  });

  const url = `${QOBUZ_API_BASE}/${path.replace(/^\/+/, "")}?${query.toString()}`;
  return fetchJson(url, {
    headers: {
      "User-Agent": UA,
      Accept: "application/json",
      "X-App-Id": appId,
    },
  });
}

const QUALITY_MAP: Record<string, string> = {
  hi_res: "hi-res",
  "hi-res": "hi-res",
  hi_res_lossless: "hi-res",
  "24": "hi-res",
  lossless: "lossless",
  "16": "lossless",
  flac: "lossless",
};

const FORMAT_IDS: Record<string, number> = {
  "hi-res": 27,
  lossless: 7,
  "16bit": 6,
  "mp3-320": 5,
};

interface Env {
  QOBUZ_APP_ID: string;
  QOBUZ_APP_SECRET: string;
}

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Content-Type": "application/json",
  };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    const url = new URL(request.url);
    const pathname = url.pathname.replace(/\/+$/, "") || "/";

    if (pathname === "/health") {
      return new Response(
        JSON.stringify({
          status: "ok",
          qobuz: Boolean(env.QOBUZ_APP_ID),
          appId: env.QOBUZ_APP_ID ?? null,
          ts: Math.floor(Date.now() / 1000),
        }),
        { status: 200, headers: corsHeaders() }
      );
    }

    const trackIdMatch = pathname.match(/^\/(\d+)$/);
    if (trackIdMatch && request.method === "GET") {
      const trackId = trackIdMatch[1];
      const quality = url.searchParams.get("quality") ?? "lossless";
      const q = QUALITY_MAP[quality] ?? "lossless";
      const formatId = FORMAT_IDS[q] ?? 7;

      const payload = await qobuzSignedGet(
        "track/getFileUrl",
        {
          track_id: trackId,
          format_id: String(formatId),
          intent: "stream",
        },
        env
      );

      const streamUrl = payload?.url;
      if (!streamUrl) {
        return new Response(
          JSON.stringify({
            error: "no_stream",
            message: `Qobuz returned no stream for track ${trackId}`,
          }),
          { status: 404, headers: corsHeaders() }
        );
      }

      const mimeType =
        typeof payload.mime_type === "string"
          ? payload.mime_type
          : "audio/flac";
      const format = mimeType.includes("flac")
        ? "flac"
        : mimeType.replace(/^audio\//, "");

      return new Response(
        JSON.stringify({
          url: streamUrl,
          streamUrl,
          format,
          quality: quality,
          mimeType,
          provider: "qobuz",
          expiresAt:
            typeof payload.etsp === "number" ? payload.etsp : undefined,
        }),
        { status: 200, headers: corsHeaders() }
      );
    }

    if (pathname === "/api/dl" && request.method === "POST") {
      try {
        const body = (await request.json()) as Record<string, string>;
        const trackId = body.id ?? body.trackId ?? "";
        const quality = body.quality ?? "lossless";
        const q = QUALITY_MAP[quality] ?? "lossless";
        const formatId = FORMAT_IDS[q] ?? 7;

        const payload = await qobuzSignedGet(
          "track/getFileUrl",
          {
            track_id: trackId,
            format_id: String(formatId),
            intent: "stream",
          },
          env
        );

        const streamUrl = payload?.url;
        if (!streamUrl) {
          return new Response(
            JSON.stringify({ error: "no_stream" }),
            { status: 404, headers: corsHeaders() }
          );
        }

        return new Response(
          JSON.stringify({
            url: streamUrl,
            format: "flac",
            quality,
            provider: "qobuz",
          }),
          { status: 200, headers: corsHeaders() }
        );
      } catch {
        return new Response(
          JSON.stringify({ error: "bad_request" }),
          { status: 400, headers: corsHeaders() }
        );
      }
    }

    return new Response(
      JSON.stringify({ error: "not_found", path: pathname }),
      { status: 404, headers: corsHeaders() }
    );
  },
};
