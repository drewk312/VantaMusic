package com.audiophile.musicplayer.data.catalog

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import org.junit.Assert.*
import org.junit.Test

class DiscoveryPolicyTest {
    @Test fun genreSyntaxRoutesWholeGenresWithoutTreatingSongsAsTags() {
        assertEquals("bedroom pop", BrowseCatalog.parseGenreQuery("genre:\"bedroom pop\""))
        assertEquals("jazz", BrowseCatalog.parseGenreQuery("GENRE:jazz"))
        assertNull(BrowseCatalog.parseGenreQuery("a song about genre:pop"))
        assertNull(BrowseCatalog.parseGenreQuery("genre:\"pop"))
    }
    @Test fun collectionMatchingUsesWholeWordsAndAliases() {
        assertTrue(BrowseCatalog.collectionMatches("Focus", "Deep Focus"))
        assertTrue(BrowseCatalog.collectionMatches("80s", "The best of the 1980s"))
        assertFalse(BrowseCatalog.collectionMatches("Pop", "Popular podcasts"))
        assertFalse(BrowseCatalog.collectionMatches("Sleep", "Party all night"))
    }
    @Test fun culturalExplorationRequiresOptIn() {
        assertEquals(listOf("Hindi Bollywood"), DiscoveryPolicy.categories("hindi", 2, false))
        assertTrue(DiscoveryPolicy.categories("Hindi Bollywood", 2, true).size > 1)
        assertEquals(listOf("Pop"), DiscoveryPolicy.categories("Pop", 0, true))
    }
    @Test fun dailyOrderIsStableAndExclusionsAndArtistLimitsAreRespected() {
        val tracks = (0..19).map { CanonicalTrack(title = "Song $it", artist = "Artist ${it / 4}") }
        val blocked = setOf(DiscoveryPolicy.key(tracks.first()))
        val first = DiscoveryPolicy.select(tracks, blocked, 42)
        assertEquals(first, DiscoveryPolicy.select(tracks, blocked, 42))
        assertFalse(first.contains(tracks.first()))
        assertTrue(first.groupBy { it.artist }.all { it.value.size <= 2 })
        assertNotEquals(first, DiscoveryPolicy.select(tracks, blocked, 43))
    }
}
