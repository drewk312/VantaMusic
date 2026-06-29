package com.audiophile.musicplayer.discovery.personalized.generators

/**
 * Query planning for Release Radar — testable without network I/O.
 */
object ReleaseRadarQueryPlanner {
    fun planQueries(seedArtists: List<String>, currentYear: Int, recencyDays: Int): List<String> {
        val years = if (recencyDays > 365) {
            listOf(currentYear, currentYear - 1)
        } else {
            listOf(currentYear, currentYear - 1)
        }
        val queries = mutableListOf<String>()
        for (artist in seedArtists.take(10)) {
            for (year in years) {
                queries += "$artist $year"
                queries += "$artist new $year"
            }
            queries += "$artist latest"
            queries += "$artist new single"
        }
        if (queries.isEmpty()) {
            queries += listOf("new music $currentYear", "latest singles $currentYear")
        }
        return queries.distinct()
    }
}
