import type { Env } from "./types";
import { CORS_HEADERS, json } from "./http";

export interface AppUpdateManifest {
  versionCode: number;
  versionName: string;
  apkUrl: string;
  donateUrl: string;
  changelog: string;
  publishedAt?: string;
}

const FALLBACK_PUBLIC = "https://vanta-music-gateway.16drewk.workers.dev";

function publicBase(env: Env): string {
  const raw = env.GATEWAY_PUBLIC_URL || env.GATEWAY_BASE_URL || FALLBACK_PUBLIC;
  return raw.replace(/\/$/, "");
}

function releasesBucket(env: Env): R2Bucket | undefined {
  return env.RELEASES;
}

async function readStoredManifest(env: Env): Promise<Partial<AppUpdateManifest> | null> {
  const bucket = releasesBucket(env);
  if (!bucket) return null;
  try {
    const object = await bucket.get("update.json");
    if (!object) return null;
    const parsed = JSON.parse(await object.text()) as Partial<AppUpdateManifest>;
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch (error) {
    if (error instanceof SyntaxError || error instanceof TypeError) return null;
    throw error;
  }
}

export async function readAppUpdateManifest(env: Env): Promise<AppUpdateManifest> {
  const stored = await readStoredManifest(env);
  const versionCode = Number.parseInt(
    env.APP_VERSION_CODE || String(stored?.versionCode ?? 3),
    10
  );
  const versionName = env.APP_VERSION_NAME?.trim() || stored?.versionName || "1.0";
  const donateUrl = env.APP_DONATE_URL?.trim() || stored?.donateUrl || "https://ko-fi.com/drewk312";
  const changelog = env.APP_CHANGELOG?.trim() || stored?.changelog ||
    "VANTA 1.0: Universal Dolby Atmos spatial audio, lossless FLAC fallback, improved lyrics, and TV sync.";
  const apkUrl = env.APP_APK_URL?.trim() || stored?.apkUrl || `${publicBase(env)}/app/download`;
  return {
    versionCode: Number.isFinite(versionCode) && versionCode > 0 ? versionCode : 2,
    versionName,
    apkUrl,
    donateUrl,
    changelog,
    publishedAt: stored?.publishedAt,
  };
}

export async function handleAppRelease(
  request: Request,
  env: Env,
  pathname: string
): Promise<Response | null> {
  if (pathname === "/app/update") {
    return json(await readAppUpdateManifest(env));
  }
  if (pathname !== "/app/download") return null;

  const bucket = releasesBucket(env);
  if (bucket) {
    const object = await bucket.get("vanta.apk");
    if (object?.body) {
      const headers = new Headers(CORS_HEADERS);
      headers.set("Content-Type", "application/vnd.android.package-archive");
      headers.set("Content-Disposition", 'attachment; filename="vanta.apk"');
      headers.set("Cache-Control", "public, max-age=300");
      if (object.size) headers.set("Content-Length", String(object.size));
      return new Response(object.body, { status: 200, headers });
    }
  }

  const manifest = await readAppUpdateManifest(env);
  if (manifest.apkUrl && !manifest.apkUrl.endsWith("/app/download")) {
    const driveId = extractGoogleDriveId(manifest.apkUrl);
    if (driveId) {
      return Response.redirect(`https://drive.usercontent.google.com/download?id=${driveId}&export=download&confirm=t`, 302);
    }
    return Response.redirect(manifest.apkUrl, 302);
  }
  return json(
    {
      error: "apk_unavailable",
      message: "No release APK has been published yet. Please configure APP_APK_URL.",
    },
    404
  );
}

export function extractGoogleDriveId(url: string): string | null {
  const match = url.match(/\/file\/d\/([a-zA-Z0-9_-]+)/) || url.match(/[?&]id=([a-zA-Z0-9_-]+)/);
  return match ? match[1] : null;
}
