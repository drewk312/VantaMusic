import { it } from "node:test";
import assert from "node:assert/strict";
import { searchSpotifyExtension, spotifyExtensionTracks } from "./spotify-extension";

const payload = { data: { searchV2: { tracksV2: { items: [{ item: { data: {
  uri: "spotify:track:6dOtVTDdiauQNBQEDOtlAB", name: "BIRDS OF A FEATHER", artists: { items: [{ profile: { name: "Billie Eilish" } }] },
  duration: { totalMilliseconds: 210000 }, albumOfTrack: { name: "HIT ME HARD AND SOFT", coverArt: { sources: [{ url: "https://i.scdn.co/image/cover", width: 640 }] } },
} } }] } } } };

it("maps Spotify web metadata without claiming an audio format", () => {
  const result = spotifyExtensionTracks(payload);
  assert.equal(result[0].provider, "spotify"); assert.equal(result[0].duration, 210);
  assert.equal(result[0].id, "6dOtVTDdiauQNBQEDOtlAB"); assert.equal(result[0].format, undefined);
  assert.equal(result[0].isDolbyAtmos, false);
});

it("bootstraps anonymous extension metadata and keeps tokens out of results", async (t) => {
  const paths: string[] = [];
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request, init?: RequestInit) => {
    const u = new URL(String(input)); paths.push(u.pathname);
    if (u.pathname === "/api/token") {
      assert.match(u.searchParams.get("totp")!, /^\d{6}$/); assert.equal(u.searchParams.get("totpVer"), "61");
      return Response.json({ accessToken: "anonymous-access", clientId: "public-client", accessTokenExpirationTimestampMs: Date.now() + 600000 });
    }
    if (u.pathname === "/") return new Response(`<script id="appServerConfig" type="text/plain">${btoa(JSON.stringify({ clientVersion: "test-version" }))}</script>`);
    if (u.pathname === "/v1/clienttoken") return Response.json({ response_type: "RESPONSE_GRANTED_TOKEN_RESPONSE", granted_token: { token: "anonymous-client-token", expires_after_seconds: 600 } });
    assert.equal(u.pathname, "/pathfinder/v2/query");
    assert.equal(new Headers(init?.headers).get("Authorization"), "Bearer anonymous-access");
    assert.equal(new Headers(init?.headers).get("Client-Token"), "anonymous-client-token");
    return Response.json(payload);
  });
  const result = await searchSpotifyExtension("Billie Eilish", 10);
  assert.equal(result.length, 1); assert.equal(paths.length, 4);
  assert.ok(!JSON.stringify(result).includes("anonymous-access"));
});

it("imports public playlist tracks via anonymous web session when client creds are absent", async (t) => {
  const { importSpotifyPlaylistExtension } = await import("./spotify-extension");
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const u = new URL(String(input));
    if (u.pathname === "/api/token") {
      return Response.json({ accessToken: "anonymous-access", clientId: "public-client", accessTokenExpirationTimestampMs: Date.now() + 600000 });
    }
    if (u.pathname === "/") return new Response(`<script id="appServerConfig" type="text/plain">${btoa(JSON.stringify({ clientVersion: "test-version" }))}</script>`);
    if (u.pathname === "/v1/clienttoken") return Response.json({ response_type: "RESPONSE_GRANTED_TOKEN_RESPONSE", granted_token: { token: "anonymous-client-token", expires_after_seconds: 600 } });
    if (u.pathname === "/v1/playlists/37i9dQZF1DXcBWIGoYBM5M" && !u.pathname.endsWith("/tracks")) {
      return Response.json({ name: "Today's Top Hits", images: [{ url: "https://i.scdn.co/image/cover" }] });
    }
    if (u.pathname === "/v1/playlists/37i9dQZF1DXcBWIGoYBM5M/tracks") {
      return Response.json({
        items: [{
          track: {
            id: "6dOtVTDdiauQNBQEDOtlAB",
            name: "BIRDS OF A FEATHER",
            artists: [{ name: "Billie Eilish" }],
            album: { name: "HIT ME HARD AND SOFT", images: [{ url: "https://i.scdn.co/image/cover" }] },
            duration_ms: 210000,
            external_ids: { isrc: "USUM72400001" },
          },
        }],
        next: null,
      });
    }
    return new Response("unexpected", { status: 500 });
  });
  const result = await importSpotifyPlaylistExtension("37i9dQZF1DXcBWIGoYBM5M", 50);
  assert.equal(result.name, "Today's Top Hits");
  assert.equal(result.tracks.length, 1);
  assert.equal(result.tracks[0].title, "BIRDS OF A FEATHER");
  assert.equal(result.tracks[0].isrc, "USUM72400001");
  assert.ok(!JSON.stringify(result).includes("anonymous-access"));
});

it("imports public playlist tracks from the embed page without any Spotify token", async (t) => {
  const { importSpotifyPlaylistEmbed } = await import("./spotify-extension");
  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const u = new URL(String(input));
    assert.equal(u.pathname, "/embed/playlist/37i9dQZF1DXcBWIGoYBM5M");
    const nextData = {
      props: {
        pageProps: {
          state: {
            data: {
              entity: {
                name: "Today's Top Hits",
                coverArt: { sources: [{ url: "https://i.scdn.co/image/cover" }] },
                trackList: [
                  {
                    uri: "spotify:track:3h5T5JypYU7huFiVYhv1dr",
                    title: "BbY WOW",
                    subtitle: "KAROL G, Judeline",
                    duration: 225834,
                  },
                ],
              },
            },
          },
        },
      },
    };
    return new Response(
      `<html><script id="__NEXT_DATA__" type="application/json">${JSON.stringify(nextData)}</script></html>`,
      { status: 200 },
    );
  });
  const result = await importSpotifyPlaylistEmbed("37i9dQZF1DXcBWIGoYBM5M", 50);
  assert.equal(result.name, "Today's Top Hits");
  assert.equal(result.tracks.length, 1);
  assert.equal(result.tracks[0].id, "3h5T5JypYU7huFiVYhv1dr");
  assert.equal(result.tracks[0].title, "BbY WOW");
  assert.equal(result.tracks[0].artist, "KAROL G, Judeline");
  assert.equal(result.tracks[0].duration, 226);
});

it("continues fetching via Web API pagination when embed has 100 tracks and maxTracks > 100", async (t) => {
  const { importSpotifyPlaylistExtension } = await import("./spotify-extension");
  const embedTracks = Array.from({ length: 100 }, (_, i) => ({
    uri: `spotify:track:3h5T5JypYU7huFiVYhv1${String(i).padStart(2, "0")}`,
    title: `Embed Track ${i}`,
    subtitle: "Artist",
    duration: 180000,
  }));

  t.mock.method(globalThis, "fetch", async (input: string | URL | Request) => {
    const urlStr = String(input);
    const u = new URL(urlStr);
    if (u.pathname.startsWith("/embed/playlist/")) {
      const nextData = {
        props: {
          pageProps: {
            state: {
              data: {
                entity: {
                  name: "Large Playlist",
                  coverArt: { sources: [{ url: "https://i.scdn.co/image/cover" }] },
                  trackList: embedTracks,
                },
              },
            },
          },
        },
      };
      return new Response(
        `<html><script id="__NEXT_DATA__" type="application/json">${JSON.stringify(nextData)}</script></html>`,
        { status: 200 },
      );
    }
    if (urlStr.includes("api/token")) {
      return Response.json({
        accessToken: "mock-token",
        clientId: "mock-client-id",
        accessTokenExpirationTimestampMs: Date.now() + 3600000,
      });
    }
    if (urlStr === "https://open.spotify.com" || urlStr === "https://open.spotify.com/") {
      const b64 = Buffer.from(JSON.stringify({ clientVersion: "1.2.3" })).toString("base64");
      return new Response(`<html><script id="appServerConfig" type="text/plain">${b64}</script></html>`);
    }
    if (urlStr.includes("clienttoken.spotify.com")) {
      return Response.json({
        response_type: "RESPONSE_GRANTED_TOKEN_RESPONSE",
        granted_token: { token: "mock-granted-token", expires_after_seconds: 3600 },
      });
    }
    if (u.pathname.endsWith("/tracks")) {
      assert.equal(u.searchParams.get("offset"), "100");
      return Response.json({
        items: [{
          track: {
            id: "3h5T5JypYU7huFiVYhv199",
            name: "Paginated Track 101",
            artists: [{ name: "Artist 101" }],
            album: { name: "Album 101", images: [{ url: "https://i.scdn.co/image/101" }] },
            duration_ms: 200000,
            external_ids: { isrc: "USUM10100001" },
          },
        }],
        next: null,
      });
    }
    return new Response("unexpected", { status: 500 });
  });

  const result = await importSpotifyPlaylistExtension("37i9dQZF1DXcBWIGoYBM5M", 200);
  assert.equal(result.name, "Large Playlist");
  assert.equal(result.tracks.length, 101);
  assert.equal(result.tracks[0].title, "Embed Track 0");
  assert.equal(result.tracks[100].title, "Paginated Track 101");
  assert.equal(result.tracks[100].isrc, "USUM10100001");
});

