package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioCandidateGateTest {

    @Test
    fun junkFilterRejectsLiveRecordings() {
        assertTrue("Studio track should be accepted", 
            !JukeboxTrackEligibility.isJunkOrVideoTrack("Denver", "Jack Harlow", 158_000L))
        
        assertTrue("Live track should be rejected", 
            JukeboxTrackEligibility.isJunkOrVideoTrack("Denver (Live at Red Rocks)", "Jack Harlow", 160_000L))
    }

    @Test
    fun radioFilterRejectsKaraoke() {
        assertTrue("Karaoke should be excluded",
            JukeboxTrackEligibility.shouldExcludeFromRadioQueue("Denver (Karaoke Version)", "Jack Harlow", 158_000L))
    }

    @Test
    fun playbackGateRejectsProviderMetadataRows() {
        val metadataOnly = track(
            id = 1L,
            title = "Blinding Lights",
            artist = "The Weeknd",
            sourceType = SourceType.SPOTIFY,
            streamUrl = "https://provider.example/metadata"
        )

        assertTrue(PlaybackIdentityGate.verify(metadataOnly) is GateVerdict.Failed)
    }

    @Test
    fun verifiedQueueKeepsPlayableTracksAndCapsArtistRepeats() {
        val intent = RadioStationIntent(
            seedType = RadioSeedType.ARTIST,
            seedTrackArtist = "The Weeknd",
            maxPerArtist = 2,
            targetQueueSize = 10
        )

        val (queue, rejections) = RadioBrain.buildVerifiedQueue(
            tracks = listOf(
                track(1L, "Blinding Lights", "The Weeknd"),
                track(2L, "Starboy", "The Weeknd"),
                track(3L, "Save Your Tears", "The Weeknd"),
                track(4L, "Levitating", "Dua Lipa"),
                track(5L, "Into You", "Ariana Grande"),
                track(6L, "Provider Row", "Metadata Only", sourceType = SourceType.SPOTIFY)
            ),
            intent = intent
        )

        assertEquals(4, queue.size)
        assertEquals(2, queue.count { it.track.artist == "The Weeknd" })
        assertFalse(queue.any { it.track.title == "Provider Row" })
        assertTrue(rejections.any { it.startsWith("artist_cap:the weeknd") })
        assertTrue(rejections.any { it.contains("not_playable_music_candidate") })
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        sourceType: SourceType = SourceType.ADDON,
        streamUrl: String = "https://streams.example/$id.mp3"
    ): UnifiedTrackWithSources {
        val unified = UnifiedTrack(
            trackId = id,
            title = title,
            artist = artist,
            albumName = "Album",
            coverArtUrl = null,
            durationMs = 200_000L
        )
        val source = TrackSource(
            sourceId = id,
            parentTrackId = id,
            sourceType = sourceType,
            streamUrl = streamUrl,
            bitrate = 320
        )
        return UnifiedTrackWithSources(track = unified, sources = listOf(source))
    }
}
