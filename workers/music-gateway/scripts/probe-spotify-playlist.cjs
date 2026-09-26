/**
 * Diagnose Spotify anonymous playlist bootstrap step-by-step.
 * Run: node scripts/probe-spotify-playlist.cjs
 */
const VERSION = 61;
const SECRET = [44,55,47,42,70,40,34,114,76,74,50,111,120,97,75,76,94,102,43,69,49,120,118,80,64,78];
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
const PLAYLIST = process.argv[2] || "37i9dQZF1DXcBWIGoYBM5M";

async function totp(ts = Date.now()) {
  const secret = SECRET.map((v, i) => v ^ ((i % 33) + 9)).join("");
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const counter = new Uint8Array(8);
  new DataView(counter.buffer).setBigUint64(0, BigInt(Math.floor(ts / 30000)));
  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", key, counter));
  const offset = digest[digest.length - 1] & 15;
  return String((new DataView(digest.buffer).getUint32(offset) & 0x7fffffff) % 1_000_000).padStart(6, "0");
}

async function main() {
  const code = await totp();
  console.log("totp", code);

  const tokenUrl = `https://open.spotify.com/api/token?reason=init&productType=web-player&totp=${code}&totpVer=${VERSION}&totpServer=${code}`;
  const tokenRes = await fetch(tokenUrl, { headers: { "User-Agent": UA }, redirect: "follow" });
  console.log("token status", tokenRes.status);
  const tokenBody = await tokenRes.text();
  console.log("token body head", tokenBody.slice(0, 200));
  let token;
  try { token = JSON.parse(tokenBody); } catch { console.log("token not json"); return; }
  if (!token.accessToken) { console.log("no accessToken"); return; }

  const homeRes = await fetch("https://open.spotify.com", { headers: { "User-Agent": UA }, redirect: "follow" });
  console.log("home status", homeRes.status);
  const home = await homeRes.text();
  const encoded = home.match(/<script id="appServerConfig" type="text\/plain">([^<]+)<\/script>/)?.[1];
  console.log("appServerConfig", !!encoded);
  let version = null;
  try { version = JSON.parse(Buffer.from(encoded || "", "base64").toString("utf8")).clientVersion; } catch (e) {
    console.log("version parse fail", e.message);
  }
  console.log("clientVersion", version);

  // Try Web API with access token alone
  const plRes = await fetch(
    `https://api.spotify.com/v1/playlists/${PLAYLIST}/tracks?limit=3&market=US`,
    { headers: { Authorization: `Bearer ${token.accessToken}`, Accept: "application/json", "User-Agent": UA } },
  );
  console.log("webapi tracks status", plRes.status);
  const plBody = await plRes.text();
  console.log("webapi tracks head", plBody.slice(0, 300));

  // Try embed page
  const embedRes = await fetch(`https://open.spotify.com/embed/playlist/${PLAYLIST}`, {
    headers: { "User-Agent": UA, Accept: "text/html" },
    redirect: "follow",
  });
  console.log("embed status", embedRes.status);
  const embedHtml = await embedRes.text();
  const nextData = embedHtml.match(/<script id="__NEXT_DATA__" type="application\/json">([^<]+)<\/script>/)?.[1];
  console.log("embed __NEXT_DATA__", !!nextData, "len", nextData?.length || 0);
  if (nextData) {
    const data = JSON.parse(nextData);
    const props = data?.props?.pageProps;
    console.log("pageProps keys", props ? Object.keys(props) : null);
    const state = props?.state || props?.entity || props;
    console.log("state keys", state && typeof state === "object" ? Object.keys(state).slice(0, 20) : null);
  }

  // Pathfinder getPlaylist / fetchPlaylistContents attempt with access + client token if available
  if (version && token.clientId) {
    const clientRes = await fetch("https://clienttoken.spotify.com/v1/clienttoken", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", "User-Agent": UA },
      body: JSON.stringify({
        client_data: {
          client_version: version,
          client_id: token.clientId,
          js_sdk_data: {
            device_brand: "unknown",
            device_model: "unknown",
            os: "windows",
            os_version: "NT 10.0",
            device_id: crypto.randomUUID().replace(/-/g, ""),
            device_type: "computer",
          },
        },
      }),
    });
    console.log("clienttoken status", clientRes.status);
    const client = await clientRes.json();
    const clientTok = client?.granted_token?.token;
    console.log("clienttoken ok", !!clientTok);

    if (clientTok) {
      const hashes = [
        // known community hashes for playlist fetches (try a few)
        { name: "fetchPlaylist", hash: "e24679dbe54cd2bcda629f1d70315925afa9726f8f961c46a1b5267933b1a387" },
        { name: "getPlaylist", hash: "87c764520c72f3c3d3aae908ec01d58a4e1f1f5c3c6d8b0f8d6c8a2e0b1a3c4d" },
        { name: "playlistV2", hash: "91d4dcdac5b4a1f9b8c0c6c0c6d0e0f0a1b2c3d4e5f60718293a4b5c6d7e8f90" },
      ];
      for (const entry of hashes) {
        const pf = await fetch("https://api-partner.spotify.com/pathfinder/v2/query", {
          method: "POST",
          headers: {
            Authorization: `Bearer ${token.accessToken}`,
            "Client-Token": clientTok,
            "Spotify-App-Version": version,
            "Content-Type": "application/json",
            "User-Agent": UA,
          },
          body: JSON.stringify({
            variables: { uri: `spotify:playlist:${PLAYLIST}`, offset: 0, limit: 5 },
            operationName: entry.name,
            extensions: { persistedQuery: { version: 1, sha256Hash: entry.hash } },
          }),
        });
        const body = await pf.text();
        console.log("pathfinder", entry.name, pf.status, body.slice(0, 180));
      }
    }
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
