package com.audiophile.musicplayer.radio

object VibeTranslator {
    private val personalTasteWords = setOf(
        "favorite", "favorites", "favourite", "favourites", "liked", "likes", "my", "mine"
    )

    private val vibeMap = mapOf(
        "quiet car ride" to listOf("chill", "acoustic", "ambient pop", "soft rock", "indie folk", "low energy"),
        "4th of july cookout" to listOf("country", "classic rock", "pop hits", "summer", "upbeat"),
        "fourth of july cookout" to listOf("country", "classic rock", "pop hits", "summer", "upbeat"),
        "july 4th" to listOf("country", "classic rock", "pop hits", "summer", "upbeat"),
        "late night drive" to listOf("synthwave", "r&b", "dark pop", "mellow hip-hop", "deep house"),
        "night drive" to listOf("synthwave", "dark r&b", "deep house", "vaporwave"),
        "road trip" to listOf("pop", "indie pop", "classic rock", "synthpop", "90s rock", "americana"),
        "workout" to listOf("hip-hop", "edm", "trap", "high energy", "hard rock", "drum and bass"),
        "running" to listOf("high energy", "edm", "pop", "rock", "uptempo"),
        "sad" to listOf("acoustic", "indie", "soul", "ballad", "melancholy", "folk"),
        "focus" to listOf("ambient", "classical", "lo-fi", "instrumental", "minimalist"),
        "study" to listOf("lo-fi", "instrumental", "ambient", "classical", "focus"),
        "party" to listOf("dance", "pop", "hip-hop", "house", "funk", "disco"),
        "chill" to listOf("lo-fi", "ambient", "indie", "r&b", "laid back", "neo-soul"),
        "relax" to listOf("acoustic", "ambient", "lo-fi", "soft pop", "smooth jazz"),
        "high energy" to listOf("edm", "hard rock", "uptempo pop", "dance", "punk"),
        "meditation" to listOf("ambient", "drone", "new age", "world music"),
        "morning coffee" to listOf("jazz", "acoustic indie", "bossa nova", "mellow folk"),
        "dinner party" to listOf("jazz", "bossa nova", "soul classics", "lounge"),
        "bedtime" to listOf("ambient", "sleep", "drone", "soft piano", "nature"),
        "sleep" to listOf("ambient", "drone", "soft piano", "sleep", "calm"),
        "romantic" to listOf("r&b", "soul", "jazz", "ballad", "soft pop"),
        "dark" to listOf("dark r&b", "synthwave", "industrial", "gothic", "doom"),
        "rainy day" to listOf("lo-fi", "acoustic", "jazz", "rainy day", "melancholy"),
        "summer" to listOf("summer hits", "tropical house", "upbeat pop", "reggae"),
        "cookout" to listOf("country", "classic rock", "barbecue", "summer hits", "soul"),
        "commute" to listOf("pop", "podcast", "rock", "indie", "talk radio"),
    )

    private val singleWordVibes = mapOf(
        "quiet" to listOf("acoustic", "ambient", "indie folk", "chill"),
        "drive" to listOf("synthwave", "driving rock", "road trip", "classic rock"),
        "driving" to listOf("synthwave", "driving rock", "road trip", "classic rock"),
        "night" to listOf("late night", "r&b", "synthwave", "midnight", "dark"),
        "car" to listOf("driving rock", "road trip", "classic rock", "indie"),
        "cookout" to listOf("country", "classic rock", "summer hits", "soul", "barbecue"),
        "july" to listOf("summer hits", "country", "classic rock", "pop", "upbeat"),
        "workout" to listOf("gym", "high energy", "EDM", "hip hop", "hard rock"),
        "party" to listOf("dance", "pop hits", "upbeat", "club", "party"),
        "chill" to listOf("lo-fi", "ambient", "indie", "r&b", "laid back"),
        "relax" to listOf("acoustic", "ambient", "lo-fi", "soft pop"),
        "sad" to listOf("heartbreak", "acoustic", "melancholy", "sad indie", "ballads"),
        "happy" to listOf("feel good", "upbeat", "pop", "summer", "happy"),
        "sleep" to listOf("ambient", "drone", "meditation", "sleep", "lullaby"),
        "focus" to listOf("lo-fi beats", "instrumental", "ambient", "classical"),
        "morning" to listOf("coffee", "acoustic", "morning", "upbeat indie"),
        "summer" to listOf("summer hits", "tropical house", "upbeat pop", "reggae"),
        "rain" to listOf("lo-fi", "acoustic", "jazz", "rainy day"),
        "gym" to listOf("workout", "high energy", "EDM", "hip hop", "metal"),
        "study" to listOf("lo-fi", "instrumental", "classical", "focus", "ambient"),
    )

    fun extractSearchQueries(prompt: String): List<String> {
        val lower = StationQuerySanitizer.cleanStationMeta(prompt).lowercase().trim()
        if (lower.isBlank()) return broadTasteFallback()

        val words = lower.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (isPersonalTastePromptWords(words)) {
            return broadTasteFallback()
        }

        for ((vibe, genres) in vibeMap) {
            if (lower.contains(vibe)) {
                return genres
            }
        }

        for ((vibe, genres) in singleWordVibes) {
            if (words.any { it == vibe }) {
                return genres
            }
        }

        return semanticFallbackQueries(words)
    }

    fun isPersonalTastePrompt(prompt: String): Boolean {
        val lower = StationQuerySanitizer.cleanStationMeta(prompt).lowercase().trim()
        val words = lower.split(Regex("""\s+""")).filter { it.isNotBlank() }
        return isPersonalTastePromptWords(words)
    }

    fun isVibePrompt(prompt: String): Boolean {
        val lower = StationQuerySanitizer.cleanStationMeta(prompt).lowercase().trim()
        if (lower.isBlank()) return false
        val words = lower.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (isPersonalTastePromptWords(words)) return false
        return vibeMap.keys.any { lower.contains(it) } || words.any { it in singleWordVibes.keys }
    }

    private fun isPersonalTastePromptWords(words: List<String>): Boolean =
        words.any { it in personalTasteWords }

    private fun semanticFallbackQueries(words: List<String>): List<String> {
        val mapped = words
            .filterNot { it in personalTasteWords }
            .flatMap { singleWordVibes[it] ?: emptyList() }
            .distinct()
        if (mapped.isNotEmpty()) return mapped
        return broadTasteFallback()
    }

    private fun broadTasteFallback(): List<String> =
        listOf("indie pop", "r&b", "alternative", "classic rock", "hip hop", "electronic", "soul")
}
