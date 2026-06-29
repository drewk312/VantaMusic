package com.audiophile.musicplayer.playback

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
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

enum class QueueMode { NORMAL_QUEUE, RADIO_QUEUE, STREAMING_STATION, SEARCH_RESULTS_QUEUE, AUTOPLAY_QUEUE, AI_DJ_QUEUE }

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
    val canPlayPrevious: Boolean = false
)

interface QueuePersistence {
    fun save(snapshot: QueueSnapshot)
    fun load(): QueueSnapshot?
}

class QueueManager(
    private val recommendationEngine: RecommendationEngine? = null,
    private val radioQueueEngine: com.audiophile.musicplayer.radio.RadioQueueEngine? = null,
    private val persistence: QueuePersistence? = null,
    private val onTrackConsumed: ((trackId: Long) -> Unit)? = null
) {

    private val mutex = Mutex()

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
        val deduplicated = deduplicateTracks(tracks)
        _originalQueue.clear()
        _originalQueue.addAll(deduplicated)
        this.queueMode = mode
        if (mode != QueueMode.STREAMING_STATION) {
            activeStreamingSeed = null
        }
        if (mode != QueueMode.RADIO_QUEUE) {
            activeRadioArtist = null
        }
        val originalTrack = tracks.getOrNull(startIndex)
        val dedupedStartIndex = if (originalTrack != null) {
            _originalQueue.indexOfFirst { it.track.trackId == originalTrack.track.trackId }.coerceAtLeast(0)
        } else {
            0
        }
        currentOriginalIndex = (dedupedStartIndex - 1).coerceAtLeast(-1)
        _priorityQueue.clear()
        _upNextQueue.clear()
        Log.d("VANTA_QUEUE_TRUTH", "set_queue mode='${mode.name}' originalBefore=${tracks.size} originalAfter=${_originalQueue.size} startIndex=$dedupedStartIndex")
        persist()
    }

    suspend fun addToOriginalQueue(track: UnifiedTrackWithSources) = mutex.withLock {
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
        _priorityQueue.add(0, track)
        persist()
    }

    suspend fun addToQueue(track: UnifiedTrackWithSources) = mutex.withLock {
        _upNextQueue.add(track)
        persist()
    }

    suspend fun appendUpcoming(tracks: List<UnifiedTrackWithSources>) = mutex.withLock {
        _upNextQueue.addAll(tracks)
        persist()
    }

    suspend fun moveUpNextItem(fromIndex: Int, toIndex: Int) = mutex.withLock {
        if (fromIndex !in _upNextQueue.indices || toIndex !in _upNextQueue.indices) return@withLock
        val moved = _upNextQueue.removeAt(fromIndex)
        _upNextQueue.add(toIndex, moved)
        persist()
    }

    suspend fun removeUpNextItem(index: Int) = mutex.withLock {
        if (index !in _upNextQueue.indices) return@withLock
        _upNextQueue.removeAt(index)
        persist()
    }

    suspend fun updateShuffleEnabled(enabled: Boolean) {
        isShuffleEnabled = enabled
        mutex.withLock { persist() }
    }

    fun markPlaybackPosition(positionMs: Long) {
        currentPositionMs = positionMs.coerceAtLeast(0L)
    }

    suspend fun markCurrentTrack(track: UnifiedTrackWithSources, positionMs: Long = 0L) = mutex.withLock {
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
        val upNextCopy = _upNextQueue.toList()
        val queueSize = originalCopy.size + priorityCopy.size + upNextCopy.size
        return QueueSnapshot(
            originalQueue = originalCopy,
            currentOriginalIndex = currentOriginalIndex,
            priorityQueue = priorityCopy,
            upNextQueue = upNextCopy,
            lastPlayedTrack = lastPlayedTrack,
            currentTrack = currentTrack,
            currentPositionMs = currentPositionMs,
            isShuffleEnabled = isShuffleEnabled,
            queueIndex = currentOriginalIndex,
            queueSize = queueSize,
            canPlayNext = priorityCopy.isNotEmpty() ||
                upNextCopy.isNotEmpty() ||
                (originalCopy.size > 1 && currentOriginalIndex < originalCopy.lastIndex),
            canPlayPrevious = originalCopy.size > 1 && currentOriginalIndex > 0
        )
    }

    private suspend fun restore(snapshot: QueueSnapshot) = mutex.withLock {
        _originalQueue.clear()
        _originalQueue.addAll(snapshot.originalQueue)
        _priorityQueue.clear()
        _priorityQueue.addAll(snapshot.priorityQueue)
        _upNextQueue.clear()
        _upNextQueue.addAll(snapshot.upNextQueue)
        currentOriginalIndex = snapshot.currentOriginalIndex
        lastPlayedTrack = snapshot.lastPlayedTrack
        currentTrack = snapshot.currentTrack
        currentPositionMs = snapshot.currentPositionMs
        isShuffleEnabled = snapshot.isShuffleEnabled
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

        if (_originalQueue.isEmpty() || currentOriginalIndex >= _originalQueue.lastIndex) {
            fillAutoplay()
            if (_originalQueue.isEmpty() || currentOriginalIndex >= _originalQueue.lastIndex) {
                return null
            }
        }

        val remainingAfterCurrent = _originalQueue.size - currentOriginalIndex - 1
        if (remainingAfterCurrent < 6 && queueMode != QueueMode.AI_DJ_QUEUE) {
            val existingIds = _originalQueue.map { it.track.trackId }.toSet()
            val seed = lastPlayedTrack
            if (seed != null) {
                if (queueMode == QueueMode.STREAMING_STATION && radioQueueEngine != null && activeStreamingSeed != null) {
                    val stationSeed = activeStreamingSeed ?: return null
                    val discoveredArtists = _originalQueue.mapNotNull { it.track.artist?.trim() }.filter { it.isNotBlank() }.distinct()
                    val enrichedSeed = stationSeed.copy(
                        seedArtists = (stationSeed.seedArtists + discoveredArtists).distinct().take(12)
                    )
                    val taste = recommendationEngine?.streamingTasteSignals()
                        ?: com.audiophile.musicplayer.radio.StreamingStationTasteSignals()
                    val refill = radioQueueEngine.refillStreamingStation(
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
                            "streaming_station_autofill seed='${stationSeed.displayName}' added=$addedCount remaining=$remainingAfterCurrent queueSize=${_originalQueue.size}"
                        )
                    }
                } else if (queueMode == QueueMode.RADIO_QUEUE && radioQueueEngine != null) {
                    val seedTitle = seed.track.title ?: ""
                    val seedArtist = activeRadioArtist ?: seed.track.artist.orEmpty()
                    val refill = radioQueueEngine.refill(
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
                        var rejectedDrift = 0
                        val lockedArtist = activeRadioArtist?.trim().orEmpty()
                        for (t in refill) {
                            if (!t.isPlayableMusicCandidate() || t.track.trackId in existingIds) continue
                            _originalQueue.add(t)
                            addedCount++
                        }
                        Log.d(
                            "VANTA_QUEUE_EXPAND",
                            "radio_autofill seed=${seed.track.artist} lockedArtist=$lockedArtist " +
                                "added=$addedCount rejectedDrift=$rejectedDrift remaining=$remainingAfterCurrent " +
                                "queueSize=${_originalQueue.size}"
                        )
                    }
                } else {
                    val more = recommendationEngine?.getSimilarTracks(seed.track.artist, seed.track.genre)
                    if (!more.isNullOrEmpty()) {
                        var addedCount = 0
                        for (t in more) {
                            if (t.isPlayableMusicCandidate() && t.track.trackId !in existingIds) {
                                _originalQueue.add(t)
                                addedCount++
                            }
                        }
                        Log.d("VANTA_QUEUE_EXPAND", "autofill seed=${seed.track.artist} added=$addedCount remaining=$remainingAfterCurrent queueSize=${_originalQueue.size}")
                    }
                }
            }
        }

        if (isShuffleEnabled) {
            val validTracks = _originalQueue
                .filter { !it.track.skipOnShuffle }
                .filter { it.track.trackId !in _playedHistory }
            if (validTracks.isEmpty()) return null
            val track = validTracks.random()
            Log.d("VANTA_QUEUE_TRUTH", "action=get_next source=shuffle title=${track.track.title}")
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

    private suspend fun fillAutoplay() {
        lastPlayedTrack?.let { seed ->
            val existingIds = _originalQueue.map { it.track.trackId }.toSet()
            val recommendations = recommendationEngine?.getSimilarTracks(
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
        if (!t.isrc.isNullOrBlank()) return "isrc:${t.isrc}"
        val normTitle = normalizeText(t.title ?: "")
        val normArtist = normalizeText(t.artist ?: "")
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
                removed.add(item.track.title ?: "unknown")
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
        val deduped = deduplicateTracks(_originalQueue.toList())
        if (deduped.size < before) {
            _originalQueue.clear()
            _originalQueue.addAll(deduped)
            Log.d("VANTA_QUEUE_TRUTH", "dedupe_in_place originalBefore=$before originalAfter=${deduped.size}")
        }
    }
}
