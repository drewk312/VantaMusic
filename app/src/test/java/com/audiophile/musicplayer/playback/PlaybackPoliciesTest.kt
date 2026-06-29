package com.audiophile.musicplayer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPoliciesTest {

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
}
