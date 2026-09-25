# VANTA music source setup

VANTA uses one public Cloudflare gateway and private, authenticated source adapters. Provider credentials stay server-side; they must never be embedded in the Android application.

## Source roles

- Qobuz: licensed FLAC/hi-res through `QOBUZ_AUTH_TOKEN`, either directly in the gateway or through `vps-backend`.
- Tidal: lossless and Dolby Atmos through a private hifi-api-compatible service at `TIDAL_API_URL`.
- Deezer, Amazon, and Pandora: supported by the gateway contract through provider-specific upstreams or an authenticated `OPERATOR_BACKEND_URL` implementation.
- Apple/iTunes: catalog metadata only, not a playback source.
- Community adapters: optional last-resort stereo fallbacks. They are disabled by default in `vps-backend` and never count as licensed or verified Atmos.

## Qobuz operator backend

Copy `vps-backend/.env.example` to a private environment file, set a long random `VANTA_BACKEND_API_KEY`, and add a valid `QOBUZ_AUTH_TOKEN` from the operator's licensed account. Then build and run `vps-backend/Dockerfile` behind HTTPS.

Configure the gateway with:

```text
OPERATOR_BACKEND_URL=https://private-backend.example
OPERATOR_BACKEND_PROVIDERS=qobuz
```

Store `OPERATOR_BACKEND_API_KEY` as a Cloudflare Worker secret with the same value as `VANTA_BACKEND_API_KEY`.

## Native Tidal Dolby Atmos

Run a private hifi-api-compatible service with a valid Tidal account/session and HTTPS. Configure the gateway's non-secret `TIDAL_API_URL`, then store these as Worker secrets:

```text
TIDAL_API_KEY=<private gateway-to-hifi-api key>
DRM_PROXY_SECRET=<independent long random HMAC secret>
```

The gateway requests `EAC3_JOC` DASH, rejects a manifest that does not identify that codec, and issues a short-lived signed `/drm/tidal/widevine` URL. Android validates that URL and passes it to Media3 as a Widevine `DrmConfiguration`.

Atmos still depends on all of the following being true:

1. The Tidal account and region are entitled to the Atmos mix.
2. The selected track has an actual `EAC3_JOC` presentation.
3. The private Tidal service has a current authenticated session.
4. The Android device and active audio route can decode or pass through that presentation.

When any condition fails, VANTA reports Atmos unavailable instead of labeling stereo audio as Atmos.

## Private multi-provider contract

An operator backend implements:

```text
GET /v1/streams/{provider}/{trackId}?quality=24|16|atmos
Authorization: Bearer <operator key>
X-Api-Key: <operator key>
```

The response includes `url`, `provider`, `format`/`codec`, `mimeType`, quality metadata, expiry, and optionally `drm: { "scheme": "widevine" }`. The gateway validates public HTTPS media URLs and independently verifies Atmos codec evidence.
