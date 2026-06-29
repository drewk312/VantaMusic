package com.audiophile.musicplayer.radio

/**
 * Lightweight taste profile used when ranking streaming station candidates.
 */
data class StreamingStationTasteSignals(
    val favoriteArtists: Set<String> = emptySet(),
    val skippedArtists: Map<String, Int> = emptyMap(),
    val genreAffinities: Map<String, Float> = emptyMap(),
    val recentTrackIds: Set<Long> = emptySet(),
    val dislikedArtists: Set<String> = emptySet()
) {
    fun artistPenalty(artist: String?): Float {
        val key = artist?.trim()?.lowercase().orEmpty()
        if (key.isBlank()) return 0f
        if (key in dislikedArtists) return -80f
        val skips = skippedArtists[key] ?: 0
        return when {
            skips >= 3 -> -50f
            skips == 2 -> -25f
            skips == 1 -> -10f
            key in favoriteArtists -> 12f
            else -> 0f
        }
    }

    fun genreBonus(genre: String?): Float {
        val key = genre?.trim()?.lowercase().orEmpty()
        if (key.isBlank()) return 0f
        return genreAffinities.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.value ?: 0f
    }
}
