package com.audiophile.musicplayer.data.dj

data class MoodChip(
    val id: String,
    val label: String,
    val aliases: List<String> = emptyList()
)

object MoodStationCatalog {
    val moods: List<MoodChip> = listOf(
        MoodChip("workout", "Workout", listOf("workout", "gym", "exercise", "running", "cardio")),
        MoodChip("chill", "Chill", listOf("chill", "chilled", "relax", "relaxed", "mellow", "laid back", "laid-back")),
        MoodChip("party", "Party", listOf("party", "dance party", "celebration", "turn up")),
        MoodChip("focus", "Focus", listOf("focus", "concentration", "study", "deep work", "productivity")),
        MoodChip("road_trip", "Road Trip", listOf("road trip", "roadtrip", "driving", "highway", "open road")),
        MoodChip("rainy_day", "Rainy Day", listOf("rainy day", "rain", "rainy", "cozy rain")),
        MoodChip("late_night", "Late Night", listOf("late night", "latenight", "after hours", "midnight")),
        MoodChip("feel_good", "Feel Good", listOf("feel good", "feel-good", "uplifting", "happy", "good vibes")),
        MoodChip("throwback_party", "Throwback Party", listOf("throwback", "throwback party", "nostalgia", "old school party")),
        MoodChip("study", "Study", listOf("study", "homework", "reading", "library")),
        MoodChip("sleep", "Sleep", listOf("sleep", "bedtime", "wind down", "wind-down", "dream")),
        MoodChip("cooking", "Cooking", listOf("cooking", "kitchen", "dinner prep", "meal prep"))
    )

    fun find(id: String): JukeboxStation? = stationFromMood(id)

    fun stationFromMood(moodId: String): JukeboxStation? {
        val mood = moods.find { it.id == moodId } ?: return null
        val spec = moodSpecs[moodId] ?: MoodSpec(
            description = "A curated mix for ${mood.label.lowercase()} moments.",
            genreKeywords = listOf(mood.label.lowercase()),
            emoji = "🎵"
        )
        return JukeboxStation(
            id = "mood_$moodId",
            name = "${mood.label} Radio",
            description = spec.description,
            genreKeywords = spec.genreKeywords + mood.aliases,
            decadeStart = spec.decadeStart,
            decadeEnd = spec.decadeEnd,
            emoji = spec.emoji,
            stationType = JukeboxStationType.MOOD
        )
    }

    fun allStations(): List<JukeboxStation> = moods.mapNotNull { stationFromMood(it.id) }

    private data class MoodSpec(
        val description: String,
        val genreKeywords: List<String>,
        val emoji: String = "🎵",
        val decadeStart: Int? = null,
        val decadeEnd: Int? = null
    )

    private val moodSpecs = mapOf(
        "workout" to MoodSpec(
            description = "High-energy tracks to push through your reps and keep your heart rate up.",
            genreKeywords = listOf("workout", "energy", "edm", "hip-hop", "hip hop", "pop", "dance", "electronic"),
            emoji = "💪"
        ),
        "chill" to MoodSpec(
            description = "Easy grooves and mellow textures for unwinding without switching off completely.",
            genreKeywords = listOf("chill", "lofi", "lo-fi", "indie", "r&b", "ambient", "soft rock"),
            emoji = "🌿"
        ),
        "party" to MoodSpec(
            description = "Floor-fillers and sing-along hits built for a room full of friends.",
            genreKeywords = listOf("party", "dance", "pop", "hip-hop", "funk", "disco", "edm"),
            emoji = "🎉"
        ),
        "focus" to MoodSpec(
            description = "Steady, low-distraction sounds to help you lock in and get things done.",
            genreKeywords = listOf("focus", "instrumental", "ambient", "classical", "electronic", "post-rock"),
            emoji = "🎯"
        ),
        "road_trip" to MoodSpec(
            description = "Sing-along anthems and open-road grooves for miles of highway ahead.",
            genreKeywords = listOf("rock", "classic rock", "country", "pop", "indie", "road trip"),
            emoji = "🛣️"
        ),
        "rainy_day" to MoodSpec(
            description = "Soft, reflective songs that pair perfectly with grey skies and a warm drink.",
            genreKeywords = listOf("indie", "folk", "singer-songwriter", "jazz", "ambient", "acoustic"),
            emoji = "🌧️"
        ),
        "late_night" to MoodSpec(
            description = "After-hours mood music — slow burns, neon glow, and unhurried grooves.",
            genreKeywords = listOf("r&b", "soul", "quiet storm", "electronic", "trip-hop", "slow"),
            emoji = "🌙"
        ),
        "feel_good" to MoodSpec(
            description = "Bright, uplifting tracks that turn an ordinary day into something better.",
            genreKeywords = listOf("pop", "funk", "soul", "disco", "feel good", "happy"),
            emoji = "☀️"
        ),
        "throwback_party" to MoodSpec(
            description = "Nostalgic hits from across the decades — the ones everyone still knows every word to.",
            genreKeywords = listOf("oldies", "80s", "90s", "classic rock", "pop", "disco", "funk"),
            decadeStart = 1960,
            decadeEnd = 1999,
            emoji = "📼"
        ),
        "study" to MoodSpec(
            description = "Calm, lyric-light instrumentals and gentle beats that stay out of your way.",
            genreKeywords = listOf("lofi", "lo-fi", "instrumental", "classical", "ambient", "jazz"),
            emoji = "📚"
        ),
        "sleep" to MoodSpec(
            description = "Soft, unhurried soundscapes to help you drift off and stay there.",
            genreKeywords = listOf("ambient", "sleep", "classical", "instrumental", "new age", "soft"),
            emoji = "😴"
        ),
        "cooking" to MoodSpec(
            description = "Upbeat kitchen companions — soul, funk, and feel-good pop while you chop and stir.",
            genreKeywords = listOf("soul", "funk", "jazz", "pop", "latin", "motown"),
            emoji = "🍳"
        )
    )
}
