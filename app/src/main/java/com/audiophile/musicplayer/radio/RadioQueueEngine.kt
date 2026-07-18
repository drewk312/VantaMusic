package com.audiophile.musicplayer.radio

import android.util.Log
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class RadioSeed(val title: String, val artist: String, val album: String?, val genre: String?)
data class RadioGenerationResult(val candidates: List<UnifiedTrackWithSources>, val totalGenerated: Int, val totalPlayableAfterFilter: Int, val failureReason: String? = null) {
    val isSuccess: Boolean get() = failureReason == null && candidates.size >= 5
}

data class StreamingStationRequest(
    val seed: StreamingStationSeed,
    val excludeTrackIds: Set<Long> = emptySet(),
    val playedTrackIds: Set<Long> = emptySet(),
    val taste: StreamingStationTasteSignals = StreamingStationTasteSignals(),
    val localEnrichment: List<UnifiedTrackWithSources> = emptyList(),
    val targetCount: Int = 30,
    val minPlayableToStart: Int = 3,
    val fastPreview: Boolean = false,
    val generationToken: Long = 0L
)

data class StreamingStationResult(val candidates: List<UnifiedTrackWithSources>, val totalGenerated: Int, val queriesExecuted: Int, val discoveredArtists: List<String> = emptyList(), val failureReason: String? = null) {
    val canStartPlayback: Boolean get() = failureReason == null && candidates.isNotEmpty()
    val isFullyReady: Boolean get() = failureReason == null && candidates.size >= 5
}

class RadioQueueEngine(
    private val sourceRegistry: SourceRegistry,
    private val trackRepository: TrackRepository,
    private val radioApiService: RadioApiService? = null
) {

    suspend fun generate(seed: RadioSeed, excludeTrackIds: Set<Long>, playedTrackIds: Set<Long>): RadioGenerationResult {
        val queries = buildDynamicQueries(seed)
        val candidates = resolveCandidates(queries, excludeTrackIds, playedTrackIds, maxTracks = 40)
        
        if (candidates.size < 5) {
            return RadioGenerationResult(emptyList(), candidates.size, candidates.size, "not_enough_clean_tracks")
        }

        val scored = candidates.map { scoreTrack(it, seed) }.sortedByDescending { it.first }
        val finalList = applyDiversityInterleaving(scored.map { it.second }, seed.artist)

        return RadioGenerationResult(finalList.take(30), candidates.size, finalList.size)
    }

    suspend fun refill(seed: RadioSeed, existingTrackIds: Set<Long>, playedTrackIds: Set<Long>): List<UnifiedTrackWithSources> {
        val excludeIds = existingTrackIds + playedTrackIds
        val result = generate(seed, excludeIds, playedTrackIds)
        return result.candidates.filter { it.track.trackId !in existingTrackIds }
    }

    suspend fun generateStreamingStation(request: StreamingStationRequest): StreamingStationResult {
        val seed = request.seed
        val excludeIds = request.excludeTrackIds + request.playedTrackIds + request.taste.recentTrackIds

        // 1. Try Backend (Gemini) if available
        val backendTracks = tryBackendFetch(seed, request, excludeIds)
        if (backendTracks.isNotEmpty()) {
            val processed = resolveCandidates(
                queries = backendTracks.map { "${it.title} ${it.artist}" },
                excludeTrackIds = excludeIds,
                playedTrackIds = request.playedTrackIds,
                maxTracks = request.targetCount,
                stationSeed = seed,
                taste = request.taste
            )
            if (processed.size >= request.minPlayableToStart) {
                val scored = processed.map { scoreTrack(it, seed, request.taste) }.sortedByDescending { it.first }
                val finalList = applyDiversityInterleaving(scored.map { it.second }, seed.seedArtist ?: "", maxPerArtist = 2)
                return StreamingStationResult(finalList.take(request.targetCount), backendTracks.size, 0, discoverArtists(processed))
            }
        }

        // 2. Fallback to Provider Text Search
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed, request.taste)
        // Start on a compact verified queue, then let the existing refill path
        // build the full station in the background. Waiting for 20 resolved
        // tracks before the first note made Radio feel stalled.
        val bootstrapTarget = request.targetCount.coerceAtMost(maxOf(request.minPlayableToStart + 3, 8))
        val collected = resolveCandidates(
            queries = queries,
            excludeTrackIds = excludeIds,
            playedTrackIds = request.playedTrackIds,
            maxTracks = bootstrapTarget,
            stationSeed = seed,
            taste = request.taste
        )
        
        val enriched = appendLocalEnrichment(collected, request.localEnrichment, excludeIds, request.playedTrackIds, request.taste)
        val scored = enriched.map { scoreTrack(it, seed, request.taste) }.sortedByDescending { it.first }
        val finalList = applyDiversityInterleaving(scored.map { it.second }, seed.seedArtist ?: "", maxPerArtist = 2)

        if (finalList.size < request.minPlayableToStart) {
            return StreamingStationResult(emptyList(), collected.size, queries.size, discoverArtists(collected), "not_enough_streaming_tracks")
        }

        return StreamingStationResult(finalList.take(request.targetCount), collected.size, queries.size, discoverArtists(collected))
    }

    suspend fun refillStreamingStation(request: StreamingStationRequest, existingTrackIds: Set<Long>): List<UnifiedTrackWithSources> {
        val seed = request.seed
        val excludeIds = existingTrackIds + request.playedTrackIds + request.taste.recentTrackIds
        
        // Attempt backend refill first
        val backendRefill = tryBackendFetch(seed, request.copy(targetCount = 15), excludeIds)
        if (backendRefill.isNotEmpty()) {
            val processed = resolveCandidates(
                queries = backendRefill.map { "${it.title} ${it.artist}" },
                excludeTrackIds = excludeIds,
                playedTrackIds = request.playedTrackIds,
                maxTracks = 15,
                stationSeed = seed,
                taste = request.taste
            )
            val scored = processed.map { scoreTrack(it, seed, request.taste) }.sortedByDescending { it.first }
            return applyDiversityInterleaving(scored.map { it.second }, seed.seedArtist ?: "", maxPerArtist = 2)
                .filter { it.track.trackId !in existingTrackIds }
                .take(15)
        }

        // Fallback expansion
        val expansion = StreamingStationQueryPlanner.planExpansionQueries(
            seed = seed,
            discoveredArtists = seed.seedArtists,
            pass = 2,
            taste = request.taste
        )
        val processed = resolveCandidates(
            queries = expansion,
            excludeTrackIds = excludeIds,
            playedTrackIds = request.playedTrackIds,
            maxTracks = 20,
            stationSeed = seed,
            taste = request.taste
        )
        val scored = processed.map { scoreTrack(it, seed, request.taste) }.sortedByDescending { it.first }
        return applyDiversityInterleaving(scored.map { it.second }, seed.seedArtist ?: "", maxPerArtist = 2)
            .filter { it.track.trackId !in existingTrackIds }
            .take(15)
    }

    // --- Core Pipeline ---

    private suspend fun resolveCandidates(
        queries: List<String>,
        excludeTrackIds: Set<Long>,
        playedTrackIds: Set<Long>,
        maxTracks: Int,
        stationSeed: StreamingStationSeed? = null,
        taste: StreamingStationTasteSignals = StreamingStationTasteSignals()
    ): List<UnifiedTrackWithSources> {
        val collected = mutableListOf<UnifiedTrackWithSources>()
        val seenKeys = mutableSetOf<String>()
        val seenStationKeys = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()

        coroutineScope {
            val deferred = queries.map { query ->
                async {
                    try { sourceRegistry.searchAll(query, timeoutMs = 10_000L) }
                    catch (e: Exception) { emptyList() }
                }
            }

            queryLoop@ for ((queryIndex, deferredResult) in deferred.withIndex()) {
                val results = deferredResult.await()
                val rankedResults = if (stationSeed != null) {
                    StreamingStationCandidateRanker.rankCandidates(
                        results = results,
                        seed = stationSeed,
                        taste = taste,
                        seenNormKeys = seenStationKeys,
                        artistCounts = artistCounts,
                        maxPerArtist = 2
                    )
                } else {
                    results
                }
                for (result in rankedResults) {
                    if (collected.size >= maxTracks) break

                    if (!result.status.canResolveStream() || !result.isLikelyMusicTrack()) continue

                    if (stationSeed != null) {
                        val gate = PlaybackIdentityGate.verifyCandidate(
                            title = result.title,
                            artist = result.artist,
                            album = result.album,
                            durationMs = result.durationMs
                        )
                        if (gate is GateVerdict.Failed) {
                            Log.d(
                                "VANTA_RADIO_ENGINE",
                                "candidate_rejected_before_resolve reason=${gate.reason} title='${result.title}' artist='${result.artist}'"
                            )
                            continue
                        }
                    }

                    val normKey = normalizeKey(result.title, result.artist)
                    val stationKey = StreamingStationCandidateRanker.normalizeCandidateKey(result.title, result.artist)
                    val isrcKey = result.isrc?.takeIf { it.isNotBlank() }?.let { "isrc:$it" }
                    if (normKey in seenKeys || stationKey in seenStationKeys || (isrcKey != null && isrcKey in seenKeys)) continue

                    try {
                        val resolved = sourceRegistry.resolveStream(result.providerId, result.id, timeoutMs = 10_000L) ?: continue
                        if (resolved.streamUrl.isBlank()) continue

                        val cleanArtist = DisplayMetadataCleaner.cleanArtistName(result.artist) ?: result.artist
                        val displayMeta = DisplayMetadataCleaner.computeDisplayMetadata(result.title, cleanArtist, result.album, providerId = result.providerId)

                        val trackId = trackRepository.addTrackSource(
                            title = displayMeta.title, artist = displayMeta.artist, album = displayMeta.album ?: result.album,
                            coverArtUrl = result.artworkUrl, sourceType = SourceType.ADDON, streamUrl = resolved.streamUrl,
                            bitrate = resolved.bitrateKbps, isrc = result.isrc, durationMs = result.durationMs,
                            externalProviderId = result.providerId, externalTrackId = result.id, expiresAtMs = resolved.expiresAt
                        )

                        val track = trackRepository.getTrackWithSources(trackId) ?: continue
                        if (track.track.trackId in excludeTrackIds || track.track.trackId in playedTrackIds) continue
                        if (!track.sourceValidityStatus().canResolveStream()) continue
                        val resolvedGate = PlaybackIdentityGate.verify(track)
                        if (resolvedGate is GateVerdict.Failed) {
                            Log.d(
                                "VANTA_RADIO_ENGINE",
                                "candidate_rejected_after_resolve reason=${resolvedGate.reason} title='${track.track.title}' artist='${track.track.artist}'"
                            )
                            continue
                        }

                        seenKeys.add(normKey)
                        seenStationKeys.add(stationKey)
                        isrcKey?.let { seenKeys.add(it) }
                        collected.add(track)
                        val artistKey = track.track.artist?.trim()?.lowercase().orEmpty()
                        if (artistKey.isNotBlank()) {
                            artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
                        }
                    } catch (e: Exception) {
                        Log.w("VANTA_RADIO_ENGINE", "resolve_error: ${e.message}")
                    }
                }
                if (collected.size >= maxTracks) {
                    // Do not wait for unrelated searches after we have a
                    // playable starting queue. Their work is superseded by the
                    // station's background expansion request.
                    deferred.drop(queryIndex + 1).forEach { it.cancel() }
                    break@queryLoop
                }
            }
        }
        return collected
    }

    // --- Dynamic Query Generation (Replaces Hardcoded Maps) ---

    private fun buildDynamicQueries(seed: RadioSeed): List<String> {
        val queries = mutableListOf<String>()
        val artist = seed.artist.trim()
        val title = seed.title.trim()
        val genre = seed.genre?.trim()

        if (artist.isNotBlank()) {
            queries.add("$artist top songs")
            queries.add("$artist similar artists")
            queries.add("$artist greatest hits")
        }
        if (title.isNotBlank() && artist.isNotBlank()) {
            queries.add("songs like $title $artist")
        }
        if (genre.isNullOrBlank().not()) {
            queries.add("best $genre songs")
            queries.add("$genre radio hits")
        }
        // Fallback broad query
        if (queries.isEmpty()) queries.add("popular music hits")

        return queries.distinct().take(8) // Limit to 8 tight queries to prevent API spam
    }

    // --- Modern Scoring & Diversity ---

    private fun scoreTrack(track: UnifiedTrackWithSources, seed: RadioSeed): Pair<Double, UnifiedTrackWithSources> {
        var score = 50.0
        val artistLower = track.track.artist?.lowercase().orEmpty()
        val seedArtistLower = seed.artist.lowercase()

        // 1. Artist Match (Highest weight)
        if (artistLower == seedArtistLower) score += 40.0
        else if (artistLower.split(Regex("\\s+")).intersect(seedArtistLower.split(Regex("\\s+"))).isNotEmpty()) score += 15.0

        // 2. Genre Match (If metadata exists)
        if (!seed.genre.isNullOrBlank() && !track.track.genre.isNullOrBlank()) {
            if (track.track.genre?.lowercase() == seed.genre?.lowercase()) score += 25.0
        }

        // 3. Audio Quality & Metadata Completeness
        if (track.track.coverArtUrl?.startsWith("http") == true) score += 10.0
        if (track.track.albumName?.isNotBlank() == true) score += 5.0
        track.sources.maxByOrNull { it.bitrate ?: 0 }?.bitrate?.let { score += (it.toDouble() / 1000.0) * 0.05 } // 320kbps = +16pts

        // 4. Penalty for explicit/non-music artifacts
        if (track.track.title?.contains("(Official Video)", ignoreCase = true) == true) score -= 10.0

        return score to track
    }

    private fun scoreTrack(track: UnifiedTrackWithSources, seed: StreamingStationSeed, taste: StreamingStationTasteSignals = StreamingStationTasteSignals()): Pair<Double, UnifiedTrackWithSources> {
        var score = 50.0
        val artistLower = track.track.artist?.lowercase().orEmpty()
        val seedArtistLower = seed.seedArtist?.lowercase().orEmpty()

        if (seedArtistLower.isNotBlank() && artistLower == seedArtistLower) score += 40.0
        if (track.track.coverArtUrl?.startsWith("http") == true) score += 10.0
        if (track.track.albumName?.isNotBlank() == true) score += 5.0
        
        // Era fit scoring (Assuming StreamingEraFilter exists)
        score += StreamingEraFilter.eraFitScore(seed, track.track.title ?: "", track.track.artist ?: "", track.track.albumName).toDouble()
        
        // Taste signals
        score += taste.artistPenalty(track.track.artist).toDouble()
        score += taste.genreBonus(track.track.genre).toDouble() * 2.0

        return score to track
    }

    /**
     * Pandora-style interleaving. Prevents the radio from playing 3 tracks from the 
     * same artist in a row, distributing variety evenly through the queue.
     */
    private fun applyDiversityInterleaving(
        sortedTracks: List<UnifiedTrackWithSources>,
        seedArtist: String,
        maxPerArtist: Int = 2
    ): List<UnifiedTrackWithSources> {
        val result = mutableListOf<UnifiedTrackWithSources>()
        val seedArtistKey = seedArtist.trim().lowercase()
        val artistBuckets = sortedTracks
            .groupBy { it.track.artist?.trim()?.lowercase()?.takeIf { artist -> artist.isNotBlank() } ?: "unknown" }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
        val seedBucket = artistBuckets.remove(seedArtistKey) ?: mutableListOf()

        var seedCount = 0
        var otherCount = 0
        val totalPerArtist = mutableMapOf<String, Int>()

        while (result.size < sortedTracks.size && (seedBucket.isNotEmpty() || artistBuckets.any { it.value.isNotEmpty() })) {
            val takeFromSeed = when {
                seedBucket.isEmpty() -> false
                artistBuckets.all { it.value.isEmpty() } -> true
                seedCount >= maxPerArtist -> false
                otherCount >= 2 -> true
                result.isEmpty() -> true
                else -> false
            }

            if (takeFromSeed && seedBucket.isNotEmpty()) {
                val track = seedBucket.removeAt(0)
                val artistKey = track.track.artist?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "unknown"
                val artistTotal = totalPerArtist[artistKey] ?: 0
                if (result.size < 10 && artistTotal >= maxPerArtist) continue
                result.add(track)
                totalPerArtist[artistKey] = (totalPerArtist[artistKey] ?: 0) + 1
                seedCount++
                otherCount = 0
            } else {
                val nextEntry = artistBuckets
                    .filter { it.value.isNotEmpty() }
                    .minWithOrNull(
                        compareBy<Map.Entry<String, MutableList<UnifiedTrackWithSources>>> { totalPerArtist[it.key] ?: 0 }
                            .thenBy { sortedTracks.indexOf(it.value.first()) }
                    )
                if (nextEntry != null) {
                    val bucket = nextEntry.value
                    val track = bucket.removeAt(0)
                    val artistKey = nextEntry.key
                    val artistTotal = totalPerArtist[artistKey] ?: 0
                    if (result.size < 10 && artistTotal >= maxPerArtist) continue
                    result.add(track)
                    totalPerArtist[artistKey] = (totalPerArtist[artistKey] ?: 0) + 1
                    otherCount++
                    seedCount = 0
                } else if (seedBucket.isNotEmpty()) {
                    result.add(seedBucket.removeAt(0))
                }
            }
        }

        return result
    }

    // --- Utilities ---

    private fun normalizeKey(title: String, artist: String): String {
        return "${title.lowercase().replace(Regex("[^a-z0-9]"), "")}|${artist.lowercase().replace(Regex("[^a-z0-9]"), "")}"
    }

    private fun discoverArtists(candidates: List<UnifiedTrackWithSources>): List<String> {
        return candidates.mapNotNull { it.track.artist?.trim() }.filter { it.length >= 2 }.distinct().take(12)
    }

    private fun appendLocalEnrichment(
        catalogTracks: List<UnifiedTrackWithSources>, localTracks: List<UnifiedTrackWithSources>,
        excludeIds: Set<Long>, playedIds: Set<Long>, taste: StreamingStationTasteSignals
    ): List<UnifiedTrackWithSources> {
        if (localTracks.isEmpty()) return catalogTracks
        val seen = catalogTracks.map { normalizeKey(it.track.title ?: "", it.track.artist ?: "") }.toMutableSet()
        val bonus = mutableListOf<UnifiedTrackWithSources>()
        
        for (track in localTracks) {
            if (!track.isPlayableMusicCandidate()) continue
            if (track.track.trackId in excludeIds || track.track.trackId in playedIds) continue
            val key = normalizeKey(track.track.title ?: "", track.track.artist ?: "")
            if (key in seen) continue
            seen.add(key)
            bonus.add(track)
            if (bonus.size >= 5) break
        }
        return catalogTracks + bonus
    }

    // --- Backend Helpers ---

    private suspend fun tryBackendFetch(seed: StreamingStationSeed, request: StreamingStationRequest, excludeIds: Set<Long>): List<TrackRecommendationV1> {
        if (radioApiService == null) return emptyList()
        return try {
            val recentHistory = request.playedTrackIds.take(10).mapNotNull { id ->
                trackRepository.getTrackWithSources(id)?.let { SimpleTrackRef(it.track.title ?: "", it.track.artist ?: "") }
            }
            val apiRequest = RadioBackendRequestPlanner.buildGenerateStationRequest(
                seed = seed,
                request = request,
                recentHistory = recentHistory
            )
            val response = radioApiService.generateStation(apiRequest)
            if (response.isSuccessful) response.body()?.tracks.orEmpty() else emptyList()
        } catch (e: Exception) {
            Log.w("VANTA_RADIO_ENGINE", "backend_fetch_failed", e)
            emptyList()
        }
    }
}
