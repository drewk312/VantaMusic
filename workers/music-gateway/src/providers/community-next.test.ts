import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  NEXT_ENVELOPE_HEADER,
  NEXT_MIN_ENVELOPE,
  NEXT_NONCE_LENGTH,
  NEXT_PUBKEY_LENGTH,
  NEXT_REQUEST_TOKEN,
  NEXT_SALT_LENGTH,
  NEXT_SERVER_PUBLIC_KEY_B64,
  buildNextWrapperBody,
  deriveNextKey,
  deriveNextShared,
  exportNextPublicKey,
  generateNextClientKey,
  importNextServerFromPublicKey,
  importNextServerPublicKey,
  nextShardTelemetry,
  openNextResponse,
  resetNextShardTelemetry,
  sealNextBody,
  sealNextRequest,
  streamViaNextCommunity,
  mapNextQuality,
  allNextHosts,
  nextHostsFor,
  rotateHosts,
  isProviderLicenseBlocked,
  nextLicenseBlockSnapshot,
  resetNextLicenseBlocks,
} from "./community-next.js";
import { markRelayPoolUp } from "./relay-health.js";

function b64ToBytes(b64: string): Uint8Array {
  return new Uint8Array(Buffer.from(b64, "base64"));
}

// ---------------------------------------------------------------------------
// Pure bigint P-256 scalar multiplication, used to independently confirm that
// ECDH deriveBits returns the RAW x-coordinate (Go crypto/ecdh
// `crypto/internal/fips140/ecdh.ECDH` → `p.BytesX()`), not a hashed value.
// ---------------------------------------------------------------------------
const P256_P = 2n ** 256n - 2n ** 224n + 2n ** 192n + 2n ** 96n - 1n;
// y² = x³ − 3x + b (P-256 uses a = −3 mod p)
const P256_A = P256_P - 3n;
const P256_B = 0x5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604bn;
const P256_GX = 0x6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296n;
const P256_GY = 0x4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5n;

function modInverse(a: bigint, m: bigint): bigint {
  a = ((a % m) + m) % m;
  let [oldR, r] = [a, m];
  let [oldS, s] = [1n, 0n];
  let [oldT, t] = [0n, 1n];
  while (r !== 0n) {
    const q = oldR / r;
    [oldR, r] = [r, oldR - q * r];
    [oldS, s] = [s, oldS - q * s];
    [oldT, t] = [t, oldT - q * t];
  }
  if (oldR !== 1n) throw new Error("no modular inverse");
  return ((oldS % m) + m) % m;
}

function pointAdd(ax: bigint, ay: bigint, bx: bigint, by: bigint): [bigint, bigint] {
  if (ax === 0n && ay === 0n) return [bx, by];
  if (bx === 0n && by === 0n) return [ax, ay];
  if (ax === bx) {
    if ((ay + by) % P256_P === 0n) return [0n, 0n];
    return pointDouble(ax, ay);
  }
  const m = ((by - ay) * modInverse(bx - ax, P256_P)) % P256_P;
  const x3 = (m * m - ax - bx) % P256_P;
  const y3 = (m * (ax - x3) - ay) % P256_P;
  return [((x3 % P256_P) + P256_P) % P256_P, ((y3 % P256_P) + P256_P) % P256_P];
}

function pointDouble(ax: bigint, ay: bigint): [bigint, bigint] {
  if (ay === 0n) return [0n, 0n];
  const m = ((3n * ax * ax + P256_A) * modInverse(2n * ay, P256_P)) % P256_P;
  const x3 = (m * m - 2n * ax) % P256_P;
  const y3 = (m * (ax - x3) - ay) % P256_P;
  return [((x3 % P256_P) + P256_P) % P256_P, ((y3 % P256_P) + P256_P) % P256_P];
}

function scalarMult(k: bigint, x: bigint, y: bigint): [bigint, bigint] {
  let [rx, ry] = [0n, 0n];
  let [tx, ty] = [x, y];
  while (k > 0n) {
    if (k & 1n) [rx, ry] = pointAdd(rx, ry, tx, ty);
    [tx, ty] = pointDouble(tx, ty);
    k >>= 1n;
  }
  return [rx, ry];
}

function bytesToBigInt(bytes: Uint8Array): bigint {
  let out = 0n;
  for (const byte of bytes) out = (out << 8n) | BigInt(byte);
  return out;
}

function bigIntToBytes32(value: bigint): Uint8Array {
  const out = new Uint8Array(32);
  let v = value;
  for (let i = 31; i >= 0; i--) {
    out[i] = Number(v & 0xffn);
    v >>= 8n;
  }
  return out;
}

describe("next host pool", () => {
  it("covers all 20 SpotBye Next services by default", () => {
    assert.equal(allNextHosts().length, 20);
    assert.equal(nextHostsFor(undefined, "qobuz").length, 5);
    assert.equal(nextHostsFor(undefined, "tidal").length, 5);
    assert.equal(nextHostsFor(undefined, "amazon").length, 5);
    assert.equal(nextHostsFor(undefined, "deezer").length, 5);
  });

  it("rotates shard order for backup spreading", () => {
    const hosts = nextHostsFor(undefined, "tidal");
    const a = rotateHosts(hosts, "seed-a");
    const b = rotateHosts(hosts, "seed-b");
    assert.equal(a.length, 5);
    assert.deepEqual([...a].sort(), [...hosts].sort());
    assert.notDeepEqual(a, b);
  });
});

describe("Next envelope protocol", () => {
  it("maps Atmos and Sony 360 request tokens onto the Next quality enum", () => {
    assert.equal(mapNextQuality("atmos"), "atmos");
    assert.equal(mapNextQuality("EAC3_JOC"), "atmos");
    assert.equal(mapNextQuality("360"), "360");
    assert.equal(mapNextQuality("360RA"), "360");
    assert.equal(mapNextQuality("sony_360"), "360");
    assert.equal(mapNextQuality("24"), "24");
    assert.equal(mapNextQuality("hi_res"), "24");
    assert.equal(mapNextQuality("16"), "16");
  });

  it("embeds a well-formed P-256 server identity key", async () => {
    const key = b64ToBytes(NEXT_SERVER_PUBLIC_KEY_B64);
    assert.equal(key.byteLength, NEXT_PUBKEY_LENGTH);
    assert.equal(key[0], 0x04);
    const x = bytesToBigInt(key.slice(1, 33));
    const y = bytesToBigInt(key.slice(33, 65));
    assert.equal((y * y) % P256_P, (x * x * x + P256_A * x + P256_B) % P256_P);
    // verify a scalar-multiplied public key is also importable by WebCrypto
    const kp = await generateNextClientKey();
    const shared = await deriveNextShared(kp, await importNextServerPublicKey());
    assert.equal(shared.byteLength, 32);
  });

  it("pins the well-known P-256 2G multiple (secp256r1, NOT secp256k1)", () => {
    const [x, y] = scalarMult(2n, P256_GX, P256_GY);
    // Verified independent of this test: OpenSSL `ec -outform DER -conv_form compressed`
    // on a d=2 SEC1 key emits `03 7cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc47669978`.
    assert.equal(
      Buffer.from(bigIntToBytes32(x)).toString("hex"),
      "7cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc47669978"
    );
    // c6047f... is secp256k1's [2]G — a classic mixup, must never regress to it.
    assert.notEqual(
      Buffer.from(bigIntToBytes32(x)).toString("hex"),
      "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
    );
    assert.equal((y * y) % P256_P, (x * x * x + P256_A * x + P256_B) % P256_P);
  });

  it("derives a golden HKDF-SHA256 output (RFC 5869 test case 1)", async () => {
    const ikm = new Uint8Array(22).fill(0x0b);
    const salt = new Uint8Array([0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c]);
    const info = new Uint8Array([0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9]);
    const okm = await deriveNextKey(ikm, salt, info, 42);
    assert.equal(
      Buffer.from(okm).toString("hex"),
      "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
    );
  });

  it("ECDH returns the raw x-coordinate (Go crypto/ecdh semantics)", async () => {
    const server = b64ToBytes(NEXT_SERVER_PUBLIC_KEY_B64);
    const sx = bytesToBigInt(server.slice(1, 33));
    const sy = bytesToBigInt(server.slice(33, 65));

    // Use a real generated pair and read its private scalar back out through a
    // JWK export (Node WebCrypto cannot import a raw P-256 scalar).
    const kp = await generateNextClientKey(true);
    const jwk = (await crypto.subtle.exportKey("jwk", kp.privateKey)) as unknown as JsonWebKey;
    const d = bytesToBigInt(new Uint8Array(Buffer.from(jwk.d as string, "base64url")));

    const peer = await importNextServerPublicKey();
    const shared = await deriveNextShared(kp, peer);

    const [expectedX, expectedY] = scalarMult(d, sx, sy);
    assert.equal(Buffer.from(shared).toString("hex"), Buffer.from(bigIntToBytes32(expectedX)).toString("hex"));
    const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bigIntToBytes32(expectedX)));
    assert.notEqual(Buffer.from(shared).toString("hex"), Buffer.from(digest).toString("hex"));
    assert.equal((expectedY * expectedY) % P256_P, (expectedX * expectedX * expectedX + P256_A * expectedX + P256_B) % P256_P);
  });

  it("builds a request envelope with the documented layout", async () => {
    const body = new TextEncoder().encode('{"id":"42","quality":"16"}');
    const { envelope } = await sealNextRequest(body);
    assert.equal(envelope[0], 0x02);
    assert.equal(envelope.byteLength, NEXT_ENVELOPE_HEADER + body.byteLength + 16);
    assert.equal(envelope[1], 0x04);
    const salt = envelope.slice(1 + NEXT_PUBKEY_LENGTH, 1 + NEXT_PUBKEY_LENGTH + NEXT_SALT_LENGTH);
    const nonce = envelope.slice(1 + NEXT_PUBKEY_LENGTH + NEXT_SALT_LENGTH, NEXT_ENVELOPE_HEADER);
    assert.equal(salt.byteLength, NEXT_SALT_LENGTH);
    assert.equal(nonce.byteLength, NEXT_NONCE_LENGTH);
    assert.notEqual(Buffer.from(salt).toString("hex"), Buffer.from(nonce).toString("hex"));
  });

  it("round-trips an encrypted request/response against an independent relay key", async () => {
    const body = new TextEncoder().encode('{"id":"999","quality":"atmos"}');
    const clientKeyPair = await generateNextClientKey();
    const { envelope } = await sealNextRequest(body, clientKeyPair);

    // Simulate the relay: it holds its own ephemeral P-256 pair, derives the
    // same shared secret from the client's transmitted public key, and replies
    // with label spotiflac-resp-v2 in an identical envelope.
    const relayPair = await generateNextClientKey();
    const clientPub = envelope.slice(1, 1 + NEXT_PUBKEY_LENGTH);
    const clientPubKey = await importNextServerFromPublicKey(clientPub);
    const relayShared = await deriveNextShared(relayPair, clientPubKey);
    const relaySalt = new Uint8Array(NEXT_SALT_LENGTH);
    const relayNonce = new Uint8Array(NEXT_NONCE_LENGTH);
    crypto.getRandomValues(relaySalt);
    crypto.getRandomValues(relayNonce);
    const relayKey = await deriveNextKey(
      relayShared,
      relaySalt,
      new TextEncoder().encode("spotiflac-resp-v2")
    );
    const responseText = '{"url":"https://cdn.example/a.flac"}';
    const relayCt = await sealNextBody(relayKey, relayNonce, new TextEncoder().encode(responseText));

    const relayPub = new Uint8Array(await exportNextPublicKey(relayPair));
    const response = new Uint8Array([0x02, ...relayPub, ...relaySalt, ...relayNonce, ...relayCt]);
    assert.ok(response.byteLength >= NEXT_MIN_ENVELOPE);

    const opened = await openNextResponse(response, clientKeyPair);
    assert.equal(new TextDecoder().decode(opened.body), responseText);
    assert.equal(Buffer.from(opened.serverPublicKey).toString("hex"), Buffer.from(relayPub).toString("hex"));
  });

  it("rejects truncated envelopes and unknown versions", async () => {
    const clientKeyPair = await generateNextClientKey();
    await assert.rejects(openNextResponse(new Uint8Array([0x02, 0x04]), clientKeyPair), /too short/);
    const badVersion = new Uint8Array(NEXT_MIN_ENVELOPE);
    badVersion.fill(0);
    badVersion[0] = 0x03;
    await assert.rejects(openNextResponse(badVersion, clientKeyPair), /unsupported secure envelope version 3/);
  });

  it("builds the live-verified wrapper body (nested object, unix-second ts)", () => {
    const parsed = JSON.parse(buildNextWrapperBody(" 381791126 ", "16", 1789229694)) as Record<string, unknown>;
    assert.equal(parsed.token, NEXT_REQUEST_TOKEN);
    assert.equal(parsed.ts, 1789229694);
    // body MUST be a nested object — the relay rejects a string body with
    // `invalid_type: expected object, received string` (verified live).
    assert.equal(typeof parsed.body, "object");
    // Digit IDs are sent as JSON numbers — Tidal Next rejects string ids.
    assert.deepEqual(parsed.body, { id: 381791126, quality: "16" });
  });

  it("keeps Amazon ASINs as strings in the Next wrapper body", () => {
    const parsed = JSON.parse(buildNextWrapperBody("B0CVGNYVLK", "360", 1789229694, undefined, "amazon")) as Record<string, unknown>;
    assert.deepEqual(parsed.body, { id: "B0CVGNYVLK", quality: "360" });
  });

  it("streamViaNextCommunity returns null without a provider pool", async () => {
    const env = {} as never;
    assert.equal(await streamViaNextCommunity(env, "deezer", "42", "16"), null);
    assert.equal(await streamViaNextCommunity(env, "qobuz", "", "16"), null);
  });
});

// ---------------------------------------------------------------------------
// Tidal MANIFEST (DASH) handling regression tests.
//
// The live `*.tdlxn` shards respond with `{"url":"MANIFEST:<base64 MPD>"}`.
// These tests fake a relay shard by (a) catching the encrypted request that
// `nextApiDl` POSTs, (b) extracting the client's ephemeral public key from the
// envelope, (c) building a `spotiflac-resp-v2` reply like a real relay would,
// with a configurable JSON payload. This exercises the real decryption path in
// `streamViaNextCommunity` end-to-end without any network.
// ---------------------------------------------------------------------------

const FETCH_ORIGINAL = globalThis.fetch;

// Build a realistic Tidal DASH MPD. `durationSeconds` is emitted as the
// `mediaPresentationDuration` attribute; `atmos` toggles an EAC3 (Dolby
// Atmos) adaptation set so the relay payload mirrors a real Atmos track.
function buildMpd(durationSeconds: number, atmos = false, mpegh = false): string {
  const h = Math.floor(durationSeconds / 3600);
  const m = Math.floor((durationSeconds % 3600) / 60);
  const s = (durationSeconds % 60).toFixed(3);
  const dur = `PT${h > 0 ? `${h}H` : ""}${m > 0 ? `${m}M` : ""}${s}S`;
  const role = `<Role schemeIdUri="urn:mpeg:dash:role:2011" value="main"/>`;
  const atmosSegment =
    atmos &&
    `<AdaptationSet mimeType="audio/mp4" codecs="ec-3" audioSamplingRate="48000" lang="en">
      <Role schemeIdUri="urn:mpeg:dash:role:2011" value="main"/>
      <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>
      <Representation id="atmos" codecs="ec-3" bandwidth="768000" audioSamplingRate="48000">
        <ContentComponent id="1" type="audio" lang="en"/>
      </Representation>
    </AdaptationSet>`;
  const mpeghSegment =
    mpegh &&
    `<AdaptationSet mimeType="audio/mp4" codecs="mha1" audioSamplingRate="48000" lang="en">
      <Role schemeIdUri="urn:mpeg:dash:role:2011" value="main"/>
      <Representation id="360ra" codecs="mha1" bandwidth="768000" audioSamplingRate="48000">
        <ContentComponent id="1" type="audio" lang="en"/>
      </Representation>
    </AdaptationSet>`;
  return `<?xml version="1.0" encoding="UTF-8"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" profiles="urn:mpeg:dash:profile:isoff-live:2011" type="static" mediaPresentationDuration="${dur}" minBufferTime="PT1.500S">
  <Period start="PT0S" duration="${dur}">
    ${atmosSegment}
    ${mpeghSegment}
    <AdaptationSet mimeType="audio/mp4" codecs="flac" audioSamplingRate="44100" lang="en">
      ${role}
      <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>
      <Representation id="flac0" codecs="flac" bandwidth="1014515" audioSamplingRate="44100">
        <SegmentTemplate timescale="44100" initialization="video/audio/159851711/init.mp4" media="video/audio/159851711/\$Number\$.m4s" startNumber="1" duration="193536"/>
        <ContentComponent id="1" type="audio" lang="en"/>
      </Representation>
    </AdaptationSet>
  </Period>
</MPD>`;
}

// Simulate a Tidal Next relay: given the encrypted request body sent by
// `nextApiDl`, decrypt-agnostically derive the shared secret from the client
// public key embedded in the envelope and return an `spotiflac-resp-v2`
// envelope carrying `payloadJson` (exactly like the real `*.tdlxn` shard).
async function installFakeNextRelay(payloadJson: string): Promise<() => void> {
  const realFetch = FETCH_ORIGINAL;
  globalThis.fetch = (async (input: Parameters<typeof fetch>[0], init?: RequestInit) => {
    const body = init?.body as Uint8Array | undefined;
    if (typeof input === "string" && body instanceof Uint8Array) {
      const clientPub = body.slice(1, 1 + NEXT_PUBKEY_LENGTH);
      const clientPubKey = await importNextServerFromPublicKey(clientPub);
      const relayPair = await generateNextClientKey();
      const shared = await deriveNextShared(relayPair, clientPubKey);
      const salt = new Uint8Array(NEXT_SALT_LENGTH);
      const nonce = new Uint8Array(NEXT_NONCE_LENGTH);
      crypto.getRandomValues(salt);
      crypto.getRandomValues(nonce);
      const key = await deriveNextKey(shared, salt, new TextEncoder().encode("spotiflac-resp-v2"));
      const ciphertext = await sealNextBody(key, nonce, new TextEncoder().encode(payloadJson));
      const relayPub = new Uint8Array(await exportNextPublicKey(relayPair));
      const envelope = new Uint8Array([0x02, ...relayPub, ...salt, ...nonce, ...ciphertext]);
      return new Response(new Uint8Array(envelope), { status: 200 });
    }
    return realFetch(input as RequestInfo | URL, init);
  }) as typeof fetch;
  return () => {
    globalThis.fetch = realFetch;
  };
}

const NEXT_TEST_ENV = {
  NEXT_COMMUNITY_ENABLED: "true",
  GATEWAY_BASE_URL: "https://gateway.example.com",
} as never;

describe("Tidal MANIFEST handling", () => {
  it("rewrites a full-length MPD into a /manifest/mpd DASH URL", async () => {
    const mpd = buildMpd(3 * 60 + 43.773);
    const restore = await installFakeNextRelay(
      JSON.stringify({ quality: "16", codec: "flac", bit_depth: 16, sample_rate: 44100, url: `MANIFEST:${Buffer.from(mpd).toString("base64")}` })
    );
    try {
      const result = await streamViaNextCommunity(NEXT_TEST_ENV, "tidal", "123", "16");
      assert.ok(result, "expected a StreamResult");
      assert.ok(result.streamUrl, "expected a rewritten streamUrl");
      assert.match(result.streamUrl, /^https:\/\/gateway\.example\.com\/manifest\/mpd\?data=/);
      assert.equal(result.mimeType, "application/dash+xml");
      assert.equal(result.isDolbyAtmos, false);
      assert.equal(result.format, "flac");
    } finally {
      restore();
    }
  });

  it("marks Atmos MPDs as Dolby Atmos / spatial / surround", async () => {
    const mpd = buildMpd(3 * 60 + 43.773, true);
    const restore = await installFakeNextRelay(
      JSON.stringify({ quality: "16", codec: "ec-3", bit_depth: 16, sample_rate: 48000, url: `MANIFEST:${Buffer.from(mpd).toString("base64")}` })
    );
    try {
      const result = await streamViaNextCommunity(NEXT_TEST_ENV, "tidal", "123", "16");
      assert.ok(result, "expected a StreamResult");
      assert.equal(result.isDolbyAtmos, true);
      assert.equal(result.isSpatialAudio, true);
      assert.equal(result.isSurround, true);
      assert.equal(result.format, "EAC3_JOC");
      assert.equal(result.mimeType, "application/dash+xml");
    } finally {
      restore();
    }
  });

  it("marks MPEG-H MPDs as Sony 360 Reality Audio", async () => {
    const mpd = buildMpd(3 * 60 + 20, false, true);
    const restore = await installFakeNextRelay(
      JSON.stringify({ quality: "360", codec: "mha1", url: `MANIFEST:${Buffer.from(mpd).toString("base64")}` })
    );
    try {
      const result = await streamViaNextCommunity(NEXT_TEST_ENV, "amazon", "B076YT2CBT", "360");
      assert.ok(result, "expected a StreamResult");
      assert.equal(result.format, "mha1");
      assert.equal(result.quality, "360 Reality Audio");
      assert.equal(result.spatialFormat, "SONY_360_REALITY_AUDIO");
      assert.equal(result.isSpatialAudio, true);
      assert.equal(result.isDolbyAtmos, false);
    } finally {
      restore();
    }
  });

  it("rejects 30s preview manifests (<= 45s) across the whole pool", async () => {
    const mpd = buildMpd(30);
    const restore = await installFakeNextRelay(
      JSON.stringify({ quality: "16", codec: "flac", url: `MANIFEST:${Buffer.from(mpd).toString("base64")}` })
    );
    try {
      const result = await streamViaNextCommunity(NEXT_TEST_ENV, "tidal", "123", "16");
      assert.equal(result, null);
    } finally {
      restore();
    }
  });

  it("falls through to null when the relay returns unusable data but records shard attempts", async () => {
    resetNextShardTelemetry();
    // The preview test above just marked the whole tidal pool down; clear the
    // breaker so this test actually exercises the shard loop instead of the
    // fast-fail gate.
    markRelayPoolUp("next:tidal");
    const restore = await installFakeNextRelay(JSON.stringify({ url: "MANIFEST:!!!not-base64!!!" }));
    try {
      const result = await streamViaNextCommunity(NEXT_TEST_ENV, "tidal", "123", "24");
      assert.equal(result, null);
    } finally {
      restore();
    }
    const telemetry = nextShardTelemetry();
    const attempts = Object.values(telemetry).reduce((sum, t) => sum + t.attempts, 0);
    assert.equal(attempts, 5, "expected all 5 tidal shards to be attempted");
  });
});

// ---------------------------------------------------------------------------
// Amazon license-agent block telemetry.
//
// The `*.amzxn` shards answer every quality tier with
// `400 {"detail":"Failed to get license: ...","denialReason":"REQUEST_BLOCKED"}`
// because the relay's Amazon device is refused by the license service
// (`com.amazon.digitalmusicdrmlicenseservice#ForbiddenException`). /health still
// reports 200, so without telemetry the pool looks healthy. These tests pin that
// a REQUEST_BLOCKED reply is recorded per provider, surfaced in the snapshot,
// attributed onto health probes, and opens the pool breaker.
// ---------------------------------------------------------------------------

describe("Amazon license-agent block telemetry", () => {
  function installBlockingRelay(body: string): { restore: () => void; fetchCount: () => number } {
    let count = 0;
    const realFetch = FETCH_ORIGINAL;
    globalThis.fetch = (async (input: Parameters<typeof fetch>[0], init?: RequestInit) => {
      if (typeof input === "string" && input.endsWith("/api/dl")) {
        count += 1;
        return new Response(body, { status: 400 });
      }
      return realFetch(input as RequestInfo | URL, init);
    }) as typeof fetch;
    return {
      restore: () => {
        globalThis.fetch = realFetch;
      },
      fetchCount: () => count,
    };
  }

  const AMZN_BLOCK =
    `{"detail":"Failed to get license: 400 {\\"denialReason\\":\\"REQUEST_BLOCKED\\",\\"message\\":\\"Request blocked for license (EligibilityException REQUEST_BLOCKED)\\"}"}`;

  it("records a REQUEST_BLOCKED denial per provider and opens the breaker", async () => {
    resetNextLicenseBlocks();
    const { restore, fetchCount } = installBlockingRelay(AMZN_BLOCK);
    try {
      const result = await streamViaNextCommunity(NEXT_TEST_ENV, "amazon", "B0CZW15FZZ", "24");
      assert.equal(result, null);
      assert.equal(isProviderLicenseBlocked("amazon"), true);
      assert.equal(isProviderLicenseBlocked("tidal"), false, "blocks are provider-scoped");
      const snapshot = nextLicenseBlockSnapshot();
      const block = snapshot.amazon;
      assert.ok(block, "expected an amazon license block in the snapshot");
      assert.equal(block.lastStatus, 400);
      assert.equal(block.blockedAt, block.lastSeenAt, "first sighting sets both timestamps");
      assert.match(block.detail, /REQUEST_BLOCKED/);
      assert.equal(fetchCount(), 1, "pool breaker short-circuits the remaining shards");

      const second = await streamViaNextCommunity(NEXT_TEST_ENV, "amazon", "B0CZW15FZZ", "16");
      assert.equal(second, null);
      assert.equal(fetchCount(), 1, "breaker fast-fail does not re-hit the relay");
    } finally {
      restore();
      markRelayPoolUp("next:amazon");
      resetNextLicenseBlocks();
    }
  });

  it("keeps the original blockedAt across repeated denials", async () => {
    resetNextLicenseBlocks();
    const { restore, fetchCount } = installBlockingRelay(AMZN_BLOCK);
    try {
      await streamViaNextCommunity(NEXT_TEST_ENV, "amazon", "B076YT2CBT", "24");
      assert.equal(fetchCount(), 1);
    } finally {
      restore();
      markRelayPoolUp("next:amazon");
    }
    const first = nextLicenseBlockSnapshot().amazon;
    assert.ok(first, "expected a recorded block");
    const firstSeen = Date.now();
    // Clear the short-lived breaker so the second request reaches the relay again.
    const restore2 = installBlockingRelay(AMZN_BLOCK);
    try {
      await new Promise((resolve) => setTimeout(resolve, 5));
      await streamViaNextCommunity(NEXT_TEST_ENV, "amazon", "B076YT2CBT", "24");
    } finally {
      restore2.restore();
      markRelayPoolUp("next:amazon");
    }
    const after = nextLicenseBlockSnapshot().amazon;
    assert.equal(after.blockedAt, first.blockedAt, "blockedAt is first-sighting, not reset");
    assert.ok(after.lastSeenAt >= after.blockedAt, "lastSeenAt advances");
    assert.ok(after.blockedAt <= firstSeen, "blockedAt anchors on the first denial");
    resetNextLicenseBlocks();
  });

  it("does not mark a license block for ordinary 4xx misses", async () => {
    resetNextLicenseBlocks();
    const { restore } = installBlockingRelay(`{"detail":"Encrypted request required."}`);
    try {
      const result = await streamViaNextCommunity(NEXT_TEST_ENV, "amazon", "B0CZW15FZZ", "24");
      assert.equal(result, null);
      assert.equal(isProviderLicenseBlocked("amazon"), false);
      assert.equal(Object.keys(nextLicenseBlockSnapshot()).length, 0);
    } finally {
      restore();
      markRelayPoolUp("next:amazon");
      resetNextLicenseBlocks();
    }
  });
});