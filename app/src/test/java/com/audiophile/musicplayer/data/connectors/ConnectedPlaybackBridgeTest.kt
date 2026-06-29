package com.audiophile.musicplayer.data.connectors

import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.DEFAULT_SOURCE_PRIORITY
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedPlaybackBridgeTest {

    @Test
    fun appleAndSpotifySourcesAreMetadataOnly() {
        assertEquals(SearchItemStatus.METADATA_ONLY, track(SourceType.SPOTIFY).sourceValidityStatus())
        assertEquals(SearchItemStatus.METADATA_ONLY, track(SourceType.APPLE_MUSIC).sourceValidityStatus())
    }

    @Test
    fun providerTypesAreNotInPlaybackPriority() {
        assertFalse(DEFAULT_SOURCE_PRIORITY.contains(SourceType.SPOTIFY))
        assertFalse(DEFAULT_SOURCE_PRIORITY.contains(SourceType.APPLE_MUSIC))
    }

    @Test
    fun providerMetadataRowsDoNotHideRealVantaSource() {
        val unified = UnifiedTrack(trackId = 2L, title = "Song", artist = "Artist", albumName = null, coverArtUrl = null)
        val mixed = UnifiedTrackWithSources(
            track = unified,
            sources = listOf(
                TrackSource(
                    sourceId = 1L,
                    parentTrackId = unified.trackId,
                    sourceType = SourceType.SPOTIFY,
                    streamUrl = "https://provider.example/metadata",
                    bitrate = 320,
                    externalProviderId = "spotify",
                    externalTrackId = "provider_id"
                ),
                TrackSource(
                    sourceId = 2L,
                    parentTrackId = unified.trackId,
                    sourceType = SourceType.ADDON,
                    streamUrl = "https://vanta.example/stream.flac",
                    bitrate = 1411,
                    externalProviderId = "lossless",
                    externalTrackId = "vanta_id"
                )
            )
        )

        assertEquals(SearchItemStatus.SOURCE_FOUND, mixed.sourceValidityStatus())
        assertTrue(mixed.sources.any { it.sourceType == SourceType.ADDON })
    }

    private fun track(type: SourceType): UnifiedTrackWithSources {
        val unified = UnifiedTrack(trackId = 1L, title = "Song", artist = "Artist", albumName = null, coverArtUrl = null)
        return UnifiedTrackWithSources(
            track = unified,
            sources = listOf(
                TrackSource(
                    sourceId = 1L,
                    parentTrackId = unified.trackId,
                    sourceType = type,
                    streamUrl = "https://provider.example/track",
                    bitrate = 320,
                    externalProviderId = type.name,
                    externalTrackId = "provider_id"
                )
            )
        )
    }
}
