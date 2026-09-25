# VANTA Music — Production Readiness & Security Audit

**Date:** 2026-07-16  
**Scope:** Android app (`app/`), station backend (`station-backend/`), Cloudflare music gateway (`workers/music-gateway/`), and supporting config  
**Method:** Static code review, dependency checks, targeted build/test execution, manual threat modeling  
**Auditor:** Codex  
**Verdict:** ❌ **NOT production-ready.** There are P0 legal/compliance and security blockers that must be resolved before any public release, store submission, or production deployment.

## Remediation Update — 2026-07-18

- Exported playback-service start commands now require a random process-local capability; normal MediaSession and Android Auto binding remains available.
- Signed/direct stream URLs are no longer written to Android logs or MediaItem IDs; logs retain only the destination host when useful.
- Worker stream results are centrally restricted to public HTTPS destinations before caching, returning, or redirecting.
- The hardcoded Qobuz app ID/secret fallback was removed. Automatic credential scraping and unofficial/community playback sources remain and are still launch blockers.
- Gateway catalog/playback auth documentation now matches the client contract: public rate-limited routes by default, Firebase-authenticated sync, and no extractable shared mobile secret.
- These mitigations do not change the P0 verdict while unofficial playback sources remain configured.

---

## Executive Summary

The code is well-organized and many security guardrails are in place (Firebase token verification, encrypted credential storage, fail-closed config checks, URL allow-listing for link resolution, MediaSession caller validation). However, the product is built around a stream-resolution pipeline that relies on third-party/community gateways, scraped Qobuz credentials, and obfuscated upstream endpoints to obtain playable audio. That architecture is almost certainly a copyright / terms-of-service violation and creates enormous legal liability. It also produces several concrete security defects (open playback intents, unguarded stream-URL logging, open redirects from resolved URLs, and hardcoded/obfuscated secrets in source).

**Do not ship until the infringing stream sources are removed and the P1 items below are fixed.**

---

## What I Verified

| Check | Result | Notes |
|---|---|---|
| `station-backend` `npm run typecheck` | ✅ Pass | |
| `station-backend` `npm test` | ✅ 58 passed (8 files) | |
| `music-gateway` `npm run typecheck` | ✅ Pass | |
| `music-gateway` `npm test` | ✅ Pass | |
| `music-gateway` `npm audit --audit-level=moderate` | ✅ 0 vulnerabilities | |
| Android `:app:compileDebugKotlin` / `:app:lintDebug` | ⚠️ Could not run | Gradle wrapper needs to download the distribution; sandbox network is blocked. Must be run on the build host. |
| `station-backend` `npm audit` | ⚠️ Could not run | Network access to npm registry was blocked by sandbox policy. Must be run on the build host. |
| Hardcoded secrets scan | ⚠️ Found | Qobuz default app id/secret and encrypted community blobs in gateway source. |

---

## Positive Security Controls

- Android OAuth tokens are stored in `EncryptedSharedPreferences` (`AES256_GCM`) with a fail-closed plaintext fallback (`FailClosedSharedPreferences`).
- Gateway API key comparison is constant-time (`constantTimeEqual`).
- Both the station backend and the gateway implement full Firebase ID-token verification (RS256, `aud`, `iss`, `exp`, `iat`, kid/JWK cache).
- Gateway protected routes fail closed when `GATEWAY_API_KEY` or `FIREBASE_PROJECT_ID` are missing in production.
- Station backend refuses to start in production without `ALLOWED_ORIGINS`, `SESSION_STORE_PATH`, `FIREBASE_PROJECT_ID`, and `GEMINI_API_KEY`.
- `/resolve?url=` on the gateway validates that supplied links are HTTPS, on a known music-host allow-list, and contain no userinfo.
- JSON-RPC batch size and string parameter length are capped; parameter types are validated.
- Android `android:allowBackup="false"` is set and the base network-security config blocks cleartext traffic.
- `PlaybackService` uses a UAMP-style `PackageValidator` with pinned caller signatures for MediaSession/MediaBrowser callers.
- Session store writes the JSON snapshot atomically with `0o600` permissions.

---

## Findings

| ID | Severity | Finding | Location | Risk | Recommendation |
|---|---|---|---|---|---|
| **F-01** | **P0 — Blocker** | Infringing/unauthorized stream sourcing | `workers/music-gateway/src/providers/*`, `wrangler.toml`, `community-crypto.ts`, `qobuz-api.ts` | Copyright / ToS violations, DMCA exposure, app-store rejection, operator liability, potential criminal liability in some jurisdictions. | Remove all community/relay/"spotiflac"/GDStudio/WJHE/MusicDL sources. Build playback only on licensed APIs (Apple MusicKit, Spotify Web API metadata, user-owned files, legitimate radio, etc.). Obtain legal review before launch. |
| **F-02** | **P0 — Blocker** | Hardcoded Qobuz app id & secret | `workers/music-gateway/src/providers/qobuz-api.ts:8-9` | Credential leak; Qobuz ToS violation; key rotation impossible without an app update. | Delete the default credentials from source. If Qobuz integration is retained through a proper partnership, store credentials as Wrangler secrets, rotate them, and never commit them. |
| **F-03** | **P1 — High** | Exported `PlaybackService` accepts arbitrary direct-stream URLs | `app/src/main/java/com/audiophile/musicplayer/playback/PlaybackService.kt:812+`, `ACTION_PLAY_DIRECT_URL` | Any app on the device can start the exported service and make VANTA play an attacker-chosen audio URL, use the app as an audio proxy, or exfil device/activity signals. | Make internal actions private. Split the exported `MediaLibraryService` from the internal command service, or protect internal actions with a `signature`-level custom permission. Validate caller UID for sensitive actions. |
| **F-04** | **P1 — High** | Stream URLs logged in release code without debug guard | `MainViewModel.kt:1340`, `MainViewModel.kt:2433`, `PlayerController.kt:318`, `PlaybackService.kt:849/1089`, `StreamResolver.kt:105` | Resolved stream URLs may contain session tokens, signed URLs, or user/account info. Truncation still leaks host/path/query and may expose pre-signed credentials to logcat/dumps. | Remove these logs or guard every one with `if (BuildConfig.DEBUG)`. Add a ProGuard rule to strip `Log.d/e` in release if logging must stay. |
| **F-05** | **P1 — High** | Gateway redirects to arbitrary resolved URLs | `workers/music-gateway/src/index.ts` (`/play`, `/stream`), `public-stream.ts:extractStreamUrl`, `stream.ts:normalizeStreamPayload` | Open redirect / mixed content. A malicious or compromised upstream can return `http://` or any `https://` URL, leading to phishing, SSRF against internal listeners, or cleartext playback. | Validate every resolved `stream.url`: require `https://`, reject `http://`, optionally enforce a host allow-list. Do not blindly 302 to third-party URLs. |
| **F-06** | **P1 — High** | Local Firebase config present and not ignored | `workers/music-gateway/.dev.vars` (real `FIREBASE_PROJECT_ID`), `app/google-services.json` (real project id/number) | Accidental commit risk. Project IDs and Firebase config can be used for recon / quota attacks. | Add `.dev.vars` and all `google-services.json` to `.gitignore`. Never commit them. If they were ever committed, rotate the Firebase project. Keep production Firebase configs in a secret manager. |
| **F-07** | **P2 — Medium** | Infrastructure identifiers and infringing upstreams committed | `workers/music-gateway/wrangler.toml` | KV namespace IDs and community gateway URLs are in version control. Aids recon and makes the infringing architecture visible. | Move sensitive/identifying values to Wrangler secrets or CI-injected vars. Audit git history and rotate KV namespaces if needed. |
| **F-08** | **P2 — Medium** | LLM prompt injection via unsanitized user input | `station-backend/src/services/gemini.ts` | User seed text, keywords, recent history, and taste signals are interpolated directly into Gemini prompts. Can alter station curation, inject instructions, or bias output. | Sanitize/escape user strings, use structured JSON inputs where possible, and validate/instruct the model to ignore embedded instructions. |
| **F-09** | **P2 — Medium** | Global IP rate limit, weak per-user controls | `station-backend/src/server.ts` (`express-rate-limit`) | 120 req/min is global per IP. Easy to exhaust model quota or abuse by rotating IPs. | Add per-Firebase-user rate limits and per-method quotas. Remove the legacy user-token fallback path. |
| **F-10** | **P2 — Medium** | Cleartext dev domains + no certificate pinning | `app/src/main/res/xml/network_security_config.xml` | Localhost/10.0.2.2 cleartext permitted; production pinning is a TODO. | Remove dev domains from release builds. Implement certificate pinning for production backends and add backup pins. |
| **F-11** | **P2 — Medium** | Unauthenticated info disclosure | `workers/music-gateway/src/index.ts` `/health`, `/status` | Reveals provider list, version, health, and hints about configured upstreams. | Protect `/status` with `X-Api-Key`. Keep `/health` minimal or rate-limit it. |
| **F-12** | **P3 — Low** | Hardcoded ElevenLabs default voice | `station-backend/src/services/elevenlabs.ts:7` | Not a secret, but inflexible. | Make the voice ID configurable via env. |
| **F-13** | **P3 — Low** | Sensitive Android permissions | `app/src/main/AndroidManifest.xml` | `RECORD_AUDIO` and `ACCESS_FINE_LOCATION` are declared. Ensure runtime permission flow and a privacy policy justify them. | Document why each permission is required; request at runtime with clear user rationale. |
| **F-14** | **P3 — Low** | Client IP may be wrong behind proxy | `station-backend/src/server.ts` (`req.ip`) | Rate limits and logs use `req.ip`. Behind a load balancer this will be the LB IP. | Configure Express `trust proxy` when running behind Cloudflare/reverse proxy, or parse `CF-Connecting-IP` / `X-Forwarded-For`. |
| **F-15** | **P3 — Low** | File-based session store not horizontally scalable | `station-backend/src/services/sessionStore.ts` | Multiple container replicas could corrupt the snapshot file. | Replace the JSON snapshot with a transactional database before scaling out, as noted in `docs/PRODUCTION.md`. |

---

## Detailed Pentest Notes

### 1. Stream sourcing is the dominant risk

The gateway tries, in order:

- Custom upstreams (`QOBUZ_STREAM_UPSTREAM`, `TIDAL_STREAM_UPSTREAM`, etc.).
- A generic `MUSICDL_BASE_URL`.
- Public/community mirrors (`streamViaCommunityGateway`, `streamViaEncryptedCommunity`, `streamViaWjhe`, `streamViaAmazonSpotbye`, `streamViaMusicDlPublic`).
- Hardcoded/obfuscated endpoints in `community-crypto.ts` and `community-api-key.ts`.

This is not a licensing gray area — it is an architecture designed to obtain paid-catalog audio from unauthorized relays. Before production, the entire stream layer must be rebuilt on legitimate sources.

### 2. Android service exported surface

`PlaybackService` is `exported="true"` (required for Android Auto/MediaBrowser). Its `onStartCommand` dispatches internal actions such as `ACTION_PLAY_DIRECT_URL`. There is no caller check, no custom permission, and no separation between the public MediaBrowser interface and the internal playback-command interface. Any third-party app with knowledge of the component can start playback of an arbitrary URL.

### 3. Logging

Several `Log.d` / `Log.w` statements print resolved stream URLs or direct URLs. They are not wrapped in `BuildConfig.DEBUG` and the current ProGuard rules do not strip them. Even truncated, these logs can expose pre-signed CDN URLs and provider-specific identifiers.

### 4. Gateway redirect validation

`extractStreamUrl` in `public-stream.ts` accepts any string starting with `http`, including `http://`. `normalizeStreamPayload` in `stream.ts` does the same. The gateway then issues a 302 to that URL. This allows open redirects and cleartext streams.

### 5. Secret hygiene

- `qobuz-api.ts` embeds `DEFAULT_APP_ID` and `DEFAULT_APP_SECRET`.
- `community-api-key.ts` / `community-crypto.ts` embed encrypted blobs that are trivially decrypted at runtime — security through obscurity, not real protection.
- `.dev.vars` and `app/google-services.json` contain a real Firebase project identifier and are sitting in the working tree but are not tracked by git. Add them to `.gitignore` immediately to prevent accidental commits.

### 6. Backend auth & rate limiting

The station backend correctly requires partner tokens for protected RPC methods and validates declared parameter types. The global rate limit is a useful first layer but is not tied to the authenticated Firebase user, so quota abuse is still possible. The development-only `demo` user path is correctly disabled in production when `NODE_ENV=production`, but the existence of `DEMO_PASSWORD` and legacy user tokens increases attack surface in misconfigured deployments.

### 7. Android network config

The base config correctly blocks cleartext, but a `domain-config` explicitly allows it for `localhost`, `127.0.0.1`, and `10.0.2.2`. That is useful for emulators but should not ship in a release APK. Certificate pinning is present only as a commented-out TODO.

---

## Remediation Roadmap

### Before any public build

1. **Remove infringing sources** (F-01). Replace with licensed integrations or user-owned media.
2. **Rotate and remove hardcoded credentials** (F-02, F-06).
3. **Fix exported service abuse** (F-03).
4. **Remove or guard all stream-URL logs** (F-04).
5. **Validate resolved stream URLs** (F-05): require `https://` and a host allow-list.

### Before production deployment

6. Add `.dev.vars` / `google-services.json` to `.gitignore`; move infra IDs out of `wrangler.toml` (F-06, F-07).
7. Sanitize LLM inputs (F-08).
8. Add per-user rate limits and tighten backend quotas (F-09).
9. Remove dev cleartext domains and implement cert pinning (F-10).
10. Protect `/status` (F-11).
11. Run `:app:lintDebugRelease`, `:app:testDebugUnitTest`, and a signed release build locally. Run `npm audit` on `station-backend` with network access.
12. Have a lawyer review the music-sourcing model and privacy policy.

---

## Bottom Line

VANTA has solid engineering fundamentals and good local security primitives, but its current stream-sourcing model is a legal and compliance time bomb, and there are concrete Android/gateway vulnerabilities that an attacker can exploit today. **Do not call this production-ready until F-01 through F-05 are fixed.**
