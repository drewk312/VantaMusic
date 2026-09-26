# Dolby Atmos + Hi-Res FLAC — Turnkey Setup

Everything in this repo is already wired to make Atmos and hi-res FLAC work.
There are **two independent routes** — no paid account is required for either
if you use the SpotiFLAC community relays.

Verified end-to-end chain (all code paths tested, 115/115 gateway tests pass):

```
Android ──GET /stream?service=tidal&quality=atmos──▶ Gateway
Gateway ──GET {base}/trackManifests?formats=EAC3_JOC──▶ hifi-api
hifi-api ──▶ verified E-AC-3 JOC .mpd + Widevine drmData
Gateway ──signs /drm/tidal/widevine?token=… ──▶ back to Android
Android ──Media3 MediaItem.DrmConfiguration(WIDEVINE).setLicenseUri(gatewayUrl)
Gateway ──/drm/tidal/widevine ──▶ hifi-api /widevine (appends Tidal token) ──▶ license
Media3 ──plays DASH E-AC-3 JOC through surround passthrough
```

## Route A — SpotiFLAC community relays (NO account, 5-min setup) ⭐ recommended

The live community relays (`tdl-oss`/`qbz-oss`/`amz-oss.spotbye.qzz.io`) serve
**real Atmos (E-AC-3 JOC) and hi-res FLAC with no paid account**. The only gate
is a **one-time human verification** (CAPTCHA) that produces a community session
the gateway uses to sign every request.

```shell
cd workers/music-gateway
node scripts/community-session.mjs   # completes once in your browser, saves env
```

This writes `COMMUNITY_INSTALL_ID`, `COMMUNITY_SESSION_ID`,
`COMMUNITY_SESSION_SECRET`, `COMMUNITY_SESSION_EXPIRES` into `.dev.vars`. Redeploy
with the same keys as secrets for production. The Worker then signs all
`/api/dl` requests automatically for every user — one session serves everyone.

- `src/providers/community-session.ts` — HMAC-SHA256 rolling-key request signing.
- `src/providers/community-signed.ts` — signed `/api/dl` client (atmos/24/16),
  wired into the `stream.ts` cascade for Tidal, Qobuz, and Amazon.
- `scripts/community-session.mjs` — one-time session bootstrap CLI.
- `src/providers/community-crypto.ts` — decrypts the **current** live relay hosts.

Re-run the CLI before `COMMUNITY_SESSION_EXPIRES` to refresh the session.

## Route B — Self-hosted hifi-api (own Tidal HiFi Plus account)

If you prefer full control / guaranteed entitlement (and accept a paid Tidal
HiFi Plus account driving it):

hifi-api (binimum/hifi-api) is a Python/FastAPI Tidal proxy. It holds your Tidal
session; the gateway and app never see the Tidal token.

```shell
git clone https://github.com/binimum/hifi-api
cd hifi-api

# One-time interactive OAuth device login (creates token.json). Use a Tidal
# HiFi Plus account — Atmos and hi-res FLAC need the HiFi Plus entitlement.
pip install -r tidal_auth/requirements.txt
python tidal_auth/tidal_auth.py        # follow the on-screen URL + code flow

# Configure + run (listens on 0.0.0.0:8000)
cp .env.example .env                   # edit as needed
pip install -r requirements.txt
python3 main.py
```

Or via Docker (image is auto-published to ghcr.io):

```shell
docker compose up -d
```

> **Warning:** Tidal is actively blocking accounts that over-use these APIs.
> hifi-api has a built-in queue (one track at a time per IP) specifically to
> reduce ban risk. Keep the instance private to your IPs and avoid switching IPs.

## 2. Expose it over HTTPS

The gateway only accepts public `https://` URLs. Put hifi-api behind a reverse
proxy with a TLS cert (Caddy/Nginx/Traefik), or tunnel it (Cloudflare Tunnel,
ngrok). Example with Caddy:

```caddyfile
tidal.example.com {
    reverse_proxy 127.0.0.1:8000
}
```

## 3. Configure the gateway

`TIDAL_API_URL` (or `HIFI_API_URL`) must point at your instance's public HTTPS
base. Also set `DRM_PROXY_SECRET` (required for Widevine license proxying).

Local dev — `workers/music-gateway/.dev.vars` (gitignored):

```ini
TIDAL_API_URL=https://tidal.example.com
TIDAL_API_KEY=optional-shared-bearer-secret
DRM_PROXY_SECRET=generate-a-long-random-hmac-secret
```

Production:

```shell
npx wrangler secret put TIDAL_API_URL
npx wrangler secret put TIDAL_API_KEY      # optional
npx wrangler secret put DRM_PROXY_SECRET
```

Restart/redeploy the worker.

## 4. Files touched

- `src/providers/hifi-api.ts` — new SpotiFLAC-style Tidal provider: `/api/search`,
  `/api/download-music?track_id=&quality=27|16|9` (hi-res FLAC), and
  `trackManifests` (verified Atmos EAC3_JOC).
- `src/providers/tidal-api.ts` — native Atmos via `trackManifests` (uses `TIDAL_API_URL`).
- `src/lib/drm-proxy.ts` — Widevine license proxy now forwards to `TIDAL_API_URL`
  **or** `HIFI_API_URL` (`/widevine`).
- `src/providers/qobuz-api.ts` — hi-res FLAC via `QOBUZ_COMMUNITY_TOKEN` (format_id 27).
- `src/types.ts`, `wrangler.toml`, `.dev.vars.example` — new env keys documented.

## 5. Verify

```shell
# Health reports Atmos availability when the instance is reachable
curl https://<gateway>/health

# Stream an Atmos file
curl -I "https://<gateway>/stream/<trackId>?service=tidal&quality=atmos"
```

On the device, play an Atmos track and watch logcat:

```shell
adb -s <device> logcat -v time -d -s VANTA_TIDAL_ATMOS
```

You should see the manifest resolve to a `.mpd` E-AC-3 JOC URL and a signed
`/drm/tidal/widevine` license URL.

## Tidal account notes

- Atmos + hi-res FLAC require a **Tidal HiFi Plus** (Premium) account on the
  hifi-api session.
- Tidal blocks by account, not country. Keep the instance on one IP and use the
  built-in queue. Do not share the token.
