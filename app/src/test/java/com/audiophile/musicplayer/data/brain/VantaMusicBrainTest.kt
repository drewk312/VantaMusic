package com.audiophile.musicplayer.data.brain

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.VariantClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VantaMusicBrainTest {

    @Test
    fun blindingLights_resolvesWeekndStudioVocal() {
        val resolution = VantaMusicBrain.resolveCatalog(
            query = "blinding lights",
            catalogCandidates = listOf(
                track("Blinding Lights", "The Weeknd", album = "After Hours", isrc = "USUG11904206", durationMs = 200_000L),
                track("Blinding Lights", "Rockabye Baby!", album = "Lullaby Renditions of the Weeknd", durationMs = 186_000L)
            )
        )

        assertEquals("The Weeknd", resolution.canonicalTrackIdentity?.artist)
        assertEquals(MatchTier.A, resolution.matchTier)
        assertEquals(VariantClassifier.VariantType.STUDIO_VOCAL, resolution.variantType)
        assertTrue(resolution.lyricsExpected)
    }

    @Test
    fun badGuy_resolvesBillieStudioVocal() {
        val resolution = VantaMusicBrain.resolveCatalog(
            query = "bad guy",
            catalogCandidates = listOf(
                track("bad guy", "Karaoke Band", album = "Karaoke Hits"),
                track("bad guy", "Billie Eilish", album = "WHEN WE ALL FALL ASLEEP, WHERE DO WE GO?", isrc = "USUM71815985")
            )
        )

        assertEquals("Billie Eilish", resolution.canonicalTrackIdentity?.artist)
        assertTrue(resolution.lyricsExpected)
    }

    @Test
    fun down_resolvesJaySeanWithLilWayneFeatured() {
        val resolution = VantaMusicBrain.resolveCatalog(
            query = "down jay sean lil wayne",
            catalogCandidates = listOf(
                track("Down", "Lil Wayne", album = "Random Mixtape"),
                track("Down", "Jay Sean", album = "All or Nothing", isrc = "GBUM70904191")
            )
        )

        assertEquals("Jay Sean", resolution.canonicalTrackIdentity?.artist)
    }

    @Test
    fun artistNameInTitle_isNotArtistMatch() {
        val resolution = VantaMusicBrain.resolveCatalog(
            query = "blinding lights weeknd",
            catalogCandidates = listOf(
                track("Blinding Lights The Weeknd", "Pancadão GD Som", album = "Seleção 26"),
                track("Blinding Lights", "The Weeknd", album = "After Hours", isrc = "USUG11904206")
            )
        )

        assertEquals("The Weeknd", resolution.canonicalTrackIdentity?.artist)
        assertFalse(resolution.rejectionReasons.isEmpty())
    }

    @Test
    fun pianoInstrumentalLullabyRejected_unlessRequested() {
        val normalResolution = VantaMusicBrain.resolveCatalog(
            query = "blinding lights",
            catalogCandidates = listOf(
                track("Blinding Lights", "Rockabye Baby!", album = "Lullaby Renditions of the Weeknd"),
                track("Blinding lights The weeknd piano", "Fredy'Sam", album = "Piano Music"),
                track("Blinding Lights", "The Weeknd", album = "After Hours")
            )
        )
        assertEquals("The Weeknd", normalResolution.canonicalTrackIdentity?.artist)

        val instrumentalIntent = IntentParser.parse("blinding lights instrumental")
        assertEquals(RequestedVariant.INSTRUMENTAL, instrumentalIntent.requestedVariant)
    }

    @Test
    fun flacWrongVersion_losesToCorrectVocalSource() {
        val resolution = VantaMusicBrain.resolveCatalog(
            query = "blinding lights",
            catalogCandidates = listOf(track("Blinding Lights", "The Weeknd", album = "After Hours", isrc = "USUG11904206")),
            sourceCandidates = listOf(
                source("1", "qobuz", "Blinding Lights", "Cover Band", "Cover Versions"),
                source("2", "deezer", "Blinding Lights", "The Weeknd", "After Hours", isrc = "USUG11904206")
            )
        )

        assertNotNull(resolution.acceptedSource)
        assertEquals("The Weeknd", resolution.acceptedSource?.artist)
    }

    @Test
    fun lyricsExpectedFalse_forNonVocalVariant() {
        val resolution = VantaMusicBrain.resolveCatalog(
            query = "blinding lights",
            catalogCandidates = listOf(track("Blinding Lights", "Roma Symphony Orchestra", album = "RSO Performs The Weeknd"))
        )

        assertFalse(resolution.lyricsExpected)
    }

    @Test
    fun exactUserTap_bypassesTasteAndStillUsesCatalogTruth() {
        val resolution = VantaMusicBrain.resolveCatalog(
            query = "blinding lights",
            exactUserTap = true,
            catalogCandidates = listOf(track("Blinding Lights", "The Weeknd", album = "After Hours", externalTrackId = "123", providerId = "qobuz_gateway"))
        )

        assertEquals(MatchTier.S, resolution.matchTier)
        assertEquals("The Weeknd", resolution.canonicalTrackIdentity?.artist)
    }

    private fun track(
        title: String,
        artist: String,
        album: String? = null,
        isrc: String? = null,
        durationMs: Long? = 200_000L,
        externalTrackId: String? = null,
        providerId: String? = "qobuz_gateway"
    ) = CanonicalTrack(
        title = title,
        artist = artist,
        album = album,
        isrc = isrc,
        durationMs = durationMs,
        sourceProviderId = providerId,
        externalTrackId = externalTrackId
    )

    private fun source(
        id: String,
        providerId: String,
        title: String,
        artist: String,
        album: String? = null,
        isrc: String? = null
    ) = SourceSearchResult(
        id = id,
        providerId = providerId,
        title = title,
        artist = artist,
        album = album,
        coverSeed = title,
        durationMs = 200_000L,
        isrc = isrc,
        status = SearchItemStatus.SOURCE_FOUND,
        qualityLabel = "FLAC"
    )
}
