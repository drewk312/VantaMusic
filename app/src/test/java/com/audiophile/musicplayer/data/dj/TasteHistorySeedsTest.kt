package com.audiophile.musicplayer.data.dj

import org.junit.Assert.assertEquals
import org.junit.Test

class TasteHistorySeedsTest {
    @Test
    fun listeningHistoryBeatsEmptySessionDefaults() {
        val artists = mergeTasteArtists(
            sessionScores = emptyMap(),
            historyArtists = listOf("Metallica", "Queens of the Stone Age"),
            favoriteArtists = emptyList(),
            skipCounts = emptyMap()
        )
        assertEquals(listOf("Metallica", "Queens of the Stone Age"), artists)
    }

    @Test
    fun heavilySkippedHistoryArtistsAreDropped() {
        val artists = mergeTasteArtists(
            sessionScores = mapOf("Radiohead" to 9),
            historyArtists = listOf("Nickelback"),
            favoriteArtists = listOf("Radiohead"),
            skipCounts = mapOf("Nickelback" to 4)
        )
        assertEquals(listOf("Radiohead"), artists)
    }
}
