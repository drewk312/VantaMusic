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

    private fun source(sourceId: Long, bitrate: Int, provider: String) = TrackSource(
        sourceId = sourceId,
        parentTrackId = 1L,
        sourceType = SourceType.ADDON,
        streamUrl = "https://example.com/$sourceId.flac",
        bitrate = bitrate,
        externalProviderId = provider,
        externalTrackId = "track-$sourceId"
    )
}
