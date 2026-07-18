package com.audiophile.musicplayer.data.canonical

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.data.source.external.ExternalTrack

object CanonicalMapper {

    fun mapToCanonicalTrack(result: SourceSearchResult, metadata: EnhancedMetadata? = null): CanonicalTrack {
        return CanonicalTrack(
            title = metadata?.title?.takeIf { it.isNotBlank() } ?: result.title,
            artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: result.artist,
            featuredArtists = result.featuredArtists,
            album = metadata?.album?.takeIf { it.isNotBlank() } ?: result.album,
            isrc = metadata?.isrc?.takeIf { it.isNotBlank() } ?: result.isrc,
            durationMs = metadata?.durationMs ?: result.durationMs,
            genre = metadata?.genres?.firstOrNull(),
            artworkUrl = metadata?.artworkUrl?.takeIf { it.isNotBlank() } ?: result.artworkUrl,
            explicit = metadata?.explicit,
            sourcePriority = 0,
            sourceStatus = result.status,
            sourceProviderId = result.providerId,
            externalTrackId = result.id,
            qualityInfo = VantaQualityInfo.fromSource(
                bitrate = null,
                quality = result.qualityLabel,
                mime = null,
                status = result.status,
                isDolbyAtmos = result.isDolbyAtmos,
                isSpatialAudio = result.isSpatialAudio,
                isSurround = result.isSurround,
                isHiRes = result.isHiRes,
                spatialEvidence = result.spatialEvidence,
                sourceProviderId = result.providerId,
                reason = when (result.status) {
                    SearchItemStatus.PREVIEW -> "preview_source"
                    SearchItemStatus.VALIDATED_PLAYABLE,
                    SearchItemStatus.LOCAL_PLAYABLE -> "validated_source"
                    else -> "unvalidated_source"
                }
            ).takeIf { it.bestQualityLabel() != null }
        )
    }

    fun mapToCanonicalTrack(ut: UnifiedTrackWithSources): CanonicalTrack {
        val t = ut.track
        val status = ut.sourceValidityStatus()
        val bestSource = ut.sources.maxByOrNull { it.bitrate }
        return CanonicalTrack(
            title = t.title,
            artist = t.artist,
            album = t.albumName,
            isrc = t.isrc,
            durationMs = t.durationMs,
            genre = t.genre,
            artworkUrl = t.coverArtUrl,
            sourcePriority = 0,
            sourceStatus = status,
            sourceProviderId = bestSource?.externalProviderId ?: bestSource?.sourceType?.name?.lowercase(),
            externalTrackId = bestSource?.externalTrackId,
            qualityInfo = VantaQualityInfo.fromTrackSource(
                source = bestSource,
                status = status
            )?.takeIf { it.bestQualityLabel() != null }
        )
    }

    fun mapToCanonicalTrack(track: ExternalTrack, providerId: String? = null): CanonicalTrack {
        return CanonicalTrack(
            title = track.title,
            artist = track.artist,
            album = track.album,
            isrc = track.isrc,
            durationMs = track.resolveDurationMs(),
            artworkUrl = track.resolveArtwork(),
            sourcePriority = 0,
            sourceProviderId = providerId?.takeIf { it.isNotBlank() },
            externalTrackId = track.id.takeIf { it.isNotBlank() }
        )
    }

    fun isPlayable(status: SearchItemStatus): Boolean {
        return status == SearchItemStatus.VALIDATED_PLAYABLE ||
               status == SearchItemStatus.LOCAL_PLAYABLE ||
               status == SearchItemStatus.PREVIEW
    }
}
