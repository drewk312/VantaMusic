package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.StreamDrmConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPoliciesTest {

    @Test
    fun resolvedStreamHandoff_acceptsExactPersistedProviderBoundUrl() {
        val now = 1_700_000_000_000L
        val stream = resolvedStream(expiresAt = now + 120_000L)
        val track = playbackTrack(streamUrl = stream.streamUrl, expiresAt = now + 120_000L)

        assertTrue(PlaybackPolicies.canUseResolvedStreamHandoff(track, stream, now))
    }

    @Test
    fun resolvedStreamHandoff_rejectsForeignExpiredOrProviderMismatchedUrl() {
        val now = 1_700_000_000_000L
        val track = playbackTrack(
            streamUrl = "https://audio.example.com/right.flac",
            expiresAt = now + 120_000L,
        )

        assertFalse(
            PlaybackPolicies.canUseResolvedStreamHandoff(
                track,
                resolvedStream(streamUrl = "https://audio.example.com/other.flac", expiresAt = now + 120_000L),
                now,
            )
        )
        assertFalse(
            PlaybackPolicies.canUseResolvedStreamHandoff(
                track,
                resolvedStream(streamUrl = "https://audio.example.com/right.flac", expiresAt = now + 5_000L),
                now,
            )
        )
        assertFalse(
            PlaybackPolicies.canUseResolvedStreamHandoff(
                track,
                resolvedStream(
                    streamUrl = "https://audio.example.com/right.flac",
                    expiresAt = now + 120_000L,
                    providerId = "youtube_music",
                ),
                now,
            )
        )
    }

    @Test
    fun canAttemptStreamRecovery_trueWhenBlankUrlAndValidIdentity() {
        assertTrue(
            PlaybackPolicies.canAttemptStreamRecovery(
                providerId = "deezer",
                externalTrackId = "12345",
                streamUrl = "",
                expired = false,
            )
        )
    }

    @Test
    fun canAttemptStreamRecovery_trueWhenExpiredAndValidIdentity() {
        assertTrue(
            PlaybackPolicies.canAttemptStreamRecovery(
                providerId = "qobuz",
                externalTrackId = "998877",
                streamUrl = "https://example.com/old",
                expired = true,
            )
        )
    }

    @Test
    fun canAttemptStreamRecovery_falseWithoutProviderIdentity() {
        assertFalse(
            PlaybackPolicies.canAttemptStreamRecovery(
                providerId = null,
                externalTrackId = "12345",
                streamUrl = "",
                expired = true,
            )
        )
        assertFalse(
            PlaybackPolicies.canAttemptStreamRecovery(
                providerId = "deezer",
                externalTrackId = "",
                streamUrl = "",
                expired = true,
            )
        )
    }

    @Test
    fun shouldAutoPlayAfterResolve_pauseWinsOverWasPlaying() {
        assertFalse(PlaybackPolicies.shouldAutoPlayAfterResolve(userPauseRequested = true, wasPlayingBefore = true))
        assertFalse(PlaybackPolicies.shouldAutoPlayAfterResolve(userPauseRequested = true, wasPlayingBefore = false))
        assertTrue(PlaybackPolicies.shouldAutoPlayAfterResolve(userPauseRequested = false, wasPlayingBefore = true))
        assertFalse(PlaybackPolicies.shouldAutoPlayAfterResolve(userPauseRequested = false, wasPlayingBefore = false))
    }

    @Test
    fun shouldResumeAfterRenewal_pauseWins() {
        assertFalse(PlaybackPolicies.shouldResumeAfterRenewal(userPauseRequested = true, playWhenReady = true))
        assertTrue(PlaybackPolicies.shouldResumeAfterRenewal(userPauseRequested = false, playWhenReady = true))
    }

    @Test
    fun trackIdToResolveForPlay_recoversColdOrFailedPlayer() {
        assertEquals(
            42L,
            PlaybackPolicies.trackIdToResolveForPlay(
                hasMediaItems = false,
                hasPlayerError = false,
                activeTrackId = null,
                stateTrackId = "42",
            )
        )
        assertEquals(
            7L,
            PlaybackPolicies.trackIdToResolveForPlay(
                hasMediaItems = true,
                hasPlayerError = true,
                activeTrackId = 7L,
                stateTrackId = "42",
            )
        )
        assertNull(
            PlaybackPolicies.trackIdToResolveForPlay(
                hasMediaItems = true,
                hasPlayerError = false,
                activeTrackId = 7L,
                stateTrackId = "42",
            )
        )
    }

    @Test
    fun isStreamExpired_detectsPastExpiryTimestamp() {
        val now = 1_700_000_000_000L
        assertTrue(
            PlaybackPolicies.isStreamExpired(
                expiresAtMs = now - 60_000L,
                streamUrl = "",
                nowMs = now,
            )
        )
        assertFalse(
            PlaybackPolicies.isStreamExpired(
                expiresAtMs = now + 60_000L,
                streamUrl = "",
                nowMs = now,
            )
        )
    }

    @Test
    fun isStreamExpired_treatsTemporaryCdnWithoutStoredExpiryAsExpired() {
        assertTrue(
            PlaybackPolicies.isStreamExpired(
                expiresAtMs = null,
                streamUrl = "https://streaming-qobuz-std.akamaized.net/file?uid=1&eid=2&fmt=7",
                nowMs = 1_700_000_000_000L,
            )
        )
    }

    @Test
    fun isStreamExpired_rejectsStaleNeteasePathUrl() {
        val issued = java.time.OffsetDateTime.of(
            2026, 9, 12, 9, 18, 31, 0, java.time.ZoneOffset.ofHours(8)
        ).toInstant().toEpochMilli()
        assertTrue(
            PlaybackPolicies.isStreamExpired(
                expiresAtMs = null,
                streamUrl = "https://m801.music.126.net/20260912091831/c62bb141.flac",
                nowMs = issued + 2L * 24L * 60L * 60L * 1000L,
            )
        )
    }

    @Test
    fun isStreamExpired_allowsFreshNeteasePathUrl() {
        val issued = java.time.OffsetDateTime.of(
            2026, 9, 12, 9, 18, 31, 0, java.time.ZoneOffset.ofHours(8)
        ).toInstant().toEpochMilli()
        assertFalse(
            PlaybackPolicies.isStreamExpired(
                expiresAtMs = null,
                streamUrl = "https://m801.music.126.net/20260912091831/c62bb141.flac",
                nowMs = issued + 60_000L,
            )
        )
    }

    @Test
    fun drmPolicy_acceptsSignedHttpsWidevineAndRejectsUnsafeLicenseUrls() {
        val valid = PlaybackDrmPolicy.validated(
            StreamDrmConfiguration(
                scheme = "widevine",
                licenseUrl = "https://vanta.example/drm/tidal/widevine?token=signed",
                licenseRequestHeaders = mapOf("X-Session" to "abc"),
            )
        )
        assertEquals("widevine", valid?.scheme)
        assertEquals("abc", valid?.licenseRequestHeaders?.get("X-Session"))
        assertNull(
            PlaybackDrmPolicy.validated(
                StreamDrmConfiguration("widevine", "http://127.0.0.1/license")
            )
        )
        assertNull(
            PlaybackDrmPolicy.validated(
                StreamDrmConfiguration("fairplay", "https://vanta.example/license")
            )
        )
    }

    private fun playbackTrack(streamUrl: String, expiresAt: Long?) = UnifiedTrackWithSources(
        track = UnifiedTrack(
            trackId = 1L,
            title = "Midnight City",
            artist = "M83",
            albumName = "Hurry Up, We're Dreaming",
            coverArtUrl = null,
        ),
        sources = listOf(
            TrackSource(
                sourceId = 1L,
                parentTrackId = 1L,
                sourceType = SourceType.ADDON,
                streamUrl = streamUrl,
                bitrate = 1411,
                externalProviderId = "cloudflare_gateway",
                externalTrackId = "deezer:123",
                expiresAtMs = expiresAt,
            )
        ),
    )

    private fun resolvedStream(
        streamUrl: String = "https://audio.example.com/right.flac",
        expiresAt: Long?,
        providerId: String = "cloudflare_gateway",
    ) = ResolvedStream(
        streamUrl = streamUrl,
        bitrateKbps = 1411,
        mimeType = "audio/flac",
        expiresAt = expiresAt,
        providerId = providerId,
    )
}
