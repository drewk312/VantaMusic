import { Blowfish } from "egoroof-blowfish";
import { md5Hex } from "../lib/md5";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import type { Env } from "../types";
import { handleAmazonAudio } from "./amazon-audio";

const BLOCK = 2048;
const IV = Uint8Array.from([0, 1, 2, 3, 4, 5, 6, 7]);
type AudioTicket = { provider: "deezer"; id: string; url: string; format: "flac" | "mp3"; expires: number } |
  { provider: "amazon"; id: string; url: string; format: "mp4"; key: string; expires: number };

export function deezerKey(id: string): Uint8Array {
  const digest = md5Hex(id);
  const salt = "g4el58wc0zvf9na1";
  return Uint8Array.from({ length: 16 }, (_, i) => digest.charCodeAt(i) ^ digest.charCodeAt(i + 16) ^ salt.charCodeAt(i));
}

/** Same 2048-byte/every-third-block transform as the SpotiFLAC extension. */
export function deezerTransform(id: string, firstBlock = 0, skip = 0, length = Infinity): TransformStream<Uint8Array, Uint8Array> {
  const cipher = new Blowfish(deezerKey(id), Blowfish.MODE.CBC, Blowfish.PADDING.NULL);
  let pending = new Uint8Array(0);
  let blockIndex = firstBlock;
  const emit = (bytes: Uint8Array, controller: TransformStreamDefaultController<Uint8Array>) => {
    const dropped = Math.min(skip, bytes.length); skip -= dropped;
    const part = bytes.subarray(dropped, dropped + Math.min(length, bytes.length - dropped));
    if (part.length) { controller.enqueue(part); length -= part.length; }
    if (length === 0) controller.terminate();
  };
  return new TransformStream({
    transform(chunk, controller) {
      const bytes = new Uint8Array(pending.length + chunk.length);
      bytes.set(pending); bytes.set(chunk, pending.length);
      const full = bytes.length - bytes.length % BLOCK;
      for (let offset = 0; offset < full; offset += BLOCK, blockIndex++) {
        if (blockIndex % 3 !== 0) continue;
        // Append a throwaway cipher block so library padding removal can never
        // trim real audio bytes (including blocks ending in zero bytes).
        const encrypted = new Uint8Array(BLOCK + 8);
        encrypted.set(bytes.subarray(offset, offset + BLOCK));
        cipher.setIv(IV);
        const decoded = cipher.decode(encrypted, Blowfish.TYPE.UINT8_ARRAY);
        bytes.set(decoded.subarray(0, BLOCK), offset);
      }
      pending = bytes.slice(full);
      emit(bytes.subarray(0, full), controller);
    },
    flush(controller) { emit(pending, controller); },
  });
}

async function ticketKey(secret: string): Promise<CryptoKey> {
  const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
  return crypto.subtle.importKey("raw", hash, "AES-GCM", false, ["encrypt", "decrypt"]);
}

/** Prefer the dedicated proxy secret; fall back to session encryption key on older deploys. */
export function extensionProxySecret(env: Env): string | undefined {
  return env.EXTENSION_PROXY_SECRET?.trim() || env.SESSION_ENCRYPTION_KEY?.trim() || undefined;
}

export async function extensionAudioUrl(env: Env, ticket: AudioTicket): Promise<string | null> {
  const secret = extensionProxySecret(env);
  if (!secret || !normalizePublicHttpsUrl(ticket.url)) return null;
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv },
    await ticketKey(secret), new TextEncoder().encode(JSON.stringify(ticket))));
  const bytes = new Uint8Array(iv.length + encrypted.length); bytes.set(iv); bytes.set(encrypted, iv.length);
  const token = btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const origin = env.GATEWAY_BASE_URL || "https://vanta-music-gateway.16drewk.workers.dev";
  return `${origin.replace(/\/$/, "")}/audio/extension?token=${token}`;
}

export async function handleExtensionAudio(request: Request, env: Env): Promise<Response> {
  if (!["GET", "HEAD"].includes(request.method)) return new Response(null, { status: 405, headers: { Allow: "GET, HEAD, OPTIONS" } });
  let ticket: AudioTicket;
  try {
    const token = new URL(request.url).searchParams.get("token") ?? "";
    const secret = extensionProxySecret(env);
    if (!secret || token.length > 12000 || !/^[\w-]+$/.test(token)) throw Error();
    const bytes = Uint8Array.from(atob(token.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
    const decoded = await crypto.subtle.decrypt({ name: "AES-GCM", iv: bytes.subarray(0, 12) },
      await ticketKey(secret), bytes.subarray(12));
    ticket = JSON.parse(new TextDecoder().decode(decoded));
    const validProvider = ticket.provider === "deezer" ? /^\d+$/.test(ticket.id) && ["flac", "mp3"].includes(ticket.format) :
      ticket.provider === "amazon" && /^B[A-Z0-9]{9}$/i.test(ticket.id) && ticket.format === "mp4" && /^[a-f\d]{32}$/i.test(ticket.key);
    if (!validProvider ||
      !Number.isFinite(ticket.expires) || ticket.expires < Date.now() || ticket.expires > Date.now() + 3600000 ||
      !normalizePublicHttpsUrl(ticket.url)) throw Error();
  } catch { return new Response("Invalid or expired audio ticket", { status: 401 }); }

  if (ticket.provider === "amazon") return handleAmazonAudio(request, ticket.url, ticket.key);

  const range = request.headers.get("Range");
  const match = range?.match(/^bytes=(\d+)-(\d*)$/);
  if (range && !match) return new Response(null, { status: 416 });
  const start = match ? Number(match[1]) : 0;
  const end = match?.[2] ? Number(match[2]) : undefined;
  if (!Number.isSafeInteger(start) || (end != null && (!Number.isSafeInteger(end) || end < start))) return new Response(null, { status: 416 });
  const aligned = Math.floor(start / BLOCK) * BLOCK;
  const headers = new Headers({ "User-Agent": "Mozilla/5.0" });
  if (range) headers.set("Range", `bytes=${aligned}-${end == null ? "" : Math.ceil((end + 1) / BLOCK) * BLOCK - 1}`);
  let upstream: Response;
  try { upstream = await fetch(ticket.url, { method: request.method, headers, redirect: "manual", signal: request.signal }); }
  catch { return new Response("Audio upstream unavailable", { status: 502 }); }
  if (!upstream.ok) { await upstream.body?.cancel(); return new Response("Audio upstream unavailable", { status: upstream.status === 416 ? 416 : 502 }); }
  const contentRange = upstream.headers.get("Content-Range")?.match(/^bytes (\d+)-(\d+)\/(\d+)$/);
  const sourceStart = upstream.status === 206 && contentRange ? Number(contentRange[1]) : 0;
  const total = contentRange ? Number(contentRange[3]) : Number(upstream.headers.get("Content-Length")) || undefined;
  if ((upstream.status === 206 && (!contentRange || sourceStart !== aligned)) || (range && !total)) {
    await upstream.body?.cancel(); return new Response("Invalid upstream byte range", { status: 502 });
  }
  if (total != null && start >= total) { await upstream.body?.cancel(); return new Response(null, { status: 416, headers: { "Content-Range": `bytes */${total}` } }); }
  const finalEnd = total != null ? Math.min(end ?? total - 1, total - 1) : end;
  const length = finalEnd == null ? Infinity : finalEnd - start + 1;
  const responseHeaders = new Headers({ "Content-Type": ticket.format === "flac" ? "audio/flac" : "audio/mpeg",
    "Accept-Ranges": "bytes", "Cache-Control": "private, no-store", "Access-Control-Allow-Origin": "*" });
  if (Number.isFinite(length)) responseHeaders.set("Content-Length", String(length));
  if (range) responseHeaders.set("Content-Range", `bytes ${start}-${finalEnd}/${total}`);
  const body = request.method === "HEAD" ? null : upstream.body?.pipeThrough(deezerTransform(ticket.id, sourceStart / BLOCK, start - sourceStart, length));
  return new Response(body, { status: range ? 206 : 200, headers: responseHeaders });
}
