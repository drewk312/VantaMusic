package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.TrustedStreamSources
import com.audiophile.musicplayer.data.source.canResolveStream

class LiveRadioTrackLibrary(
    private val trackRepository: TrackRepository,
    private val metadataResolver: MetadataResolver,
    private val sourceRegistry: SourceRegistry
) {
    suspend fun addIdentifiedTrack(title: String, artist: String): UnifiedTrackWithSources? {
        val trimmedTitle = title.trim()
        val trimmedArtist = artist.trim()
        if (trimmedTitle.isBlank()) return null

        val metadata = metadataResolver.resolveRawMetadata(trimmedTitle, trimmedArtist, null)
        val canonical = CanonicalTrack(
            title = metadata?.title?.trim().orEmpty().ifBlank { trimmedTitle },
            artist = metadata?.artist?.trim().orEmpty().ifBlank { trimmedArtist.ifBlank { "Unknown Artist" } },
            album = metadata?.album,
            artworkUrl = metadata?.artworkUrl,
            genre = metadata?.genres?.firstOrNull(),
            isrc = metadata?.isrc,
            durationMs = metadata?.durationMs,
            releaseYear = metadata?.releaseYear
        )

        val searchQuery = "${canonical.title} ${canonical.artist}".trim()
        val sourceResults = sourceRegistry.searchAll(searchQuery, includeSupplemental = false)
            .filter { !com.audiophile.musicplayer.data.source.SourceIdentityGate.isSupplementalPlaybackProvider(it.providerId) }
            .filter { candidate ->
                candidate.title.equals(canonical.title, ignoreCase = true) ||
                    candidate.title.contains(canonical.title, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<SourceSearchResult> {
                    !canonical.isrc.isNullOrBlank() && canonical.isrc.equals(it.isrc, ignoreCase = true)
                }.thenByDescending { it.artworkUrl != null }
            )

        var resolvedStream: com.audiophile.musicplayer.data.source.ResolvedStream? = null
        var playable: SourceSearchResult? = null
        val triedProviders = mutableSetOf<String>()
        for (candidate in sourceResults) {
            if (!candidate.status.canResolveStream()) continue
            if (!triedProviders.add(candidate.providerId)) continue
            val stream = sourceRegistry.resolveStream(candidate.providerId, candidate.id)
            if (stream != null && stream.streamUrl.isNotBlank()) {
                playable = candidate
                resolvedStream = stream
                break
            }
        }
        if (playable == null || resolvedStream == null || resolvedStream.streamUrl.isBlank()) {
            return null
        }
        if (!TrustedStreamSources.isTrustedStreamUrl(resolvedStream.streamUrl)) {
            return null
        }

        val trackId = trackRepository.addTrackSource(
            title = playable.title,
            artist = playable.artist,
            album = playable.album ?: canonical.album,
            coverArtUrl = canonical.artworkUrl ?: playable.artworkUrl,
            sourceType = SourceType.ADDON,
            streamUrl = resolvedStream.streamUrl,
            bitrate = resolvedStream.bitrateKbps,
            genre = canonical.genre,
            isrc = canonical.isrc ?: playable.isrc,
            durationMs = canonical.durationMs ?: playable.durationMs,
            externalProviderId = playable.providerId,
            externalTrackId = playable.id,
            expiresAtMs = resolvedStream.expiresAt
        )
        if (trackId <= 0L) return null
        return trackRepository.getTrackWithSources(trackId)
    }
}
