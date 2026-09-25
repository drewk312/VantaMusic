package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIdentityScorerTest {

    private fun track(
        title: String,
        artist: String,
        album: String? = null,
        isrc: String? = null,
        externalTrackId: String? = null
    ) = CanonicalTrack(
        title = title,
        artist = artist,
        album = album,
        isrc = isrc,
        externalTrackId = externalTrackId,
        sourcePriority = 5
    )

    @Test
    fun catalogIntentHandlesMisspellingsWithoutPerSongProviderRules() {
        val cases = listOf(
            Triple("wonder all", "Wonderwall", "Oasis"),
            Triple("hotel calfornia", "Hotel California", "Eagles"),
            Triple("bohemian rapsody", "Bohemian Rhapsody", "Queen"),
            Triple("blinding lites", "Blinding Lights", "The Weeknd"),
            Triple("smels like teen spirit", "Smells Like Teen Spirit", "Nirvana"),
            Triple("shape of yuo", "Shape of You", "Ed Sheeran")
        )

        cases.forEach { (query, title, artist) ->
            assertEquals(query, UnifiedSearchEngine.providerQuery(query))
            val response = UnifiedSearchEngine.process(
                query,
                listOf(
                    track(title, artist, album = "Original Album"),
                    track(query, if (query == "bohemian rapsody") "BOHEMIAN RAPSODY" else "Exact Text Collision", album = "Obscure Match"),
                    track(title, "Karaoke Cover Band", album = "Tribute Collection"),
                    track("Something Else", "Another Artist")
                )
            )
            assertEquals(query, title, response.topResult?.title)
            assertEquals(query, artist, response.topResult?.artist)
        }
    }

    @Test
    fun providerLookupPreservesEveryQueryAndSearchDoesNotInventTracks() {
        assertEquals("blinding lights", UnifiedSearchEngine.providerQuery("blinding lights"))
        assertEquals("blinding lights piano", UnifiedSearchEngine.providerQuery("blinding lights piano"))
        assertEquals("unknown deep cut", UnifiedSearchEngine.providerQuery("unknown deep cut"))

        val empty = UnifiedSearchEngine.process("bad guy", emptyList())
        assertEquals(null, empty.topResult)
        assertTrue(empty.songs.isEmpty())
    }

    @Test
    fun resolvedIdentityRemovesCoverArtistsAndAlbumsFromRails() {
        val response = UnifiedSearchEngine.process(
            "wonder all",
            listOf(
                track("Wonderwall", "Oasis", album = "(What's the Story) Morning Glory?"),
                track("Wonderwall", "Ohasis", album = "Cover Songs"),
                track("Wonderwall", "Sponsors", album = "Party Covers"),
                track("Wonder", "Shawn Mendes", album = "Wonder")
            )
        )

        assertEquals(listOf("Oasis"), response.artists.map { it.name })
        assertTrue(response.albums.all { it.artist == "Oasis" })
        assertTrue(response.songs.all { it.artist == "Oasis" })
    }

    @Test
    fun happyResolvesToPharrellNotAMovieArtistNamedHappy() {
        val response = UnifiedSearchEngine.process(
            "Happy",
            listOf(
                track("Happy (From Despicable Me 2)", "Happy", album = "Despicable Me 2"),
                track("Despicable Me 2", "Happy", album = "Despicable Me 2"),
                track("Happy", "Pharrell Williams", album = "G I R L", isrc = "USUM71311296"),
                track("Happy", "Pharrell Williams", album = "Despicable Me 2", isrc = "USUM71311296")
            )
        )
        assertEquals("Happy", response.topResult?.title)
        assertEquals("Pharrell Williams", response.topResult?.artist)
        assertTrue(response.songs.any { it.artist == "Pharrell Williams" })
        assertTrue(response.songs.none { it.artist == "Happy" && it.title.contains("Despicable", ignoreCase = true) } ||
            response.topResult?.artist == "Pharrell Williams")
    }

    @Test
    fun activeIdentityBreaksAmbiguousPrefixWithRealCatalogResult() {
        val wonderwall = track("Wonderwall", "Oasis", album = "(What's the Story) Morning Glory?")
        val response = UnifiedSearchEngine.process(
            query = "wonder",
            results = listOf(
                track("Wonder", "Shawn Mendes", album = "Wonder"),
                wonderwall,
                track("Wonderful Tonight", "Eric Clapton", album = "Slowhand")
            ),
            personalization = SearchPersonalization(
                activeTitle = "Wonderwall",
                activeArtist = "Oasis"
            )
        )

        assertEquals("Wonderwall", response.topResult?.title)
        assertEquals("Oasis", response.topResult?.artist)
        assertEquals(listOf("Oasis"), response.artists.map { it.name })
    }

    @Test
    fun recentLibraryIdentityBreaksAmbiguousPrefixWithoutFabricatingTracks() {
        val wonderwallKey = UnifiedSearchEngine.identityKey("Wonderwall", "Oasis")
        val personalized = SearchPersonalization(
            libraryIdentityKeys = setOf(wonderwallKey),
            recentIdentityKeys = setOf(wonderwallKey)
        )
        val response = UnifiedSearchEngine.process(
            query = "wonder",
            results = listOf(
                track("Wonder", "Shawn Mendes", album = "Wonder"),
                track("Wonderwall", "Oasis", album = "(What's the Story) Morning Glory?")
            ),
            personalization = personalized
        )
        assertEquals("Wonderwall", response.topResult?.title)

        val unavailable = UnifiedSearchEngine.process(
            query = "wonder",
            results = listOf(track("Wonder", "Shawn Mendes", album = "Wonder")),
            personalization = personalized
        )
        assertEquals("Wonder", unavailable.topResult?.title)
        assertTrue(unavailable.songs.none { it.artist == "Oasis" })
    }

    @Test
    fun `partial artist query prefers exact artist recordings over event uploaders`() {
        val response = UnifiedSearchEngine.process(
            query = "tame i",
            results = listOf(
                track(
                    "Tame Impala at Parque de Quetzalcoatl for Cercle",
                    "Cercle and Tame Impala"
                ),
                track("The Less I Know The Better", "Tame Impala", album = "Currents"),
                track("Borderline", "Tame Impala", album = "The Slow Rush")
            )
        )

        assertEquals("Tame Impala", response.topResult?.artist)
        assertTrue(response.songs.take(2).all { it.artist == "Tame Impala" })
        assertEquals(listOf("Tame Impala"), response.artists.map { it.name })
    }

    @Test
    fun badGuy_exactArtistWins() {
        val intent = UnifiedSearchEngine.parse("bad guy")
        val studio = track("bad guy", "Billie Eilish")
        val seo = track("Bad Guy Billie Eilish", "Aiden Yoo")

        val results = UnifiedSearchEngine.rank(intent, listOf(seo, studio))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Billie Eilish", top.artist)
    }

    @Test
    fun down_featuredArtistLogic() {
        val intent = UnifiedSearchEngine.parse("Down Jay Sean feat Lil Wayne")
        val correct = track("Down", "Jay Sean", album = "All or Nothing")
        val lilWayneWrong = track("That Ain't Me", "Lil Wayne")
        val cover = track("Down", "Random Cover Artist")

        val results = UnifiedSearchEngine.rank(intent, listOf(lilWayneWrong, cover, correct))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Jay Sean", top.artist)
        assertEquals("Down", top.title)
    }

    @Test
    fun artistInTitleDoesNotEqualArtistMatch() {
        val intent = UnifiedSearchEngine.SearchQueryIntent("bad guy", "bad guy", "Billie Eilish", emptyList())
        val evaluation = UnifiedSearchEngine.score(
            intent,
            track("Bad Guy Billie Eilish", "Aiden Yoo")
        )
        assertFalse("Should not be eligible if artist is in title but not primary artist", evaluation.eligibleForTop)
    }

    @Test
    fun wrongTitleCannotWinBecauseArtistMatches() {
        val intent = UnifiedSearchEngine.parse("Down Jay Sean")
        val evaluation = UnifiedSearchEngine.score(intent, track("That Ain't Me", "Lil Wayne"))
        assertFalse(evaluation.eligibleForTop)
    }

    @Test
    fun exactTitleArtistBeatsHigherQualityWrongArtist() {
        val intent = UnifiedSearchEngine.SearchQueryIntent(
            rawQuery = "bad guy Billie Eilish",
            songTitle = "bad guy",
            primaryArtist = "Billie Eilish",
            featuredArtists = emptyList()
        )
        val correct = UnifiedSearchEngine.score(intent, track("bad guy", "Billie Eilish"))
        val wrong = UnifiedSearchEngine.score(intent, track("bad guy", "Aiden Yoo"))
        assertTrue(correct.finalScore > wrong.finalScore)
        assertTrue(correct.eligibleForTop)
        assertFalse(wrong.eligibleForTop)
    }

    @Test
    fun parseDownJaySean() {
        val intent = UnifiedSearchEngine.parse("Down Jay Sean feat Lil Wayne")
        assertEquals("down jay sean", intent.songTitle)
        assertEquals(null, intent.primaryArtist)
        assertTrue(intent.featuredArtists.any { it.contains("lil wayne") })
    }

    @Test
    fun processDownJaySeanCarriesFeaturedArtistToResults() {
        val response = UnifiedSearchEngine.process(
            "Down Jay Sean feat Lil Wayne",
            listOf(
                track("That Ain't Me", "Lil Wayne"),
                track("Down", "Random Cover Artist"),
                track("Down", "Jay Sean", album = "All or Nothing")
            )
        )

        assertEquals("Jay Sean", response.topResult?.artist)
        assertEquals("Down", response.topResult?.title)
        assertTrue(response.topResult?.featuredArtists.orEmpty().any { it.contains("lil wayne") })
        assertTrue(response.songs.first().featuredArtists.any { it.contains("lil wayne") })
    }

    @Test
    fun desertRose_stingBeatsJazzCoverAndUploader() {
        val correct = track("Desert Rose", "Sting")
        val cover = track("Desert Rose", "The Jazz Quartet")
        val uploader = track("Desert Rose (Official Audio)", "Lyrics Channel")

        val response = UnifiedSearchEngine.process("sting desert rose", listOf(uploader, cover, correct))
        assertEquals("Sting", response.topResult?.artist)
        assertEquals("Desert Rose", response.topResult?.title)
    }

    @Test
    fun victoryLapFive_fredAgainBeatsWrongArtist() {
        val intent = UnifiedSearchEngine.parse("victory lap five")
        val correct = track("Victory Lap Five (feat. Skepta)", "Fred Again..")
        val wrong = track("Victory Lap", "Some Rapper")
        val cover = track("Victory Lap Five", "Cover Band")

        val results = UnifiedSearchEngine.rank(intent, listOf(cover, wrong, correct))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Fred Again..", top.artist)
    }

    @Test
    fun wrongArtistWithExactTitle_isRejected() {
        val intent = UnifiedSearchEngine.parse("desert rose sting")
        val evaluation = UnifiedSearchEngine.score(intent, track("Desert Rose", "Jazz Covers Weekly", album = "Smooth Jazz"))
        assertFalse("Uploader with exact title should not be eligible", evaluation.eligibleForTop)
    }

    @Test
    fun uploaderChannel_isHeavilyPenalized() {
        val intent = UnifiedSearchEngine.parse("bad guy")
        val seo = UnifiedSearchEngine.score(intent, track("bad guy", "Lyrics Channel"))
        val studio = UnifiedSearchEngine.score(intent, track("bad guy", "Billie Eilish"))
        assertTrue(studio.finalScore > seo.finalScore)
        assertFalse("Uploader channel should not be eligible for top", seo.eligibleForTop)
    }

    @Test
    fun processDeduplicatesIdenticalSongs() {
        val response = UnifiedSearchEngine.process(
            "sting desert rose",
            listOf(
                track("Desert Rose", "Sting"),
                track("Desert Rose", "Sting"),
                track("Desert Rose", "Sting"),
                track("Desert Rose", "Sting", album = "Brand New Day"),
                track("Desert Rose (Official Audio)", "Sting")
            )
        )
        assertTrue("Too many duplicate Sting results: ${response.songs.size}", response.songs.size <= 2)
    }

    @Test
    fun parseDownJaySeanDash() {
        val intent = UnifiedSearchEngine.parse("Down - Jay Sean")
        assertEquals("down", intent.songTitle?.lowercase())
        assertEquals("jay sean", intent.primaryArtist?.lowercase())
    }

    @Test
    fun processDownJaySeanDashSelectsCorrectRecording() {
        val response = UnifiedSearchEngine.process(
            "Down - Jay Sean",
            listOf(
                track("Down", "Random Cover Artist"),
                track("Down", "Jay-Z"),
                track("Down", "Jay Sean", album = "All or Nothing")
            )
        )
        assertEquals("Jay Sean", response.topResult?.artist)
        assertEquals("Down", response.topResult?.title)
    }

    @Test
    fun allOfTheLights_kanyeWestWins() {
        val intent = UnifiedSearchEngine.parse("all of the lights kanye west")
        val correct = track("All of the Lights", "Kanye West")
        val cover = track("All of the Lights", "Piano Tribute Players")
        val seo = track("All of the Lights (Lyrics)", "Lyrics World")

        val results = UnifiedSearchEngine.rank(intent, listOf(seo, cover, correct))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Kanye West", top.artist)
        assertEquals("All of the Lights", top.title)
    }

    @Test
    fun spiritInTheSky_prioritizesNormanGreenbaum() {
        val response = UnifiedSearchEngine.process(
            "Spirit in the Sky",
            listOf(
                track("Spirit in the Sky", "Doctor and the Medics"),
                track(
                    "Spirit in the Sky",
                    "Norman Greenbaum",
                    album = "Spirit in the Sky",
                    isrc = "USRE19900123",
                    externalTrackId = "catalog:original"
                ),
                track("Spirit In The Sky Karaoke", "Karaoke Artist"),
                track("Spirit in the Sky", "Classic Rock Tribute Band")
            )
        )

        assertEquals("Norman Greenbaum", response.topResult?.artist)
        assertEquals("Spirit in the Sky", response.topResult?.title)
    }

    @Test
    fun tearsForFears_exactTitleAndArtistBeatsLooseSameArtistMatches() {
        val response = UnifiedSearchEngine.process(
            "Everybody Wants to Rule the World Tears for Fears",
            listOf(
                track("Shout", "Tears for Fears"),
                track("Everybody Wants To Rule The World", "Cover Band"),
                track("Everybody Wants To Rule The World (Live)", "Tears for Fears"),
                track("Everybody Wants To Rule The World", "Tears for Fears")
            )
        )

        assertEquals("Tears for Fears", response.topResult?.artist)
        assertEquals("Everybody Wants To Rule The World", response.topResult?.title)
    }

    @Test
    fun sweetChildOMine_prioritizesGunsNRosesAndRejectsGospelKaraoke() {
        val response = UnifiedSearchEngine.process(
            "Sweet Child O Mine",
            listOf(
                track("Sweet Child O Mine", "Guns N Roses"),
                track("Sweet Child O Mine", "Jesus Loves Me Worship Band"),
                track("Sweet Child O Mine (Karaoke Version)", "Karaoke Hits"),
                track("Sweet Child O Mine (Instrumental)", "Rock Instrumentals")
            )
        )
        assertEquals("Guns N Roses", response.topResult?.artist)
        assertEquals("Sweet Child O Mine", response.topResult?.title)
    }

    @Test
    fun sweetChildOMineWithArtist_requiresArtistMatch() {
        val response = UnifiedSearchEngine.process(
            "Sweet Child O Mine Guns N Roses",
            listOf(
                track("Sweet Child O Mine", "Guns N Roses"),
                track("Sweet Child O Mine", "Tribute Band"),
                track("Sweet Child O Mine", "Gospel Choir")
            )
        )
        assertEquals("Guns N Roses", response.topResult?.artist)
    }

    @Test
    fun blindingLights_prioritizesTheWeekndAndRejectsVariants() {
        val response = UnifiedSearchEngine.process(
            "Blinding Lights The Weeknd",
            listOf(
                track("Blinding Lights", "The Weeknd"),
                track("Blinding Lights (Cover)", "Piano Tribute Players"),
                track("Blinding Lights (Karaoke)", "Karaoke Stars"),
                track("Blinding Lights Remix", "DJ Remix"),
                track("Blinding Lights", "Some Random Artist")
            )
        )
        assertEquals("The Weeknd", response.topResult?.artist)
        assertEquals("Blinding Lights", response.topResult?.title)
    }

    @Test
    fun elPaso_prioritizesMartyRobbinsAndRejectsTravelVideos() {
        val response = UnifiedSearchEngine.process(
            "El Paso Marty Robbins",
            listOf(
                track("El Paso", "Marty Robbins"),
                track("El Paso Travel Guide", "Travel Channel"),
                track("El Paso News Report", "News Channel"),
                track("El Paso", "Cover Artist")
            )
        )
        assertEquals("Marty Robbins", response.topResult?.artist)
        assertEquals("El Paso", response.topResult?.title)
    }

    @Test
    fun theLessIKnowTheBetter_prioritizesTameImpalaAndRejectsLullabies() {
        val response = UnifiedSearchEngine.process(
            "The Less I Know The Better Tame Impala",
            listOf(
                track("The Less I Know The Better", "Tame Impala"),
                track("The Less I Know The Better", "Lullaby Players"),
                track("The Less I Know The Better (Cover)", "Acoustic Covers"),
                track("The Less I Know The Better", "Unknown Artist")
            )
        )
        assertEquals("Tame Impala", response.topResult?.artist)
        assertEquals("The Less I Know The Better", response.topResult?.title)
    }

    @Test
    fun victoryLap_prioritizesNipseyHussleAndRejectsSportsMotivation() {
        val response = UnifiedSearchEngine.process(
            "Victory Lap Nipsey Hussle",
            listOf(
                track("Victory Lap", "Nipsey Hussle"),
                track("Victory Lap", "Motivational Speaker"),
                track("Victory Lap Sports Highlights", "Sports Channel"),
                track("Victory Lap", "Cover Band")
            )
        )
        assertEquals("Nipsey Hussle", response.topResult?.artist)
        assertEquals("Victory Lap", response.topResult?.title)
    }

}
