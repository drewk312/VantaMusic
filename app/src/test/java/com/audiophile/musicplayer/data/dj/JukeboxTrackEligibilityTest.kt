package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JukeboxTrackEligibilityTest {

    @Test
    fun isJunkOrVideoTrack_detectsEdSullivanAndLiveMarkers() {
        assertTrue(JukeboxTrackEligibility.isJunkOrVideoTrack("Stayin' Alive", "Bee Gees on Ed Sullivan", null))
        assertTrue(JukeboxTrackEligibility.isJunkOrVideoTrack("Live at Wembley", "Queen", null))
        assertTrue(JukeboxTrackEligibility.isJunkOrVideoTrack("Tiny Desk Concert", "Artist", null))
        assertTrue(JukeboxTrackEligibility.isJunkOrVideoTrack("Long Jam", "Artist", 11 * 60 * 1000L))
    }

    @Test
    fun isJunkOrVideoTrack_allowsStudioTracks() {
        assertFalse(JukeboxTrackEligibility.isJunkOrVideoTrack("Stayin' Alive", "Bee Gees", 229_000L))
        assertFalse(JukeboxTrackEligibility.isJunkOrVideoTrack("Blinding Lights", "The Weeknd", 200_000L))
    }

    @Test
    fun isVariantArtifact_detectsRemixKaraokeAndSlowed() {
        assertTrue(JukeboxTrackEligibility.isVariantArtifact("Song (Slowed + Reverb)", "Artist", null))
        assertTrue(JukeboxTrackEligibility.isVariantArtifact("Song", "Artist", "Karaoke Version"))
        assertTrue(JukeboxTrackEligibility.isVariantArtifact("Song (Remix)", "Artist", null))
        assertTrue(JukeboxTrackEligibility.isVariantArtifact("Instrumental", "Artist", null))
    }

    @Test
    fun shouldExcludeFromRadioQueue_rejectsJunkAndVariants() {
        val junk = track("Live at Madison Square Garden", "Bee Gees")
        val remix = track("How Deep Is Your Love (Remix)", "Bee Gees")
        val studio = track("How Deep Is Your Love", "Bee Gees")

        assertTrue(JukeboxTrackEligibility.shouldExcludeFromRadioQueue(junk))
        assertTrue(JukeboxTrackEligibility.shouldExcludeFromRadioQueue(remix))
        assertFalse(JukeboxTrackEligibility.shouldExcludeFromRadioQueue(studio))
    }

    private fun track(title: String, artist: String): UnifiedTrackWithSources {
        val unified = UnifiedTrack(
            trackId = title.hashCode().toLong(),
            title = title,
            artist = artist,
            albumName = "Album",
            coverArtUrl = null,
        )
        val source = TrackSource(
            sourceId = unified.trackId,
            parentTrackId = unified.trackId,
            sourceType = SourceType.ADDON,
            streamUrl = "https://example.com/stream",
            bitrate = 1411,
        )
        return UnifiedTrackWithSources(track = unified, sources = listOf(source))
    }
}
