package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastMetadataFilterTest {

    @Test
    fun isBroadcastLikeMetadata_detectsExplicitRadioMarkers() {
        assertTrue(isBroadcastLikeMetadata("Best Radio Pop Hits", "Various", null))
        assertTrue(isBroadcastLikeMetadata("Chill Mix", "Internet Radio", null))
        assertTrue(isBroadcastLikeMetadata("24/7 Lo-Fi", "Station", null))
        assertTrue(isBroadcastLikeMetadata("Live Stream Session", "DJ", "Nonstop Mix"))
    }

    @Test
    fun isBroadcastLikeMetadata_allowsNormalSongs() {
        assertFalse(isBroadcastLikeMetadata("Blinding Lights", "The Weeknd", "After Hours"))
        assertFalse(isBroadcastLikeMetadata("Radioactive", "Imagine Dragons", "Night Visions"))
        assertFalse(isBroadcastLikeMetadata("On the Radio", "Regina Spektor", "Begin to Hope"))
    }

    @Test
    fun isLikelyContinuousStream_rejectsLongRuntime() {
        assertTrue(isLikelyContinuousStream(21 * 60 * 1000L))
        assertTrue(isLikelyContinuousStream(60 * 60 * 1000L))
    }

    @Test
    fun isLikelyContinuousStream_allowsNormalSongLength() {
        assertFalse(isLikelyContinuousStream(null))
        assertFalse(isLikelyContinuousStream(0L))
        assertFalse(isLikelyContinuousStream(3 * 60 * 1000L))
        assertFalse(isLikelyContinuousStream(19 * 60 * 1000L))
    }

    @Test
    fun isLikelyMusicTrack_filtersBroadcastSearchResults() {
        val broadcast = SourceSearchResult(
            id = "1",
            providerId = "test",
            title = "24/7 Pop Hits Radio",
            artist = "Live Stream",
            album = null,
            coverSeed = "",
            durationMs = 3_600_000L,
            isrc = null,
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = null
        )
        assertFalse(broadcast.isLikelyMusicTrack())

        val song = SourceSearchResult(
            id = "2",
            providerId = "test",
            title = "Levitating",
            artist = "Dua Lipa",
            album = "Future Nostalgia",
            coverSeed = "",
            durationMs = 203_000L,
            isrc = "USRC12345678",
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = null
        )
        assertTrue(song.isLikelyMusicTrack())
    }

    @Test
    fun isLikelyMusicTrack_allowsOfficialMusicVideoMetadata() {
        val officialVideo = SourceSearchResult(
            id = "yt-piano-man",
            providerId = "youtube_music",
            title = "Billy Joel - Piano Man (Official HD Video)",
            artist = "Billy Joel",
            album = null,
            coverSeed = "",
            durationMs = 342_000L,
            isrc = null,
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = "Audio Stream"
        )

        assertTrue(officialVideo.isLikelyMusicTrack())
    }
}
