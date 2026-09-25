package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.radio.genome.MusicGenomeVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingStationCandidateRankerTest {

    private val seed = StreamingStationSeed(
        id = "artist_weeknd",
        displayName = "The Weeknd Radio",
        kind = StreamingStationKind.ARTIST,
        seedArtist = "The Weeknd",
        queryPhrases = listOf("The Weeknd songs")
    )

    @Test
    fun isStationJunk_rejectsKaraoke() {
        val result = sampleResult(title = "Blinding Lights (Karaoke Version)", artist = "Karaoke Stars")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(result, seed))
    }

    @Test
    fun isStationJunk_rejectsCountryRadioTitle() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            seedArtists = listOf("Johnny Cash", "Luke Combs"),
            hintKeywords = listOf("country", "americana")
        )
        val junk = sampleResult(title = "Country Radio", artist = "Horseshoes & Hand Grenades")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(junk, seed))
    }

    @Test
    fun isStationJunk_rejectsTopElectronicPlaylist() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            seedArtists = listOf("Johnny Cash"),
            hintKeywords = listOf("country")
        )
        val junk = sampleResult(title = "Run (Top Electronic Songs)", artist = "Some DJ")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(junk, seed))
    }

    @Test
    fun isStationJunk_allowsCountryRoadsWithCountryArtist() {
        val countrySeed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            seedArtists = listOf("John Denver", "Johnny Cash"),
            hintKeywords = listOf("country")
        )
        val result = sampleResult(title = "Take Me Home, Country Roads", artist = "John Denver")
        assertFalse(StreamingStationCandidateRanker.isStationJunk(result, countrySeed))
    }

    @Test
    fun isStationJunk_allowsRealTrack() {
        val result = sampleResult(title = "Blinding Lights", artist = "The Weeknd")
        assertFalse(StreamingStationCandidateRanker.isStationJunk(result, seed))
    }

    @Test
    fun isStationJunk_rejectsTabataWorkoutVariant() {
        val songSeed = StreamingStationSeed(
            id = "song_seed",
            displayName = "Song Radio",
            kind = StreamingStationKind.SONG,
            seedTitle = "Blinding Lights",
            seedArtist = "The Weeknd",
            queryPhrases = listOf("Blinding Lights The Weeknd")
        )
        val junk = sampleResult(title = "Blinding Lights (Tabata)", artist = "Workout Hits")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(junk, songSeed))
    }

    @Test
    fun isStationJunk_rejectsInNStylesPack() {
        val songSeed = StreamingStationSeed(
            id = "song_seed",
            displayName = "Song Radio",
            kind = StreamingStationKind.SONG_SIMILAR,
            seedTitle = "Blinding Lights",
            seedArtist = "The Weeknd",
            queryPhrases = listOf("songs like Blinding Lights")
        )
        val junk = sampleResult(title = "Blinding Lights in 13 Styles", artist = "Style Pack Orchestra")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(junk, songSeed))
    }

    @Test
    fun isStationJunk_rejectsFitnessAndHiitPacks() {
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(title = "Levitating (HIIT Mix)", artist = "Fitness Beats"),
                seed
            )
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(title = "Starboy in 8 Versions", artist = "Cover Collective"),
                seed
            )
        )
    }

    @Test
    fun scoreCandidate_boostsSeedArtist() {
        val onTheme = sampleResult(title = "Starboy", artist = "The Weeknd")
        val offTheme = sampleResult(title = "Starboy", artist = "Random Cover Band")
        val onScore = StreamingStationCandidateRanker.scoreCandidate(onTheme, seed, StreamingStationTasteSignals())
        val offScore = StreamingStationCandidateRanker.scoreCandidate(offTheme, seed, StreamingStationTasteSignals())
        assertTrue(onScore > offScore)
    }

    @Test
    fun rankCandidates_capsArtistWithinSingleBatch() {
        val ranked = StreamingStationCandidateRanker.rankCandidates(
            results = listOf(
                sampleResult(id = "1", title = "Blinding Lights", artist = "The Weeknd"),
                sampleResult(id = "2", title = "Save Your Tears", artist = "The Weeknd"),
                sampleResult(id = "3", title = "Starboy", artist = "The Weeknd"),
                sampleResult(id = "4", title = "Levitating", artist = "Dua Lipa")
            ),
            seed = seed,
            taste = StreamingStationTasteSignals(),
            seenNormKeys = emptySet(),
            artistCounts = emptyMap(),
            maxPerArtist = 2
        )

        assertTrue(ranked.count { it.artist == "The Weeknd" } <= 2)
        assertTrue(ranked.any { it.artist == "Dua Lipa" })
    }

    @Test
    fun isStationJunk_rejectsEdmInCountryStation() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            hintKeywords = listOf("country", "americana"),
            seedArtists = listOf("Johnny Cash", "Dolly Parton")
        )
        val edm = sampleResult(title = "Run (Top Electronic Songs)", artist = "EDM Tribe")
        val radio = sampleResult(title = "Country Radio", artist = "Various Artists")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(edm, seed))
        assertTrue(StreamingStationCandidateRanker.isStationJunk(radio, seed))
    }

    @Test
    fun isStationJunk_allowsCountryArtist() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            hintKeywords = listOf("country", "americana"),
            seedArtists = listOf("Johnny Cash", "Dolly Parton")
        )
        val track = sampleResult(title = "Ring of Fire", artist = "Johnny Cash")
        assertFalse(StreamingStationCandidateRanker.isStationJunk(track, seed))
    }

    @Test
    fun isStationJunk_rejectsWeekendKeywordCollapseFromWeekndSeed() {
        val songSeed = StreamingStationSeed(
            id = "song_seed",
            displayName = "Song Radio",
            kind = StreamingStationKind.SONG,
            seedTitle = "Blinding Lights",
            seedArtist = "The Weeknd",
            queryPhrases = listOf("The Weeknd Blinding Lights")
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(title = "Love on the Weekend", artist = "John Mayer"),
                songSeed
            )
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(title = "The Weekend", artist = "SZA"),
                songSeed
            )
        )
    }

    @Test
    fun scoreCandidate_steeredGenomeReordersCandidates() {
        val moodSeed = StreamingStationSeed(
            id = "mood_focus",
            displayName = "Focus",
            kind = StreamingStationKind.MOOD
        )
        val acoustic = sampleResult(title = "Test Song (Piano Version)", artist = "Unknown Artist")
        val club = sampleResult(title = "Test Song (Club Mix)", artist = "Unknown Artist")
        val taste = StreamingStationTasteSignals()

        val acousticSteer = MusicGenomeVector(
            acousticWeight = 0.95f,
            electronicWeight = 0.05f,
            subBassWeight = 0.10f,
            rockElectricWeight = 0.0f,
            energyLevel = 0.20f,
            danceability = 0.10f,
            productionWarmth = 0.90f
        )
        val danceSteer = MusicGenomeVector(
            acousticWeight = 0.05f,
            electronicWeight = 0.95f,
            subBassWeight = 0.90f,
            danceability = 0.95f,
            energyLevel = 0.90f
        )

        val acousticWithAcousticSteer =
            StreamingStationCandidateRanker.scoreCandidate(acoustic, moodSeed, taste, acousticSteer)
        val acousticWithDanceSteer =
            StreamingStationCandidateRanker.scoreCandidate(acoustic, moodSeed, taste, danceSteer)
        val clubWithAcousticSteer =
            StreamingStationCandidateRanker.scoreCandidate(club, moodSeed, taste, acousticSteer)
        val clubWithDanceSteer =
            StreamingStationCandidateRanker.scoreCandidate(club, moodSeed, taste, danceSteer)

        // A thumb-steered genome must move the SAME candidate in the direction
        // of the liked sound (cross-candidate ranking is confounded by base
        // lexical/junk penalties, so compare each candidate against itself).
        assertTrue(acousticWithAcousticSteer > acousticWithDanceSteer)
        assertTrue(clubWithDanceSteer > clubWithAcousticSteer)
    }

    @Test
    fun scoreCandidate_steerGenomeChangesTheScore() {
        val moodSeed = StreamingStationSeed(
            id = "mood_focus",
            displayName = "Focus",
            kind = StreamingStationKind.MOOD
        )
        val candidate = sampleResult(title = "Test Song (Piano Version)", artist = "Unknown Artist")
        val taste = StreamingStationTasteSignals()

        val steady = StreamingStationCandidateRanker.scoreCandidate(candidate, moodSeed, taste)
        val steered = StreamingStationCandidateRanker.scoreCandidate(
            candidate,
            moodSeed,
            taste,
            MusicGenomeVector(acousticWeight = 0.95f, electronicWeight = 0.05f, energyLevel = 0.2f)
        )

        assertTrue(steady != steered)
    }

    @Test
    fun isStationJunk_rejectsCompilationEssentialsTitle() {
        val moodSeed = StreamingStationSeed(
            id = "mood_party",
            displayName = "Party Radio",
            kind = StreamingStationKind.MOOD,
            hintKeywords = listOf("party", "dance", "funk", "disco")
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(title = "Deep Heat Essentials", artist = "Ibiza Dance Party"),
                moodSeed
            )
        )
    }

    @Test
    fun isStationJunk_rejectsCompilationEssentialsAlbum() {
        val moodSeed = StreamingStationSeed(
            id = "mood_party",
            displayName = "Party Radio",
            kind = StreamingStationKind.MOOD,
            hintKeywords = listOf("party", "dance", "funk", "disco")
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(
                    title = "FTB",
                    artist = "Berox",
                    album = "Heatwave Party Essentials 2026: Big Room"
                ),
                moodSeed
            )
        )
    }

    @Test
    fun isStationJunk_rejectsProdTagJunkTracks() {
        val moodSeed = StreamingStationSeed(
            id = "mood_party",
            displayName = "Party Radio",
            kind = StreamingStationKind.MOOD,
            hintKeywords = listOf("party", "dance", "funk", "disco")
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(title = "party kit", artist = "prod by jxsh"),
                moodSeed
            )
        )
        assertTrue(
            StreamingStationCandidateRanker.isStationJunk(
                sampleResult(title = "You Know (Type Beat)", artist = "Beat Squad"),
                moodSeed
            )
        )
    }

    @Test
    fun scoreCandidate_moodSeedDoesNotLiteralBoostHintKeywords() {
        // A title that merely echoes the mood word ("party") must not receive a
        // lexical bonus. Two near-identical metadata rows differing only in a
        // mood-word vs a neutral-word title must score within a few points of
        // each other (old behaviour: +8 for the literal "party" hit).
        val moodSeed = StreamingStationSeed(
            id = "mood_party",
            displayName = "Party Radio",
            kind = StreamingStationKind.MOOD,
            hintKeywords = listOf("party", "dance", "funk", "disco")
        )
        val taste = StreamingStationTasteSignals()
        val literalTitled = sampleResult(title = "Party Mix 2024", artist = "Random DJ")
        val neutralTitled = sampleResult(title = "Club Mix 2024", artist = "Random DJ")

        val literalScore = StreamingStationCandidateRanker.scoreCandidate(literalTitled, moodSeed, taste)
        val neutralScore = StreamingStationCandidateRanker.scoreCandidate(neutralTitled, moodSeed, taste)

        assertTrue(literalScore - neutralScore < 6.0)
    }

    @Test
    fun scoreCandidate_titleEchoOfMoodWordIsPenalized() {
        val moodSeed = StreamingStationSeed(
            id = "mood_party",
            displayName = "Party Radio",
            kind = StreamingStationKind.MOOD,
            hintKeywords = listOf("party", "dance", "funk", "disco")
        )
        val taste = StreamingStationTasteSignals()

        // The penalty function itself must be applied for mood words and
        // skipped for real tracks with their own identity.
        val echoPenalty = StreamingStationCandidateRanker.titleEchoPenalty(moodSeed, "Party Time")
        assertTrue(echoPenalty < 0.0)
        assertEquals("Levitating", 0.0, StreamingStationCandidateRanker.titleEchoPenalty(moodSeed, "Levitating"), 0.001)

        // And the full score must carry it: a title-echo track cannot outrank a
        // real catalogue track with identical metadata otherwise.
        val base = sampleResult(title = "Levitating", artist = "The Haxans")
        val echo = sampleResult(title = "Party Time", artist = "The Haxans")
        val baseScore = StreamingStationCandidateRanker.scoreCandidate(base, moodSeed, taste)
        val echoScore = StreamingStationCandidateRanker.scoreCandidate(echo, moodSeed, taste)
        assertTrue("echo=$echoScore base=$baseScore", baseScore > echoScore)
    }

    private fun sampleResult(
        title: String,
        artist: String,
        id: String = "123",
        album: String = "Album"
    ): SourceSearchResult =
        SourceSearchResult(
            id = id,
            providerId = "deezer_gateway",
            title = title,
            artist = artist,
            album = album,
            coverSeed = "https://example.com/cover.jpg",
            durationMs = 200_000L,
            isrc = "USRC123",
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = "FLAC"
        )
}
