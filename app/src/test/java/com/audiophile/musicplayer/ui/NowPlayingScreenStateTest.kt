package com.audiophile.musicplayer.ui

import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.ui.nowplaying.resolveNowPlayingDisplaySnapshot
import com.audiophile.musicplayer.ui.nowplaying.shouldShowQualityChip
import com.audiophile.musicplayer.ui.nowplaying.titleMaxLinesForNowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingScreenStateTest {

    @Test
    fun displaySnapshot_usesNowPlayingIdentity_notPulseOrModeState() {
        val snapshot = resolveNowPlayingDisplaySnapshot(
            nowPlayingState = NowPlayingState(
                trackId = "42",
                title = "Denver",
                artist = "Jack Harlow",
                album = "Come Home The Kids Miss You"
            ),
            enhancedMetadata = EnhancedMetadata(
                title = "Wrong Title",
                artist = "Wrong Artist",
                album = "Wrong Album"
            ),
            lyricsTrackId = "42"
        )

        assertEquals("Denver", snapshot.title)
        assertEquals("Jack Harlow", snapshot.artist)
        assertEquals("Come Home The Kids Miss You", snapshot.album)
    }

    @Test
    fun displaySnapshot_blocksLyrics_whenTrackIdsDoNotMatch() {
        val snapshot = resolveNowPlayingDisplaySnapshot(
            nowPlayingState = NowPlayingState(trackId = "100", title = "Song", artist = "Artist"),
            enhancedMetadata = null,
            lyricsTrackId = "99"
        )

        assertFalse(snapshot.canDisplayLyrics)
    }

    @Test
    fun displaySnapshot_allowsLyrics_whenTrackIdsMatch() {
        val snapshot = resolveNowPlayingDisplaySnapshot(
            nowPlayingState = NowPlayingState(trackId = "100", title = "Song", artist = "Artist"),
            enhancedMetadata = null,
            lyricsTrackId = "100"
        )

        assertTrue(snapshot.canDisplayLyrics)
    }

    @Test
    fun qualityChipHidden_whenLabelMissing() {
        assertFalse(shouldShowQualityChip(null))
        assertFalse(
            shouldShowQualityChip(
                VantaQualityInfo.fromSource(
                    bitrate = null,
                    quality = null,
                    mime = null,
                    status = null
                )
            )
        )
    }

    @Test
    fun titleUsesTwoLinesMax() {
        assertEquals(2, titleMaxLinesForNowPlaying())
    }
}
