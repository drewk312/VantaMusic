# VANTA Experience-Layer Improvements

This release focuses on the AI DJ, DSP, Android Auto, Spatial Audio, and Social
features requested in the audit follow-up. The stream-sourcing architecture and
hardcoded Qobuz credentials were intentionally left untouched per user request.

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
