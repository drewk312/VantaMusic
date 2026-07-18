package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.common.VantaResult
import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import com.audiophile.musicplayer.data.dj.StreamingSeedParams
import com.audiophile.musicplayer.data.dj.toStreamingSeed
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.QueueMode
import com.audiophile.musicplayer.radio.PlaybackIdentityGate
import com.audiophile.musicplayer.radio.RadioBrain
import com.audiophile.musicplayer.radio.RadioEnergyLevel
import com.audiophile.musicplayer.radio.RadioSeedType
import com.audiophile.musicplayer.radio.RadioStationIntent
import com.audiophile.musicplayer.radio.StreamingStationRequest
import com.audiophile.musicplayer.radio.StreamingStationSeed
import com.audiophile.musicplayer.radio.StreamingStationSeedResolver
import com.audiophile.musicplayer.radio.StreamingStationKind
import com.audiophile.musicplayer.radio.toStreamingSeedParams
import com.audiophile.musicplayer.radio.toStationSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StationUiState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val activeStationName: String? = null,
    val error: String? = null,
)

sealed class StationUiEvent {
    data class StartJukeboxStation(val stationId: String, val shuffle: Boolean = false) : StationUiEvent()
    data class StartStreamingStation(val userInput: String, val shuffle: Boolean = false) : StationUiEvent()
    data object StopStation : StationUiEvent()
    data class PreviewStation(val stationId: String) : StationUiEvent()
}

/**
 * Owns AI station and radio playback: jukebox stations, streaming stations,
 * and the endless background refill monitor.
 *
 * Previously embedded in [MainViewModel]; extracted for single-responsibility.
 */
@HiltViewModel
class StationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(StationUiState())
    val uiState: StateFlow<StationUiState> = _uiState.asStateFlow()

    // -- Endless-station state -------------------------------------------------
    private var activeStationSeed: StreamingSeedParams? = null
    private val playedStationTrackIds = ArrayDeque<String>(500)
    private var isRefillingStation = false
    private var stationMonitorJob: Job? = null
    private val REFILL_THRESHOLD = 5
    private var generationToken = 0L
    private var lastGenerationToken = 0L

    fun onEvent(event: StationUiEvent) {
        when (event) {
            is StationUiEvent.StartJukeboxStation -> startJukeboxStation(event.stationId, event.shuffle)
            is StationUiEvent.StartStreamingStation -> startStreamingStation(event.userInput, event.shuffle)
            is StationUiEvent.StopStation -> stopStationMonitor()
            is StationUiEvent.PreviewStation -> { /* handled via suspend fun previewStation */ }
        }
    }

    suspend fun previewStation(stationId: String): List<UnifiedTrackWithSources> =
        container.radioStationPreviewLoader.previewTracks(stationId)

    fun startJukeboxStation(stationId: String, shuffle: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Curating station...", error = null) }
            try {
                val station = JukeboxCatalog.resolveStation(stationId, container.customStationStore)
                if (station == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Station not found") }
                    return@launch
                }
                val seedParams = station.toStreamingSeed()
                activeStationSeed = seedParams
                playedStationTrackIds.clear()
                stopStationMonitor()

                val stationSeed = seedParams.toStationSeed()
                withContext(Dispatchers.IO) {
                    container.queueManager.setStreamingStationSeed(stationSeed)
                }
                val result = withContext(Dispatchers.IO) {
                    container.radioQueueEngine.generateStreamingStation(
                        StreamingStationRequest(
                            seed = stationSeed,
                            targetCount = 30,
                            minPlayableToStart = 3
                        )
                    )
                }

                if (!result.canStartPlayback) {
                    withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(null) }
                    _uiState.update { it.copy(isLoading = false, error = "No tracks found for ${station.name}") }
                    return@launch
                }

                val intent = RadioBrain.buildIntent(
                    seedType = when (stationSeed.kind) {
                        StreamingStationKind.SONG,
                        StreamingStationKind.SONG_SIMILAR -> RadioSeedType.SONG
                        StreamingStationKind.ARTIST -> RadioSeedType.ARTIST
                        StreamingStationKind.MOOD -> RadioSeedType.MOOD
                        StreamingStationKind.ERA -> RadioSeedType.ERA
                        else -> RadioSeedType.PROMPT
                    },
                    trackTitle = stationSeed.seedTitle,
                    trackArtist = stationSeed.seedArtist,
                    userInput = station.name
                )
                val (verifiedQueue, gateRejections) = RadioBrain.buildVerifiedQueue(
                    tracks = result.candidates,
                    intent = intent,
                    previousTrackIds = emptySet()
                )
                if (verifiedQueue.size < 3) {
                    VantaLogger.w(
                        VantaLogger.Tag.STATION,
                        "jukebox_all_rejected_by_gate station='${station.name}' rejections=${gateRejections.take(10).joinToString("|")}"
                    )
                    withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(null) }
                    _uiState.update { it.copy(isLoading = false, error = "No playable tracks found for ${station.name}") }
                    return@launch
                }

                val finalTracks = prepareStationQueue(stationSeed, verifiedQueue, shuffle)
                if (finalTracks.isEmpty()) {
                    withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(null) }
                    _uiState.update { it.copy(isLoading = false, error = "No playable tracks found for ${station.name}") }
                    return@launch
                }
                rememberStationTracks(finalTracks)

                withContext(Dispatchers.Main) {
                    container.playerController.playQueue(finalTracks, 0, QueueMode.STREAMING_STATION)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeStationName = station.name,
                            statusMessage = "Now playing: ${station.name}"
                        )
                    }
                }
                startStationMonitor()
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.STATION, "jukebox_start_failed stationId=$stationId", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to start station: ${e.message}") }
            }
        }
    }

    fun startStreamingStation(userInput: String, shuffle: Boolean = false) {
        val trimmed = userInput.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            stopStationMonitor()
            generationToken++
            val currentToken = generationToken
            _uiState.update { it.copy(isLoading = true, statusMessage = "Building station\u2026", error = null) }

            val seed = withContext(Dispatchers.IO) { StreamingStationSeedResolver.fromUserInput(trimmed) }
            activeStationSeed = seed.toStreamingSeedParams()
            playedStationTrackIds.clear()
            val taste = withContext(Dispatchers.IO) { container.aiDjRecommendationEngine.streamingTasteSignals() }
            val playedIds = withContext(Dispatchers.IO) { container.queueManager.playedHistory.toSet() }
            val currentQueueIds = withContext(Dispatchers.IO) { container.queueManager.originalQueue.map { it.track.trackId }.toSet() }
            val minPlayableToStart = 4

            VantaLogger.d(VantaLogger.Tag.STATION, "start seed='${seed.displayName}' kind=${seed.kind} token=$currentToken")

            val bootstrap = withContext(Dispatchers.IO) {
                container.radioQueueEngine.generateStreamingStation(
                    StreamingStationRequest(
                        seed = seed,
                        excludeTrackIds = currentQueueIds,
                        playedTrackIds = playedIds,
                        taste = taste,
                        targetCount = 20,
                        minPlayableToStart = minPlayableToStart,
                        generationToken = currentToken
                    )
                )
            }

            if (!bootstrap.canStartPlayback) {
                VantaLogger.w(VantaLogger.Tag.STATION, "failed seed='${seed.displayName}' reason='${bootstrap.failureReason}'")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Couldn't find playable tracks for ${seed.displayName}. Check your music sources in Settings."
                    )
                }
                return@launch
            }

            withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(seed) }

            val intent = RadioBrain.buildIntent(
                seedType = when (seed.kind) {
                    StreamingStationKind.SONG,
                    StreamingStationKind.SONG_SIMILAR -> RadioSeedType.SONG
                    StreamingStationKind.ARTIST -> RadioSeedType.ARTIST
                    StreamingStationKind.MOOD -> RadioSeedType.MOOD
                    StreamingStationKind.ERA -> RadioSeedType.ERA
                    StreamingStationKind.FREE_TEXT,
                    StreamingStationKind.ACTIVITY -> RadioSeedType.PROMPT
                    else -> RadioSeedType.PROMPT
                },
                trackTitle = seed.seedTitle,
                trackArtist = seed.seedArtist,
                userInput = trimmed
            )

            val (verifiedQueue, gateRejections) = RadioBrain.buildVerifiedQueue(
                tracks = bootstrap.candidates,
                intent = intent,
                previousTrackIds = currentQueueIds + playedIds
            )
            if (verifiedQueue.size < minPlayableToStart) {
                val genLog = RadioBrain.buildGenerationLog(
                    seedIdentity = seed.displayName,
                    intent = intent,
                    candidates = bootstrap.candidates,
                    rejections = emptyList(),
                    gateRejections = gateRejections,
                    verifiedQueue = verifiedQueue,
                    totalQueries = bootstrap.queriesExecuted
                )
                RadioBrain.logGeneration(genLog)
                withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(null) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Couldn't find enough trusted playable tracks for ${seed.displayName}."
                    )
                }
                return@launch
            }

            val playQueue = prepareStationQueue(seed, verifiedQueue, shuffle)
            if (playQueue.isEmpty()) {
                withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(null) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Couldn't find playable tracks for ${seed.displayName}."
                    )
                }
                return@launch
            }

            val genLog = RadioBrain.buildGenerationLog(
                seedIdentity = seed.displayName,
                intent = intent,
                candidates = bootstrap.candidates,
                rejections = emptyList(),
                gateRejections = gateRejections,
                verifiedQueue = verifiedQueue,
                totalQueries = bootstrap.queriesExecuted
            )
            RadioBrain.logGeneration(genLog)

            rememberStationTracks(playQueue)
            container.playerController.playQueue(playQueue, 0, QueueMode.STREAMING_STATION)
            withContext(Dispatchers.IO) {
                val first = playQueue.first()
                container.nowPlayingStateStore.save(
                    NowPlayingState(
                        trackId = first.track.trackId.toString(),
                        title = first.track.title,
                        artist = first.track.artist,
                        album = first.track.albumName,
                        artworkUrl = first.track.coverArtUrl,
                        durationMs = first.track.durationMs ?: 0L,
                        isPlaying = true
                    )
                )
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    activeStationName = seed.displayName,
                    statusMessage = "Playing ${seed.displayName}"
                )
            }
            lastGenerationToken = currentToken
            startStationMonitor()

            // Background expand
            launch(Dispatchers.IO) {
                if (lastGenerationToken != currentToken) return@launch
                val expanded = container.radioQueueEngine.generateStreamingStation(
                    StreamingStationRequest(
                        seed = seed,
                        excludeTrackIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet(),
                        playedTrackIds = container.queueManager.playedHistory.toSet(),
                        taste = taste,
                        targetCount = 45,
                        minPlayableToStart = 8,
                        generationToken = currentToken
                    )
                )
                if (expanded.candidates.isNotEmpty()) {
                    val (expVerified, expRejections) = RadioBrain.buildVerifiedQueue(
                        tracks = expanded.candidates,
                        intent = intent,
                        previousTrackIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet() +
                            container.queueManager.playedHistory.toSet()
                    )
                    if (expVerified.isNotEmpty()) {
                        val expansionQueue = prepareStationQueue(seed, expVerified, shuffle = false)
                        if (expansionQueue.isNotEmpty()) {
                            val added = container.queueManager.appendToOriginalQueueIfAbsent(expansionQueue)
                            if (added > 0) {
                                rememberStationTracks(expansionQueue)
                                container.playerController.refreshQueueTimeline()
                            }
                            VantaLogger.d(VantaLogger.Tag.STATION, "bg_expand added=$added total=${expVerified.size} gateRejected=${expRejections.size}")
                        }
                    }
                }
            }
        }
    }

    private fun startStationMonitor() {
        stationMonitorJob = viewModelScope.launch {
            while (isActive && activeStationSeed != null) {
                if (lastGenerationToken != generationToken) break
                val upcomingSize = container.queueManager.upcomingOriginalQueue().size
                if (upcomingSize < REFILL_THRESHOLD && !isRefillingStation) {
                    isRefillingStation = true
                    VantaLogger.d(VantaLogger.Tag.STATION, "refill_triggered upcoming=$upcomingSize token=$generationToken")
                    try {
                        val seedParams = activeStationSeed ?: break
                        if (lastGenerationToken != generationToken) break
                        val stationSeed = seedParams.toStationSeed()
                        val stationContext = withContext(Dispatchers.IO) {
                            Triple(
                                container.queueManager.originalQueue.map { it.track.trackId }.toSet(),
                                container.queueManager.playedHistory.toSet(),
                                container.aiDjRecommendationEngine.streamingTasteSignals()
                            )
                        }
                        val queuedIds = stationContext.first
                        val playedIds = stationContext.second
                        val taste = stationContext.third
                        val existingIds = queuedIds + playedStationTrackIds.mapNotNull { it.toLongOrNull() }.toSet()
                        val newTracks = withContext(Dispatchers.IO) {
                            container.radioQueueEngine.refillStreamingStation(
                                request = StreamingStationRequest(
                                    seed = stationSeed,
                                    excludeTrackIds = queuedIds,
                                    playedTrackIds = playedIds,
                                    taste = taste,
                                    targetCount = 20,
                                    minPlayableToStart = 3,
                                    generationToken = generationToken
                                ),
                                existingTrackIds = existingIds
                            )
                        }
                        if (lastGenerationToken != generationToken) break
                        val (verifiedRefill, refillRejections) = RadioBrain.buildVerifiedQueue(
                            tracks = newTracks,
                            intent = RadioBrain.buildIntent(
                                seedType = when (stationSeed.kind) {
                                    StreamingStationKind.SONG,
                                    StreamingStationKind.SONG_SIMILAR -> RadioSeedType.SONG
                                    StreamingStationKind.ARTIST -> RadioSeedType.ARTIST
                                    else -> RadioSeedType.PROMPT
                                },
                                trackTitle = stationSeed.seedTitle,
                                trackArtist = stationSeed.seedArtist
                            ),
                            previousTrackIds = existingIds
                        )
                        if (verifiedRefill.isNotEmpty()) {
                            val refillQueue = prepareStationQueue(stationSeed, verifiedRefill, shuffle = false)
                            if (refillQueue.isNotEmpty()) {
                                val added = container.queueManager.appendToOriginalQueueIfAbsent(refillQueue)
                                rememberStationTracks(refillQueue)
                                container.playerController.refreshQueueTimeline()
                                VantaLogger.d(VantaLogger.Tag.STATION, "refill_ok added=$added gateRejected=${refillRejections.size}")
                            }
                        } else {
                            VantaLogger.w(VantaLogger.Tag.STATION, "refill_all_rejected_by_gate rejections=${refillRejections.take(10).joinToString("|")}")
                        }
                    } catch (e: Exception) {
                        VantaLogger.w(VantaLogger.Tag.STATION, "refill_failed will_retry: ${e.message}")
                    } finally {
                        isRefillingStation = false
                    }
                }
                delay(3000)
            }
        }
    }

    private fun stopStationMonitor() {
        stationMonitorJob?.cancel()
        stationMonitorJob = null
        isRefillingStation = false
    }

    private fun prepareStationQueue(
        seed: StreamingStationSeed,
        tracks: List<UnifiedTrackWithSources>,
        shuffle: Boolean
    ): List<UnifiedTrackWithSources> {
        val cleaned = tracks
            .filterNot { JukeboxTrackEligibility.shouldExcludeFromRadioQueue(it) }
            .distinctBy { it.track.trackId }
            .let { filtered ->
                if (seed.kind != StreamingStationKind.ARTIST) return@let filtered
                val artistKey = seed.seedArtist?.trim().orEmpty().ifBlank {
                    seed.displayName.removeSuffix(" Radio").trim()
                }
                filtered.sortedByDescending { track ->
                    if (artistKey.isNotBlank() && track.track.artist.equals(artistKey, ignoreCase = true)) 1 else 0
                }
            }
        val ordered = if (shuffle) cleaned.shuffled() else cleaned
        return interleaveByArtist(ordered)
    }

    private fun rememberStationTracks(tracks: List<UnifiedTrackWithSources>) {
        tracks.map { it.track.trackId.toString() }.forEach { id ->
            if (id !in playedStationTrackIds) {
                playedStationTrackIds.addLast(id)
                if (playedStationTrackIds.size > 500) playedStationTrackIds.removeFirst()
            }
        }
    }

    private fun interleaveByArtist(tracks: List<UnifiedTrackWithSources>): List<UnifiedTrackWithSources> {
        if (tracks.size <= 2) return tracks
        val buckets = tracks
            .groupBy { it.track.artist?.trim()?.lowercase().orEmpty().ifBlank { "unknown" } }
            .mapValues { (_, items) -> items.toMutableList() }
            .toMutableMap()
        val order = buckets.keys.toList()
        val result = mutableListOf<UnifiedTrackWithSources>()
        while (buckets.values.any { it.isNotEmpty() }) {
            for (artist in order) {
                val bucket = buckets[artist] ?: continue
                if (bucket.isEmpty()) continue
                result.add(bucket.removeAt(0))
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        stopStationMonitor()
    }
}
