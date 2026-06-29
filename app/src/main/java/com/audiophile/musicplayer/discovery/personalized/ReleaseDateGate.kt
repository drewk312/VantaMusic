package com.audiophile.musicplayer.discovery.personalized

import java.time.LocalDate

/**
 * Conservative future-release gate — architecture derived from SoulSync
 * `core/metadata/release_dates.py` (#705). Only blocks when confidently future;
 * missing or unparseable dates are treated as released.
 */
object ReleaseDateGate {
    fun isFutureRelease(releaseDateStr: String?, today: LocalDate = LocalDate.now()): Boolean {
        if (releaseDateStr.isNullOrBlank()) return false
        val parts = releaseDateStr.trim().split('-')
        val year = parts.firstOrNull()?.toIntOrNull() ?: return false
        if (parts.size == 1 || parts[1].isBlank()) {
            return year > today.year
        }
        val month = parts[1].toIntOrNull() ?: return year > today.year
        if (month !in 1..12) return year > today.year
        if (parts.size == 2 || parts[2].isBlank()) {
            return year > today.year || (year == today.year && month > today.monthValue)
        }
        val day = parts[2].take(2).toIntOrNull() ?: return year > today.year ||
            (year == today.year && month > today.monthValue)
        return runCatching { LocalDate.of(year, month, day) > today }
            .getOrElse { year > today.year || (year == today.year && month > today.monthValue) }
    }

    fun trackReleaseDate(albumReleaseDate: String?, trackReleaseDate: String? = null): String? =
        albumReleaseDate?.takeIf { it.isNotBlank() } ?: trackReleaseDate?.takeIf { it.isNotBlank() }

    fun splitReleasedUnreleased(
        candidates: List<MixCandidate>,
        today: LocalDate = LocalDate.now()
    ): Pair<List<MixCandidate>, List<MixCandidate>> {
        val released = mutableListOf<MixCandidate>()
        val unreleased = mutableListOf<MixCandidate>()
        for (candidate in candidates) {
            val date = trackReleaseDate(candidate.releaseDate, candidate.releaseDate)
            if (isFutureRelease(date, today)) unreleased.add(candidate) else released.add(candidate)
        }
        return released to unreleased
    }

    fun isLikelyRemasterOrReupload(candidate: MixCandidate, currentYear: Int): Boolean {
        val haystack = "${candidate.title} ${candidate.album.orEmpty()}".lowercase()
        if (haystack.contains("remaster") || haystack.contains("re-upload") || haystack.contains("reupload")) {
            return true
        }
        val releaseYear = candidate.releaseYear ?: return false
        return releaseYear < currentYear - 5 && haystack.contains("deluxe")
    }
}
