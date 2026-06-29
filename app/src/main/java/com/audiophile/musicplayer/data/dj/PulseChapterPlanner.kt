package com.audiophile.musicplayer.data.dj

import kotlin.random.Random

/**
 * Chapter-based DJ logic for Pulse Live — not a fixed "six songs then switch" counter.
 * Measures engagement and decides stay, evolve, or bridge to a new pocket.
 */
class PulseChapterPlanner {

    data class ChapterPlan(
        val genreCluster: String,
        val targetTracks: Int,
        val vibeDescription: String,
        val bridgeFrom: String? = null
    )

    data class ChapterDecision(
        val action: Action,
        val nextGenre: String? = null,
        val bridgeGenre: String? = null,
        val extensionTracks: Int = 0
    ) {
        enum class Action { STAY, EVOLVE, TRANSITION }
    }

    /** Adjacent genre clusters for musically sensible journeys. */
    private val genreAdjacency = mapOf(
        "soul" to listOf("funk", "r&b", "motown", "quiet storm"),
        "funk" to listOf("soul", "disco", "yacht rock", "r&b"),
        "yacht rock" to listOf("soft rock", "classic pop", "funk", "rock"),
        "soft rock" to listOf("yacht rock", "classic pop", "rock", "country"),
        "classic pop" to listOf("soft rock", "pop", "soul", "rock"),
        "motown" to listOf("soul", "r&b", "funk", "classic pop"),
        "disco" to listOf("funk", "pop", "dance", "soul"),
        "rock" to listOf("classic rock", "southern rock", "alternative", "soft rock"),
        "alternative" to listOf("rock", "indie", "grunge", "pop"),
        "country" to listOf("americana", "southern rock", "folk", "rock"),
        "r&b" to listOf("soul", "quiet storm", "funk", "pop"),
        "quiet storm" to listOf("r&b", "soul", "jazz"),
        "jazz" to listOf("quiet storm", "soul", "funk"),
        "electronic" to listOf("dance", "pop", "synth", "house"),
        "hip-hop" to listOf("r&b", "funk", "soul", "pop")
    )

    fun pickInitialCluster(profile: AiDjTasteProfile): String {
        val fromProfile = profile.favoriteGenres.firstOrNull()?.lowercase()
        if (fromProfile != null) {
            val normalized = normalizeGenre(fromProfile)
            if (normalized != null) return normalized
        }
        val defaults = listOf("soul", "classic pop", "rock", "r&b", "yacht rock")
        return defaults[Random.nextInt(defaults.size)]
    }

    fun planChapter(
        profile: AiDjTasteProfile,
        previousCluster: String?,
        likes: Int,
        skips: Int,
        forceTransition: Boolean = false
    ): ChapterPlan {
        val cluster = when {
            forceTransition && previousCluster != null -> pickAdjacentCluster(previousCluster, profile)
            previousCluster != null && likes >= 2 && skips == 0 -> previousCluster
            previousCluster != null && skips >= 2 -> pickAdjacentCluster(previousCluster, profile)
            previousCluster != null -> previousCluster
            else -> pickInitialCluster(profile)
        }

        val bridgeFrom = if (forceTransition && previousCluster != null && previousCluster != cluster) {
            findBridgeGenre(previousCluster, cluster)
        } else null

        val baseSize = Random.nextInt(4, 8)
        val target = when {
            likes >= 2 -> baseSize + Random.nextInt(1, 3)
            skips >= 2 -> Random.nextInt(3, 5)
            else -> baseSize
        }

        val vibe = when {
            bridgeFrom != null -> "bridging from $bridgeFrom toward $cluster"
            likes >= 2 -> "staying in the $cluster pocket"
            skips >= 2 -> "opening up from $cluster"
            else -> "$cluster chapter"
        }

        return ChapterPlan(
            genreCluster = cluster,
            targetTracks = target.coerceIn(3, 9),
            vibeDescription = vibe,
            bridgeFrom = bridgeFrom
        )
    }

    fun evaluateChapter(
        chapterLikes: Int,
        chapterSkips: Int,
        tracksPlayedInChapter: Int,
        targetTracks: Int
    ): ChapterDecision {
        if (chapterLikes >= 2 && tracksPlayedInChapter < targetTracks + 3) {
            return ChapterDecision(
                action = ChapterDecision.Action.STAY,
                extensionTracks = Random.nextInt(2, 4)
            )
        }
        if (chapterSkips >= 2 || tracksPlayedInChapter >= targetTracks) {
            return ChapterDecision(action = ChapterDecision.Action.TRANSITION)
        }
        if (tracksPlayedInChapter >= targetTracks - 1 && chapterLikes > 0) {
            return ChapterDecision(action = ChapterDecision.Action.EVOLVE)
        }
        return ChapterDecision(action = ChapterDecision.Action.STAY)
    }

    fun pickAdjacentCluster(current: String, profile: AiDjTasteProfile): String {
        val neighbors = genreAdjacency[current] ?: genreAdjacency[normalizeGenre(current)] ?: emptyList()
        val favored = profile.favoriteGenres.mapNotNull { normalizeGenre(it) }
        val match = neighbors.firstOrNull { it in favored }
        if (match != null) return match
        if (neighbors.isNotEmpty()) return neighbors[Random.nextInt(neighbors.size)]
        return pickInitialCluster(profile)
    }

    fun findBridgeGenre(from: String, to: String): String? {
        val fromNeighbors = genreAdjacency[from] ?: emptyList()
        val toNeighbors = genreAdjacency[to] ?: emptyList()
        return fromNeighbors.firstOrNull { it in toNeighbors } ?: fromNeighbors.firstOrNull()
    }

    fun normalizeGenre(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val lower = raw.trim().lowercase()
        val aliases = mapOf(
            "rnb" to "r&b",
            "r and b" to "r&b",
            "soft rock" to "soft rock",
            "classic rock" to "rock",
            "alt" to "alternative",
            "hip hop" to "hip-hop",
            "rap" to "hip-hop"
        )
        return aliases[lower] ?: lower
    }

    fun clusterKeywords(cluster: String): List<String> = when (cluster) {
        "soul" -> listOf("soul", "r&b", "rnb")
        "funk" -> listOf("funk", "groove")
        "yacht rock" -> listOf("yacht", "soft rock", "smooth")
        "soft rock" -> listOf("soft rock", "adult contemporary", "rock")
        "classic pop" -> listOf("pop", "classic", "oldies")
        "motown" -> listOf("motown", "soul")
        "disco" -> listOf("disco", "dance")
        "rock" -> listOf("rock", "classic rock")
        "alternative" -> listOf("alternative", "alt", "grunge", "indie")
        "country" -> listOf("country", "americana")
        "r&b" -> listOf("r&b", "rnb", "rhythm")
        "quiet storm" -> listOf("quiet storm", "slow", "smooth")
        "jazz" -> listOf("jazz", "smooth jazz")
        "electronic" -> listOf("electronic", "edm", "synth", "house")
        "hip-hop" -> listOf("hip-hop", "hip hop", "rap")
        "southern rock" -> listOf("southern", "rock")
        else -> listOf(cluster)
    }
}
