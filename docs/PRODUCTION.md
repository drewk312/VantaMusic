# Production deployment

## Required release inputs

- JDK 17, Android SDK 36, NDK 28.2.13676358, and Node.js 22.
- `app/google-services.json` for the production Firebase project.
- An HTTPS station-backend URL.
- An Android upload keystore. Keep its file and passwords outside Git.
- Cloudflare Worker secrets `GATEWAY_API_KEY` and `FIREBASE_PROJECT_ID`.
- Backend secrets `GEMINI_API_KEY` and the same Firebase project ID.

Set Android values with environment variables, Gradle properties, or root
`local.properties`:

```properties
STATION_BACKEND_URL=https://radio.example.com/
VANTA_RELEASE_STORE_FILE=C:/secure/vanta-upload.jks
VANTA_RELEASE_STORE_PASSWORD=replace-me
VANTA_RELEASE_KEY_ALIAS=vanta-upload
VANTA_RELEASE_KEY_PASSWORD=replace-me
```

Verify the inputs and build the signed artifact:

```powershell
.\gradlew.bat :app:verifyProductionConfig :app:bundleRelease
```

The Apple Music sign-in SDK is optional. Add the licensed
`app/libs/musickitauth-release-1.1.2.aar` only when Apple Music authentication
is part of the release; otherwise its callback activity remains disabled.

## Station backend

Copy `station-backend/.env.example` into your secret manager. Production startup
fails closed without `ALLOWED_ORIGINS`, `SESSION_STORE_PATH`,
`FIREBASE_PROJECT_ID`, or `GEMINI_API_KEY`.

Build and run the container with a persistent volume:

```bash
docker build -t vanta-station ./station-backend
docker run --env-file /secure/vanta-station.env -p 3456:3456 \
  -v vanta-station-data:/data vanta-station
```

The JSON snapshot store is suitable for one backend instance. Before horizontal
scaling, replace it with a transactional shared database; do not mount the same
snapshot file into multiple writers.

## Cloudflare music gateway

Review the KV namespace IDs in `workers/music-gateway/wrangler.toml`, then set
secrets and deploy:

```bash
cd workers/music-gateway
npm ci
npx wrangler secret put FIREBASE_PROJECT_ID
npm run typecheck
npm test
npx wrangler deploy --dry-run
npx wrangler deploy
npm run smoke:live
```

Catalog and playback routes are public, rate-limited app services by default;
sync requires Firebase and fails closed. `GATEWAY_API_KEY` is an optional
operator-to-operator control and must not be embedded as a shared secret in the
Android app. If it is configured, every protected catalog/playback request must
present it. Rate limiting requires the configured `RATE_LIMITER` binding. Replace
or contractually approve every configured community streaming upstream before a
public launch; their availability, authorization, and data handling are outside
this repository.

Use `npm run deploy:verified` for normal production deployments. It refuses a
dirty gateway worktree, runs typecheck/tests/dry-run, deploys, and executes the
live New/search/audio-range canary. The `VANTA_ALLOW_DIRTY_GATEWAY=1` override is
reserved for explicit emergency recovery.

## Release gate

CI compiles Android and desktop, runs Android lint/unit tests, builds the release
APK, type-checks both services, runs all service tests, audits production
dependencies, and performs a Worker dry deployment. Before store submission,
also run the signed bundle command above and complete device smoke tests for
sign-in, playback, background audio, Android Auto, imports, and database upgrade.

iOS cannot be release-verified from Windows. Build and test the Xcode target on
macOS, including its database migration path, before describing iOS as supported.
