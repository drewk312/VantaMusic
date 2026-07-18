package com.audiophile.musicplayer.data.source

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.local.toPlayableQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFlowEligibilityTest {

    @Test
    fun directLocalSongDataMaterializesIntoPlayableQueueItem() {
        val queueItem = LocalSongEntity(
            id = 7L,
            title = "Local Track",
            artist = "Artist",
            sourceType = SourceType.LOCAL,
            streamUrl = "file:///music/local-track.mp3"
        ).toPlayableQueueItem()

        assertNotNull(queueItem)
        val playable = requireNotNull(queueItem)
        assertTrue(playable.sourceValidityStatus().canEnterPlaybackFlow())
        assertEquals(SearchItemStatus.LOCAL_PLAYABLE, playable.sourceValidityStatus())
    }

    @Test
    fun metadataOnlyLocalRowsDropOutOfPlaylistQueueMaterialization() {
        val playable = LocalSongEntity(
            id = 1L,
            title = "Playable",
            artist = "Artist",
            sourceType = SourceType.LOCAL,
            streamUrl = "file:///music/playable.mp3"
        )
        val metadataOnly = LocalSongEntity(
            id = 2L,
            title = "Metadata Only",
            artist = "Artist",
            sourceType = SourceType.ADDON,
            streamUrl = null
        )

        val playableQueue = listOf(playable, metadataOnly)
            .mapNotNull { song -> song.toPlayableQueueItem()?.takeIf { it.sourceValidityStatus().canEnterPlaybackFlow() } }

        assertEquals(1, playableQueue.size)
        assertEquals("Playable", playableQueue.single().track.title)
        assertNull(metadataOnly.toPlayableQueueItem())
    }

    @Test
    fun metadataOnlyAndUnavailableSearchStatusesCannotEnterPlaybackFlow() {
        assertFalse(SearchItemStatus.METADATA_ONLY.canEnterPlaybackFlow())
        assertFalse(SearchItemStatus.ENHANCED.canEnterPlaybackFlow())
        assertFalse(SearchItemStatus.STREAM_UNAVAILABLE.canEnterPlaybackFlow())
        assertFalse(SearchItemStatus.NOT_PLAYABLE.canEnterPlaybackFlow())
    }

    @Test
    fun albumAndArtistPlaybackGatingUsesSourceValidityStatus() {
        val playableTrack = UnifiedTrackWithSources(
            track = UnifiedTrack(trackId = 10L, title = "Playable", artist = "Artist", albumName = "Album", coverArtUrl = null),
            sources = listOf(
                TrackSource(
                    parentTrackId = 10L,
                    sourceType = SourceType.ADDON,
                    streamUrl = "https://streams.example/song.mp3",
                    bitrate = 320
                )
            )
        )
        val metadataOnlyTrack = UnifiedTrackWithSources(
            track = UnifiedTrack(trackId = 11L, title = "Metadata", artist = "Artist", albumName = "Album", coverArtUrl = null),
            sources = emptyList()
        )

        assertTrue(playableTrack.sourceValidityStatus().canEnterPlaybackFlow())
        assertFalse(metadataOnlyTrack.sourceValidityStatus().canEnterPlaybackFlow())
    }
}
