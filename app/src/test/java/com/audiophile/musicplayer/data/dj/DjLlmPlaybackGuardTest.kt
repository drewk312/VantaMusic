package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjLlmPlaybackGuardTest {

    @Test
    fun canEnterPlaybackFromResolvedCandidate_rejectsNullAndJunk() {
        assertFalse(DjLlmPlaybackGuard.canEnterPlaybackFromResolvedCandidate(null))
        assertFalse(DjLlmPlaybackGuard.canEnterPlaybackFromResolvedCandidate(track("Live at Wembley", "Queen")))
        assertFalse(DjLlmPlaybackGuard.canEnterPlaybackFromResolvedCandidate(track("Song (Karaoke Version)", "Artist")))
    }

    @Test
    fun canEnterPlaybackFromResolvedCandidate_allowsPlayableStudioTrack() {
        assertTrue(DjLlmPlaybackGuard.canEnterPlaybackFromResolvedCandidate(track("Blinding Lights", "The Weeknd")))
    }

    private fun track(title: String, artist: String): UnifiedTrackWithSources {
        val unified = UnifiedTrack(
            trackId = (title + artist).hashCode().toLong(),
            title = title,
            artist = artist,
            albumName = "Album",
            coverArtUrl = null,
        )
        val source = TrackSource(
            sourceId = 1L,
            parentTrackId = unified.trackId,
            sourceType = SourceType.ADDON,
            streamUrl = "https://example.com/stream.mp3",
            bitrate = 1411,
            externalProviderId = "deezer",
            externalTrackId = "12345",
        )
        return UnifiedTrackWithSources(track = unified, sources = listOf(source))
    }
}
