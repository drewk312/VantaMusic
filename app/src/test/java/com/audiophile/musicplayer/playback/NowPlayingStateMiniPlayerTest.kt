package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingStateMiniPlayerTest {

    @Test
    fun playingWithTrack_isVisible() {
        val state = NowPlayingState(trackId = "1", isPlaying = true)
        assertTrue(state.shouldShowMiniPlayer())
    }

    @Test
    fun pausedWithTrack_isVisible() {
        val state = NowPlayingState(trackId = "1", isPlaying = false)
        assertTrue(state.shouldShowMiniPlayer())
    }

    @Test
    fun emptySnapshotButQueueCurrent_isVisible() {
        val state = NowPlayingState()
        val snap = QueueSnapshot(
            currentTrack = sampleTrack(),
            queueSize = 1
        )
        assertTrue(state.shouldShowMiniPlayer(snap))
    }

    @Test
    fun resolvingWithQueueItem_isVisible() {
        val state = NowPlayingState(isBuffering = true, bufferedMs = 100)
        val snap = QueueSnapshot(currentTrack = sampleTrack())
        assertTrue(state.shouldShowMiniPlayer(snap))
    }

    @Test
    fun noTrackNoQueue_isHidden() {
        val state = NowPlayingState()
        assertFalse(state.shouldShowMiniPlayer())
        assertFalse(state.shouldShowMiniPlayer(QueueSnapshot()))
    }

    @Test
    fun pendingPlayback_resetsStaleProgressAndUsesSelectedTrackIdentity() {
        val state = NowPlayingState.pendingPlayback(
            track = sampleTrackWithSource(),
            queuePosition = 2,
            queueSize = 5,
            preferredProviderId = "monochrome.tidal",
            preferredExternalTrackId = "tidal-123",
            userQuery = "Genesis"
        )

        assertEquals("42", state.trackId)
        assertEquals("Genesis", state.title)
        assertEquals("Grimes", state.artist)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.bufferedMs)
        assertEquals(237_000L, state.durationMs)
        assertEquals(2, state.queuePosition)
        assertEquals(5, state.queueSize)
        assertEquals("monochrome.tidal", state.preferredProviderId)
        assertEquals("tidal-123", state.preferredExternalTrackId)
        assertEquals("Genesis", state.userQuery)
        assertNull(state.errorMessage)
        assertFalse(state.isPlaying)
        assertTrue(state.isBuffering)
        assertTrue(state.shouldShowMiniPlayer())
    }

    @Test
    fun fromTrackChange_preservesPreferredIdentityAndFavoriteOnSameTrack() {
        val previous = NowPlayingState(
            trackId = "42",
            title = "Genesis",
            artist = "Grimes",
            isrc = "USUM71201234",
            isFavorite = true,
            preferredProviderId = "monochrome.tidal",
            preferredExternalTrackId = "tidal-123",
            userQuery = "Genesis Grimes",
            streamUrl = "https://cdn.example/a.flac"
        )
        val state = NowPlayingState.fromTrackChange(
            track = sampleTrackWithSource(),
            qualityInfo = null,
            queuePosition = 1,
            queueSize = 3,
            previous = previous
        )

        assertEquals("42", state.trackId)
        assertEquals("USUM71201234", state.isrc) // previous ISRC wins on same track
        assertTrue(state.isFavorite)
        assertEquals("monochrome.tidal", state.preferredProviderId)
        assertEquals("tidal-123", state.preferredExternalTrackId)
        assertEquals("Genesis Grimes", state.userQuery)
        assertTrue(state.isBuffering)
        assertFalse(state.isPlaying)
    }

    @Test
    fun fromTrackChange_doesNotLetHigherBitrateAlternateOverwritePreferred() {
        val previous = NowPlayingState(
            trackId = "42",
            title = "Genesis",
            artist = "Grimes",
            preferredProviderId = "monochrome.tidal",
            preferredExternalTrackId = "tidal-123",
            streamUrl = "https://example.test/genesis.flac"
        )
        val competing = UnifiedTrackWithSources(
            track = sampleTrackWithSource().track,
            sources = listOf(
                TrackSource(
                    parentTrackId = 42L,
                    sourceType = SourceType.ADDON,
                    streamUrl = "https://example.test/deezer.flac",
                    bitrate = 9999,
                    externalProviderId = "cloudflare_gateway",
                    externalTrackId = "deezer:1"
                ),
                TrackSource(
                    parentTrackId = 42L,
                    sourceType = SourceType.ADDON,
                    streamUrl = "https://example.test/genesis.flac",
                    bitrate = 1411,
                    externalProviderId = "monochrome.tidal",
                    externalTrackId = "tidal-123"
                )
            )
        )
        val state = NowPlayingState.fromTrackChange(
            track = competing,
            qualityInfo = null,
            queuePosition = 0,
            queueSize = 1,
            previous = previous
        )
        assertEquals("monochrome.tidal", state.preferredProviderId)
        assertEquals("tidal-123", state.preferredExternalTrackId)
    }

    @Test
    fun fromTrackChange_readsPreferredIdentityFromTrackSources() {
        val state = NowPlayingState.fromTrackChange(
            track = sampleTrackWithSource(),
            qualityInfo = null,
            queuePosition = 0,
            queueSize = 1,
            previous = NowPlayingState(trackId = "99", isFavorite = true, userQuery = "other")
        )

        assertEquals("42", state.trackId)
        assertFalse(state.isFavorite)
        assertNull(state.userQuery)
        assertEquals("monochrome.tidal", state.preferredProviderId)
        assertEquals("tidal-123", state.preferredExternalTrackId)
    }

    @Test
    fun normalized_bufferingNeverClaimsPlaying() {
        val state = NowPlayingState(
            trackId = "7",
            isPlaying = true,
            isBuffering = true
        ).normalized()

        assertFalse(state.isPlaying)
        assertTrue(state.isBuffering)
        assertEquals(PlaybackPhase.BUFFERING, state.phase)
    }

    @Test
    fun normalized_errorClearsActivePlaybackFlags() {
        val state = NowPlayingState(
            trackId = "7",
            isPlaying = true,
            isBuffering = true,
            errorMessage = "  Stream failed  "
        ).normalized()

        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals("Stream failed", state.errorMessage)
        assertEquals(PlaybackPhase.ERROR, state.phase)
    }

    private fun sampleTrack() = UnifiedTrackWithSources(
        track = UnifiedTrack(
            trackId = 1L,
            title = "Mr. Know It All",
            artist = "Teddy Swims",
            albumName = null,
            coverArtUrl = null
        ),
        sources = emptyList()
    )

    private fun sampleTrackWithSource() = UnifiedTrackWithSources(
        track = UnifiedTrack(
            trackId = 42L,
            title = "Genesis",
            artist = "Grimes",
            albumName = "Visions",
            coverArtUrl = "https://example.test/genesis.jpg",
            isrc = "US1234567890",
            durationMs = 237_000L
        ),
        sources = listOf(
            TrackSource(
                parentTrackId = 42L,
                sourceType = SourceType.ADDON,
                streamUrl = "https://example.test/genesis.flac",
                bitrate = 1411,
                externalProviderId = "monochrome.tidal",
                externalTrackId = "tidal-123"
            )
        )
    )
}
