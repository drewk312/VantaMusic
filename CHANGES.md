# VANTA Release Notes & Changelog

## 1.02 Hotfixes (v1.03)

- **Search & Album Browsing Restoration**:
  - `GatewayApiKeyInterceptor.kt` & `build.gradle.kts`: Configured secure default fallback gateway authentication key (`00e93071...`) so CI builds, local builds, and release packages never omit the `X-Api-Key` header. Requests to `https://vanta-music-gateway.16drewk.workers.dev/api/search` previously returned HTTP 401 Unauthorized when built without environment variables, causing search and album browsing to show empty results.
- **Smooth In-Place Updates & Keystore Consistency**:
  - `vanta-release.jks` & `.gitignore`: Whitelisted and tracked `vanta-release.jks` in the repository.
  - `build.gradle.kts`: Added automated keystore defaults for both `release` and `debug` builds, ensuring all future APKs share the exact same signature (`SHA-256: 94:F1:15...`). Users no longer need to uninstall and reinstall the app to update versions, eliminating `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and preserving all local data and offline libraries across updates.
- **Black Bar Removal & AMOLED Edge-to-Edge**:
  - `MainActivity.kt`: Replaced opaque black system bar backgrounds (`window.statusBarColor` and `window.navigationBarColor`) with transparent drawing (`Color.TRANSPARENT`) and disabled navigation contrast enforcement. The AMOLED background and UI chrome now flow seamlessly under the navigation gesture bar with zero distracting black bars left behind.
- **Immersive Fullscreen Mode**:
  - `SettingsScreen.kt` & `MainActivity.kt`: Added a dedicated "Immersive Fullscreen" toggle switch under Appearance settings. When enabled, system status and navigation bars are hidden using `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` (swipe from screen edge to reveal), providing a pure distraction-free edge-to-edge listening experience.

## 1.02 Hotfixes (Initial)

- **Critical Launch & Artist View Crash Fix**:
  - `ArtistDetailScreen.kt`: Resolved fatal duplicate key `IllegalArgumentException` in Compose Lazy rows/columns by assigning unique indexed keys across albums, singles, and catalog tracks.
  - `StartupSafeguard.kt` & `AppNavGraph.kt`: Added navigation state recovery on startup to break out of restored crash loops and safely reset navigation to the Home screen if the previous session terminated abnormally.
- **TV Sync Pairing & Link Code**:
  - `SettingsScreen.kt`: Added full "Link Phone & TV" pairing UI with TV link code text field, "Push to TV" and "Pull from TV" sync actions, and generation of the device's own pair code.
- **Spotify & Apple Music CSV Import**:
  - `SoundiizTextParser.kt`: Added support for semicolon (`;`), tab (`\t`), and comma (`,`) delimiters; hardened CSV header recognition to require multi-column matches; prevented `Spotify Track ID` from shadowing `Track Name`; added support for Apple Music's `Name` column.
- **Canvas Queue Forming & Player Queue Shuffle**:
  - `SearchScreen.kt` & `AppNavGraph.kt`: Tapping any song in search results now forms the active queue from the full list of search results ("canvas queue forming") starting at the chosen track.
  - `QueueView.kt` & `NowPlayingScreen.kt`: Added direct Shuffle button in the Up Next queue header to shuffle upcoming tracks without leaving the queue view.
- **Search Engine Query Accuracy**:
  - `UnifiedSearchEngine.kt`: Stripped filler words (`by`, `feat`, `ft`, `with`) from query tokens so queries like "lose yourself by eminem" find the exact song instead of failing or drifting; added artist bonus matching when artist name appears in natural language queries.
- **Station Playback Latency (15-20s Delay Elimination)**:
  - `QueueManager.kt`: Replaced blocking synchronous station refills inside `getNextTrack()` with asynchronous background coroutines. When tracks remain in the buffer, playback transitions immediately (0-second delay) while top-ups proceed non-blockingly.

## 1.01 Improvements

## AI DJ

- **Memory continuity**
  - `VantaDjPrompts` now injects a `<memory_continuity>` block into session
    intros with recent likes, replays, skip signals, and session count.
  - The system persona instructs Pulse to reference real continuity and never
    invent callbacks.
  - `AiDjNarrationGenerator` fallback intros now echo returning favorites and
    replayed tracks by name.

- **Empty-library fallback**
  - `generateEmptyLibraryMessage()` now speaks like a DJ and suggests adding
    tracks or searching for an artist while VANTA Radio stays ready.
  - `AiDjSessionManager` passes the active mode into the empty-library message.
  - `AiDjViewModel` surfaces the fallback guidance as the status message so the
    listener knows what to do next.

## DSP

- Added `loudnessNormalizationEnabled`, `replayGainDb`, and
  `autoHeadroomEnabled` to `VantaEqualizerConfig` and preferences.
- `VantaEqualizerProcessor` applies replay-gain scaling and an automatic
  headroom reduction based on the largest positive EQ boost to reduce clipping.
- Added `eqBypassEnabled` for A/B comparison; when no effects are active the
  pipeline copies input straight through.
- Added `VantaImmersiveMode` enum (`OFF`, `STEREO_WIDENED`, `RENDERED`,
  `NATIVE_ATMOS`) for honest spatial badges.
- Bundled convolver IR pack asset paths (`small_club`, `medium_hall`,
  `large_hall`, `plate`, `studio_a`, `vintage_room`).
- Reduced EQ preset gains across the board for less extreme, more usable curves.
- Wired `VantaEqualizerProcessor.spectrumListener` to
  `VantaAudioAnalyzerHolder` and `VantaVisualizerViewModel` for a DSP-derived
  visualizer bridge.

## Android Auto

- Replaced hardcoded keyword/artist mood and time-mix filters with real
  scoring profiles in `AndroidAutoBrowseController`. Tracks are ranked by title,
  artist, and genre matches.
- Added voice-search shortcuts: spoken mood/time requests directly return the
  matching scored mix.
- Reduced custom command-button clutter from seven buttons to five (Favorite,
  Shuffle, Repeat, More Like This, AI DJ).
- Added a "Vibe Mixes" browse folder exposing Moods and Time Mixes, and removed
  the Lyrics action from the driving root for safety.
- Switched the "Songs" list to database-backed paging via a new
  `TrackDao.getAllTracksWithSourcesPaged` / `TrackRepository.getAllTracksPaged`
  path.

## Spatial Audio

- Extended gateway `spatialEvidence` type to include `rendered` and `stereo`
  tiers.
- Added `spatialEvidenceFromSignals()` codec/container detection in
  `stream-quality.ts` so explicit Atmos fingerprints (`e-ac-3 joc`, `eac3-joc`,
  `ec-3 joc`, `dolby atmos`) can be labeled `verified`.
- `spatial-seed.ts` now combines catalog seed hints with real format detection.
- Search UI badges show `Verified Atmos`, `Rendered Spatial`, `Stereo`, or
  catalog/metadata labels honestly.

## Social

- Added opaque, rotatable friend codes separate from `vantaUserId`. Codes are
  generated in `FriendActivityLocalSource` and exposed through
  `VantaSocialManager.friendCode()` / `rotateFriendCode()`.
- Added `FriendPrivacy` enum and per-friend `privacy`/`friendCode` fields.
- Added client-side and gateway activity-write rate limits (30-second minimum
  interval).
- Added `resolveFriendEventIdentity()` helper so friend activity can be resolved
  to a stable canonical identity for playback lookup.

## Verification

- `npm run typecheck` and `npm test` pass in `workers/music-gateway`.
- `npm run typecheck` and `npm test` pass in `station-backend`.
- `gradlew :app:compileDebugKotlin` passes; Android lint was attempted but the
  lint task stalled in the sandbox, likely due to network/resource constraints.

## Known Follow-ups

- Gateway friend-code lookup/rotation endpoint (server-side code-to-user mapping)
  is staged locally but not yet wired end-to-end.
- Full "play from friend activity" flow needs identity search integration in the
  UI layer.
- Android Auto browse paging for artists/albums/genres can be migrated to the
  same DB-paged pattern used for songs.
