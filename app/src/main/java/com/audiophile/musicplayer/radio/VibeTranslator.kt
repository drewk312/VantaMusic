package com.audiophile.musicplayer.radio

/**
 * Maps user "vibes" to actual musical genres or seeds.
 * Prevents literal word searches for non-musical prompt terms.
 */
object VibeTranslator {
    private val vibeMap = mapOf(
        "quiet car ride" to listOf("ambient", "chill", "acoustic", "lo-fi", "indie folk", "post-rock"),
        "workout" to listOf("hip-hop", "edm", "trap", "high energy", "hard rock", "drum and bass"),
        "night drive" to listOf("synthwave", "vaporwave", "dark r&b", "deep house", "techno"),
        "road trip" to listOf("pop", "indie pop", "classic rock", "synthpop", "90s rock", "americana"),
        "sad" to listOf("acoustic", "indie", "soul", "ballad", "melancholy", "folk"),
        "focus" to listOf("ambient", "classical", "lo-fi", "instrumental", "minimalist"),
        "party" to listOf("dance", "pop", "hip-hop", "house", "funk", "disco"),
        "chill" to listOf("lo-fi", "ambient", "indie", "r&b", "laid back", "neo-soul"),
        "relax" to listOf("acoustic", "ambient", "lo-fi", "soft pop", "smooth jazz"),
        "high energy" to listOf("edm", "hard rock", "uptempo pop", "dance", "punk"),
        "meditation" to listOf("ambient", "drone", "new age", "world music", "nature sounds"),
        "morning coffee" to listOf("jazz", "acoustic indie", "bossa nova", "mellow folk")
    )

    fun extractSearchQueries(prompt: String): List<String> {
        val lowerPrompt = prompt.lowercase().trim()
        
        // 1. Check for exact or partial vibe matches in the map
        for ((vibe, genres) in vibeMap) {
            if (lowerPrompt.contains(vibe)) {
                return genres
            }
        }
        
        // 2. Fallback: Return the raw prompt as the single seed
        // Do NOT append "radio" or "station" here; let the planner handle expansion.
        return listOf(prompt.trim())
    }
}
