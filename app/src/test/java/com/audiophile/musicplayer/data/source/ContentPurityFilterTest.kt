package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPurityFilterTest {
    @Test fun `radio rejects technical explainers without banning technical song titles`() {
        assertTrue(ContentPurityFilter.isClearlyNonMusicContent(
            "20 Things in Electronics That Confused me 20 Years Ago", "LeftyMaker"))
        assertTrue(ContentPurityFilter.isClearlyNonMusicContent("Transistors explained for beginners", "Studio"))
        assertFalse(ContentPurityFilter.isClearlyNonMusicContent("Electronics", "The Tutorial"))
        assertFalse(ContentPurityFilter.isClearlyNonMusicContent("How to Save a Life", "The Fray"))
    }


    @Test
    fun `blocks restaurant review videos shown in now playing`() {
        val videos = listOf(
            "Andrea's Pizza (New York, NY)" to "Barstool Pizza Review",
            "Slicehaus Pizzeria (New York, NY) presented by Rhoback" to "Barstool Pizza Review",
            "Angelo's Pizzeria (Philadelphia)" to "Barstool Pizza Review"
        )

        videos.forEach { (title, artist) ->
            assertTrue(ContentPurityFilter.isClearlyNonMusicContent(title, artist))
            assertFalse(ContentPurityFilter.isAllowed(title, artist, null, 121_000L, "youtube"))
        }
    }

    @Test
    fun `blocks publisher interviews and political commentary shown in now playing`() {
        assertTrue(
            ContentPurityFilter.isClearlyNonMusicContent(
                "Voice Acting Legend Jim Cummings Answers Voice Acting Questions",
                "WIRED",
                durationMs = 281_000L
            )
        )
        assertTrue(
            ContentPurityFilter.isClearlyNonMusicContent(
                "The U.S. Just Walked Into Iran's Deadliest Trap | Col Douglas",
                "Front America",
                durationMs = 888_000L
            )
        )
        assertTrue(
            ContentPurityFilter.isClearlyNonMusicContent(
                "Interpreter Breaks Down How Real-Time Translation Works | WIRED",
                "WIRED",
                durationMs = 533_000L
            )
        )
        assertTrue(
            ContentPurityFilter.isClearlyNonMusicContent(
                "How to Spend 3 Days in SYDNEY Australia | The Perfect Travel Itinerary",
                "UltimateTravelists",
                durationMs = 793_000L
            )
        )
    }

    @Test
    fun `does not mistake real artist names or song titles for non music channels`() {
        assertTrue(ContentPurityFilter.isAllowed("Song of the South", "Alabama", null, 192_000L, "catalog"))
        assertTrue(ContentPurityFilter.isAllowed("Paranoid Android", "Radiohead", "OK Computer", 387_000L, "catalog"))
        assertTrue(ContentPurityFilter.isAllowed("Dust in the Wind", "Kansas", "Point of Know Return", 206_000L, "catalog"))
        assertFalse(ContentPurityFilter.isClearlyNonMusicContent("The Rhythm", "WIRED", durationMs = 210_000L))
    }

    @Test
    fun `blocks karaoke impersonators mixes and backing tracks`() {
        assertFalse(
            ContentPurityFilter.isAllowed(
                "Texas Hold 'Em (Originally Performed by Beyoncé)",
                "The Backing Tracks",
                "Karaoke Hits",
                210_000L,
                "qobuz"
            )
        )
        assertFalse(
            ContentPurityFilter.isAllowed(
                "Sex Hero Trey Songs , August alsina, usher type Beyonce",
                "DJ Mix 2024",
                null,
                240_000L,
                "catalog"
            )
        )
        assertTrue(ContentPurityFilter.isCompilationUpload("Beyonce 10 years 22 songs"))
        assertTrue(
            ContentPurityFilter.isAllowed(
                "Texas Hold 'Em",
                "Beyoncé",
                "Cowboy Carter",
                234_000L,
                "qobuz"
            )
        )
    }

    @Test
    fun `blocks workout and style pack variants for studio catalog`() {
        assertFalse(
            ContentPurityFilter.isAllowed(
                "Blinding Lights (Tabata)",
                "Workout Hits",
                null,
                200_000L,
                "catalog"
            )
        )
        assertFalse(
            ContentPurityFilter.isAllowed(
                "Blinding Lights in 13 Styles",
                "Style Pack Orchestra",
                null,
                210_000L,
                "catalog"
            )
        )
        assertFalse(
            ContentPurityFilter.isAllowed(
                "Levitating (HIIT Mix)",
                "Fitness Beats",
                null,
                180_000L,
                "deezer_gateway"
            )
        )
        assertTrue(
            VariantClassifier.isWorkoutOrStylePackVariant("Save Your Tears in 5 Styles", "Pack Band")
        )
        assertFalse(
            VariantClassifier.isWorkoutOrStylePackVariant("Blinding Lights", "The Weeknd")
        )
        val (choreoRejected, _) = VariantClassifier.isRejectedForStudioIntent(
            "Blinding Lights - Vintage Dance Choreography - Roberto F",
            "The Weeknd",
            null,
            userQuery = null
        )
        assertTrue(choreoRejected)
        assertTrue(VariantClassifier.isByArtistReupload("Blinding Lights by The Weeknd", "ButenkoDV"))
        assertFalse(VariantClassifier.isByArtistReupload("Blinding Lights", "The Weeknd"))
        val (coverParenRejected, _) = VariantClassifier.isRejectedForStudioIntent(
            "Blinding Lights (The Weeknd Cover)",
            "X Ambassadors",
            null,
            userQuery = null
        )
        assertTrue(coverParenRejected)
    }

    @Test
    fun `allows workout variant only when explicitly searched`() {
        assertTrue(
            ContentPurityFilter.isAllowed(
                "Blinding Lights (Tabata)",
                "Workout Hits",
                null,
                200_000L,
                "catalog",
                userQuery = "blinding lights tabata workout"
            )
        )
    }
}
