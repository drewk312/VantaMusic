package com.audiophile.musicplayer.data.canonical

import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.external.ExternalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CanonicalMapperTest {

    @Test
    fun mapFromSearchResult_preservesProviderScopedIdentity() {
        val result = SourceSearchResult(
            id = "12345678",
            providerId = "deezer",
            title = "How Deep Is Your Love",
            artist = "Bee Gees",
            album = "Children of the World",
            coverSeed = "https://example.com/art.jpg",
            durationMs = 241_000L,
            isrc = "USRC17607839",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            qualityLabel = "FLAC"
        )

        val canonical = CanonicalMapper.mapToCanonicalTrack(result)

        assertEquals("deezer", canonical.sourceProviderId)
        assertEquals("12345678", canonical.externalTrackId)
        assertEquals("How Deep Is Your Love", canonical.title)
    }

    @Test
    fun mapFromUnifiedTrack_preservesExternalProviderIds() {
        val track = UnifiedTrack(
            trackId = 42L,
            title = "Stayin' Alive",
            artist = "Bee Gees",
            albumName = "Saturday Night Fever",
            coverArtUrl = null,
        )
        val source = TrackSource(
            sourceId = 1L,
            parentTrackId = 42L,
            sourceType = SourceType.ADDON,
            streamUrl = "https://example.com/stream",
            bitrate = 1411,
            externalProviderId = "qobuz",
            externalTrackId = "99887766",
        )
        val unified = UnifiedTrackWithSources(track = track, sources = listOf(source))

        val canonical = CanonicalMapper.mapToCanonicalTrack(unified)

        assertEquals("qobuz", canonical.sourceProviderId)
        assertEquals("99887766", canonical.externalTrackId)
    }

    @Test
    fun mapFromExternalTrack_keepsProviderNamespaceSeparateFromTrackId() {
        val external = ExternalTrack(
            id = "tidal-track-abc",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000L,
            durationSeconds = null,
            artworkUrl = null,
            artworkURL = null,
            coverUrl = null,
            isrc = null,
            format = null,
            quality = null,
            streamUrl = null,
            playable = true,
        )

        val deezer = CanonicalMapper.mapToCanonicalTrack(external, providerId = "deezer")
        val qobuz = CanonicalMapper.mapToCanonicalTrack(external, providerId = "qobuz")

        assertEquals("deezer", deezer.sourceProviderId)
        assertEquals("qobuz", qobuz.sourceProviderId)
        assertEquals("tidal-track-abc", deezer.externalTrackId)
        assertEquals("tidal-track-abc", qobuz.externalTrackId)
        assertNotNull(deezer.externalTrackId)
    }
}
