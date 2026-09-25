package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.SelectedRecordingIdentity
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StreamResolverIdentityFallbackTest {

    @Test
    fun persistedWrongRecordingIsRejectedBeforeResolve() {
        val selected = SelectedRecordingIdentity(
            title = "La Grange",
            artist = "ZZ Top",
            preferredProviderId = "cloudflare_gateway",
            preferredExternalTrackId = "deezer:4092566321"
        )
        val wrong = result(
            id = "deezer:4092566321",
            title = "La Grange (ZZ Top)",
            artist = "Matteo Leonetti",
            album = "L'alfabeto Del ROCK",
            providerId = "cloudflare_gateway"
        )

        assertNull(
            findVerifiedIdentityCandidate(
                selected,
                providerId = "cloudflare_gateway",
                externalId = "deezer:4092566321",
                candidates = listOf(wrong)
            )
        )
    }

    @Test
    fun fallbackRanksCanonicalRecordingAheadOfResolvableKaraoke() {
        val track = UnifiedTrackWithSources(
            track = UnifiedTrack(
                trackId = 1L,
                title = "Spirit In The Sky",
                artist = "Norman Greenbaum",
                albumName = "Spirit In The Sky",
                coverArtUrl = null,
                durationMs = 240_000L
            ),
            sources = emptyList()
        )

        val ranked = rankFallbackStreamCandidates(
            track = track,
            candidates = listOf(
                result(
                    id = "karaoke",
                    title = "Spirit In The Sky Karaoke",
                    artist = "Karaoke Artist",
                    album = "Karaoke Hits"
                ),
                result(
                    id = "canonical",
                    title = "Spirit In The Sky",
                    artist = "Norman Greenbaum",
                    album = "Spirit In The Sky",
                    durationMs = 240_000L
                ),
                result(
                    id = "tribute",
                    title = "Spirit In The Sky",
                    artist = "Classic Rock Tribute Band",
                    album = "Cover Versions"
                )
            )
        )

        assertEquals("canonical", ranked.first().id)
        assertFalse(ranked.any { it.id == "karaoke" })
        assertFalse(ranked.any { it.id == "tribute" })
    }

    @Test
    fun playbackSourceQualityRank_prefersQobuzTidalOverYouTubeAtSimilarBitrate() {
        val youtube = source(
            sourceId = 1L,
            bitrate = 138,
            provider = "youtube_music",
            sourceType = SourceType.YOUTUBE_MUSIC
        )
        val qobuzTidal = source(
            sourceId = 2L,
            bitrate = 128,
            provider = "qobuz_tidal",
            sourceType = SourceType.ADDON
        )

        assertTrue(playbackSourceQualityRank(qobuzTidal) > playbackSourceQualityRank(youtube))
    }

    @Test
    fun fallbackRanksCatalogAheadOfYouTubeWhenIdentityMatches() {
        val track = UnifiedTrackWithSources(
            track = UnifiedTrack(
                trackId = 1L,
                title = "Blinding Lights",
                artist = "The Weeknd",
                albumName = "After Hours",
                coverArtUrl = null,
                durationMs = 200_000L
            ),
            sources = emptyList()
        )

        val ranked = rankFallbackStreamCandidates(
            track = track,
            candidates = listOf(
                result(
                    id = "yt",
                    title = "Blinding Lights",
                    artist = "The Weeknd",
                    album = "After Hours",
                    durationMs = 200_000L,
                    providerId = "youtube_music"
                ),
                result(
                    id = "qobuz",
                    title = "Blinding Lights",
                    artist = "The Weeknd",
                    album = "After Hours",
                    durationMs = 200_000L,
                    providerId = "qobuz_tidal"
                )
            )
        )

        assertEquals("qobuz", ranked.first().id)
        assertEquals("youtube_music", ranked.last().providerId)
    }

    @Test
    fun playbackSourceQualityRank_prefersHigherQualityLosslessSource() {
        val lowQuality = source(
            sourceId = 1L,
            bitrate = 320,
            provider = "deezer"
        )
        val hiRes = source(
            sourceId = 2L,
            bitrate = 2304,
            provider = "qobuz"
        )

        assertTrue(playbackSourceQualityRank(hiRes) > playbackSourceQualityRank(lowQuality))
    }

    @Test
    fun searchFallbackPersistsTheResolvedCandidateNotTheFailedIdentity() {
        val persisted = "qobuz_tidal" to "qobuz:1"
        val actual = persistIdentityForResolvedCandidate("tidal_gateway", "tidal:99")
        assertEquals("tidal_gateway", actual.first)
        assertEquals("tidal:99", actual.second)
        assertFalse(actual == persisted)
    }

    @Test
    fun tapBudgetDoesNotLeaveATwentySecondResolveWindow() {
        val totalBudget = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.TAP_PLAY_TOTAL_BUDGET_MS
        assertTrue("tap-to-play budget must stay bounded", totalBudget in 1L..15_000L)
        assertEquals(
            totalBudget,
            com.audiophile.musicplayer.data.source.CloudLibraryHelpers.TAP_PLAY_RESOLVE_TIMEOUT_MS
        )
        assertTrue(com.audiophile.musicplayer.data.source.CloudLibraryHelpers.TAP_PLAY_SEARCH_TIMEOUT_MS <= 4_000L)
        assertEquals(
            0L,
            com.audiophile.musicplayer.data.source.CloudLibraryHelpers.remainingTapBudgetMs(
                startedAtMs = 0L,
                budgetMs = 8_000L,
                nowMs = 8_000L
            )
        )
        assertEquals(
            3_000L,
            com.audiophile.musicplayer.data.source.CloudLibraryHelpers.remainingTapBudgetMs(
                startedAtMs = 1_000L,
                budgetMs = 8_000L,
                nowMs = 6_000L
            )
        )
    }

    private fun result(
        id: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long = 240_000L,
        providerId: String = "test_provider"
    ) = SourceSearchResult(
        id = id,
        providerId = providerId,
        title = title,
        artist = artist,
        album = album,
        coverSeed = title,
        durationMs = durationMs,
        status = SearchItemStatus.SOURCE_FOUND,
        qualityLabel = null
    )

    private fun source(
        sourceId: Long,
        bitrate: Int,
        provider: String,
        sourceType: SourceType = SourceType.ADDON
    ) = TrackSource(
        sourceId = sourceId,
        parentTrackId = 1L,
        sourceType = sourceType,
        streamUrl = "https://example.com/$sourceId.flac",
        bitrate = bitrate,
        externalProviderId = provider,
        externalTrackId = "track-$sourceId"
    )
}
