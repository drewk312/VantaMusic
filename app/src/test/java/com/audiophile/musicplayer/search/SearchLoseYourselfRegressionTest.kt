package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression for the reported real-world failure: searching
 * "lose yourself by eminem" surfaced "no full track matches" / wrong same-name
 * songs on device. The gateway returns 29 rows with a Qobuz "Lose Yourself / Eminem"
 * as the top hit; the app engine must keep it first in the songs rail and as Top Result.
 */
class SearchLoseYourselfRegressionTest {

    private fun gatewayTrack(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
        isrc: String? = null,
        durationSec: Long? = null,
        status: SearchItemStatus = SearchItemStatus.SOURCE_FOUND
    ) = CanonicalTrack(
        title = title,
        artist = artist,
        album = album,
        isrc = isrc,
        durationMs = durationSec?.times(1000L),
        sourceStatus = status,
        sourceProviderId = "cloudflare_gateway",
        externalTrackId = id
    )

    // Exact row shape the deployed gateway returns for "lose yourself by eminem".
    private fun realGatewayRows() = listOf(
        gatewayTrack("qobuz:3972271", "Lose Yourself", "Eminem", "Curtain Call: The Hits", "USIR10211559", 326),
        gatewayTrack("qobuz:2449079", "Lose Yourself", "Eminem", null, "USIR10211560", 320),
        gatewayTrack("deezer:17869472", "Lose Yourself (From \"8 Mile\")", "Eminem", null, "FRUM72100679", 267),
        gatewayTrack("spotify:662kU2lCwIzyW6xs7z0hyq", "Lose Yourself - From \"8 Mile\" Soundtrack", "Eminem", null, "USIR10211559", 321),
        gatewayTrack("apple:225345", "Lose Yourself", "Eminem", "8 Mile", null, null, SearchItemStatus.METADATA_ONLY),
        gatewayTrack("youtube:zZ5t6mXiz4U", "Lose Yourself", "GoldenSoulFM", null, null, 374),
        gatewayTrack("qobuz:3974921", "Lose Yourself", "Vitamin String Quartet", null, null, 263),
        gatewayTrack("deezer:31760202", "Lose Yourself", "Maria Fiselier", null, null, 257),
        gatewayTrack("qobuz:61055421", "Lose Yourself Eminem", "Heavenly Harmony", null, null, 233),
        gatewayTrack("qobuz:62373726", "Eminem (Lose Yourself)[Epic Version]", "TM.", null, null, 138)
    )

    @Test
    fun loseYourselfByEminemKeepsRealTrackFirstInSongs() {
        val response: UnifiedSearchResponse =
            UnifiedSearchEngine.process("lose yourself by eminem", realGatewayRows())

        assertFalse("songs rail must not be empty", response.songs.isEmpty())

        val first = response.songs.first()
        assertEquals("Lose Yourself", first.title)
        assertEquals("Eminem", first.artist)
        assertTrue(first.isLikelyMusicTrack())

        val eminemCount = response.songs.count { it.artist.equals("Eminem", ignoreCase = true) }
        assertTrue("primary Eminem recordings surface (got $eminemCount)", eminemCount >= 3)

        val firstNonEminem = response.songs.indexOfFirst { !it.artist.equals("Eminem", ignoreCase = true) }
        val lastEminem = response.songs.indexOfLast { it.artist.equals("Eminem", ignoreCase = true) }
        if (firstNonEminem >= 0) {
            assertTrue(
                "all Eminem rows rank before any cover (eminem max index $lastEminem, cover at $firstNonEminem)",
                lastEminem == response.songs.lastIndex || lastEminem < firstNonEminem
            )
        }
    }

    @Test
    fun loseYourselfByEminemPicksQobuzMasterAsTopResult() {
        val response: UnifiedSearchResponse =
            UnifiedSearchEngine.process("lose yourself by eminem", realGatewayRows())

        val top = response.topResult
        assertNotNull("Top Result exists", top)
        assertEquals("Eminem", top?.artist)
        assertEquals("qobuz:3972271", top?.externalTrackId)
        assertTrue(response.identityMatch)
    }

    @Test
    fun loseYourselfByEminemDoesNotPromoteUploaderChannels() {
        val response: UnifiedSearchResponse =
            UnifiedSearchEngine.process("lose yourself by eminem", realGatewayRows())

        val titles = response.songs.map { it.title to it.artist }
        assertFalse(
            "80s-YouTube-style cover named exactly 'Lose Yourself' must not outrank the recording",
            titles.indexOfFirst { it.second.equals("GoldenSoulFM", ignoreCase = true) } == 0
        )
        assertFalse(
            "alphabetical-cover-index copies must not outrank the recording",
            titles.indexOfFirst { it.second.equals("Maria Fiselier", ignoreCase = true) } == 0
        )
    }
}