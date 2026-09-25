package com.audiophile.musicplayer.radio

import android.util.Log
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import com.audiophile.musicplayer.data.source.SourceIdentityGate
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.TrustedStreamSources
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val generationToken: Long = 0L,
    /** Live station genome from [MusicGenomeEngine], already steered by thumbs. */
    val steerGenome: com.audiophile.musicplayer.radio.genome.MusicGenomeVector? = null,
    val discoveryMode: RadioDiscoveryMode = RadioDiscoveryMode.HYBRID_MIX
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
        // Apply station junk ranker/gate for Song Radio the same way streaming stations do.
        val stationSeed = StreamingStationSeed(
            id = "song_radio_${seed.title.lowercase().replace(' ', '_')}",
            displayName = "${seed.title} Radio",
            kind = StreamingStationKind.SONG,
            seedTitle = seed.title,
            seedArtist = seed.artist,
            queryPhrases = queries
        )
        val candidates = resolveCandidates(
            queries = queries,
            excludeTrackIds = excludeTrackIds,
            playedTrackIds = playedTrackIds,
            maxTracks = 40,
            stationSeed = stationSeed
        )

        if (candidates.isEmpty()) {
            return RadioGenerationResult(emptyList(), 0, 0, "not_enough_clean_tracks")
        }

        val scored = candidates.map { scoreTrack(it, seed) }.sortedByDescending { it.first }
        val finalList = applyDiversityInterleaving(
            scored.filter { it.first > 0 }.map { it.second },
            seed.artist
        )
        // Return partial lists so Song Radio can seed+supplement instead of discarding work.
        val failure = if (finalList.size < 5) "not_enough_clean_tracks" else null
        return RadioGenerationResult(finalList.take(30), candidates.size, finalList.size, failure)
    }

    suspend fun refill(seed: RadioSeed, existingTrackIds: Set<Long>, playedTrackIds: Set<Long>): List<UnifiedTrackWithSources> {
        val excludeIds = existingTrackIds + playedTrackIds
        val result = generate(seed, excludeIds, playedTrackIds)
        return result.candidates.filter { it.track.trackId !in existingTrackIds }
    }

    suspend fun generateStreamingStation(request: StreamingStationRequest): StreamingStationResult {
        val seed = request.seed
        val excludeIds = request.excludeTrackIds + request.playedTrackIds + request.taste.recentTrackIds

        // 1. Try Backend (Gemini) if available — tightly bounded so a silent
        //    backend never eats the whole station-start window.
        val backendTracks = withTimeoutOrNull(8_000L) { tryBackendFetch(seed, request, excludeIds) }.orEmpty()
        if (backendTracks.isNotEmpty()) {
            val processed = resolveCandidates(
                queries = backendTracks.map { "${it.title} ${it.artist}" },
                excludeTrackIds = excludeIds,
                playedTrackIds = request.playedTrackIds,
                maxTracks = request.targetCount,
                stationSeed = seed,
                taste = request.taste,
                steerGenome = request.steerGenome,
                discoveryMode = request.discoveryMode,
                wallClockBudgetMs = 20_000L
            )
            if (processed.size >= request.minPlayableToStart) {
                val scored = processed.map { scoreTrack(it, seed, request.taste, request.steerGenome) }.sortedByDescending { it.first }
                val finalList = applyDiversityInterleaving(scored.map { it.second }, seed.seedArtist ?: "", maxPerArtist = 2)
                return StreamingStationResult(finalList.take(request.targetCount), backendTracks.size, 0, discoverArtists(processed))
            }
        }

        // 2. Fallback to Provider Text Search
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed, request.taste)
        // Bootstrap with a small playable buffer so Up Next has tracks immediately.
        // Honour minPlayableToStart exactly: with min=1 a single verified stream
        // starts the station instead of waiting for 3 in a failure storm.
        val bootstrapTarget = request.minPlayableToStart.coerceAtLeast(1).coerceAtMost(5)
        val collected = resolveCandidates(
            queries = queries,
            excludeTrackIds = excludeIds,
            playedTrackIds = request.playedTrackIds,
            maxTracks = bootstrapTarget,
            stationSeed = seed,
            taste = request.taste,
            steerGenome = request.steerGenome,
            discoveryMode = request.discoveryMode,
            wallClockBudgetMs = 18_000L
        )

        val enriched = appendLocalEnrichment(collected, request.localEnrichment, excludeIds, request.playedTrackIds, request.taste)
        val scored = enriched.map { scoreTrack(it, seed, request.taste, request.steerGenome) }.sortedByDescending { it.first }
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
                taste = request.taste,
                steerGenome = request.steerGenome,
                discoveryMode = request.discoveryMode
            )
            val scored = processed.map { scoreTrack(it, seed, request.taste, request.steerGenome) }.sortedByDescending { it.first }
            return applyDiversityInterleaving(scored.map { it.second }, seed.seedArtist ?: "", maxPerArtist = 2)
                .filter { it.track.trackId !in existingTrackIds }
                .take(15)
        }

        // Fallback expansion (Pass 0 anchors to seed artists and catalog tracks)
        val expansion = StreamingStationQueryPlanner.planExpansionQueries(
            seed = seed,
            discoveredArtists = seed.seedArtists,
            pass = 0,
            taste = request.taste
        )
        val processed = resolveCandidates(
            queries = expansion,
            excludeTrackIds = excludeIds,
            playedTrackIds = request.playedTrackIds,
            maxTracks = 20,
            stationSeed = seed,
            taste = request.taste,
            steerGenome = request.steerGenome,
            discoveryMode = request.discoveryMode
        )
        val scored = processed.map { scoreTrack(it, seed, request.taste, request.steerGenome) }.sortedByDescending { it.first }
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
        taste: StreamingStationTasteSignals = StreamingStationTasteSignals(),
        steerGenome: com.audiophile.musicplayer.radio.genome.MusicGenomeVector? = null,
        discoveryMode: RadioDiscoveryMode = RadioDiscoveryMode.HYBRID_MIX,
        wallClockBudgetMs: Long = 30_000L
    ): List<UnifiedTrackWithSources> {
        val collected = mutableListOf<UnifiedTrackWithSources>()
        val deadlineMs = System.currentTimeMillis() + wallClockBudgetMs
        val seenKeys = mutableSetOf<String>()
        val seenStationKeys = mutableSetOf<String>()
        val artistCounts = mutableMapOf<String, Int>()

        coroutineScope {
            val deferred = queries.map { query ->
                async {
                    try { sourceRegistry.searchAll(query, timeoutMs = 10_000L, includeSupplemental = false) }
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
                        maxPerArtist = 2,
                        steerGenome = steerGenome,
                        discoveryMode = discoveryMode
                    )
                } else {
                    results
                }
                // Race top lossless candidates across active providers (Deezer, Qobuz, Tidal, Amazon)
                // so one good stream starts the station quickly.
                val toResolve = rankedResults
                    .filter { candidate ->
                        val id = candidate.id.lowercase()
                        id.startsWith("deezer:") || id.startsWith("qobuz:") || id.startsWith("tidal:") || id.startsWith("amazon:")
                    }
                    .filter { it.status.canResolveStream() && it.isLikelyMusicTrack() }
                    .filterNot { SourceIdentityGate.isSupplementalPlaybackProvider(it.providerId) }
                    .filterNot {
                        JukeboxTrackEligibility.shouldExcludeFromRadioQueue(
                            it.title, it.artist, it.durationMs, it.album
                        )
                    }
                    .sortedBy { candidateResolvePriority(it.id) }
                    .take(6)
                if (toResolve.isEmpty()) {
                    Log.d(
                        "VANTA_RADIO_ENGINE",
                        "query_has_no_playable_candidates queryIndex=$queryIndex raw=${rankedResults.size}"
                    )
                    continue
                }

                if (collected.isEmpty()) {
                    if (System.currentTimeMillis() > deadlineMs) {
                        deferred.drop(queryIndex).forEach { it.cancel() }
                        break@queryLoop
                    }
                    val raced = raceFirstTrustedStream(
                        candidates = toResolve,
                        stationSeed = stationSeed,
                        seenKeys = seenKeys,
                        seenStationKeys = seenStationKeys,
                        excludeTrackIds = excludeTrackIds,
                        playedTrackIds = playedTrackIds,
                        artistCounts = artistCounts
                    )
                    if (raced != null) {
                        collected.add(raced)
                        if (collected.size >= maxTracks) {
                            deferred.drop(queryIndex + 1).forEach { it.cancel() }
                            break@queryLoop
                        }
                        continue
                    }
                }

                val remainingSlots = maxTracks - collected.size
                val resolvable = toResolve.take(remainingSlots)
                    .filter { System.currentTimeMillis() <= deadlineMs }
                val parallelResolved = resolvable.map { result ->
                    async(Dispatchers.IO) {
                        resolveTrustedCandidate(
                            result = result,
                            stationSeed = stationSeed,
                            seenKeys = seenKeys,
                            seenStationKeys = seenStationKeys,
                            excludeTrackIds = excludeTrackIds,
                            playedTrackIds = playedTrackIds,
                            artistCounts = artistCounts
                        )
                    }
                }.map { it.await() }
                for (track in parallelResolved) {
                    if (track != null && collected.size < maxTracks && System.currentTimeMillis() <= deadlineMs) {
                        collected.add(track)
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

        // Catalog artist/title only. "greatest hits" / "songs like" queries pull
        // YouTube compilations and talk videos instead of recordings.
        if (artist.isNotBlank()) {
            queries.add(artist)
            if (title.isNotBlank()) {
                queries.add("$artist $title")
            }
        }
        if (!genre.isNullOrBlank() && artist.isNotBlank()) {
            queries.add("$artist $genre")
        }
        if (queries.isEmpty() && title.isNotBlank()) queries.add(title)

        return queries.distinct().take(8)
    }

    // --- Modern Scoring & Diversity ---

    private fun scoreTrack(track: UnifiedTrackWithSources, seed: RadioSeed): Pair<Double, UnifiedTrackWithSources> {
        var score = 50.0
        val artistLower = track.track.artist.lowercase()
        val seedArtistLower = seed.artist.lowercase()
        val title = track.track.title

        // Lexical collapse / foreign covers never enter Song Radio scoring.
        if (SongRadioRelatedness.isArtistNameCollision(seed.artist, title, track.track.artist, track.track.albumName) ||
            SongRadioRelatedness.isWeakTitleTokenSpam(seed.title, seed.artist, title, track.track.artist) ||
            SongRadioRelatedness.isForeignHitCover(seed.title, title, track.track.artist, seed.artist) ||
            SongRadioRelatedness.isListicleOrCompilationAlbum(title, track.track.artist, track.track.albumName)
        ) {
            return -1000.0 to track
        }

        // 1. Artist Match (Highest weight) — exact/contains only, not stop-token intersection
        if (SongRadioRelatedness.artistsMatch(seedArtistLower, artistLower)) {
            score += if (artistLower == seedArtistLower) 40.0 else 20.0
        }

        // 2. Genre Match (If metadata exists)
        if (!seed.genre.isNullOrBlank() && !track.track.genre.isNullOrBlank()) {
            if (track.track.genre.lowercase() == seed.genre.lowercase()) score += 25.0
        }

        // 3. Audio Quality & Metadata Completeness
        if (track.track.coverArtUrl?.startsWith("http") == true) score += 10.0
        if (track.track.albumName?.isNotBlank() == true) score += 5.0
        track.sources.maxByOrNull { it.bitrate }?.bitrate?.let { score += (it.toDouble() / 1000.0) * 0.05 }

        // 4. Musical Genome Similarity
        val seedVector = com.audiophile.musicplayer.radio.genome.MusicGenomeExtractor.extract(
            title = seed.title,
            artist = seed.artist,
            genre = seed.genre
        )
        val trackVector = com.audiophile.musicplayer.radio.genome.MusicGenomeExtractor.extract(track)
        val genomeSim = seedVector.cosineSimilarity(trackVector)
        score += (genomeSim * 30.0)

        // 5. Penalty for explicit/non-music artifacts
        if (track.track.title.contains("(Official Video)", ignoreCase = true)) score -= 10.0

        return score to track
    }

    private fun scoreTrack(
        track: UnifiedTrackWithSources,
        seed: StreamingStationSeed,
        taste: StreamingStationTasteSignals = StreamingStationTasteSignals(),
        steerGenome: com.audiophile.musicplayer.radio.genome.MusicGenomeVector? = null
    ): Pair<Double, UnifiedTrackWithSources> {
        var score = 50.0
        val artistLower = track.track.artist.lowercase()
        val seedArtistLower = seed.seedArtist?.lowercase().orEmpty()

        if (seedArtistLower.isNotBlank() && artistLower == seedArtistLower) score += 40.0
        if (track.track.coverArtUrl?.startsWith("http") == true) score += 10.0
        if (track.track.albumName?.isNotBlank() == true) score += 5.0

        // Vibe/word-echo penalty: a title that repeats the mood/genre keyword
        // ("Party Time" on a Party station) is a lexical match, not a musical
        // fit. Keep real catalogue tracks ahead of title-echo hits.
        score += StreamingStationCandidateRanker.titleEchoPenalty(seed, track.track.title)
        
        // Musical Genome Similarity — steered by the live station genome when present.
        val seedGenre = seed.hintKeywords.firstOrNull()
            ?: if (seed.kind == StreamingStationKind.GENRE) seed.displayName else null
        val referenceVector = steerGenome ?: com.audiophile.musicplayer.radio.genome.MusicGenomeExtractor.extract(
            title = seed.seedTitle ?: seed.displayName,
            artist = seed.seedArtist ?: seed.seedArtists.firstOrNull().orEmpty(),
            genre = seedGenre
        )
        val trackVector = com.audiophile.musicplayer.radio.genome.MusicGenomeExtractor.extract(track)
        val genomeSim = referenceVector.cosineSimilarity(trackVector)
        score += (genomeSim * 30.0)

        when (seed.genomeMode) {
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.CHILL -> {
                score += (trackVector.acousticWeight * 20.0)
                score -= (trackVector.energyLevel * 25.0)
                if (trackVector.tempoBpmNorm < 0.45f) score += 15.0
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.UPBEAT -> {
                score += (trackVector.energyLevel * 25.0)
                score += (trackVector.danceability * 20.0)
                if (trackVector.tempoBpmNorm > 0.48f) score += 15.0
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.DISCOVERY -> {
                val isSeedArtist = seed.seedArtists.any { SongRadioRelatedness.artistsMatch(it, track.track.artist) } ||
                    SongRadioRelatedness.artistsMatch(seed.seedArtist.orEmpty(), track.track.artist)
                if (isSeedArtist) {
                    score -= 30.0
                } else {
                    score += 15.0
                }
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.CROWD_FAVES -> {
                val isSeedArtist = seed.seedArtists.any { SongRadioRelatedness.artistsMatch(it, track.track.artist) } ||
                    SongRadioRelatedness.artistsMatch(seed.seedArtist.orEmpty(), track.track.artist)
                if (isSeedArtist) score += 20.0
                score += (trackVector.danceability * 12.0)
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.DEEP_CUTS -> {
                if (track.track.title.contains("feat.") || track.track.title.contains("remix")) score += 8.0
            }
            else -> {}
        }

        // Era fit scoring (Assuming StreamingEraFilter exists)
        score += StreamingEraFilter.eraFitScore(seed, track.track.title, track.track.artist, track.track.albumName).toDouble()
        
        // Taste signals
        score += taste.artistPenalty(track.track.artist).toDouble()
        score += taste.genreBonus(track.track.genre).toDouble() * 2.0

        return score to track
    }

    /**
     * Artist/title diversity interleaving. Caps seed artist around 20–25% and prevents
     * immediate same-artist spam so related artists can surface.
     */
    private fun applyDiversityInterleaving(
        sortedTracks: List<UnifiedTrackWithSources>,
        seedArtist: String,
        maxPerArtist: Int = 2,
        seedArtistShareCap: Double = 0.25
    ): List<UnifiedTrackWithSources> {
        val result = mutableListOf<UnifiedTrackWithSources>()
        val seedArtistKey = seedArtist.trim().lowercase()
        val artistBuckets = sortedTracks
            .groupBy { it.track.artist.trim().lowercase().takeIf { artist -> artist.isNotBlank() } ?: "unknown" }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
        val seedBucket = artistBuckets.remove(seedArtistKey) ?: mutableListOf()

        var seedCount = 0
        var otherCount = 0
        val totalPerArtist = mutableMapOf<String, Int>()
        var seedArtistTotal = 0

        while (result.size < sortedTracks.size && (seedBucket.isNotEmpty() || artistBuckets.any { it.value.isNotEmpty() })) {
            val projectedSeedShare = if (result.isEmpty()) {
                1.0
            } else {
                (seedArtistTotal + 1).toDouble() / (result.size + 1).toDouble()
            }
            val seedShareExceeded = result.size >= 3 && projectedSeedShare > seedArtistShareCap

            val takeFromSeed = when {
                seedBucket.isEmpty() -> false
                artistBuckets.all { it.value.isEmpty() } -> true
                seedShareExceeded -> false
                seedCount >= maxPerArtist -> false
                otherCount >= 2 -> true
                result.isEmpty() -> true
                else -> false
            }

            if (takeFromSeed && seedBucket.isNotEmpty()) {
                val track = seedBucket.removeAt(0)
                val artistKey = track.track.artist.trim().lowercase().takeIf { it.isNotBlank() } ?: "unknown"
                val artistTotal = totalPerArtist[artistKey] ?: 0
                if (result.size < 10 && artistTotal >= maxPerArtist) continue
                result.add(track)
                totalPerArtist[artistKey] = artistTotal + 1
                seedArtistTotal++
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
                    totalPerArtist[artistKey] = artistTotal + 1
                    otherCount++
                    seedCount = 0
                } else if (seedBucket.isNotEmpty() && !seedShareExceeded) {
                    result.add(seedBucket.removeAt(0))
                    seedArtistTotal++
                } else {
                    break
                }
            }
        }

        return result
    }

    // --- Utilities ---

    private suspend fun raceFirstTrustedStream(
        candidates: List<SourceSearchResult>,
        stationSeed: StreamingStationSeed?,
        seenKeys: MutableSet<String>,
        seenStationKeys: MutableSet<String>,
        excludeTrackIds: Set<Long>,
        playedTrackIds: Set<Long>,
        artistCounts: MutableMap<String, Int>
    ): UnifiedTrackWithSources? = supervisorScope {
        if (candidates.isEmpty()) return@supervisorScope null
        val winner = CompletableDeferred<UnifiedTrackWithSources>()
        val workers = candidates.map { candidate ->
            launch(Dispatchers.IO) {
                val track = resolveTrustedCandidate(
                    result = candidate,
                    stationSeed = stationSeed,
                    seenKeys = seenKeys,
                    seenStationKeys = seenStationKeys,
                    excludeTrackIds = excludeTrackIds,
                    playedTrackIds = playedTrackIds,
                    artistCounts = artistCounts
                ) ?: return@launch
                winner.complete(track)
            }
        }
        try {
            withTimeoutOrNull(7_000L) { winner.await() }
                ?: if (winner.isCompleted) winner.await() else null
        } finally {
            workers.forEach { it.cancel() }
        }
    }

    private suspend fun resolveTrustedCandidate(
        result: SourceSearchResult,
        stationSeed: StreamingStationSeed?,
        seenKeys: MutableSet<String>,
        seenStationKeys: MutableSet<String>,
        excludeTrackIds: Set<Long>,
        playedTrackIds: Set<Long>,
        artistCounts: MutableMap<String, Int>
    ): UnifiedTrackWithSources? {
        if (stationSeed != null) {
            val gate = PlaybackIdentityGate.verifyCandidate(
                title = result.title,
                artist = result.artist,
                album = result.album,
                durationMs = result.durationMs
            )
            if (gate is GateVerdict.Failed) return null
            if (SongRadioRelatedness.isSongRadioKind(stationSeed.kind)) {
                if (SongRadioRelatedness.isArtistNameCollision(
                        stationSeed.seedArtist, result.title, result.artist, result.album
                    ) ||
                    SongRadioRelatedness.isWeakTitleTokenSpam(
                        stationSeed.seedTitle, stationSeed.seedArtist, result.title, result.artist
                    ) ||
                    SongRadioRelatedness.isForeignHitCover(
                        stationSeed.seedTitle, result.title, result.artist, stationSeed.seedArtist
                    ) ||
                    SongRadioRelatedness.isListicleOrCompilationAlbum(
                        result.title, result.artist, result.album
                    )
                ) {
                    return null
                }
            }
        }

        val normKey = identityKey(result.title, result.artist, result.isrc)
        val stationKey = StreamingStationCandidateRanker.normalizeCandidateKey(result.title, result.artist)
        synchronized(seenKeys) {
            if (normKey in seenKeys || stationKey in seenStationKeys) return null
        }

        return try {
            val resolved = sourceRegistry.resolveStream(
                result.providerId,
                result.id,
                timeoutMs = 7_000L
            ) ?: return null
            if (resolved.streamUrl.isBlank()) return null
            if (!TrustedStreamSources.isTrustedStreamUrl(resolved.streamUrl)) {
                Log.d(
                    "VANTA_RADIO_ENGINE",
                    "rejected_untrusted_stream provider=${result.providerId} url=${resolved.streamUrl.take(80)} title='${result.title}'"
                )
                return null
            }

            val cleanArtist = DisplayMetadataCleaner.cleanArtistName(result.artist) ?: result.artist
            val displayMeta = DisplayMetadataCleaner.computeDisplayMetadata(
                result.title, cleanArtist, result.album, providerId = result.providerId
            )
            val trackId = trackRepository.addTrackSource(
                title = displayMeta.title,
                artist = displayMeta.artist,
                album = displayMeta.album ?: result.album,
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
            val track = trackRepository.getTrackWithSources(trackId) ?: return null
            if (track.track.trackId in excludeTrackIds || track.track.trackId in playedTrackIds) return null
            if (!track.sourceValidityStatus().canResolveStream()) return null
            val resolvedGate = PlaybackIdentityGate.verify(track)
            if (resolvedGate is GateVerdict.Failed) return null

            synchronized(seenKeys) {
                if (normKey in seenKeys || stationKey in seenStationKeys) return null
                seenKeys.add(normKey)
                seenStationKeys.add(stationKey)
            }
            val artistKey = track.track.artist.trim().lowercase()
            if (artistKey.isNotBlank()) {
                synchronized(artistCounts) {
                    artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
                }
            }
            Log.d(
                "VANTA_RADIO_ENGINE",
                "trusted_stream_ok id=${result.id} title='${result.title}'"
            )
            track
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VANTA_RADIO_ENGINE", "resolve_error: ${e.message}")
            null
        }
    }

    /** Skip catalog ids that currently cannot stream without expired community sessions,
     *  or that the gateway frequently misroutes (qobuz → SoundCloud for mood radio). */
    private fun isSlowOrUnplayableCatalogId(trackId: String): Boolean {
        val id = trackId.lowercase()
        return id.startsWith("spotify:") ||
            id.startsWith("amazon:") ||
            id.startsWith("apple:") ||
            id.startsWith("tidal:") ||
            id.startsWith("qobuz:")
    }

    private fun candidateResolvePriority(trackId: String): Int {
        val id = trackId.lowercase()
        return when {
            id.startsWith("deezer:") -> 0
            id.startsWith("qobuz:") -> 1
            id.startsWith("tidal:") -> 2
            id.startsWith("amazon:") -> 3
            id.startsWith("spotify:") -> 10
            id.startsWith("apple:") -> 11
            else -> 5
        }
    }

    private fun normalizeKey(title: String, artist: String): String {
        return "${title.lowercase().replace(Regex("[^a-z0-9]"), "")}|${artist.lowercase().replace(Regex("[^a-z0-9]"), "")}"
    }

    /** Prefer ISRC identity so cross-provider duplicates collapse in radio queues. */
    private fun identityKey(title: String, artist: String, isrc: String?): String {
        val cleanIsrc = isrc?.trim()?.takeIf { it.isNotBlank() }
        if (cleanIsrc != null) return "isrc:${cleanIsrc.uppercase()}"
        return "norm:${normalizeKey(title, artist)}"
    }

    private fun identityKey(track: UnifiedTrackWithSources): String =
        identityKey(track.track.title, track.track.artist, track.track.isrc)

    private fun discoverArtists(candidates: List<UnifiedTrackWithSources>): List<String> {
        return candidates.map { it.track.artist.trim() }.filter { it.length >= 2 }.distinct().take(12)
    }

    private fun appendLocalEnrichment(
        catalogTracks: List<UnifiedTrackWithSources>, localTracks: List<UnifiedTrackWithSources>,
        excludeIds: Set<Long>, playedIds: Set<Long>, taste: StreamingStationTasteSignals
    ): List<UnifiedTrackWithSources> {
        if (localTracks.isEmpty()) return catalogTracks
        val seen = catalogTracks.map { identityKey(it) }.toMutableSet()
        val bonus = mutableListOf<UnifiedTrackWithSources>()
        
        for (track in localTracks) {
            if (!track.isPlayableMusicCandidate()) continue
            if (track.track.trackId in excludeIds || track.track.trackId in playedIds) continue
            val key = identityKey(track)
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
                trackRepository.getTrackWithSources(id)?.let { SimpleTrackRef(it.track.title, it.track.artist) }
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
