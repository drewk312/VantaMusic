package com.audiophile.musicplayer.radio

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

data class RadioStationIntent(
    val seedType: RadioSeedType,
    val seedTrackTitle: String? = null,
    val seedTrackArtist: String? = null,
    val mood: String? = null,
    val genre: String? = null,
    val eraStart: Int? = null,
    val eraEnd: Int? = null,
    val energy: RadioEnergyLevel = RadioEnergyLevel.ANY,
    val discoveryLevel: RadioDiscoveryLevel = RadioDiscoveryLevel.FAMILIAR,
    val exclusions: List<String> = emptyList(),
    val maxPerArtist: Int = 2,
    val targetQueueSize: Int = 30,
    val minPlayableToStart: Int = 5
)

enum class RadioSeedType { SONG, ARTIST, MOOD, ERA, PROMPT, ACTIVITY }
enum class RadioEnergyLevel { ANY, LOW, MEDIUM, HIGH }
enum class RadioDiscoveryLevel { FAMILIAR, BALANCED, DISCOVERY }

sealed class RadioFeedbackAction {
    data object MoreLikeThis : RadioFeedbackAction()
    data object LessLikeThis : RadioFeedbackAction()
    data class NeverPlayThis(val trackId: Long, val title: String, val artist: String) : RadioFeedbackAction()
    data object MoreFamiliar : RadioFeedbackAction()
    data object MoreDiscovery : RadioFeedbackAction()
    data object MoreEnergy : RadioFeedbackAction()
    data object LessEnergy : RadioFeedbackAction()
    data object StayOnThisVibe : RadioFeedbackAction()
    data class ChangeTheVibe(val newMood: String) : RadioFeedbackAction()
}

data class RadioGenerationLog(
    val seedIdentity: String,
    val stationIntent: String,
    val candidateCount: Int,
    val rejectedCandidates: List<String>,
    val gateRejections: List<String>,
    val finalQueueSize: Int,
    val firstTrackTitle: String,
    val firstTrackArtist: String,
    val top10Reasons: List<String>,
    val seedType: RadioSeedType,
    val totalQueries: Int
)

object RadioBrain {

    private const val TAG = "VANTA_RADIO_BRAIN"

    fun buildIntent(
        seedType: RadioSeedType,
        trackTitle: String? = null,
        trackArtist: String? = null,
        userInput: String? = null,
        energy: RadioEnergyLevel = RadioEnergyLevel.ANY,
        discoveryLevel: RadioDiscoveryLevel = RadioDiscoveryLevel.FAMILIAR
    ): RadioStationIntent {
        val resolvedMood: String?
        val resolvedGenre: String?
        val eraStart: Int?
        val eraEnd: Int?

        when (seedType) {
            RadioSeedType.SONG -> {
                resolvedMood = null
                resolvedGenre = null
                eraStart = null
                eraEnd = null
            }
            RadioSeedType.ARTIST -> {
                resolvedMood = null
                resolvedGenre = null
                eraStart = null
                eraEnd = null
            }
            RadioSeedType.MOOD -> {
                val vibe = VibeTranslator.extractSearchQueries(userInput.orEmpty())
                resolvedMood = vibe.firstOrNull()
                resolvedGenre = vibe.getOrNull(1)
                val era = detectEraFromMood(userInput.orEmpty())
                eraStart = era.first
                eraEnd = era.second
            }
            RadioSeedType.PROMPT -> {
                val parsed = parsePrompt(userInput.orEmpty())
                resolvedMood = parsed.mood
                resolvedGenre = parsed.genre
                eraStart = parsed.eraStart
                eraEnd = parsed.eraEnd
            }
            else -> {
                resolvedMood = null
                resolvedGenre = null
                eraStart = null
                eraEnd = null
            }
        }

        return RadioStationIntent(
            seedType = seedType,
            seedTrackTitle = trackTitle,
            seedTrackArtist = trackArtist,
            mood = resolvedMood,
            genre = resolvedGenre,
            eraStart = eraStart,
            eraEnd = eraEnd,
            energy = energy,
            discoveryLevel = discoveryLevel,
            targetQueueSize = if (seedType == RadioSeedType.MOOD || seedType == RadioSeedType.PROMPT) 40 else 30,
            minPlayableToStart = if (seedType == RadioSeedType.MOOD || seedType == RadioSeedType.PROMPT) 8 else 5
        )
    }

    fun buildVerifiedQueue(
        tracks: List<UnifiedTrackWithSources>,
        intent: RadioStationIntent,
        previousTrackIds: Set<Long> = emptySet(),
        generationToken: Long = 0L
    ): Pair<List<UnifiedTrackWithSources>, List<String>> {
        val verified = mutableListOf<UnifiedTrackWithSources>()
        val rejections = mutableListOf<String>()
        val artistCount = mutableMapOf<String, Int>()
        val seenTitleArtist = mutableSetOf<String>()

        for (track in tracks) {
            if (track.track.trackId in previousTrackIds) {
                rejections.add("already_played:${track.track.title}")
                continue
            }

            val gateResult = PlaybackIdentityGate.verify(track)
            if (gateResult !is GateVerdict.Passed) {
                rejections.add("gate:${(gateResult as GateVerdict.Failed).reason}:${track.track.title}:${track.track.artist}")
                continue
            }

            val artistKey = track.track.artist.lowercase().trim()
            val titleArtistKey = "${track.track.title.lowercase().trim()}|$artistKey"

            if (titleArtistKey in seenTitleArtist) {
                rejections.add("duplicate:$titleArtistKey")
                continue
            }
            seenTitleArtist.add(titleArtistKey)

            if (verified.size < 10) {
                val currentArtistCount = artistCount[artistKey] ?: 0
                if (currentArtistCount >= intent.maxPerArtist) {
                    rejections.add("artist_cap:$artistKey")
                    continue
                }
            }

            // Song radio: keep seed artist around 20–25% of the verified queue.
            if (intent.seedType == RadioSeedType.SONG &&
                !intent.seedTrackArtist.isNullOrBlank() &&
                SongRadioRelatedness.artistsMatch(intent.seedTrackArtist, artistKey)
            ) {
                val seedSoFar = artistCount.entries
                    .filter { SongRadioRelatedness.artistsMatch(it.key, intent.seedTrackArtist) }
                    .sumOf { it.value }
                val nextShare = (seedSoFar + 1).toDouble() / (verified.size + 1).toDouble()
                if (verified.size >= 3 && nextShare > 0.25) {
                    rejections.add("seed_artist_share_cap:$artistKey")
                    continue
                }
            }

            // Song radio relatedness gate (Weeknd→Weekend, foreign covers, listicles).
            if (intent.seedType == RadioSeedType.SONG) {
                val title = track.track.title
                val artist = track.track.artist
                if (SongRadioRelatedness.isArtistNameCollision(
                        intent.seedTrackArtist,
                        title,
                        artist,
                        track.track.albumName
                    ) ||
                    SongRadioRelatedness.isWeakTitleTokenSpam(
                        intent.seedTrackTitle,
                        intent.seedTrackArtist,
                        title,
                        artist
                    ) ||
                    SongRadioRelatedness.isForeignHitCover(
                        intent.seedTrackTitle,
                        title,
                        artist,
                        intent.seedTrackArtist
                    ) ||
                    SongRadioRelatedness.isListicleOrCompilationAlbum(
                        title,
                        artist,
                        track.track.albumName
                    )
                ) {
                    rejections.add("song_radio_relatedness:$title:$artist")
                    continue
                }
            }

            artistCount[artistKey] = (artistCount[artistKey] ?: 0) + 1
            verified.add(track)

            if (verified.size >= intent.targetQueueSize) break
        }

        return verified to rejections
    }

    fun applyFeedback(intent: RadioStationIntent, action: RadioFeedbackAction): RadioStationIntent {
        return when (action) {
            is RadioFeedbackAction.MoreLikeThis -> intent.copy(
                energy = when (intent.energy) {
                    RadioEnergyLevel.LOW -> RadioEnergyLevel.MEDIUM
                    else -> intent.energy
                },
                discoveryLevel = RadioDiscoveryLevel.FAMILIAR
            )
            is RadioFeedbackAction.LessLikeThis -> intent.copy(
                maxPerArtist = (intent.maxPerArtist - 1).coerceAtLeast(1)
            )
            is RadioFeedbackAction.NeverPlayThis -> intent.copy(
                exclusions = intent.exclusions + "${action.title} ${action.artist}"
            )
            is RadioFeedbackAction.MoreFamiliar -> intent.copy(
                discoveryLevel = RadioDiscoveryLevel.FAMILIAR
            )
            is RadioFeedbackAction.MoreDiscovery -> intent.copy(
                discoveryLevel = RadioDiscoveryLevel.DISCOVERY
            )
            is RadioFeedbackAction.MoreEnergy -> intent.copy(
                energy = when (intent.energy) {
                    RadioEnergyLevel.LOW -> RadioEnergyLevel.MEDIUM
                    RadioEnergyLevel.MEDIUM -> RadioEnergyLevel.HIGH
                    else -> RadioEnergyLevel.HIGH
                }
            )
            is RadioFeedbackAction.LessEnergy -> intent.copy(
                energy = when (intent.energy) {
                    RadioEnergyLevel.HIGH -> RadioEnergyLevel.MEDIUM
                    RadioEnergyLevel.MEDIUM -> RadioEnergyLevel.LOW
                    else -> RadioEnergyLevel.LOW
                }
            )
            is RadioFeedbackAction.StayOnThisVibe -> intent.copy(
                discoveryLevel = RadioDiscoveryLevel.FAMILIAR
            )
            is RadioFeedbackAction.ChangeTheVibe -> intent.copy(
                mood = action.newMood
            )
        }
    }

    fun buildGenerationLog(
        seedIdentity: String,
        intent: RadioStationIntent,
        candidates: List<UnifiedTrackWithSources>,
        rejections: List<String>,
        gateRejections: List<String>,
        verifiedQueue: List<UnifiedTrackWithSources>,
        totalQueries: Int
    ): RadioGenerationLog {
        val top10Reasons = verifiedQueue.take(10).map { track ->
            val artist = track.track.artist
            val title = track.track.title
            val genre = track.track.genre ?: "unknown"
            "$title by $artist (genre=$genre)"
        }
        return RadioGenerationLog(
            seedIdentity = seedIdentity,
            stationIntent = "${intent.seedType} mood=${intent.mood} genre=${intent.genre} era=${intent.eraStart}-${intent.eraEnd} energy=${intent.energy}",
            candidateCount = candidates.size,
            rejectedCandidates = rejections,
            gateRejections = gateRejections,
            finalQueueSize = verifiedQueue.size,
            firstTrackTitle = verifiedQueue.firstOrNull()?.track?.title ?: "none",
            firstTrackArtist = verifiedQueue.firstOrNull()?.track?.artist ?: "none",
            top10Reasons = top10Reasons,
            seedType = intent.seedType,
            totalQueries = totalQueries
        )
    }

    fun logGeneration(log: RadioGenerationLog) {
        Log.i(TAG, "=== RADIO GENERATION ===")
        Log.i(TAG, "seed_identity='${log.seedIdentity}'")
        Log.i(TAG, "station_intent='${log.stationIntent}'")
        Log.i(TAG, "candidate_count=${log.candidateCount} total_queries=${log.totalQueries}")
        Log.i(TAG, "gate_rejections=${log.gateRejections.size}: ${log.gateRejections.joinToString(" | ")}")
        Log.i(TAG, "candidate_rejections=${log.rejectedCandidates.size}: ${log.rejectedCandidates.take(20).joinToString(" | ")}")
        Log.i(TAG, "final_queue_size=${log.finalQueueSize}")
        Log.i(TAG, "first_track='${log.firstTrackTitle}' by '${log.firstTrackArtist}'")
        Log.i(TAG, "seed_type=${log.seedType}")
        Log.i(TAG, "top10:")
        log.top10Reasons.forEachIndexed { index, reason ->
            Log.i(TAG, "  [${index + 1}] $reason")
        }
        Log.i(TAG, "=== END ===")
    }

    private data class PromptParse(
        val mood: String? = null,
        val genre: String? = null,
        val eraStart: Int? = null,
        val eraEnd: Int? = null
    )

    private fun parsePrompt(input: String): PromptParse {
        val lower = input.lowercase().trim()
        var mood: String? = null
        var genre: String? = null
        var eraStart: Int? = null
        var eraEnd: Int? = null

        val eraPattern = Regex("""\b(\d{2})s\b""")
        val eraMatch = eraPattern.find(lower)
        if (eraMatch != null) {
            val decade = eraMatch.groupValues[1].toIntOrNull()
            if (decade != null) {
                eraStart = 1900 + decade
                eraEnd = eraStart + 9
            }
        }

        val knownGenres = listOf(
            "classic rock", "country", "hip hop", "r&b", "jazz", "blues", "metal", "punk",
            "indie", "alternative", "electronic", "edm", "house", "techno", "pop", "soul",
            "funk", "disco", "folk", "americana", "synthwave", "lofi", "ambient"
        )
        for (g in knownGenres) {
            if (lower.contains(g)) {
                genre = g
                break
            }
        }

        val moodKeywords = mapOf(
            "chill" to "ambient",
            "quiet" to "ambient",
            "relax" to "ambient",
            "sad" to "acoustic",
            "happy" to "pop",
            "upbeat" to "pop",
            "angry" to "metal",
            "dark" to "synthwave",
            "party" to "dance",
            "workout" to "high energy",
            "focus" to "ambient",
            "sleep" to "ambient",
            "romantic" to "soul",
            "driving" to "rock",
            "cookout" to "country",
            "summer" to "pop",
        )
        for ((keyword, targetMood) in moodKeywords) {
            if (lower.contains(keyword)) {
                mood = targetMood
                break
            }
        }

        return PromptParse(mood, genre, eraStart, eraEnd)
    }

    private fun detectEraFromMood(input: String): Pair<Int?, Int?> {
        val lower = input.lowercase()
        return when {
            "4th of july" in lower || "cookout" in lower || "summer" in lower -> 1990 to 2010
            "late night" in lower || "night drive" in lower -> 2010 to null
            "retro" in lower || "vintage" in lower -> 1970 to 1990
            "80s" in lower || "80's" in lower -> 1980 to 1989
            "90s" in lower || "90's" in lower -> 1990 to 1999
            else -> null to null
        }
    }
}
