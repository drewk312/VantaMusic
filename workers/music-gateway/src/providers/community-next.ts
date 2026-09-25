import type { Env, ProviderId, StreamResult } from "../types";
import { normalizePublicHttpsUrl } from "../lib/safe-stream-url";
import { durationFromMpd } from "./tidal-api";
import { isRelayPoolDown, markRelayPoolDown, markRelayPoolUp } from "./relay-health";
import { hasImmersiveContainerSignal, hasSony360Signal } from "../lib/stream-quality";
import { extensionAudioUrl } from "../extensions/audio-proxy";
import { amazonPayloadDecryptionKey, amazonDirectUrlIsLocked } from "../lib/playable-stream";

/**
 * SpotiFLAC-Next "secure envelope" protocol for the lettered `*.{qbz,tdl,amz,dzr}xn.qzz.io`
 * relay shards (a–e). Older `*-N.spotbye.qzz.io` hostnames are DNS-dead.
 *
 * Protocol recovered by static reverse engineering of `SpotiFLAC-Next.exe`
 * v1.5.4 (Go 1.27.1 — see `scripts/re-annot.py`, `scripts/re-hkdf.py`):
 *
 *  - Key exchange: ECDH P-256 via Go `crypto/ecdh`. Go returns the RAW
 *    x-coordinate of d·Q (see `crypto/internal/fips140/ecdh`: `p.BytesX()`),
 *    32 bytes big-endian. No internal HKDF.
 *  - Key derivation: HKDF-SHA256(secret = raw x-coordinate,
 *    salt = 16 per-request random bytes, info = 16-byte label, length = 32).
 *    Request label `spotiflac-req-v2`, response label `spotiflac-resp-v2`.
 *    The custom expand loop in the binary bumps a block counter and stops at
 *    0x20 bytes, i.e. standard extract-then-expand with a single T(1) block.
 *  - Cipher: AES-256-GCM, nonce = 12 per-request random bytes, no additional
 *    data (the builder clears both AAD slots before sealing).
 *  - Envelope (both directions, min length 110 bytes):
 *      [0x02][peer pubkey 65B][salt 16B][nonce 12B][AES-GCM ct || tag 16B]
 *    Request embeds the client's ephemeral public key; the embedded
 *    `envelopeServerPublicKey` constant is used as the ECDH peer.
 *    Response embeds the server's ephemeral public key.
 *  - Sealed plaintext wrapper: `{"token": NEXT_REQUEST_TOKEN,
 *    "body": {"id": trackId, "quality": "16"|"24"|"atmos"|"360"},
 *    "ts": <unix seconds>}`. The freshness window is enforced on `ts`;
 *    `body` must be a nested object, not a string.
 *
 * Classic `*-oss` relays still reject this with wait/overload breaks; the Next
 * shards reject plain JSON with `400 Encrypted request required` — so this
 * envelope is the only body the lettered `*xn` pool accepts.
 */

export const NEXT_ENVELOPE_VERSION = 0x02;
export const NEXT_REQ_LABEL = "spotiflac-req-v2"; // 16 bytes
export const NEXT_RESP_LABEL = "spotiflac-resp-v2"; // 16 bytes
export const NEXT_KEY_LENGTH = 32;
export const NEXT_SALT_LENGTH = 16;
export const NEXT_NONCE_LENGTH = 12;
export const NEXT_PUBKEY_LENGTH = 65;
/** Fixed header before the ciphertext: version + pubkey + salt + nonce. */
export const NEXT_ENVELOPE_HEADER = 1 + NEXT_PUBKEY_LENGTH + NEXT_SALT_LENGTH + NEXT_NONCE_LENGTH;
/** Minimum response length: header + empty ciphertext (tag only). */
export const NEXT_MIN_ENVELOPE = NEXT_ENVELOPE_HEADER + 16;

/** The long-lived server identity point parsed from the desktop binary. */
export const NEXT_SERVER_PUBLIC_KEY_B64 =
  "BNV5TIGu2QTUN+bPqd4CAHiqDedLaixISDpxko/h6Q8e6vaeskKkfECeYJ2n6UehSbHxUjfLz4hUebG5w8HcBzg=";

/**
 * Client-side anti-abuse token sent as the `token` field of the sealed wrapper.
 * Recovers the full protocol in `docs/next-protocol.md`, which REDACTS the
 * literal value. Runtime reads `NEXT_REQUEST_TOKEN` env/secret first and only
 * falls back to the desktop-binary embedded constant (kept here so the worker
 * and tests keep working without a secret). Treat the value as credential-like:
 * do not log it, and do not re-print it in docs or probes.
 */
export const NEXT_REQUEST_TOKEN = "padlock-unified-attractor-ovary-letdown";

/** Resolve the wrapper token: env override wins, else the embedded constant. */
export function nextRequestToken(env?: Env): string {
  return env?.NEXT_REQUEST_TOKEN?.trim() || NEXT_REQUEST_TOKEN;
}

const textEncoder = new TextEncoder();

/**
 * The `@cloudflare/workers-types` `SubtleCrypto` binding types the ECDH/HKDF
 * algorithm descriptors differently than the runtime WebCrypto, so all crypto
 * goes through this narrow runtime-shaped facade (standard WebCrypto under the
 * hood on both Workers and Node).
 */
type BufferInput = ArrayBufferView | ArrayBuffer;
type SubtleFacade = {
  importKey(
    format: "raw",
    data: BufferInput,
    algorithm: unknown,
    extractable: boolean,
    usages: string[]
  ): Promise<CryptoKey>;
  generateKey(algorithm: unknown, extractable: boolean, usages: string[]): Promise<CryptoKeyPair>;
  exportKey(format: "raw", key: CryptoKey): Promise<ArrayBuffer>;
  deriveBits(algorithm: unknown, baseKey: CryptoKey, lengthBits: number): Promise<ArrayBuffer>;
  encrypt(algorithm: unknown, key: CryptoKey, data: BufferInput): Promise<ArrayBuffer>;
  decrypt(algorithm: unknown, key: CryptoKey, data: BufferInput): Promise<ArrayBuffer>;
  digest(algorithm: unknown, data: BufferInput): Promise<ArrayBuffer>;
};

const subtle = crypto.subtle as unknown as SubtleFacade;

function toBytes(value: Uint8Array): Uint8Array {
  const copy = new Uint8Array(value.byteLength);
  copy.set(value);
  return copy;
}

function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((sum, part) => sum + part.byteLength, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.byteLength;
  }
  return out;
}

/** Import the embedded server ECDH public key (P-256, 65 bytes, 0x04 point). */
export async function importNextServerPublicKey(): Promise<CryptoKey> {
  const raw = Buffer.from(NEXT_SERVER_PUBLIC_KEY_B64, "base64");
  if (raw.byteLength !== NEXT_PUBKEY_LENGTH || raw[0] !== 0x04) {
    throw new Error("next envelope: embedded server public key is not a P-256 point");
  }
  return subtle.importKey(
    "raw",
    raw as unknown as ArrayBuffer,
    { name: "ECDH", namedCurve: "P-256" },
    false,
    []
  );
}

export async function generateNextClientKey(extractable = false): Promise<CryptoKeyPair> {
  return subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, extractable, ["deriveBits"]);
}

export function exportNextPublicKey(keyPair: CryptoKeyPair): Promise<ArrayBuffer> {
  return subtle.exportKey("raw", keyPair.publicKey);
}

export function importNextServerFromPublicKey(publicKeyRaw: Uint8Array): Promise<CryptoKey> {
  if (publicKeyRaw.byteLength !== NEXT_PUBKEY_LENGTH || publicKeyRaw[0] !== 0x04) {
    throw new Error("next envelope: invalid response public key");
  }
  return subtle.importKey(
    "raw",
    publicKeyRaw as unknown as ArrayBuffer,
    { name: "ECDH", namedCurve: "P-256" },
    false,
    []
  );
}

/**
 * ECDH shared secret (raw P-256 x-coordinate, 32 bytes) between a local key and
 * a remote public key. Mirrors Go `crypto/ecdh` which returns `p.BytesX()`.
 */
export async function deriveNextShared(
  keyPair: CryptoKeyPair,
  remotePublicKey: CryptoKey
): Promise<Uint8Array> {
  const shared = await subtle.deriveBits(
    { name: "ECDH", public: remotePublicKey },
    keyPair.privateKey,
    NEXT_KEY_LENGTH * 8
  );
  return new Uint8Array(shared);
}

/**
 * NIST SP 800-56C HKDF-SHA256 (extract + expand to `length` bytes).
 * Maps one-to-one onto WebCrypto's HKDF: IKM = secret, salt and info as given.
 */
export async function deriveNextKey(
  sharedSecret: Uint8Array,
  salt: Uint8Array,
  info: Uint8Array,
  length = NEXT_KEY_LENGTH
): Promise<Uint8Array> {
  const hkdfKey = await subtle.importKey("raw", sharedSecret as unknown as ArrayBuffer, { name: "HKDF" }, false, [
    "deriveBits",
  ]);
  const bits = await subtle.deriveBits(
    {
      name: "HKDF",
      hash: "SHA-256",
      salt: salt as unknown as ArrayBuffer,
      info: info as unknown as ArrayBuffer,
    },
    hkdfKey,
    length * 8
  );
  return new Uint8Array(bits);
}

/** AES-256-GCM seal of the plaintext with a derived key and 12-byte nonce. */
export async function sealNextBody(
  key: Uint8Array,
  nonce: Uint8Array,
  plaintext: Uint8Array
): Promise<Uint8Array> {
  const aes = await subtle.importKey("raw", key as unknown as ArrayBuffer, { name: "AES-GCM" }, false, ["encrypt"]);
  const ciphertext = await subtle.encrypt(
    { name: "AES-GCM", iv: nonce as unknown as ArrayBuffer, tagLength: 128 },
    aes,
    plaintext as unknown as ArrayBuffer
  );
  return new Uint8Array(ciphertext);
}

export async function openNextBody(
  key: Uint8Array,
  nonce: Uint8Array,
  ciphertext: Uint8Array
): Promise<Uint8Array> {
  const aes = await subtle.importKey("raw", key as unknown as ArrayBuffer, { name: "AES-GCM" }, false, ["decrypt"]);
  const plaintext = await subtle.decrypt(
    { name: "AES-GCM", iv: nonce as unknown as ArrayBuffer, tagLength: 128 },
    aes,
    ciphertext as unknown as ArrayBuffer
  );
  return new Uint8Array(plaintext);
}

/**
 * Build a Next request envelope: ECDH P-256 with the embedded server identity
 * key, HKDF-SHA256 key from a fresh random salt, AES-256-GCM body, all wrapped
 * in `[0x02][clientPub][salt][nonce][ct||tag]`. The returned ephemeral keypair
 * must be kept until the matching response is opened.
 */
export async function sealNextRequest(
  body: Uint8Array,
  clientKeyPair?: CryptoKeyPair
): Promise<{ envelope: Uint8Array; clientKeyPair: CryptoKeyPair }> {
  const keyPair =
    clientKeyPair ??
    (await subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]));

  const serverKey = await importNextServerPublicKey();
  const shared = await subtle.deriveBits({ name: "ECDH", public: serverKey }, keyPair.privateKey, NEXT_KEY_LENGTH * 8);

  const salt = new Uint8Array(NEXT_SALT_LENGTH);
  crypto.getRandomValues(salt);
  const nonce = new Uint8Array(NEXT_NONCE_LENGTH);
  crypto.getRandomValues(nonce);

  const key = await deriveNextKey(new Uint8Array(shared), salt, textEncoder.encode(NEXT_REQ_LABEL));
  const ciphertext = await sealNextBody(key, nonce, body);

  const publicKey = new Uint8Array(await subtle.exportKey("raw", keyPair.publicKey));
  const envelope = concatBytes(new Uint8Array([NEXT_ENVELOPE_VERSION]), publicKey, salt, nonce, ciphertext);
  return { envelope, clientKeyPair: keyPair };
}

/**
 * Open a Next response envelope. Structure mirrors the request: live server
 * public key, its own salt/nonce/label, and the response label
 * `spotiflac-resp-v2`. The same ephemeral client keypair from the request is
 * used as the ECDH local key.
 */
export async function openNextResponse(
  envelope: Uint8Array,
  clientKeyPair: CryptoKeyPair
): Promise<{ body: Uint8Array; serverPublicKey: Uint8Array }> {
  if (envelope.byteLength < NEXT_MIN_ENVELOPE) {
    throw new Error("next envelope: response too short");
  }
  if (envelope[0] !== NEXT_ENVELOPE_VERSION) {
    throw new Error(`next envelope: unsupported secure envelope version ${envelope[0]}`);
  }

  const serverPublicKey = envelope.slice(1, 1 + NEXT_PUBKEY_LENGTH);
  const salt = envelope.slice(1 + NEXT_PUBKEY_LENGTH, 1 + NEXT_PUBKEY_LENGTH + NEXT_SALT_LENGTH);
  const nonce = envelope.slice(1 + NEXT_PUBKEY_LENGTH + NEXT_SALT_LENGTH, NEXT_ENVELOPE_HEADER);
  const ciphertext = envelope.slice(NEXT_ENVELOPE_HEADER);

  const serverKey = await importNextServerFromPublicKey(serverPublicKey);
  const shared = await subtle.deriveBits(
    { name: "ECDH", public: serverKey },
    clientKeyPair.privateKey,
    NEXT_KEY_LENGTH * 8
  );
  const key = await deriveNextKey(new Uint8Array(shared), salt, textEncoder.encode(NEXT_RESP_LABEL));
  const body = await openNextBody(key, nonce, ciphertext);
  return { body, serverPublicKey };
}

export interface NextDlResult {
  ok: boolean;
  status: number;
  bodyText: string;
}

/**
 * POST a download request as a Next envelope to a `*-N` relay shard. The
 * sealed plaintext is the wrapper object
 * `{token, body:{id, quality}, ts:<unix seconds>}` — reverse-engineered from
 * the desktop request builder at 0x1407f93a0 (map keys "token"/"body"/"ts",
 * ts = unix seconds via Go time.Now unixToInternal). Verified live against
 * a.qbzxn.qzz.io: returns `{"quality":"16","url":"https://streaming-qobuz-std…"}`
 * with a full-length CDN URL (no range param). `body` must be a nested
 * object — the relay rejects a string-encoded body with
 * `invalid_type: expected object, received string`.
 */
/**
 * Build the sealed-wrapper plaintext: `{token, body:{id,quality}, ts}` with
 * `body` as a nested object (the relay rejects a string body) and `ts` in
 * unix seconds (the freshness window is enforced against it).
 */
export function mapNextQuality(quality: string): "16" | "24" | "atmos" | "360" {
  const value = quality.trim().toLowerCase();
  if (value.includes("atmos") || value === "eac3_joc" || value === "dolby_atmos") return "atmos";
  if (
    value === "360" ||
    value === "360ra" ||
    value === "sony360" ||
    value === "sony_360" ||
    value === "360_reality_audio" ||
    value.includes("360 reality")
  ) {
    return "360";
  }
  if (value === "hi_res" || value === "hi_res_lossless" || value === "24") return "24";
  return "16";
}

export function buildNextWrapperBody(
  trackId: string,
  quality: string,
  tsSeconds?: number,
  token?: string,
  provider?: string
): string {
  const trimmed = trackId.trim();
  // Tidal/Qobuz/Deezer Next shards validate `id` as an integer. Amazon keeps ASIN strings.
  const numericId = /^\d{4,}$/.test(trimmed) ? Number(trimmed) : null;
  const preferNumeric =
    numericId != null &&
    Number.isSafeInteger(numericId) &&
    (provider == null || provider === "tidal" || provider === "qobuz" || provider === "deezer");
  return JSON.stringify({
    token: token ?? NEXT_REQUEST_TOKEN,
    body: { id: preferNumeric ? numericId : trimmed, quality },
    ts: tsSeconds ?? Math.floor(Date.now() / 1000),
  });
}

export async function nextApiDl(
  base: string,
  installId: string,
  appVersion: string,
  trackId: string,
  quality: string,
  clientKeyPair?: CryptoKeyPair,
  token?: string,
  provider?: string
): Promise<NextDlResult> {
  const bodyText = buildNextWrapperBody(trackId, quality, undefined, token, provider);
  const { envelope, clientKeyPair: usedPair } = await sealNextRequest(textEncoder.encode(bodyText), clientKeyPair);

  const endpoint = `${base.replace(/\/+$/, "")}/api/dl`;
  const headers: Record<string, string> = {
    "Content-Type": "application/octet-stream",
    "Accept": "application/octet-stream",
    "X-Installation-ID": installId || "shared-gateway-install",
    "X-App-Version": appVersion || "unknown",
  };

  let response: Response;
  try {
    response = await fetch(endpoint, {
      method: "POST",
      headers,
      body: envelope as unknown as BodyInit,
    });
  } catch (cause) {
    return { ok: false, status: 0, bodyText: `fetch failed: ${String(cause)}` };
  }

  const raw = new Uint8Array(await response.arrayBuffer());
  if (!raw.byteLength) {
    return { ok: response.ok, status: response.status, bodyText: "" };
  }
  // Relays often return plaintext JSON errors (`{"detail":...}`) with HTTP 4xx.
  // Byte 0x7B is `{` — not a new envelope version.
  if (raw[0] === 0x7b /* '{' */) {
    return { ok: response.ok, status: response.status, bodyText: new TextDecoder().decode(raw) };
  }
  try {
    const { body } = await openNextResponse(raw, usedPair);
    return { ok: response.ok, status: response.status, bodyText: new TextDecoder().decode(body) };
  } catch (cause) {
    return { ok: false, status: response.status, bodyText: `envelope open failed: ${String(cause)}` };
  }
}

/** Lettered Next shards recovered from SpotiFLAC-Next.exe v1.5.4
 * (`a..e.{qbz,tdl,amz,dzr}xn.qzz.io`). Maps 1:1 to SpotBye status keys
 * `qobuz_a..e` / `tidal_a..e` / … */
const DEFAULT_NEXT_PROVIDER_HOSTS: Record<string, string[]> = {
  qobuz: [
    "https://a.qbzxn.qzz.io",
    "https://b.qbzxn.qzz.io",
    "https://c.qbzxn.qzz.io",
    "https://d.qbzxn.qzz.io",
    "https://e.qbzxn.qzz.io",
  ],
  tidal: [
    "https://a.tdlxn.qzz.io",
    "https://b.tdlxn.qzz.io",
    "https://c.tdlxn.qzz.io",
    "https://d.tdlxn.qzz.io",
    "https://e.tdlxn.qzz.io",
  ],
  amazon: [
    "https://a.amzxn.qzz.io",
    "https://b.amzxn.qzz.io",
    "https://c.amzxn.qzz.io",
    "https://d.amzxn.qzz.io",
    "https://e.amzxn.qzz.io",
  ],
  deezer: [
    "https://a.dzrxn.qzz.io",
    "https://b.dzrxn.qzz.io",
    "https://c.dzrxn.qzz.io",
    "https://d.dzrxn.qzz.io",
    "https://e.dzrxn.qzz.io",
  ],
};

const NEXT_HOST_NEEDLE: Record<string, string> = {
  qobuz: "qbzxn",
  tidal: "tdlxn",
  amazon: "amzxn",
  deezer: "dzrxn",
};

/** SpotBye Next’s “20 services” = 5 lettered shards × 4 providers. Env
 * `SPOTBYE_NODES` can override; defaults cover the full online pool. */
export function nextHostsFor(env: Env | undefined, provider: string): string[] {
  const defaults = DEFAULT_NEXT_PROVIDER_HOSTS[provider] ?? [];
  const raw = env?.SPOTBYE_NODES?.trim();
  if (!raw) return [...defaults];
  const needle = NEXT_HOST_NEEDLE[provider];
  if (!needle) return [...defaults];
  const fromEnv = raw
    .split(",")
    .map((value) => value.trim().replace(/\/+$/, ""))
    .filter((value) => value.startsWith("https://") && value.includes(needle));
  return fromEnv.length ? fromEnv : [...defaults];
}

export function allNextHosts(env?: Env): string[] {
  return (["qobuz", "tidal", "amazon", "deezer"] as const).flatMap((provider) => nextHostsFor(env, provider));
}

export interface NextServiceHealth {
  host: string;
  provider: string;
  online: boolean;
  latencyMs: number;
  region?: string;
  qualities?: string[];
  error?: string;
  /** True when the provider's relay license agent is known to be refused by
   * the upstream license service (e.g. Amazon `REQUEST_BLOCKED`). The
   * `/health` probe still returns 200, so without telemetry these shards look
   * "online" while every real resolution dies with `400 Failed to get license`. */
  licenseBlocked?: boolean;
}

export interface NextLicenseBlock {
  provider: string;
  blockedAt: number;
  lastSeenAt: number;
  lastStatus: number | null;
  /** First 240 chars of the denial body, e.g. `denialReason":"REQUEST_BLOCKED"`. */
  detail: string;
}

const nextLicenseBlocks = new Map<string, NextLicenseBlock>();
/** A license block is only annotated onto health probes while recently observed. */
const LICENSE_BLOCK_ATTRIBUTION_MS = 10 * 60 * 1000;

export function isProviderLicenseBlocked(provider: string): boolean {
  const block = nextLicenseBlocks.get(provider);
  return Boolean(block && Date.now() - block.lastSeenAt < LICENSE_BLOCK_ATTRIBUTION_MS);
}

export function nextLicenseBlockSnapshot(): Record<string, NextLicenseBlock> {
  const snapshot: Record<string, NextLicenseBlock> = {};
  for (const [provider, block] of nextLicenseBlocks) snapshot[provider] = { ...block };
  return snapshot;
}

export function resetNextLicenseBlocks(): void {
  nextLicenseBlocks.clear();
}

function recordNextLicenseBlock(provider: string, status: number | null, detail: string): void {
  const now = Date.now();
  const existing = nextLicenseBlocks.get(provider);
  nextLicenseBlocks.set(provider, {
    provider,
    blockedAt: existing?.blockedAt ?? now,
    lastSeenAt: now,
    lastStatus: status,
    detail: detail.slice(0, 240),
  });
}

let nextHealthCache: { at: number; value: NextServiceHealth[] } | null = null;
const NEXT_HEALTH_TTL_MS = 60_000;

function providerFromNextHost(host: string): string {
  if (host.includes("qbzxn") || host.includes("qbz-")) return "qobuz";
  if (host.includes("tdlxn") || host.includes("tdl-")) return "tidal";
  if (host.includes("amzxn") || host.includes("amz-")) return "amazon";
  if (host.includes("dzrxn") || host.includes("dzr-")) return "deezer";
  return "unknown";
}

async function probeNextHost(base: string): Promise<NextServiceHealth> {
  const host = base.replace(/^https:\/\//, "").replace(/\/+$/, "");
  const provider = providerFromNextHost(host);
  const started = Date.now();
  try {
    const response = await fetch(`${base.replace(/\/+$/, "")}/health`, {
      method: "GET",
      headers: { Accept: "application/json", "User-Agent": "VANTA-MusicGateway/2.5" },
      signal: AbortSignal.timeout(4_000),
      redirect: "manual",
    });
    const latencyMs = Date.now() - started;
    const payload = (await response.json().catch(() => null)) as Record<string, unknown> | null;
    const data = (payload?.data && typeof payload.data === "object" ? payload.data : payload) as Record<string, unknown> | null;
    const online = response.ok && (
      payload?.ok === true ||
      payload?.success === true ||
      payload?.status === "ok" ||
      data?.status === "ok" ||
      typeof data?.service === "string"
    );
    const qualities = Array.isArray(data?.qualities)
      ? data.qualities.filter((q): q is string => typeof q === "string")
      : undefined;
    const region = typeof data?.region === "string" ? data.region : undefined;
    return { host, provider, online, latencyMs, region, qualities, error: online ? undefined : `HTTP ${response.status}` };
  } catch (error) {
    return {
      host,
      provider,
      online: false,
      latencyMs: Date.now() - started,
      error: error instanceof Error ? error.message : "probe failed",
    };
  }
}

/** Lightweight SpotBye-style Next pool status (cached ~60s). */
export async function checkNextServicesHealth(env?: Env, force = false): Promise<{
  total: number;
  online: number;
  offline: number;
  services: NextServiceHealth[];
}> {
  const now = Date.now();
  const decorate = (services: NextServiceHealth[]): { total: number; online: number; offline: number; services: NextServiceHealth[] } => {
    const decorated = services.map((service) => ({
      ...service,
      licenseBlocked: service.licenseBlocked || (service.provider !== "unknown" && isProviderLicenseBlocked(service.provider)),
    }));
    const online = decorated.filter((s) => s.online).length;
    return { total: decorated.length, online, offline: decorated.length - online, services: decorated };
  };
  if (!force && nextHealthCache && now - nextHealthCache.at < NEXT_HEALTH_TTL_MS) {
    return decorate(nextHealthCache.value);
  }
  const hosts = allNextHosts(env);
  const services = await Promise.all(hosts.map((base) => probeNextHost(base)));
  nextHealthCache = { at: now, value: services };
  return decorate(services);
}

/** Rotate shard order so backups share load instead of always burning shard 1. */
export function rotateHosts(hosts: string[], seed: string): string[] {
  if (hosts.length <= 1) return [...hosts];
  let hash = 2166136261;
  for (let i = 0; i < seed.length; i++) {
    hash ^= seed.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  const start = (hash >>> 0) % hosts.length;
  return [...hosts.slice(start), ...hosts.slice(0, start)];
}

export interface NextShardTelemetry {
  attempts: number;
  successes: number;
  failures: number;
  lastStatus: number | null;
  lastElapsedMs: number;
}

/** In-memory per-shard counters so incidents are attributable to a specific
 * `*-N` host (and not just "the Next pool"). Read via `nextShardTelemetry()`.
 * Deliberately not persisted — Worker memory is ephemeral by design. */
const nextTelemetry = new Map<string, NextShardTelemetry>();

export function nextShardTelemetry(): Record<string, NextShardTelemetry> {
  const snapshot: Record<string, NextShardTelemetry> = {};
  for (const [host, telemetry] of nextTelemetry) snapshot[host] = { ...telemetry };
  return snapshot;
}

export function resetNextShardTelemetry(): void {
  nextTelemetry.clear();
}

function recordNextShard(host: string, ok: boolean, status: number | null, elapsedMs: number): void {
  const entry = nextTelemetry.get(host) ?? {
    attempts: 0,
    successes: 0,
    failures: 0,
    lastStatus: null,
    lastElapsedMs: 0,
  };
  entry.attempts += 1;
  entry.lastStatus = status;
  entry.lastElapsedMs = elapsedMs;
  if (ok) entry.successes += 1;
  else entry.failures += 1;
  nextTelemetry.set(host, entry);
}

/**
 * Resolve a stream through the encrypted Next shards. The ECDH session is
 * generated once per request and reused across the pool, so retries stay
 * inside the Worker subrequest budget.
 */
export async function streamViaNextCommunity(
  env: Env,
  provider: ProviderId,
  trackId: string,
  quality: string
): Promise<StreamResult | null> {
  const hosts = rotateHosts(nextHostsFor(env, provider), `${provider}:${trackId}:${quality}`);
  if (!hosts.length || !trackId.trim()) return null;
  // Kill-switch for tests / incidents. Enabled by default: this path needs no
  // session or user config — only the embedded server key and static token.
  if (env.NEXT_COMMUNITY_ENABLED === "false") return null;

  const scope = `next:${provider}`;
  if (isRelayPoolDown(scope)) {
    console.warn(
      "VANTA_NEXT_BREAKER",
      JSON.stringify({ scope, trackId, action: "skip", reason: "next pool marked down" })
    );
    return null;
  }

  const installId = env.COMMUNITY_INSTALL_ID?.trim() || "shared-gateway-install";
  const appVersion = env.COMMUNITY_APP_VERSION?.trim() || "unknown";
  const requested = mapNextQuality(quality);

  const keyPair = await subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);

  let transportFailures = 0;
  let catalogMisses = 0;
  for (const base of hosts) {
    const startedAtMs = performance.now();
    const attempt = await nextApiDl(base, installId, appVersion, trackId, requested, keyPair, nextRequestToken(env), provider);
    const elapsedMs = Math.round(performance.now() - startedAtMs);
    if (!attempt.ok) {
      const isBlocked = attempt.bodyText.toLowerCase().includes("request_blocked") || attempt.bodyText.toLowerCase().includes("forbiddenexception");
      if (isBlocked) {
        const detail = attempt.bodyText.slice(0, 240);
        recordNextLicenseBlock(provider, attempt.status, detail);
        markRelayPoolDown(scope, "relay license agent blocked (REQUEST_BLOCKED)", 60_000);
        recordNextShard(base, false, attempt.status, elapsedMs);
        console.warn(
          "VANTA_NEXT_LICENSE_BLOCK",
          JSON.stringify({
            provider,
            trackId,
            base,
            status: attempt.status,
            quality: requested,
            elapsedMs,
            detail,
          })
        );
        return null;
      }
      const miss = isNextCatalogMiss(attempt.status, attempt.bodyText);
      if (miss) catalogMisses++;
      else transportFailures++;
      recordNextShard(base, false, attempt.status, elapsedMs);
      console.warn(
        "VANTA_NEXT_SHARD",
        JSON.stringify({
          provider,
          trackId,
          base,
          status: attempt.status,
          elapsedMs,
          miss: miss ? "catalog" : "transport",
          detail: attempt.bodyText.slice(0, 120),
        })
      );
      // Track-level "Atmos not available" is definitive for this id — do not
      // burn the remaining shards or trip the pool breaker.
      if (miss && (attempt.status === 404 || attempt.status === 409)) {
        return null;
      }
      continue;
    }
    console.info(
      "VANTA_NEXT_SHARD_OK",
      JSON.stringify({ provider, trackId, base, status: attempt.status, elapsedMs })
    );
    let payload: Record<string, unknown>;
    try {
      payload = JSON.parse(attempt.bodyText) as Record<string, unknown>;
    } catch {
      transportFailures++;
      recordNextShard(base, false, attempt.status, elapsedMs);
      console.warn("VANTA_NEXT_SHARD", JSON.stringify({ provider, trackId, base, reason: "decrypted body is not json" }));
      continue;
    }
    const rawUrl = typeof payload.url === "string" ? payload.url : null;
    let streamUrl = rawUrl ? normalizePublicHttpsUrl(rawUrl) : null;
    let isMpdManifest = false;
    let mpdIsAtmos = false;
    let mpdIs360 = false;

    if (rawUrl && rawUrl.startsWith("MANIFEST:")) {
      const b64 = rawUrl.slice("MANIFEST:".length).trim();
      let xml = "";
      try {
        xml = atob(b64);
      } catch {
        // invalid base64
      }
      if (xml) {
        const duration = durationFromMpd(xml);
        if (duration != null && duration <= 45) {
          catalogMisses++;
          recordNextShard(base, false, attempt.status, elapsedMs);
          console.warn(
            "VANTA_TIDAL_PREVIEW_REJECTED",
            JSON.stringify({ trackId, base, duration, reason: `next manifest duration ${duration}s` })
          );
          continue;
        }
        isMpdManifest = true;
        mpdIsAtmos = /EAC3_JOC|ec-3|eac3/i.test(xml);
        mpdIs360 = /mha1|mhm1|mpeg-h|360.?ra|360 reality/i.test(xml);
        const safeB64 = b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
        const gatewayOrigin = env.GATEWAY_BASE_URL?.trim() || "https://vanta-music-gateway.16drewk.workers.dev";
        streamUrl = `${gatewayOrigin.replace(/\/+$/, "")}/manifest/mpd?data=${safeB64}`;
      }
    }

    if (!streamUrl) {
      transportFailures++;
      recordNextShard(base, false, attempt.status, elapsedMs);
      console.warn("VANTA_NEXT_SHARD", JSON.stringify({ provider, trackId, base, reason: "no usable url", payload }));
      continue;
    }
    const payloadKey = amazonPayloadDecryptionKey(payload);
    if (amazonDirectUrlIsLocked(provider, streamUrl, payloadKey)) {
      catalogMisses++;
      recordNextShard(base, false, attempt.status, elapsedMs);
      console.warn(
        "VANTA_NEXT_SHARD",
        JSON.stringify({ provider, trackId, base, reason: "amazon cenc without key" })
      );
      continue;
    }
    if (provider === "amazon" && payloadKey) {
      const proxied = await extensionAudioUrl(env, {
        provider: "amazon",
        id: trackId,
        url: streamUrl,
        key: payloadKey,
        format: "mp4",
        expires: Date.now() + 30 * 60_000,
      });
      if (!proxied) {
        transportFailures++;
        recordNextShard(base, false, attempt.status, elapsedMs);
        console.warn(
          "VANTA_NEXT_SHARD",
          JSON.stringify({ provider, trackId, base, reason: "amazon decrypt proxy unavailable" })
        );
        continue;
      }
      streamUrl = proxied;
    }
    markRelayPoolUp(scope);
    recordNextShard(base, true, attempt.status, elapsedMs);
    const payloadFormat =
      typeof payload.format === "string" ? payload.format
      : typeof payload.codec === "string" ? payload.codec
      : undefined;
    const resolvedQuality = typeof payload.quality === "string" ? payload.quality : requested;
    const payloadSaysAtmos = /atmos|EAC3_JOC|eac3|ec-3/i.test(`${payloadFormat ?? ""} ${resolvedQuality}`);
    const payloadSays360 = /360|mha1|mhm1|mpeg-?h/i.test(`${payloadFormat ?? ""} ${resolvedQuality}`);
    const format = mpdIsAtmos || (payloadSaysAtmos && isMpdManifest)
      ? "EAC3_JOC"
      : mpdIs360 || payloadSays360
        ? (payloadFormat && /mha1|mhm1|mpeg-?h/i.test(payloadFormat) ? payloadFormat : "mha1")
        : payloadFormat ?? (isMpdManifest ? "flac" : "flac");
    const atmos = mpdIsAtmos || payloadSaysAtmos;
    const mimeType = isMpdManifest ? "application/dash+xml" : format.includes("/") ? format : `audio/${format}`;
    const sony360 = !atmos && (
      mpdIs360 ||
      payloadSays360 ||
      hasSony360Signal(resolvedQuality, format, mimeType) ||
      hasImmersiveContainerSignal(format, mimeType)
    );
    return {
      url: streamUrl,
      streamUrl,
      format,
      quality: atmos ? "Dolby Atmos" : (sony360 ? "360 Reality Audio" : resolvedQuality),
      mimeType,
      provider,
      isDolbyAtmos: atmos,
      isSpatialAudio: atmos || sony360,
      isSurround: atmos || sony360,
      spatialFormat: atmos ? "DOLBY_ATMOS" : (sony360 ? "SONY_360_REALITY_AUDIO" : undefined),
    };
  }
  // Only trip the breaker on transport pain — catalog misses (404/409 "not
  // available for this track") must not block the next Atmos-capable request.
  if (transportFailures === hosts.length && catalogMisses === 0) {
    markRelayPoolDown(scope, "all next shards failed transport", 15_000);
  }
  return null;
}

/** True when the relay answered but this track/quality is simply unavailable. */
function isNextCatalogMiss(status: number, bodyText: string): boolean {
  if (status === 404 || status === 409) return true;
  const detail = bodyText.toLowerCase();
  return (
    detail.includes("not available") ||
    detail.includes("not eligible") ||
    detail.includes("content_not_eligible") ||
    detail.includes("atmos is not available") ||
    detail.includes("360 reality audio is not available") ||
    detail.includes("resource not found") ||
    detail.includes("request_blocked") ||
    detail.includes("forbiddenexception") ||
    detail.includes("failed to get license")
  );
}