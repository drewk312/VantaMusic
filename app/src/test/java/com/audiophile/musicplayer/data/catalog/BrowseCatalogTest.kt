package com.audiophile.musicplayer.data.catalog

import org.junit.Assert.*
import org.junit.Test

class BrowseCatalogTest {
    @Test fun everyNonChartCategoryHasAnIndependentFallback() {
        for (category in BrowseCatalog.categories.filter { it.title != "Hits" && it.artists.isEmpty() }) {
            assertTrue(category.title, BrowseCatalog.fallbackQueries(category.title).size >= 4)
        }
    }
    @Test fun aliasesUseTheSameRelevantRecordings() {
        assertEquals(BrowseCatalog.fallbackQueries("80s"), BrowseCatalog.fallbackQueries("1980s"))
        assertEquals(BrowseCatalog.fallbackQueries("Focus"), BrowseCatalog.fallbackQueries("study"))
        assertTrue(BrowseCatalog.fallbackQueries("80s").contains("a-ha Take on Me"))
    }
    @Test fun collectionMatchingUsesWordsAndAliases() {
        assertTrue(BrowseCatalog.collectionMatches("Chill", "Morning Chill"))
        assertTrue(BrowseCatalog.collectionMatches("Focus", "Music to study to"))
        assertFalse(BrowseCatalog.collectionMatches("80s", "2010s hits"))
    }
}
