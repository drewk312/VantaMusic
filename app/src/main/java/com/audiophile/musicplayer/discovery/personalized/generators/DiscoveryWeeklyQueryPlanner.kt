package com.audiophile.musicplayer.discovery.personalized.generators

/**
 * Query planning for Discovery Weekly — testable without network I/O.
 */
object DiscoveryWeeklyQueryPlanner {
    fun planQueries(
        seedArtists: List<String>,
        seedGenres: List<String>,
        limit: Int
    ): List<String> {
        val queries = mutableListOf<String>()
        for (artist in seedArtists.take(6)) {
            queries += "$artist songs"
            queries += "$artist top tracks"
        }
        for (genre in seedGenres.take(4)) {
            queries += "$genre mix"
            queries += "best $genre songs"
            val adjacent = adjacentGenre(genre)
            if (adjacent != null) queries += "$adjacent discovery"
        }
        if (queries.isEmpty()) {
            queries += listOf("indie discovery", "new music friday", "alternative hits 2024")
        }
        return queries.distinct().take((limit / 2).coerceAtLeast(8))
    }

    fun adjacentGenre(genre: String): String? = when (genre.lowercase()) {
        "indie" -> "dream pop"
        "alternative" -> "indie rock"
        "pop" -> "synth pop"
        "r&b", "rnb" -> "neo soul"
        "rock" -> "garage rock"
        "hip hop", "hip-hop" -> "conscious rap"
        "electronic" -> "house"
        else -> null
    }
}
