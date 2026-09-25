package com.audiophile.musicplayer.data.canonical

import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceIdentityGate
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.data.source.external.ExternalTrack

object CanonicalMapper {

    fun mapToCanonicalTrack(result: SourceSearchResult, metadata: EnhancedMetadata? = null): CanonicalTrack {
        val rawTitle = metadata?.title?.takeIf { it.isNotBlank() } ?: result.title
        val rawArtist = metadata?.artist?.takeIf { it.isNotBlank() } ?: result.artist
        val featured = (
            result.featuredArtists +
                DisplayMetadataCleaner.extractFeaturedArtists(rawTitle, rawArtist)
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val primary = DisplayMetadataCleaner.splitArtistCredits(rawArtist).primary
            .ifBlank { rawArtist }
        return CanonicalTrack(
            title = rawTitle,
            artist = primary,
            featuredArtists = featured,
            album = metadata?.album?.takeIf { it.isNotBlank() } ?: result.album,
            isrc = metadata?.isrc?.takeIf { it.isNotBlank() } ?: result.isrc,
            durationMs = metadata?.durationMs ?: result.durationMs,
            genre = metadata?.genres?.firstOrNull(),
            artworkUrl = metadata?.artworkUrl?.takeIf { it.isNotBlank() } ?: result.artworkUrl,
            explicit = metadata?.explicit,
            sourcePriority = SourceIdentityGate.sourceDisplayPriority(result.providerId),
            sourceStatus = result.status,
            sourceProviderId = result.providerId,
            externalTrackId = result.id,
            qualityInfo = VantaQualityInfo.fromSource(
                bitrate = null,
                quality = result.qualityLabel,
                mime = null,
                status = result.status,
                isDolbyAtmos = result.isDolbyAtmos,
                isSony360RealityAudio = result.isSony360RealityAudio,
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
            ).takeIf { it.bestQualityLabel() != null },
            atmosMixAvailable = result.atmosMixAvailable
        )
    }

    fun mapToCanonicalTrack(ut: UnifiedTrackWithSources): CanonicalTrack {
        val t = ut.track
        val status = ut.sourceValidityStatus()
        val bestSource = ut.sources.maxWithOrNull(
            compareBy<TrackSource> { SourceIdentityGate.playbackProviderRank(it.externalProviderId) }
                .thenBy { it.bitrate }
        )
        return CanonicalTrack(
            title = t.title,
            artist = t.artist,
            album = t.albumName,
            isrc = t.isrc,
            durationMs = t.durationMs,
            genre = t.genre,
            artworkUrl = t.coverArtUrl,
            sourcePriority = SourceIdentityGate.sourceDisplayPriority(bestSource?.externalProviderId),
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
            sourcePriority = SourceIdentityGate.sourceDisplayPriority(providerId),
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
