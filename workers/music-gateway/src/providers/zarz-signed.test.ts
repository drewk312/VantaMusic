import { afterEach, describe, it } from "node:test";
import assert from "node:assert/strict";
import { streamViaZarzSigned, sessionFromEnv, signedJson, refreshZarzSession, bootstrapZarzSession, zarzCanPlay } from "./zarz-signed.js";
import { signCommunityRequest } from "./community-session.js";
import type { Env } from "../types";

const originalFetch = globalThis.fetch;

function envFor(provider: "qobuz" | "tidal" | "amazon" | "deezer") {
  const suffix = provider.toUpperCase();
  return {
    ZARZ_INSTALL_ID: "install-id",
    [`ZARZ_${suffix}_SESSION_ID`]: "session-id",
    [`ZARZ_${suffix}_SESSION_SECRET`]: "session-secret",
    [`ZARZ_${suffix}_SESSION_EXPIRES`]: new Date(Date.now() + 60 * 60_000).toISOString(),
  } as unknown as Env;
}

afterEach(() => {
  globalThis.fetch = originalFetch;
});

describe("current SpotiFLAC Zarz signed provider", () => {
  it("signs the complete /v2 path using the extension's rolling key contract", async () => {
    const session = sessionFromEnv(envFor("amazon"), "amazon")!;
    globalThis.fetch = (async (input, init) => {
      assert.equal(String(input), "https://api.zarz.moe/v2/tickets");
      assert.equal(init?.redirect, "manual");
      const headers = new Headers(init?.headers);
      const expected = await signCommunityRequest(session, "POST", "/v2/tickets", "", new TextEncoder().encode(String(init?.body)),
        headers.get("X-Zarz-Timestamp")!, headers.get("X-Zarz-Nonce")!,
        { schemeLabel: "ZARZ-HMAC-V1", headerPrefix: "X-Zarz-", timeWindowSeconds: 300, rollingKeyEncoding: "base64url", userAgent: "SpotiFLAC-Mobile/amzn@2.3.3" });
      assert.equal(headers.get("X-Zarz-Signature"), expected["X-Zarz-Signature"]);
      return Response.json({ ticket_id: "test" });
    }) as typeof fetch;
    assert.deepEqual(await signedJson(session, "/tickets", { provider: "amazeamazeamaze" }), { ticket_id: "test" });
  });

  for (const provider of ["amazon", "deezer"] as const) {
    it(`wraps encrypted ${provider} audio in an opaque playback ticket`, async () => {
      const env = { ...envFor(provider), EXTENSION_PROXY_SECRET: "test-proxy-secret", GATEWAY_BASE_URL: "https://gateway.example" };
      globalThis.fetch = (async (input) => {
        if (String(input).endsWith("/tickets")) return Response.json({ ticket_id: "ticket" });
        return Response.json(provider === "amazon" ? [{ audio: { url: "https://audio.example/encrypted", key: "00112233445566778899aabbccddeeff", codec: "flac", bitDepth: 24, sampleRate: 44100 } }] :
          { success: true, requires_client_decryption: true, direct_download_url: "https://audio.example/encrypted", deezer_format: "flac" });
      }) as typeof fetch;
      const result = await streamViaZarzSigned(env as never, provider, provider === "amazon" ? "B0G2FCHJ2Z" : "1109731", "24");
      assert.ok(result?.url.startsWith("https://gateway.example/audio/extension?token="));
      assert.equal(result?.mimeType, provider === "amazon" ? "audio/mp4" : "audio/flac");
      assert.ok(result?.expiresAt);
      assert.ok(!JSON.stringify(result).includes("0011223344556677"));
    });
  }
  it("mints a Qobuz ticket and preserves hi-res metadata", async () => {
    const requests: Array<{ url: string; headers: Headers; body: Record<string, unknown> }> = [];
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      const headers = new Headers(init?.headers);
      const body = JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>;
      requests.push({ url, headers, body });
      if (url.endsWith("/tickets")) {
        return new Response(JSON.stringify({ ticket_id: "ticket-1" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify({
        url: "https://streaming-qobuz-std.akamaized.net/full-track.flac",
        bit_depth: 24,
        sampling_rate: 96,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }) as typeof fetch;

    const result = await streamViaZarzSigned(envFor("qobuz"), "qobuz", "381791126", "24");

    assert.ok(result);
    assert.equal(result?.bitDepth, 24);
    assert.equal(result?.sampleRateHz, 96_000);
    assert.equal(result?.mimeType, "audio/flac");
    assert.equal(requests.length, 2);
    assert.equal(requests[0].headers.get("X-Zarz-App-Version"), "qobuz-web@1.2.10");
    assert.equal(requests[0].body.provider, "qbz");
    assert.equal(requests[1].headers.get("X-Zarz-Ticket"), "ticket-1");
    assert.equal(requests[1].body.quality, "hi-res-max");
  });

  it("returns a verified Tidal Atmos DASH manifest", async () => {
    globalThis.fetch = (async (input, init) => {
      const url = String(input);
      if (url.endsWith("/tickets")) {
        return new Response(JSON.stringify({ ticket_id: "ticket-atmos" }), { status: 200 });
      }
      const headers = new Headers(init?.headers);
      assert.equal(headers.get("X-Zarz-Ticket"), "ticket-atmos");
      const body = JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>;
      assert.deepEqual(body.formats, ["EAC3_JOC"]);
      return new Response(JSON.stringify({
        data: { data: { attributes: {
          formats: ["EAC3_JOC"],
          uri: "https://audio.tidal.com/manifest.mpd",
        } } },
      }), { status: 200 });
    }) as typeof fetch;

    const result = await streamViaZarzSigned(envFor("tidal"), "tidal", "487719101", "atmos");

    assert.ok(result);
    assert.equal(result?.mimeType, "application/dash+xml");
    assert.equal(result?.format, "eac3-joc");
    assert.equal(result?.isDolbyAtmos, true);
  });

  it("requests Amazon AC-4 Atmos before E-AC-3", async () => {
    const codecs: string[] = [];
    globalThis.fetch = (async (_input, init) => {
      const url = String(_input);
      if (url.endsWith("/tickets")) return Response.json({ ticket_id: "ticket-ac4" });
      const body = JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>;
      codecs.push(String(body.codec));
      if (body.codec === "ac4") {
        return Response.json([{
          audio: {
            url: "https://audio.example/ac4",
            key: "00112233445566778899aabbccddeeff",
            codec: "ac-4",
          },
        }]);
      }
      return new Response("{}", { status: 404 });
    }) as typeof fetch;
    const env = { ...envFor("amazon"), EXTENSION_PROXY_SECRET: "test-proxy-secret", GATEWAY_BASE_URL: "https://gateway.example" };
    const result = await streamViaZarzSigned(env as never, "amazon", "B0G2FCHJ2Z", "atmos");
    assert.equal(codecs[0], "ac4");
    assert.equal(result?.format, "ac-4");
    assert.equal(result?.isDolbyAtmos, true);
    assert.ok(result?.url.startsWith("https://gateway.example/audio/extension?token="));
  });

  it("requests Amazon MPEG-H for Sony 360 Reality Audio", async () => {
    const codecs: string[] = [];
    globalThis.fetch = (async (_input, init) => {
      const url = String(_input);
      if (url.endsWith("/tickets")) return Response.json({ ticket_id: "ticket-360" });
      const body = JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>;
      codecs.push(String(body.codec));
      if (body.codec === "mha1") {
        return Response.json([{
          audio: {
            url: "https://audio.example/mha1",
            key: "00112233445566778899aabbccddeeff",
            codec: "mha1",
          },
        }]);
      }
      return new Response("{}", { status: 404 });
    }) as typeof fetch;
    const env = { ...envFor("amazon"), EXTENSION_PROXY_SECRET: "test-proxy-secret", GATEWAY_BASE_URL: "https://gateway.example" };
    const result = await streamViaZarzSigned(env as never, "amazon", "B076YT2CBT", "360");
    assert.equal(codecs[0], "mha1");
    assert.equal(result?.format, "mha1");
    assert.equal(result?.quality, "360 Reality Audio");
    assert.equal(result?.spatialFormat, "SONY_360_REALITY_AUDIO");
    assert.ok(result?.url.startsWith("https://gateway.example/audio/extension?token="));
  });

  it("rejects missing and expired provider sessions without network access", async () => {
    let called = false;
    globalThis.fetch = (async () => {
      called = true;
      return new Response("{}");
    }) as typeof fetch;
    assert.equal(await streamViaZarzSigned({} as never, "qobuz", "381791126", "24"), null);
    assert.equal(called, false);
  });

  it("refreshes with a signed install_id body and maps SESSION_INVALID to bootstrap", async () => {
    const session = sessionFromEnv(envFor("deezer"), "deezer")!;
    globalThis.fetch = (async (input, init) => {
      assert.equal(String(input), "https://api.zarz.moe/v2/session/refresh");
      assert.equal(init?.method, "POST");
      assert.equal(JSON.parse(String(init?.body)).install_id, "install-id");
      const headers = new Headers(init?.headers);
      assert.equal(headers.get("X-Zarz-App-Version"), "deezer@1.3.5");
      return Response.json(
        { error: "invalid session", code: "SESSION_INVALID", origin: "gateway", action: "bootstrap_session" },
        { status: 401 }
      );
    }) as typeof fetch;
    assert.deepEqual(await refreshZarzSession(session), { action: "bootstrap_session" });
  });

  it("bootstraps with an unsigned GET and accepts a silent session", async () => {
    const session = sessionFromEnv(envFor("deezer"), "deezer")!;
    const expires = new Date(Date.now() + 60 * 60_000).toISOString();
    globalThis.fetch = (async (input, init) => {
      const url = new URL(String(input));
      assert.equal(url.origin + url.pathname, "https://api.zarz.moe/v2/bootstrap");
      assert.equal(url.searchParams.get("install_id"), "install-id");
      assert.equal(url.searchParams.get("app_version"), "deezer@1.3.5");
      assert.equal(init?.method ?? "GET", "GET");
      const headers = new Headers(init?.headers);
      assert.equal(headers.get("User-Agent"), "SpotiFLAC-Mobile/deezer@1.3.5");
      assert.equal(headers.get("X-Zarz-Signature"), null);
      return Response.json({ session_id: "boot", session_secret: "next", expires_at: expires });
    }) as typeof fetch;
    assert.deepEqual(await bootstrapZarzSession(session), {
      session_id: "boot",
      session_secret: "next",
      expires_at: expires,
    });
  });

  it("treats a bootstrap challenge as verification, not a manufactured grant", async () => {
    const session = sessionFromEnv(envFor("qobuz"), "qobuz")!;
    globalThis.fetch = (async () => Response.json({ challenge_id: "challenge" })) as typeof fetch;
    assert.deepEqual(await bootstrapZarzSession(session), { action: "verify", challenge: true });
  });

  it("skips Zarz when the Durable Object is waiting on a human challenge", async () => {
    const env = {
      EXTENSION_SESSIONS: {
        getByName: () => ({
          health: async () => ({ status: "verification_required" }),
          getSession: async () => {
            throw new Error("must not bootstrap during a live challenge");
          },
        }),
      },
    } as unknown as Env;
    assert.equal(await zarzCanPlay(env, "qobuz"), false);
    assert.equal(await zarzCanPlay(envFor("qobuz"), "qobuz"), true);
  });

  it("plays from fresh Worker secrets even when DO health still says verification_required", async () => {
    const env = {
      ...envFor("tidal"),
      EXTENSION_SESSIONS: {
        getByName: () => ({
          health: async () => ({ status: "verification_required" }),
        }),
      },
    } as unknown as Env;
    assert.equal(await zarzCanPlay(env, "tidal"), true);
  });

  it("keeps other Zarz providers playable when only one is challenged", async () => {
    const env = {
      EXTENSION_SESSIONS: {
        getByName: (provider: string) => ({
          health: async () => ({ status: provider === "qobuz" ? "verification_required" : "ready" }),
        }),
      },
    } as unknown as Env;
    assert.equal(await zarzCanPlay(env, "qobuz"), false);
    assert.equal(await zarzCanPlay(env, "tidal"), true);
    assert.equal(await zarzCanPlay(env, "amazon"), true);
  });
});
