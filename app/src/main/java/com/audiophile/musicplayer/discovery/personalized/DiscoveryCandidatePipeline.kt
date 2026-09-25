package com.audiophile.musicplayer.discovery.personalized

import android.util.Log
import com.audiophile.musicplayer.data.dj.AiDjRecommendationEngine
import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceIdentityGate
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Normalizes, dedupes, scores, diversity-caps, filters junk, and resolves streams.
 * Returns only playable [UnifiedTrackWithSources].
 */
class DiscoveryCandidatePipeline(
    private val sourceRegistry: SourceRegistry,
    private val trackRepository: TrackRepository
) {
    companion object {
        private const val TAG = "DiscoveryPipeline"
    }

    suspend fun process(
        rawCandidates: List<MixCandidate>,
        config: PersonalizedMixConfig,
        excludeNormKeys: Set<String> = emptySet()
    ): List<UnifiedTrackWithSources> = withContext(Dispatchers.IO) {
        val normalized = rawCandidates
            .map { DiscoveryCandidateFilters.normalize(it) }
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .filter { it.normKey !in excludeNormKeys }

        val deduped = DiscoveryCandidateFilters.dedupe(normalized)
        val ranked = deduped.sortedByDescending { it.score }
        val diversified = DiscoveryCandidateFilters.applyDiversityCaps(ranked, config.maxPerArtist, config.maxPerAlbum)
        val filtered = diversified.filterNot { isExcludedArtifact(it) }
        val resolved = resolvePlayable(filtered, config.limit)
        Log.d(TAG, "process raw=${rawCandidates.size} deduped=${deduped.size} resolved=${resolved.size}")
        resolved
    }

    fun normalize(candidate: MixCandidate): MixCandidate = DiscoveryCandidateFilters.normalize(candidate)

    fun dedupe(candidates: List<MixCandidate>): List<MixCandidate> = DiscoveryCandidateFilters.dedupe(candidates)

    fun applyDiversityCaps(
        candidates: List<MixCandidate>,
        maxPerArtist: Int,
        maxPerAlbum: Int
    ): List<MixCandidate> = DiscoveryCandidateFilters.applyDiversityCaps(candidates, maxPerArtist, maxPerAlbum)

    private fun isExcludedArtifact(candidate: MixCandidate): Boolean {
        val stub = UnifiedTrackWithSources(
            track = com.audiophile.musicplayer.data.local.entities.UnifiedTrack(
                title = candidate.title,
                artist = candidate.artist,
                albumName = candidate.album,
                coverArtUrl = candidate.artworkUrl
            ),
            sources = emptyList()
        )
        return JukeboxTrackEligibility.shouldExcludeFromJukebox(stub)
    }

    private suspend fun resolvePlayable(
        candidates: List<MixCandidate>,
        limit: Int
    ): List<UnifiedTrackWithSources> {
        val seenTrackIds = mutableSetOf<Long>()
        val results = mutableListOf<UnifiedTrackWithSources>()
        for (candidate in candidates) {
            if (results.size >= limit) break
            val query = listOfNotNull(
                candidate.title.takeIf { it.isNotBlank() },
                candidate.artist.takeIf { it.isNotBlank() }
            ).joinToString(" ")
            if (query.isBlank()) continue

            val searchResults = sourceRegistry.searchAll(query, includeSupplemental = false)
            val matched = searchResults
                .filter { it.status.canResolveStream() }
                .filter { !SourceIdentityGate.isSupplementalPlaybackProvider(it.providerId) }
                .filter { it.isLikelyMusicTrack() }

            val triedProviders = mutableSetOf<String>()
            for (result in matched.take(4)) {
                if (!triedProviders.add(result.providerId)) continue
                try {
                    val resolved = sourceRegistry.resolveStream(result.providerId, result.id) ?: continue
                    val trackId = trackRepository.addTrackSource(
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.artworkUrl,
                        sourceType = SourceType.ADDON,
                        streamUrl = resolved.streamUrl,
                        bitrate = resolved.bitrateKbps,
                        isrc = result.isrc,
                        durationMs = result.durationMs,
                        externalProviderId = result.providerId,
                        externalTrackId = result.id,
                        expiresAtMs = resolved.expiresAt
                    )
                    val track = trackRepository.getTrackWithSources(trackId) ?: continue
                    if (!track.isPlayableMusicCandidate() || trackId in seenTrackIds) continue
                    seenTrackIds.add(trackId)
                    results.add(track)
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "resolve_failed title='${candidate.title}' reason='${e.message}'")
                }
            }
        }
        return results
    }
}

class PersonalizedMixDeps(
    val sourceRegistry: SourceRegistry,
    val trackRepository: TrackRepository,
    val tasteEngine: AiDjRecommendationEngine,
    val genreResolver: com.audiophile.musicplayer.data.lastfm.LastFmGenreResolver,
    val localLibraryRepository: LocalLibraryRepository,
    val artistResolver: DiscoveryArtistResolver,
    val candidatePipeline: DiscoveryCandidatePipeline,
    val listeningHistory: com.audiophile.musicplayer.data.local.ListeningHistoryRepository? = null
)
