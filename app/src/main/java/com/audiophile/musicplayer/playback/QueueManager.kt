package com.audiophile.musicplayer.playback

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.isMusicContentAllowed
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RecommendationEngine {
    suspend fun getSimilarTracks(seedArtist: String, seedGenre: String?): List<UnifiedTrackWithSources>
    fun streamingTasteSignals(): com.audiophile.musicplayer.radio.StreamingStationTasteSignals =
        com.audiophile.musicplayer.radio.StreamingStationTasteSignals()
}

enum class QueueMode { NORMAL_QUEUE, RADIO_QUEUE, STREAMING_STATION, SEARCH_RESULTS_QUEUE, AUTOPLAY_QUEUE, AI_DJ_QUEUE, SONIC_RADIO }

data class QueueSnapshot(
    val originalQueue: List<UnifiedTrackWithSources> = emptyList(),
    val currentOriginalIndex: Int = -1,
    val priorityQueue: List<UnifiedTrackWithSources> = emptyList(),
    val upNextQueue: List<UnifiedTrackWithSources> = emptyList(),
    val lastPlayedTrack: UnifiedTrackWithSources? = null,
    val currentTrack: UnifiedTrackWithSources? = null,
    val currentPositionMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val canPlayNext: Boolean = false,
    val canPlayPrevious: Boolean = false,
    val isRadio: Boolean = false
)

interface QueuePersistence {
    fun save(snapshot: QueueSnapshot)
    fun load(): QueueSnapshot?
}

class QueueManager(
    private val recommendationEngine: RecommendationEngine? = null,
    private val radioQueueEngine: com.audiophile.musicplayer.radio.RadioQueueEngine? = null,
    private val recommendationEngineProvider: (() -> RecommendationEngine?)? = null,
    private val radioQueueEngineProvider: (() -> com.audiophile.musicplayer.radio.RadioQueueEngine?)? = null,
    private val persistence: QueuePersistence? = null,
    private val onTrackConsumed: ((trackId: Long) -> Unit)? = null
) {

    private fun currentRecommendationEngine(): RecommendationEngine? =
        recommendationEngine ?: recommendationEngineProvider?.invoke()

    private fun currentRadioQueueEngine(): com.audiophile.musicplayer.radio.RadioQueueEngine? =
        radioQueueEngine ?: radioQueueEngineProvider?.invoke()

    private val mutex = Mutex()
    private val queueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isRefilling = false

    private val _originalQueue = CopyOnWriteArrayList<UnifiedTrackWithSources>()
    private val _priorityQueue = CopyOnWriteArrayList<UnifiedTrackWithSources>()
    private val _upNextQueue = CopyOnWriteArrayList<UnifiedTrackWithSources>()
    private val _playedHistory = CopyOnWriteArrayList<Long>()

    val originalQueue: List<UnifiedTrackWithSources> get() = _originalQueue.toList()
    val priorityQueue: List<UnifiedTrackWithSources> get() = _priorityQueue.toList()
    val upNextQueue: List<UnifiedTrackWithSources> get() = _upNextQueue.toList()
    val playedHistory: List<Long> get() = _playedHistory.toList()

    @Volatile private var currentOriginalIndex = -1
    @Volatile private var lastPlayedTrack: UnifiedTrackWithSources? = null

    @Volatile var currentTrack: UnifiedTrackWithSources? = null
        private set

    @Volatile var currentPositionMs: Long = 0L
        private set

    @Volatile var isShuffleEnabled = false

    @Volatile var queueMode: QueueMode = QueueMode.NORMAL_QUEUE

    @Volatile var activeStreamingSeed: com.audiophile.musicplayer.radio.StreamingStationSeed? = null
        private set

    /** Locked seed artist for artist-radio refill — prevents drift to unrelated artists after skips. */
    @Volatile var activeRadioArtist: String? = null
        private set

    suspend fun setRadioArtistLock(artist: String?) = mutex.withLock {
        activeRadioArtist = artist?.trim()?.takeIf { it.isNotBlank() }
    }

    @Volatile var activeSonicRadioSession: com.audiophile.musicplayer.radio.sonic.RadioSessionManager? = null
        private set

    @Volatile var activeDiscoveryMode: com.audiophile.musicplayer.radio.RadioDiscoveryMode = com.audiophile.musicplayer.radio.RadioDiscoveryMode.HYBRID_MIX
        private set

    suspend fun setDiscoveryMode(mode: com.audiophile.musicplayer.radio.RadioDiscoveryMode) = mutex.withLock {
        activeDiscoveryMode = mode
    }

    suspend fun toggleShuffle(): Boolean = mutex.withLock {
        isShuffleEnabled = !isShuffleEnabled
        if (isShuffleEnabled) {
            val currentUpNext = _upNextQueue.toList()
            if (currentUpNext.size > 1) {
                _upNextQueue.clear()
                _upNextQueue.addAll(currentUpNext.shuffled())
            }
            val playedCount = (currentOriginalIndex + 1).coerceAtLeast(0)
            val preserved = _originalQueue.take(playedCount)
            val upcoming = _originalQueue.drop(playedCount)
            if (upcoming.size > 1) {
                _originalQueue.clear()
                _originalQueue.addAll(preserved + upcoming.shuffled())
            }
        }
        persist()
        isShuffleEnabled
    }

    suspend fun shuffleUpNext() = mutex.withLock {
        isShuffleEnabled = true
        val currentUpNext = _upNextQueue.toList()
        if (currentUpNext.size > 1) {
            _upNextQueue.clear()
            _upNextQueue.addAll(currentUpNext.shuffled())
        }
        val playedCount = (currentOriginalIndex + 1).coerceAtLeast(0)
        val preserved = _originalQueue.take(playedCount)
        val upcoming = _originalQueue.drop(playedCount)
        if (upcoming.size > 1) {
            _originalQueue.clear()
            _originalQueue.addAll(preserved + upcoming.shuffled())
        }
        persist()
    }

    suspend fun reshapeUpcomingQueue(
        mode: com.audiophile.musicplayer.radio.RadioDiscoveryMode,
        favoritesIds: Set<String>,
        recentHistoryIds: Set<String>
    ) = mutex.withLock {
        activeDiscoveryMode = mode
        val playedCount = (currentOriginalIndex + 1).coerceAtLeast(0)
        val preserved = _originalQueue.take(playedCount)
        val upcoming = _originalQueue.drop(playedCount)
        if (upcoming.isEmpty()) return@withLock

        val reshuffled = when (mode) {
            com.audiophile.musicplayer.radio.RadioDiscoveryMode.MY_FAVORITES -> {
                val (favs, others) = upcoming.partition { it.track.trackId.toString() in favoritesIds }
                favs + others
            }
            com.audiophile.musicplayer.radio.RadioDiscoveryMode.HYBRID_MIX -> {
                upcoming.shuffled()
            }
            com.audiophile.musicplayer.radio.RadioDiscoveryMode.DEEP_DISCOVERY -> {
                val nonFavs = upcoming.filter { it.track.trackId.toString() !in favoritesIds && it.track.trackId.toString() !in recentHistoryIds }
                val remaining = upcoming.filter { it !in nonFavs }
                nonFavs + remaining
            }
        }

        _originalQueue.clear()
        _originalQueue.addAll(preserved + reshuffled)
        persist()
    }

    suspend fun setSonicRadioSession(session: com.audiophile.musicplayer.radio.sonic.RadioSessionManager?) = mutex.withLock {
        activeSonicRadioSession = session
    }

    suspend fun onSonicThumbsUp(track: com.audiophile.musicplayer.radio.sonic.SonicTrack): com.audiophile.musicplayer.radio.sonic.FeatureVector? = mutex.withLock {
        val session = activeSonicRadioSession ?: return@withLock null
        val updatedFeatures = session.onThumbsUp(track)
        val upcoming = session.getUpcomingQueue()
        val playedCount = (currentOriginalIndex + 1).coerceAtLeast(0)
        val preserved = _originalQueue.take(playedCount)
        val freshUpcoming = upcoming.mapNotNull { it.rawUnifiedTrack }
            .filter { candidate -> preserved.none { it.track.trackId == candidate.track.trackId } }

        _originalQueue.clear()
        _originalQueue.addAll(preserved + freshUpcoming)
        persist()
        updatedFeatures
    }

    suspend fun onSonicThumbsDown(track: com.audiophile.musicplayer.radio.sonic.SonicTrack): com.audiophile.musicplayer.radio.sonic.FeatureVector? = mutex.withLock {
        val session = activeSonicRadioSession ?: return@withLock null
        val updatedFeatures = session.onThumbsDown(track)
        val upcoming = session.getUpcomingQueue()
        val playedCount = (currentOriginalIndex + 1).coerceAtLeast(0)
        val preserved = _originalQueue.take(playedCount)
        val freshUpcoming = upcoming.mapNotNull { it.rawUnifiedTrack }
            .filter { candidate -> preserved.none { it.track.trackId == candidate.track.trackId } && candidate.track.trackId.toString() != track.id }

        _originalQueue.clear()
        _originalQueue.addAll(preserved + freshUpcoming)
        persist()
        updatedFeatures
    }

    @Volatile private var nextInFlight = false

    init {
        try {
            persistence?.load()?.let { snapshot ->
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    restore(snapshot)
                }
            }
        } catch (e: Exception) {
            Log.e("VANTA_QUEUE_TRUTH", "Queue restore failed, starting with empty queue", e)
            if (persistence is SharedPreferencesQueuePersistence) {
                persistence.clear()
            }
        }
    }

    suspend fun clearOriginalQueue() = mutex.withLock {
        _originalQueue.clear()
        currentOriginalIndex = -1
        currentTrack = null
        _priorityQueue.clear()
        _upNextQueue.clear()
        Log.d("VANTA_QUEUE_TRUTH", "clear_original_queue")
        persist()
    }

    suspend fun setStreamingStationSeed(seed: com.audiophile.musicplayer.radio.StreamingStationSeed?) = mutex.withLock {
        activeStreamingSeed = seed
    }

    suspend fun setOriginalQueue(tracks: List<UnifiedTrackWithSources>, startIndex: Int = 0, mode: QueueMode = QueueMode.NORMAL_QUEUE) = mutex.withLock {
        val musicTracks = tracks.filter { it.isMusicContentAllowed() }
        val deduplicated = deduplicateTracks(musicTracks)
        _originalQueue.clear()
        _originalQueue.addAll(deduplicated)
        this.queueMode = mode
        if (mode != QueueMode.STREAMING_STATION) {
            activeStreamingSeed = null
        }
        if (mode != QueueMode.RADIO_QUEUE) {
            activeRadioArtist = null
        }
        if (mode != QueueMode.SONIC_RADIO) {
            activeSonicRadioSession = null
        }
        val originalTrack = tracks.getOrNull(startIndex)?.takeIf { it.isMusicContentAllowed() }
        val dedupedStartIndex = if (originalTrack != null) {
            // Match by dedupe key, not trackId: deduplicateTracks may have kept a
            // higher-quality variant whose trackId differs from the requested start
            // track (e.g. a cloud copy replacing a local one of the same song).
            val startKey = deduplicateKey(originalTrack)
            _originalQueue.indexOfFirst { deduplicateKey(it) == startKey }.coerceAtLeast(0)
        } else {
            0
        }
        currentOriginalIndex = (dedupedStartIndex - 1).coerceAtLeast(-1)
        _priorityQueue.clear()
        _upNextQueue.clear()
        Log.d("VANTA_QUEUE_TRUTH", "set_queue mode='${mode.name}' originalBefore=${tracks.size} musicOnly=${musicTracks.size} originalAfter=${_originalQueue.size} startIndex=$dedupedStartIndex")
        persist()
    }

    suspend fun addToOriginalQueue(track: UnifiedTrackWithSources) = mutex.withLock {
        if (!track.isMusicContentAllowed()) return@withLock
        _originalQueue.add(track)
        persist()
    }

    suspend fun appendToOriginalQueueIfAbsent(tracks: List<UnifiedTrackWithSources>): Int = mutex.withLock {
        val existing = _originalQueue.map { it.track.trackId }.toSet()
        var added = 0
        for (track in tracks) {
            if (!track.isPlayableMusicCandidate()) continue
            if (track.track.trackId in existing) continue
            _originalQueue.add(track)
            added++
        }
        if (added > 0) {
            Log.d("VANTA_QUEUE_TRUTH", "append_original added=$added queueSize=${_originalQueue.size}")
            persist()
        }
        added
    }

    suspend fun removeUpcomingTracksMatching(predicate: (UnifiedTrackWithSources) -> Boolean): Int = mutex.withLock {
        if (currentOriginalIndex < 0) return@withLock 0
        val indices = _originalQueue.indices
            .filter { it > currentOriginalIndex }
            .filter { predicate(_originalQueue[it]) }
        if (indices.isEmpty()) return@withLock 0
        var removed = 0
        indices.sortedDescending().forEach { index ->
            _originalQueue.removeAt(index)
            removed++
        }
        Log.d("VANTA_QUEUE_TRUTH", "remove_upcoming removed=$removed queueSize=${_originalQueue.size}")
        persist()
        removed
    }

    fun upcomingOriginalQueue(): List<UnifiedTrackWithSources> {
        val copy = _originalQueue.toList()
        return copy.drop(currentOriginalIndex + 1)
    }

    suspend fun playNext(track: UnifiedTrackWithSources) = mutex.withLock {
        if (!track.isMusicContentAllowed()) return@withLock
        _priorityQueue.add(0, track)
        persist()
    }

    suspend fun addToQueue(track: UnifiedTrackWithSources) = mutex.withLock {
        if (!track.isMusicContentAllowed()) return@withLock
        _upNextQueue.add(track)
        persist()
    }

    suspend fun appendUpcoming(tracks: List<UnifiedTrackWithSources>) = mutex.withLock {
        _upNextQueue.addAll(tracks.filter { it.isMusicContentAllowed() })
        persist()
    }

    suspend fun moveUpNextItem(fromIndex: Int, toIndex: Int) = mutex.withLock {
        if (fromIndex in _upNextQueue.indices && toIndex in _upNextQueue.indices) {
            val moved = _upNextQueue.removeAt(fromIndex)
            _upNextQueue.add(toIndex, moved)
            persist()
            return@withLock
        }
        val offset = _priorityQueue.size + _upNextQueue.size
        val origFrom = currentOriginalIndex + 1 + (fromIndex - offset)
        val origTo = currentOriginalIndex + 1 + (toIndex - offset)
        if (origFrom in _originalQueue.indices && origTo in _originalQueue.indices) {
            val moved = _originalQueue.removeAt(origFrom)
            _originalQueue.add(origTo, moved)
            persist()
        }
    }

    suspend fun removeUpNextItem(index: Int) = mutex.withLock {
        if (index in _upNextQueue.indices) {
            _upNextQueue.removeAt(index)
            persist()
            return@withLock
        }
        val offset = _priorityQueue.size + _upNextQueue.size
        val origIndex = currentOriginalIndex + 1 + (index - offset)
        if (origIndex in _originalQueue.indices) {
            _originalQueue.removeAt(origIndex)
            persist()
        }
    }

    suspend fun updateShuffleEnabled(enabled: Boolean) {
        isShuffleEnabled = enabled
        mutex.withLock { persist() }
    }

    fun markPlaybackPosition(positionMs: Long) {
        currentPositionMs = positionMs.coerceAtLeast(0L)
    }

    suspend fun markCurrentTrack(track: UnifiedTrackWithSources, positionMs: Long = 0L) = mutex.withLock {
        if (!track.isMusicContentAllowed()) {
            Log.w("VANTA_QUEUE_TRUTH", "blocked_non_music_current trackId=${track.track.trackId} title='${track.track.title}'")
            return@withLock
        }
        currentTrack = track
        val originalIndex = _originalQueue.indexOfFirst { it.track.trackId == track.track.trackId }
        if (originalIndex >= 0) {
            currentOriginalIndex = originalIndex
        }
        currentPositionMs = positionMs.coerceAtLeast(0L)
        Log.d("VANTA_QUEUE_TRUTH", "action=mark_current trackId=${track.track.trackId} queueIdx=$currentOriginalIndex queueSize=${_originalQueue.size} priority=${_priorityQueue.size} upNext=${_upNextQueue.size}")
        persist()
    }

    fun snapshot(): QueueSnapshot {
        val originalCopy = _originalQueue.toList()
        val priorityCopy = _priorityQueue.toList()
        val manualUpNext = _upNextQueue.toList()
        val playedCount = (currentOriginalIndex + 1).coerceAtLeast(0)
        val remainingOriginal = if (originalCopy.isNotEmpty()) originalCopy.drop(playedCount) else emptyList()
        val visibleUpNext = (priorityCopy + manualUpNext + remainingOriginal).filter { it.isMusicContentAllowed() }
        val queueSize = originalCopy.size + priorityCopy.size + manualUpNext.size
        return QueueSnapshot(
            originalQueue = originalCopy,
            currentOriginalIndex = currentOriginalIndex,
            priorityQueue = priorityCopy,
            upNextQueue = visibleUpNext,
            lastPlayedTrack = lastPlayedTrack,
            currentTrack = currentTrack,
            currentPositionMs = currentPositionMs,
            isShuffleEnabled = isShuffleEnabled,
            queueIndex = currentOriginalIndex,
            queueSize = queueSize,
            canPlayNext = visibleUpNext.isNotEmpty(),
            canPlayPrevious = originalCopy.size > 1 && currentOriginalIndex > 0,
            isRadio = queueMode == QueueMode.RADIO_QUEUE ||
                queueMode == QueueMode.STREAMING_STATION ||
                queueMode == QueueMode.SONIC_RADIO
        )
    }

    private suspend fun restore(snapshot: QueueSnapshot) = mutex.withLock {
        val restoredOriginal = snapshot.originalQueue.filter { it.isMusicContentAllowed() }
        val restoredPriority = snapshot.priorityQueue.filter { it.isMusicContentAllowed() }
        val restoredUpNext = snapshot.upNextQueue.filter { it.isMusicContentAllowed() }
        val indexedTrack = snapshot.originalQueue.getOrNull(snapshot.currentOriginalIndex)
            ?.takeIf { it.isMusicContentAllowed() }
        _originalQueue.clear()
        _originalQueue.addAll(restoredOriginal)
        _priorityQueue.clear()
        _priorityQueue.addAll(restoredPriority)
        _upNextQueue.clear()
        _upNextQueue.addAll(restoredUpNext)
        currentOriginalIndex = indexedTrack?.let { indexed ->
            val key = deduplicateKey(indexed)
            _originalQueue.indexOfFirst { deduplicateKey(it) == key }
        } ?: -1
        lastPlayedTrack = snapshot.lastPlayedTrack?.takeIf { it.isMusicContentAllowed() }
        currentTrack = snapshot.currentTrack?.takeIf { it.isMusicContentAllowed() }
        currentPositionMs = if (currentTrack != null) snapshot.currentPositionMs else 0L
        isShuffleEnabled = snapshot.isShuffleEnabled
        val removed = (snapshot.originalQueue.size - restoredOriginal.size) +
            (snapshot.priorityQueue.size - restoredPriority.size) +
            (snapshot.upNextQueue.size - restoredUpNext.size)
        if (removed > 0 || (snapshot.currentTrack != null && currentTrack == null)) {
            Log.w("VANTA_QUEUE_TRUTH", "sanitized_restored_queue removed=$removed clearedCurrent=${snapshot.currentTrack != null && currentTrack == null}")
            persist()
        }
    }

    private suspend fun persist() {
        persistence?.save(snapshot())
    }

    suspend fun getNextTrack(): UnifiedTrackWithSources? = mutex.withLock {
        if (nextInFlight) {
            Log.d("VANTA_QUEUE_TRUTH", "next_ignored reason='in_flight'")
            return@withLock null
        }
        nextInFlight = true
        try {
            return@withLock try {
                deduplicateInPlace()
                getNextTrackInternal()
            } catch (e: IndexOutOfBoundsException) {
                Log.e("VANTA_QUEUE_TRUTH", "prevented_crash getNextTrack idx=$currentOriginalIndex size=${_originalQueue.size} message='${e.message}'")
                null
            } catch (e: Exception) {
                Log.e("VANTA_QUEUE_TRUTH", "prevented_crash getNextTrack unexpected message='${e.message}'")
                null
            }
        } finally {
            nextInFlight = false
        }
    }

    private suspend fun getNextTrackInternal(): UnifiedTrackWithSources? {
        Log.d("VANTA_QUEUE_TRUTH", "mode='${queueMode.name}' action='get_next'")
        if (_priorityQueue.isNotEmpty()) {
            val track = _priorityQueue.removeAt(0)
            Log.d("VANTA_QUEUE_TRUTH", "action=get_next source=priority title=${track.track.title}")
            return consume(track)
        }

        if (_upNextQueue.isNotEmpty()) {
            val track = _upNextQueue.removeAt(0)
            Log.d("VANTA_QUEUE_TRUTH", "action=get_next source=upNext title=${track.track.title}")
            return consume(track)
        }

        val remainingAfterCurrent = _originalQueue.size - currentOriginalIndex - 1
        if (remainingAfterCurrent < 6 && queueMode != QueueMode.AI_DJ_QUEUE) {
            if (remainingAfterCurrent > 0) {
                // Return next song immediately without delay; refill buffer in background
                triggerBackgroundRefill()
            } else {
                // Queue is exhausted, must fetch synchronously before proceeding
                refillQueueIfNeeded()
            }
        }

        if (_originalQueue.isEmpty() || currentOriginalIndex >= _originalQueue.lastIndex) {
            Log.d("VANTA_QUEUE_TRUTH", "no_next_track reason='exhausted' idx=$currentOriginalIndex size=${_originalQueue.size}")
            return null
        }

        if (isShuffleEnabled) {
            val validTracks = _originalQueue
                .filter { !it.track.skipOnShuffle }
                .filter { it.track.trackId !in _playedHistory }
            if (validTracks.isEmpty()) return null
            val track = validTracks.random()
            // Anchor the playhead to the chosen track so getPreviousTrack and the
            // autoplay-refill threshold stay correct (previously the index was frozen).
            currentOriginalIndex = _originalQueue.indexOfFirst { it.track.trackId == track.track.trackId }
            Log.d("VANTA_QUEUE_TRUTH", "action=get_next source=shuffle title=${track.track.title} playhead=$currentOriginalIndex")
            return consume(track)
        }

        currentOriginalIndex++
        if (currentOriginalIndex !in _originalQueue.indices) {
            currentOriginalIndex = _originalQueue.lastIndex.coerceAtLeast(-1)
            Log.d("VANTA_QUEUE_TRUTH", "no_next_track reason='bounds_guard_exact_line_233' idx=$currentOriginalIndex size=${_originalQueue.size}")
            return null
        }
        val nextTrack = _originalQueue.getOrNull(currentOriginalIndex)
        if (nextTrack == null) {
            Log.d("VANTA_QUEUE_TRUTH", "no_next_track reason='null_guard' idx=$currentOriginalIndex size=${_originalQueue.size}")
            return null
        }
        Log.d("VANTA_QUEUE_TRUTH", "action=get_next source=original idx=$currentOriginalIndex title=${nextTrack.track.title}")
        return consume(nextTrack)
    }

    suspend fun getPreviousTrack(): UnifiedTrackWithSources? = mutex.withLock {
        if (_originalQueue.isEmpty() || currentOriginalIndex <= 0) return@withLock null
        currentOriginalIndex--
        if (currentOriginalIndex !in _originalQueue.indices) {
            currentOriginalIndex = 0.coerceAtMost(_originalQueue.lastIndex)
            Log.d("VANTA_QUEUE_TRUTH", "no_previous_track reason='bounds_guard' idx=$currentOriginalIndex size=${_originalQueue.size}")
            return@withLock null
        }
        val previousTrack = _originalQueue.getOrNull(currentOriginalIndex)
        if (previousTrack == null) {
            Log.d("VANTA_QUEUE_TRUTH", "no_previous_track reason='null_guard' idx=$currentOriginalIndex size=${_originalQueue.size}")
            return@withLock null
        }
        Log.d("VANTA_QUEUE_TRUTH", "action=get_previous idx=$currentOriginalIndex title=${previousTrack.track.title}")
        consume(previousTrack)
    }

    private fun triggerBackgroundRefill() {
        if (isRefilling) return
        isRefilling = true
        queueScope.launch {
            try {
                refillQueueIfNeeded()
            } catch (e: Exception) {
                Log.e("VANTA_QUEUE_EXPAND", "background refill failed: ${e.message}", e)
            } finally {
                isRefilling = false
            }
        }
    }

    private suspend fun refillQueueIfNeeded() {
        val existingIds = _originalQueue.map { it.track.trackId }.toSet()
        val seed = lastPlayedTrack ?: currentTrack ?: _originalQueue.getOrNull(currentOriginalIndex) ?: return
        val activeRecommendationEngine = currentRecommendationEngine()
        val activeRadioQueueEngine = currentRadioQueueEngine()

        if (queueMode == QueueMode.STREAMING_STATION && activeRadioQueueEngine != null && activeStreamingSeed != null) {
            val stationSeed = activeStreamingSeed ?: return
            val discoveredArtists = _originalQueue.map { it.track.artist.trim() }.filter { it.isNotBlank() }.distinct()
            val enrichedSeed = stationSeed.copy(
                seedArtists = (stationSeed.seedArtists + discoveredArtists).distinct().take(12)
            )
            val taste = activeRecommendationEngine?.streamingTasteSignals()
                ?: com.audiophile.musicplayer.radio.StreamingStationTasteSignals()
            val refill = activeRadioQueueEngine.refillStreamingStation(
                request = com.audiophile.musicplayer.radio.StreamingStationRequest(
                    seed = enrichedSeed,
                    excludeTrackIds = existingIds,
                    playedTrackIds = _playedHistory.toSet(),
                    taste = taste
                ),
                existingTrackIds = existingIds
            )
            if (refill.isNotEmpty()) {
                var addedCount = 0
                for (t in refill) {
                    if (t.isPlayableMusicCandidate() && t.track.trackId !in existingIds) {
                        _originalQueue.add(t)
                        addedCount++
                    }
                }
                Log.d(
                    "VANTA_QUEUE_EXPAND",
                    "streaming_station_autofill seed='${stationSeed.displayName}' added=$addedCount queueSize=${_originalQueue.size}"
                )
            }
        } else if (queueMode == QueueMode.SONIC_RADIO && activeSonicRadioSession != null) {
            val session = activeSonicRadioSession
            if (session != null) {
                session.topUpQueue()
                val upcoming = session.getUpcomingQueue()
                var addedCount = 0
                for (sonic in upcoming) {
                    val unified = sonic.rawUnifiedTrack
                    if (unified != null && unified.isPlayableMusicCandidate() && unified.track.trackId !in existingIds) {
                        _originalQueue.add(unified)
                        addedCount++
                    }
                }
                Log.d(
                    "VANTA_QUEUE_EXPAND",
                    "sonic_radio_autofill added=$addedCount queueSize=${_originalQueue.size}"
                )
            }
        } else if (queueMode == QueueMode.RADIO_QUEUE && activeRadioQueueEngine != null) {
            val seedTitle = seed.track.title
            val seedArtist = activeRadioArtist ?: seed.track.artist.orEmpty()
            val refill = activeRadioQueueEngine.refill(
                com.audiophile.musicplayer.radio.RadioSeed(
                    title = seedTitle,
                    artist = seedArtist,
                    album = seed.track.albumName,
                    genre = seed.track.genre
                ),
                existingTrackIds = existingIds,
                playedTrackIds = _playedHistory.toSet()
            )
            if (refill.isNotEmpty()) {
                var addedCount = 0
                val lockedArtist = activeRadioArtist?.trim().orEmpty()
                for (t in refill) {
                    if (!t.isPlayableMusicCandidate() || t.track.trackId in existingIds) continue
                    _originalQueue.add(t)
                    addedCount++
                }
                Log.d(
                    "VANTA_QUEUE_EXPAND",
                    "radio_autofill seed=${seed.track.artist} lockedArtist=$lockedArtist added=$addedCount queueSize=${_originalQueue.size}"
                )
            }
        } else {
            fillAutoplay()
        }
    }

    private suspend fun fillAutoplay() {
        val seed = lastPlayedTrack ?: currentTrack ?: _originalQueue.getOrNull(currentOriginalIndex) ?: return
        val existingIds = _originalQueue.map { it.track.trackId }.toSet()
        val recommendations = currentRecommendationEngine()?.getSimilarTracks(
            seedArtist = seed.track.artist,
            seedGenre = seed.track.genre
        )
        if (!recommendations.isNullOrEmpty()) {
            var addedCount = 0
            for (t in recommendations) {
                if (t.isPlayableMusicCandidate() && t.track.trackId !in existingIds) {
                    _originalQueue.add(t)
                    addedCount++
                }
            }
            Log.d("VANTA_QUEUE_EXPAND", "fillAutoplay seed=${seed.track.artist} added=$addedCount queueSize=${_originalQueue.size}")
            if (addedCount > 0) return
        }

        // Streaming radio fallback if local recommendations had no results
        val activeRadioQueueEngine = currentRadioQueueEngine()
        if (activeRadioQueueEngine != null) {
            val refill = activeRadioQueueEngine.refill(
                com.audiophile.musicplayer.radio.RadioSeed(
                    title = seed.track.title,
                    artist = seed.track.artist,
                    album = seed.track.albumName,
                    genre = seed.track.genre
                ),
                existingTrackIds = existingIds,
                playedTrackIds = _playedHistory.toSet()
            )
            if (refill.isNotEmpty()) {
                var addedCount = 0
                for (t in refill) {
                    if (t.isPlayableMusicCandidate() && t.track.trackId !in existingIds) {
                        _originalQueue.add(t)
                        addedCount++
                    }
                }
                Log.d("VANTA_QUEUE_EXPAND", "fillAutoplay_streaming seed=${seed.track.artist} added=$addedCount queueSize=${_originalQueue.size}")
            }
        }
    }

    suspend fun loadPlayedHistory(ids: List<Long>) = mutex.withLock {
        _playedHistory.clear()
        _playedHistory.addAll(ids.take(10))
    }

    fun getRecentTracks(limit: Int): List<UnifiedTrackWithSources> {
        val historyIds = _playedHistory.toList().takeLast(limit)
        val allTracks = _originalQueue.toList()
        return historyIds.mapNotNull { id -> allTracks.find { it.track.trackId == id } }
    }

    private fun consume(track: UnifiedTrackWithSources): UnifiedTrackWithSources {
        lastPlayedTrack = track
        currentTrack = track
        currentPositionMs = 0L
        _playedHistory.add(track.track.trackId)
        if (_playedHistory.size > 10) _playedHistory.removeAt(0)
        onTrackConsumed?.invoke(track.track.trackId)
        return track
    }

    // ── Deduplication & filtering ──

    private fun normalizeText(s: String): String {
        return s.lowercase()
            .replace(Regex("[‘’'ʼ]"), "'")
            .replace(Regex("[\\p{P}\\p{S}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun deduplicateKey(track: UnifiedTrackWithSources): String {
        val t = track.track
        // Prefer durable recording identity; provider IDs are not song identity.
        if (!t.isrc.isNullOrBlank()) return "isrc:${t.isrc.trim().uppercase()}"
        val normTitle = normalizeText(t.title)
        val normArtist = normalizeText(t.artist)
        return "norm:${normTitle}|${normArtist}"
    }

    private fun pickHigherQuality(a: UnifiedTrackWithSources, b: UnifiedTrackWithSources): UnifiedTrackWithSources {
        fun score(t: UnifiedTrackWithSources): Int {
            var s = 0
            if (!t.track.coverArtUrl.isNullOrBlank()) s += 10
            if (!t.track.artist.isNullOrBlank()) s += 5
            if (t.sources.any { it.streamUrl.isNotBlank() }) s += 8
            if (t.track.localLibraryId != null) s += 6
            if (!t.track.isrc.isNullOrBlank()) s += 3
            return s
        }
        return if (score(a) >= score(b)) a else b
    }

    private fun deduplicateTracks(tracks: List<UnifiedTrackWithSources>): List<UnifiedTrackWithSources> {
        val seen = mutableMapOf<String, UnifiedTrackWithSources>()
        val removed = mutableListOf<String>()
        for (item in tracks) {
            val key = deduplicateKey(item)
            val existing = seen[key]
            if (existing == null) {
                seen[key] = item
            } else {
                val better = pickHigherQuality(existing, item)
                seen[key] = better
                removed.add(item.track.title)
            }
        }
        val result = seen.values.toList()
        if (tracks.size != result.size) {
            Log.d("VANTA_QUEUE_TRUTH", "dedupe originalBefore=${tracks.size} originalAfter=${result.size} removed=${removed.size}")
        }
        return result
    }

    private fun deduplicateInPlace() {
        val before = _originalQueue.size
        if (before <= 1) return
        val currentKey = currentTrack?.let { deduplicateKey(it) }
        // Guard: if we have no current track but have a stale index, reset it
        if (currentKey == null && currentOriginalIndex >= 0) {
            currentOriginalIndex = 0.coerceAtMost(_originalQueue.lastIndex)
            Log.d("VANTA_QUEUE_TRUTH", "dedupe_in_place reason=no_current_track resetIdx=$currentOriginalIndex")
            return
        }
        val deduped = deduplicateTracks(_originalQueue.toList())
        if (deduped.size < before) {
            _originalQueue.clear()
            _originalQueue.addAll(deduped)
            // Re-anchor the playhead to the currently playing track by its
            // dedupe key. Items removed by dedup may have sat *before* the
            // playhead, which would otherwise shift currentOriginalIndex and
            // make getNextTrack advance to the wrong track.
            if (currentKey != null) {
                val newIndex = _originalQueue.indexOfFirst { deduplicateKey(it) == currentKey }
                currentOriginalIndex = if (newIndex >= 0) {
                    newIndex
                } else {
                    // Current track was deduped out; anchor to the first item
                    0.coerceAtMost(_originalQueue.lastIndex)
                }
            }
            Log.d("VANTA_QUEUE_TRUTH", "dedupe_in_place originalBefore=$before originalAfter=${deduped.size} playhead=$currentOriginalIndex")
        }
    }
}

