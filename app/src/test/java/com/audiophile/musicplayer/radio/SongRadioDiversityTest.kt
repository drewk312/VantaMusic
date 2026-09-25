package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1.5 Radio diversity: Blinding Lights / Weeknd must not collapse into
 * unrelated "Weekend" lexical matches, foreign covers, or Tabata junk.
 */
class SongRadioDiversityTest {

    private val blindingLightsSeed = StreamingStationSeed(
        id = "song_blinding_lights_weeknd",
        displayName = "Blinding Lights Radio",
        kind = StreamingStationKind.SONG,
        seedTitle = "Blinding Lights",
        seedArtist = "The Weeknd",
        queryPhrases = listOf(
            "The Weeknd Blinding Lights",
            "artists like The Weeknd",
            "The Weeknd top songs"
        )
    )

    @Test
    fun weekendKeywordCollapse_fromBlindingLightsSeed_isRejected() {
        val junk = listOf(
            "I Can't Wait Until the Weekend" to "The Soul Notes",
            "Out on the Weekend" to "Joy Oladokun",
            "Half of My Weekend" to "SleepySkies",
            "Love on the Weekend" to "John Mayer",
            "Ready for the Weekend" to "Calvin Harris",
            "Working for the Weekend" to "Loverboy"
        )
        junk.forEach { (title, artist) ->
            assertTrue(
                "Expected rejection for '$title' — $artist",
                StreamingStationCandidateRanker.isStationJunk(sample(title, artist), blindingLightsSeed)
            )
            assertTrue(
                SongRadioRelatedness.isArtistNameCollision("The Weeknd", title, artist)
            )
        }
    }

    @Test
    fun hymnForTheWeekendCover_isRejected() {
        val cover = sample("Hymn for the Weekend (Cover)", artist = "Marvin")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(cover, blindingLightsSeed))
        assertTrue(
            SongRadioRelatedness.isForeignHitCover(
                seedTitle = "Blinding Lights",
                candidateTitle = "Hymn for the Weekend (Cover)",
                candidateArtist = "Marvin",
                seedArtist = "The Weeknd"
            )
        )
        // Even without explicit "cover" marker, Weeknd→Weekend collision rejects it.
        assertTrue(
            SongRadioRelatedness.isArtistNameCollision(
                "The Weeknd",
                "Hymn for the Weekend",
                "Marvin"
            )
        )
    }

    @Test
    fun theWeekendBySza_vs_theWeeknd_isRejected() {
        val collision = sample("The Weekend", artist = "SZA")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(collision, blindingLightsSeed))
        assertTrue(
            SongRadioRelatedness.isArtistNameCollision("The Weeknd", "The Weekend", "SZA")
        )
    }

    @Test
    fun tabataStillRejected() {
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sample("Blinding Lights (Tabata)", "Workout Hits"),
                blindingLightsSeed
            )
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sample("Blinding Lights in 13 Styles", "Style Pack Orchestra"),
                blindingLightsSeed
            )
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sample("Blinding Lights Choreography", "Dance Tutorial Channel"),
                blindingLightsSeed
            )
        )
    }

    @Test
    fun seedAndRelatedWeekndTracks_areAllowed() {
        assertFalse(
            StreamingStationCandidateRanker.isStationJunk(
                sample("Blinding Lights", "The Weeknd"),
                blindingLightsSeed
            )
        )
        assertFalse(
            StreamingStationCandidateRanker.isStationJunk(
                sample("Save Your Tears", "The Weeknd"),
                blindingLightsSeed
            )
        )
        assertFalse(
            StreamingStationCandidateRanker.isStationJunk(
                sample("Levitating", "Dua Lipa"),
                blindingLightsSeed
            )
        )
    }

    @Test
    fun songRadioQueries_avoidSongsLikeTitleTokenExpansion() {
        val queries = StreamingStationQueryPlanner.planInitialQueries(blindingLightsSeed)
        assertTrue(queries.any { it.contains("The Weeknd", ignoreCase = true) })
        assertTrue(queries.any { it.contains("artists like", ignoreCase = true) })
        assertFalse(
            "Must not emit songs-like title OR-token queries",
            queries.any { it.lowercase().startsWith("songs like") }
        )
        assertFalse(
            queries.any {
                val lower = it.lowercase()
                lower == "blinding lights" || lower == "weekend" || lower == "lights"
            }
        )
    }

    @Test
    fun rankCandidates_filtersWeekendDumpAndKeepsRelated() {
        val ranked = StreamingStationCandidateRanker.rankCandidates(
            results = listOf(
                sample("Blinding Lights", "The Weeknd", id = "1"),
                sample("Save Your Tears", "The Weeknd", id = "2"),
                sample("Starboy", "The Weeknd", id = "3"),
                sample("Levitating", "Dua Lipa", id = "4"),
                sample("Don't Start Now", "Dua Lipa", id = "5"),
                sample("Love on the Weekend", "John Mayer", id = "6"),
                sample("The Weekend", "SZA", id = "7"),
                sample("Hymn for the Weekend (Cover)", "Marvin", id = "8"),
                sample("Blinding Lights (Tabata)", "Workout Hits", id = "9")
            ),
            seed = blindingLightsSeed,
            taste = StreamingStationTasteSignals(),
            seenNormKeys = emptySet(),
            artistCounts = emptyMap(),
            maxPerArtist = 2
        )

        assertTrue(ranked.any { it.title == "Blinding Lights" && it.artist == "The Weeknd" })
        assertTrue(ranked.any { it.artist == "Dua Lipa" })
        assertFalse(ranked.any { it.title.contains("Weekend", ignoreCase = true) && it.artist != "The Weeknd" })
        assertFalse(ranked.any { it.title.contains("Tabata", ignoreCase = true) })
        assertTrue(ranked.count { it.artist.equals("The Weeknd", ignoreCase = true) } <= 2)
    }

    @Test
    fun listicleAlbum_isRejected() {
        val listicle = SourceSearchResult(
            id = "listicle",
            providerId = "deezer_gateway",
            title = "Blinding Lights",
            artist = "Various Artists",
            album = "Now That's What I Call Music Hits",
            coverSeed = "https://example.com/cover.jpg",
            durationMs = 200_000L,
            isrc = null,
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = "FLAC"
        )
        assertTrue(StreamingStationCandidateRanker.isStationJunk(listicle, blindingLightsSeed))
    }

    @Test
    fun rankCandidates_rejectsNonSeedArtistPerformingSeedArtistTitle() {
        val ranked = StreamingStationCandidateRanker.rankCandidates(
            results = listOf(
                sample("Save Your Tears", "The Weeknd", id = "w1"),
                sample("Save Your Tears", "Avoid", id = "cover1"),
                sample("I Feel It Coming", "Grandmix", id = "cover2"),
                sample("Levitating", "Dua Lipa", id = "ok1")
            ),
            seed = blindingLightsSeed,
            taste = StreamingStationTasteSignals(),
            seenNormKeys = emptySet(),
            artistCounts = emptyMap(),
            maxPerArtist = 2
        )
        assertFalse(ranked.any { it.artist == "Avoid" })
        assertFalse(ranked.any { it.artist.equals("Grandmix", ignoreCase = true) })
        assertTrue(ranked.any { it.artist == "Dua Lipa" })
        assertTrue(ranked.any { it.artist == "The Weeknd" && it.title == "Save Your Tears" })
    }

    @Test
    fun seedArtistMentionTalkVideo_isRejected() {
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sample(
                    "The Weeknd Reveals How to Write a Hit Song in 8 Minutes!",
                    "SongAholic"
                ),
                blindingLightsSeed
            )
        )
    }

    @Test
    fun weekendAlbumPlaylistCollapse_isRejected() {
        val brunch = SourceSearchResult(
            id = "brunch",
            providerId = "deezer_gateway",
            title = "Harmony Among the Trees",
            artist = "Forest Jazz Cafe",
            album = "Cozy Chic Cafe Jazz for a Weekend Brunch Playlist",
            coverSeed = "https://example.com/cover.jpg",
            durationMs = 200_000L,
            isrc = "TCJPD2556702",
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = "FLAC"
        )
        assertTrue(StreamingStationCandidateRanker.isStationJunk(brunch, blindingLightsSeed))
        assertTrue(
            SongRadioRelatedness.isArtistNameCollision(
                "The Weeknd",
                "Harmony Among the Trees",
                "Forest Jazz Cafe",
                "Cozy Chic Cafe Jazz for a Weekend Brunch Playlist"
            )
        )
    }

    private fun sample(title: String, artist: String, id: String = title.hashCode().toString()): SourceSearchResult =
        SourceSearchResult(
            id = id,
            providerId = "deezer_gateway",
            title = title,
            artist = artist,
            album = "Album",
            coverSeed = "https://example.com/cover.jpg",
            durationMs = 200_000L,
            isrc = "USRC$id",
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = "FLAC"
        )
}
