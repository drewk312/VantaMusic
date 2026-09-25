package com.audiophile.musicplayer.radio.sonic

import java.util.LinkedList

/**
 * Dynamic Queue Mixer maintaining a rolling lookahead buffer, dynamic refills,
 * and artist fatigue prevention.
 */
class RadioSessionManager(
    val seedTrack: SonicTrack,
    private val engine: RadioEngine
) {
    private val lock = Any()
    private val playbackQueue = LinkedList<SonicTrack>()
    private val recentArtistHistory = LinkedList<String>()
    private val bannedTrackIds = mutableSetOf<String>()
    private val MAX_HISTORY = 4

    @Volatile
    var currentTargetFeatures: FeatureVector = seedTrack.features
        private set

    init {
        synchronized(lock) {
            // Queue starts instantly with the user's selected song
            playbackQueue.add(seedTrack)
            topUpQueue()
        }
    }

    /**
     * Nudges the station target features towards the liked track and reshapes
     * the upcoming lookahead queue to reflect the updated sonic taste.
     */
    fun onThumbsUp(track: SonicTrack, weight: Double = 0.28): FeatureVector = synchronized(lock) {
        currentTargetFeatures = currentTargetFeatures.nudgeTowards(track.features, weight)

        // Reshape upcoming lookahead buffer (preserve the immediate next song if playing, re-evaluate remaining)
        if (playbackQueue.size > 1) {
            val head = playbackQueue.poll() // Keep currently playing / next track
            playbackQueue.clear()
            if (head != null) {
                playbackQueue.add(head)
            }
        }
        topUpQueue()
        currentTargetFeatures
    }

    /**
     * Pushes the station target features away from the disliked track,
     * permanently bans the track from the session, purges it from upcoming lookahead,
     * and tops up the queue.
     */
    fun onThumbsDown(track: SonicTrack, weight: Double = 0.22): FeatureVector = synchronized(lock) {
        currentTargetFeatures = currentTargetFeatures.pushAway(track.features, weight)
        bannedTrackIds.add(track.id)
        playbackQueue.removeAll { it.id == track.id }
        topUpQueue()
        currentTargetFeatures
    }

    /**
     * Advances the queue, tracks artist history for anti-fatigue, and tops up the lookahead buffer.
     */
    fun nextTrack(): SonicTrack? = synchronized(lock) {
        if (playbackQueue.isEmpty()) return null

        val played = playbackQueue.poll()

        // Track history to prevent playing the same artist repeatedly
        if (played != null) {
            recentArtistHistory.add(played.artist)
            if (recentArtistHistory.size > MAX_HISTORY) {
                recentArtistHistory.poll()
            }
        }

        // Top up looking ahead dynamically
        topUpQueue()
        return played
    }

    fun getUpcomingQueue(): List<SonicTrack> = synchronized(lock) {
        playbackQueue.toList()
    }

    fun topUpQueue() = synchronized(lock) {
        // Keep the upcoming lookahead buffer at 4 songs
        while (playbackQueue.size < 4) {
            val candidatePool = engine.getSimilarTracks(
                targetFeatures = currentTargetFeatures,
                seedSubGenres = seedTrack.subGenres,
                excludeIds = bannedTrackIds + setOf(seedTrack.id),
                limit = 15
            )

            // Filter out artists played too recently to avoid fatigue
            val freshCandidates = candidatePool.filter {
                it.artist !in recentArtistHistory &&
                it !in playbackQueue &&
                it.id !in bannedTrackIds
            }

            // Fallback to general pool if the pool is too small to fulfill artist variety
            val nextTrack = freshCandidates.shuffled().firstOrNull()
                ?: candidatePool.filter { it !in playbackQueue && it.id !in bannedTrackIds }.shuffled().firstOrNull()
                ?: break // Out of unique tracks completely

            playbackQueue.add(nextTrack)
        }
    }
}
