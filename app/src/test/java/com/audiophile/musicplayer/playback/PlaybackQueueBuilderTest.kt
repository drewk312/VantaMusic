package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueBuilderTest {
    @Test
    fun build_prefersAlbumTracksWhenMultipleExist() {
        val start = track(1L, "Song A", "Artist", "Album X")
        val library = listOf(
            start,
            track(2L, "Song B", "Artist", "Album X"),
            track(3L, "Song C", "Other", "Album Y"),
        )

        val queue = PlaybackQueueBuilder.build(start, library)

        assertEquals(2, queue.size)
        assertTrue(queue.all { it.track.albumName == "Album X" })
    }

    @Test
    fun build_fallsBackToLibraryWhenSingleAlbumTrack() {
        val start = track(1L, "Song A", "Artist", "Album X")
        val library = listOf(
            start,
            track(2L, "Song B", "Artist", "Album Y"),
            track(3L, "Song C", "Artist", "Album Z"),
        )

        val queue = PlaybackQueueBuilder.build(start, library)

        assertEquals(3, queue.size)
    }

    private fun track(id: Long, title: String, artist: String, album: String): UnifiedTrackWithSources {
        val track = UnifiedTrack(
            trackId = id,
            title = title,
            artist = artist,
            albumName = album,
            coverArtUrl = null,
        )
        val source = TrackSource(
            sourceId = id,
            parentTrackId = id,
            sourceType = SourceType.LOCAL,
            streamUrl = "content://track/$id",
            bitrate = 1411,
        )
        return UnifiedTrackWithSources(track = track, sources = listOf(source))
    }
}
