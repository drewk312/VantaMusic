# VANTA Music

A premium Android music player with AI-powered discovery, multi-source streaming, and a personal library.

## Project Structure

```
MusicPlayer/
├── app/                    # Android app (Jetpack Compose + Media3/ExoPlayer + Room)
├── shared/                 # Kotlin multiplatform shared module
├── station-backend/        # Node/TypeScript radio/station backend
└── workers/music-gateway/  # Cloudflare Worker gateway for search/stream resolution
```

## Build Requirements

- Android SDK 36 (compileSdk), minSdk 26
- JDK 17+
- Node.js 22+ (for service tooling)
- Wrangler (for music-gateway)

## Configuration

Set the backend URL through the `STATION_BACKEND_URL` environment variable, a
Gradle property, or the root `local.properties` file:

```properties
STATION_BACKEND_URL=https://your-station-backend.example.com/
```

The optional Apple MusicKit authentication AAR may be placed at
`app/libs/musickitauth-release-1.1.2.aar`. Builds remain reproducible without it;
the authentication callback activity is disabled when the licensed SDK is absent.

Other credentials (TorBox, Real-Debrid, LLM keys, Apple Music token) are configured at runtime through the app's Settings screen and stored in encrypted SharedPreferences.

See [`docs/PRODUCTION.md`](docs/PRODUCTION.md) for signing, secret management,
container deployment, Worker deployment, and the final release gate.

## Build & Test

```bash
# Android app
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug

# Station backend
cd station-backend
npm ci
npm run typecheck
npm test
npm run build
npm start

# Music gateway
cd workers/music-gateway
npm ci
npm run typecheck
npm test
npx wrangler deploy --dry-run
```

## Architecture

- **UI Layer**: Jetpack Compose screens with a single `UiState` per screen.
- **Playback Layer**: Media3 `PlaybackService` + `QueueAwarePlayer` wrapping ExoPlayer.
- **Data Layer**: Room database, repositories, and a `SourceRegistry` that routes searches and streams to configured providers.
- **Search/Identity**: `UnifiedSearchEngine` parses intent, scores candidates, and `VantaMusicBrain` resolves catalog identity.
- **Station Backend**: Express JSON-RPC service for AI-curated radio stations.

## Status

Phase 1 of the quality rebuild is complete. See `CHANGES.md` for details and `VANTA_FULL_REBUILD_PROMPT.md` for the full plan.
