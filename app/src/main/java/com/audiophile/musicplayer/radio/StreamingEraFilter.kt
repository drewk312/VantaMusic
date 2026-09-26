package com.audiophile.musicplayer.radio

/**
 * Keeps era-locked stations (e.g. '90s Hits) from drifting into obvious wrong decades.
 * Uses release year when present in metadata text; never blocks when year is unknown.
 */
object StreamingEraFilter {
    private val yearInText = Regex("""\b(19[5-9]\d|20[0-2]\d)\b""")
    private val decadeInText = Regex("""\b([5-9]0)s\b""", RegexOption.IGNORE_CASE)

    fun inferYear(releaseDate: String?, text: String? = null): Int? {
        if (!releaseDate.isNullOrBlank()) {
            val dateYear = yearInText.find(releaseDate)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (dateYear != null) return dateYear
        }
        if (!text.isNullOrBlank()) {
            val textYear = yearInText.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (textYear != null) return textYear
        }
        return null
    }

    fun inferYear(text: String): Int? = inferYear(null, text)

    fun eraFitScore(
        seed: StreamingStationSeed,
        title: String,
        artist: String,
        album: String?,
        releaseDate: String? = null
    ): Float {
        val start = seed.eraStart ?: return 0f
        val end = seed.eraEnd ?: return 0f
        val haystack = "$title $artist ${album.orEmpty()}"
        val year = inferYear(releaseDate, haystack)
        if (year != null) {
            return when {
                year in start..end -> 35f
                year in (start - 3)..(end + 1) -> 8f
                else -> -100f
            }
        }
        val targetShort = (start % 100).toString().padStart(2, '0')
        decadeInText.findAll(haystack).forEach { match ->
            val decade = match.groupValues[1]
            if (decade != targetShort && decade != (start / 10 % 10).toString() + "0") {
                return -30f
            }
        }
        if (containsWrongEraCompilation(haystack, start, end)) return -35f
        return 0f
    }

    private val modernTraps = listOf(
        "karaoke",
        "tribute",
        "cover version",
        "re-recorded",
        "lounge version"
    )

    fun passesEraGate(
        seed: StreamingStationSeed,
        title: String,
        artist: String,
        album: String?,
        releaseDate: String? = null
    ): Boolean {
        val start = seed.eraStart ?: return true
        val end = seed.eraEnd ?: return true

        val haystack = "$title $artist ${album.orEmpty()}".lowercase()
        if (modernTraps.any { haystack.contains(it) }) return false

        val year = inferYear(releaseDate, haystack)
        if (year != null && (year < start - 2 || year > end + 2)) {
            return false
        }

        return eraFitScore(seed, title, artist, album, releaseDate) >= -12f
    }

    fun passesEraGate(trackYear: Int, trackTitle: String, spec: com.audiophile.musicplayer.radio.sonic.SonicStationSpec): Boolean {
        if (trackYear !in spec.eraStart..spec.eraEnd) return false
        val lower = trackTitle.lowercase()
        val traps = listOf("remix", "karaoke", "tribute", "cover version", "re-recorded", "lounge version")
        if (traps.any { lower.contains(it) }) return false
        return true
    }

    private fun containsWrongEraCompilation(haystack: String, start: Int, end: Int): Boolean {
        val lower = haystack.lowercase()
        if (!lower.contains("hits") && !lower.contains("oldies") && !lower.contains("mix")) return false
        for (decadeStart in 1950..2010 step 10) {
            val short = (decadeStart % 100).toString().padStart(2, '0')
            if (lower.contains("${short}s") || lower.contains("'$short")) {
                if (decadeStart < start - 5 || decadeStart > end + 5) return true
            }
        }
        return false
    }
}
