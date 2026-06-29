package com.audiophile.musicplayer.data.taste

import com.audiophile.musicplayer.radio.RadioQueueEngine
import com.audiophile.musicplayer.radio.RadioSeed
import java.util.Calendar
import java.util.Locale

enum class TasteEventType {
    PLAYED, SKIPPED, LIKED, REPLAYED, SAVED, REMOVED, SEARCHED, CORRECTED
}

enum class TimeOfDay {
    MORNING, AFTERNOON, EVENING, LATE_NIGHT
}

enum class DiscoveryBucket {
    SAFE_FAMILIAR, ADJACENT_DISCOVERY, ADVENTUROUS
}

data class TasteEvent(
    val track: String,
    val artist: String,
    val genre: String? = null,
    val event: TasteEventType,
    val station: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun timeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..16 -> TimeOfDay.AFTERNOON
            in 17..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.LATE_NIGHT
        }
    }
}

data class TasteProfile(
    val favoriteArtists: Set<String> = emptySet(),
    val dislikedArtists: Set<String> = emptySet(),
    val likedGenres: Set<String> = emptySet(),
    val skippedGenres: Set<String> = emptySet(),
    val replayedTracks: Set<String> = emptySet(),
    val savedTracks: Set<String> = emptySet(),
    val explicitAllowed: Boolean = true,
    val vocalPreference: VocalPreference = VocalPreference.VOCAL_FIRST,
    val discoveryOpenness: Float = 0.35f,
    val highSkipRate: Boolean = false
)

enum class VocalPreference { VOCAL_FIRST, INSTRUMENTAL_OK }

interface TasteEventStore {
    fun append(event: TasteEvent)
    fun all(): List<TasteEvent>
    fun forStation(station: String): List<TasteEvent>
}

class InMemoryTasteEventStore : TasteEventStore {
    private val events = mutableListOf<TasteEvent>()
    override fun append(event: TasteEvent) { events += event }
    override fun all(): List<TasteEvent> = events.toList()
    override fun forStation(station: String): List<TasteEvent> = events.filter { it.station == station }
}

data class TasteVector(
    val artistAffinity: Map<String, Float>,
    val genreAffinity: Map<String, Float>,
    val replayAffinity: Map<String, Float>,
    val skipRate: Float,
    val currentMood: String,
    val timeOfDay: TimeOfDay
)

object TasteVectorBuilder {
    fun fromEvents(events: List<TasteEvent>): TasteVector {
        val artistAffinity = linkedMapOf<String, Float>()
        val genreAffinity = linkedMapOf<String, Float>()
        val replayAffinity = linkedMapOf<String, Float>()
        var skips = 0f
        var total = 0f

        events.forEach { event ->
            total += 1f
            val artistKey = normalize(event.artist)
            val trackKey = normalize("${event.track}|${event.artist}")
            val genreKey = normalize(event.genre)
            val delta = when (event.event) {
                TasteEventType.LIKED -> 2.5f
                TasteEventType.SAVED -> 2.0f
                TasteEventType.REPLAYED -> 1.8f
                TasteEventType.PLAYED -> 1.0f
                TasteEventType.SEARCHED -> 0.6f
                TasteEventType.CORRECTED -> 1.4f
                TasteEventType.SKIPPED -> { skips += 1f; -1.8f }
                TasteEventType.REMOVED -> -1.0f
            }
            if (artistKey.isNotBlank()) artistAffinity[artistKey] = (artistAffinity[artistKey] ?: 0f) + delta
            if (genreKey.isNotBlank()) genreAffinity[genreKey] = (genreAffinity[genreKey] ?: 0f) + delta
            if (event.event == TasteEventType.REPLAYED || event.event == TasteEventType.LIKED) {
                replayAffinity[trackKey] = (replayAffinity[trackKey] ?: 0f) + delta
            }
        }

        val recentEvents = events.takeLast(10)
        val recentSkips = recentEvents.count { it.event == TasteEventType.SKIPPED }
        val currentMood = when {
            recentSkips >= 3 -> "rejecting_current_vibe"
            recentEvents.any { it.event == TasteEventType.REPLAYED } -> "locked_in"
            else -> "neutral"
        }

        return TasteVector(
            artistAffinity = artistAffinity,
            genreAffinity = genreAffinity,
            replayAffinity = replayAffinity,
            skipRate = if (total == 0f) 0f else skips / total,
            currentMood = currentMood,
            timeOfDay = events.lastOrNull()?.timeOfDay() ?: TimeOfDay.EVENING
        )
    }
}

data class StationDirection(
    val stage: Int,
    val allowedDistance: Float,
    val accepted: Boolean,
    val reason: String
)

object RadioSeedLock {
    fun acceptsCandidate(seed: RadioSeed, candidateArtist: String): Boolean {
        val seedArtist = normalize(seed.artist)
        val candidate = normalize(candidateArtist)
        if (seedArtist.isBlank() || candidate.isBlank()) return true
        if (seedArtist == candidate) return true

        val seedTerms = seedArtist.split(" ").filter { it.length >= 3 }.toSet()
        val candidateTerms = candidate.split(" ").filter { it.length >= 3 }.toSet()
        if (seedTerms.isEmpty() || candidateTerms.isEmpty()) return false
        return seedTerms.intersect(candidateTerms).isNotEmpty()
    }
}

object FatigueManager {
    fun artistPenalty(events: List<TasteEvent>, artist: String): Float {
        val normalizedArtist = normalize(artist)
        if (normalizedArtist.isBlank()) return 0f
        return events
            .takeLast(20)
            .filter { normalize(it.artist) == normalizedArtist }
            .sumOf {
                when (it.event) {
                    TasteEventType.SKIPPED -> 1.2
                    TasteEventType.REPLAYED, TasteEventType.LIKED, TasteEventType.SAVED -> -0.6
                    else -> 0.2
                }
            }
            .toFloat()
            .coerceAtLeast(0f)
    }

    fun tightenForHighSkipRate(skipRate: Float): Float =
        when {
            skipRate >= 0.50f -> 0.20f
            skipRate >= 0.35f -> 0.35f
            else -> 0.55f
        }
}

object StationPlanner {
    fun decideStage(seed: RadioSeed, events: List<TasteEvent>, candidateArtist: String, candidateGenre: String?): StationDirection {
        if (!RadioSeedLock.acceptsCandidate(seed, candidateArtist)) {
            return StationDirection(stage = 1, allowedDistance = 0f, accepted = false, reason = "seed_lock_rejected")
        }
        val vector = TasteVectorBuilder.fromEvents(events)
        val skipTightness = FatigueManager.tightenForHighSkipRate(vector.skipRate)
        val seedArtist = normalize(seed.artist)
        val candidateArtistKey = normalize(candidateArtist)
        val sameArtist = seedArtist.isNotBlank() && candidateArtistKey == seedArtist
        val sameGenre = candidateGenre != null && normalize(candidateGenre) == normalize(seed.genre)
        val stage = when {
            sameArtist -> 1
            sameGenre -> 2
            else -> 3
        }
        val accepted = when (stage) {
            1 -> true
            2 -> skipTightness >= 0.30f
            else -> skipTightness >= 0.50f
        }
        return StationDirection(
            stage = stage,
            allowedDistance = skipTightness,
            accepted = accepted,
            reason = if (accepted) "stage_${stage}_accepted" else "skip_rate_tightened"
        )
    }
}

data class DiscoveryPlan(
    val safeCount: Int,
    val adjacentCount: Int,
    val adventurousCount: Int
)

object DiscoveryPlanner {
    fun plan(totalCount: Int, profile: TasteProfile): DiscoveryPlan {
        val safeRatio = when {
            profile.highSkipRate -> 0.75f
            profile.discoveryOpenness >= 0.65f -> 0.50f
            else -> 0.60f
        }
        val adventurousRatio = when {
            profile.highSkipRate -> 0.10f
            profile.discoveryOpenness >= 0.65f -> 0.22f
            else -> 0.15f
        }
        val safe = (totalCount * safeRatio).toInt()
        val adventurous = (totalCount * adventurousRatio).toInt()
        val adjacent = (totalCount - safe - adventurous).coerceAtLeast(0)
        return DiscoveryPlan(
            safeCount = safe,
            adjacentCount = adjacent,
            adventurousCount = adventurous
        )
    }
}

private fun normalize(value: String?): String =
    value.orEmpty().trim().lowercase(Locale.US)
