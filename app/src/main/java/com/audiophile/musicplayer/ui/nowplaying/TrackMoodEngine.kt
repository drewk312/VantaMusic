package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.ui.graphics.Color

enum class TrackMood(val label: String) {
    CALM("calm"),
    ENERGETIC("energetic"),
    DARK("dark"),
    NOSTALGIC("nostalgic"),
    ROMANTIC("romantic"),
    AGGRESSIVE("aggressive"),
    CELEBRATORY("celebratory"),
    UNKNOWN("unknown")
}

data class MoodInference(
    val mood: TrackMood,
    val confidence: Float,
    val bassWeight: Float = 0.5f,
    val energy: Float = 0.5f,
    val valence: Float = 0.5f
)

data class MoodOrbPalette(
    val primary: Color,
    val glow: Color,
    val core: Color,
    val accent: Color
)

object TrackMoodEngine {

    private val energeticKeywords = setOf(
        "fire", "burn", "party", "dance", "workout", "energy", "power", "rage",
        "boom", "bang", "fight", "war", "running", "fast", "speed", "ignite",
        "electric", "thunder", "wild", "crazy", "riot", "revolution"
    )
    private val darkKeywords = setOf(
        "dark", "shadow", "night", "black", "demon", "devil", "hell", "evil",
        "ghost", "pain", "suffering", "alone", "void", "abyss", "funeral",
        "death", "bleed", "break", "fall", "lost", "gone"
    )
    private val nostalgicKeywords = setOf(
        "remember", "memory", "old", "golden", "yesterday", "childhood", "summer",
        "back then", "those days", "miss", "wish", "time", "years", "ago",
        "retro", "classic", "vintage", "nostalgia"
    )
    private val romanticKeywords = setOf(
        "love", "kiss", "hug", "heart", "baby", "sweet", "forever", "together",
        "hold", "touch", "passion", "desire", "adore", "cherish", "romance",
        "moon", "stars", "dream", "wonder", "beautiful"
    )
    private val aggressiveKeywords = setOf(
        "kill", "hate", "angry", "rage", "destroy", "death", "blood", "war",
        "violence", "revenge", "scream", "break", "burn", "enemy",
        "attack", "savage", "brutal"
    )
    private val celebratoryKeywords = setOf(
        "celebrate", "victory", "win", "champion", "glory", "party", "joy",
        "happy", "fun", "dance", "freedom", "amazing", "bless", "hallelujah",
        "raise", "toast", "cheers", "summer"
    )
    private val calmKeywords = setOf(
        "calm", "peace", "slow", "gentle", "soft", "quiet", "silence", "ocean",
        "rain", "river", "flow", "breathe", "serene", "tranquil", "meditate",
        "lullaby", "sleep", "dream", "ambient", "chill"
    )

    fun infer(
        title: String?,
        artist: String?,
        album: String?,
        genre: String?,
        energy: Float,
        bassEnergy: Float
    ): MoodInference {
        val titleLower = title?.lowercase().orEmpty()
        val artistLower = artist?.lowercase().orEmpty()
        val albumLower = album?.lowercase().orEmpty()
        val genreLower = genre?.lowercase().orEmpty()

        val text = listOf(titleLower, albumLower, "$titleLower $artistLower").joinToString(" ")

        val genreSignals = inferFromGenre(genreLower)
        val keywordSignals = inferFromKeywords(text)
        val energySignals = inferFromEnergy(energy, bassEnergy)

        val combined = mutableMapOf<TrackMood, Float>()

        genreSignals.forEach { (mood, weight) ->
            combined[mood] = (combined[mood] ?: 0f) + weight * 0.35f
        }
        keywordSignals.forEach { (mood, weight) ->
            combined[mood] = (combined[mood] ?: 0f) + weight * 0.40f
        }
        energySignals.forEach { (mood, weight) ->
            combined[mood] = (combined[mood] ?: 0f) + weight * 0.25f
        }

        val best = combined.maxByOrNull { it.value }
        val mood = best?.key ?: TrackMood.UNKNOWN
        val confidence = (best?.value ?: 0f).coerceIn(0f, 1f)

        return MoodInference(
            mood = mood,
            confidence = confidence,
            bassWeight = bassEnergy.coerceIn(0f, 1f),
            energy = energy.coerceIn(0f, 1f),
            valence = valenceFromMood(mood, energy)
        )
    }

    private fun inferFromGenre(genre: String): Map<TrackMood, Float> {
        val signals = mutableMapOf<TrackMood, Float>()
        when {
            genre.contains("metal") || genre.contains("hardcore") || genre.contains("punk") -> {
                signals[TrackMood.AGGRESSIVE] = 0.7f; signals[TrackMood.ENERGETIC] = 0.5f
            }
            genre.contains("rap") || genre.contains("hip") || genre.contains("trap") || genre.contains("drill") -> {
                signals[TrackMood.ENERGETIC] = 0.6f; signals[TrackMood.CELEBRATORY] = 0.3f
            }
            genre.contains("ambient") || genre.contains("chill") || genre.contains("downtempo") || genre.contains("lofi") -> {
                signals[TrackMood.CALM] = 0.7f
            }
            genre.contains("classical") || genre.contains("orchestra") || genre.contains("piano") -> {
                signals[TrackMood.CALM] = 0.4f; signals[TrackMood.DARK] = 0.3f
            }
            genre.contains("jazz") || genre.contains("blues") || genre.contains("soul") -> {
                signals[TrackMood.NOSTALGIC] = 0.5f; signals[TrackMood.ROMANTIC] = 0.3f
            }
            genre.contains("r&b") || genre.contains("rnb") -> {
                signals[TrackMood.ROMANTIC] = 0.5f; signals[TrackMood.NOSTALGIC] = 0.3f
            }
            genre.contains("country") || genre.contains("folk") -> {
                signals[TrackMood.NOSTALGIC] = 0.4f; signals[TrackMood.CALM] = 0.3f
            }
            genre.contains("edm") || genre.contains("electronic") || genre.contains("house") || genre.contains("techno") -> {
                signals[TrackMood.ENERGETIC] = 0.6f; signals[TrackMood.CELEBRATORY] = 0.4f
            }
            genre.contains("pop") -> {
                signals[TrackMood.CELEBRATORY] = 0.3f; signals[TrackMood.ENERGETIC] = 0.3f
            }
            genre.contains("reggae") || genre.contains("ska") -> {
                signals[TrackMood.CELEBRATORY] = 0.4f; signals[TrackMood.CALM] = 0.3f
            }
        }
        return signals
    }

    private fun inferFromKeywords(text: String): Map<TrackMood, Float> {
        val signals = mutableMapOf<TrackMood, Float>()
        val textLower = text.lowercase()

        fun countMatches(keywords: Set<String>): Int =
            keywords.count { textLower.contains(it) }

        val energetic = countMatches(energeticKeywords)
        val dark = countMatches(darkKeywords)
        val nostalgic = countMatches(nostalgicKeywords)
        val romantic = countMatches(romanticKeywords)
        val aggressive = countMatches(aggressiveKeywords)
        val celebratory = countMatches(celebratoryKeywords)
        val calm = countMatches(calmKeywords)

        val total = energetic + dark + nostalgic + romantic + aggressive + celebratory + calm
        if (total == 0) return signals

        fun score(count: Int): Float = (count.toFloat() / total.toFloat()).coerceIn(0f, 0.8f)
        if (energetic > 0) signals[TrackMood.ENERGETIC] = score(energetic)
        if (dark > 0) signals[TrackMood.DARK] = score(dark)
        if (nostalgic > 0) signals[TrackMood.NOSTALGIC] = score(nostalgic)
        if (romantic > 0) signals[TrackMood.ROMANTIC] = score(romantic)
        if (aggressive > 0) signals[TrackMood.AGGRESSIVE] = score(aggressive)
        if (celebratory > 0) signals[TrackMood.CELEBRATORY] = score(celebratory)
        if (calm > 0) signals[TrackMood.CALM] = score(calm)

        return signals
    }

    private fun inferFromEnergy(energy: Float, bass: Float): Map<TrackMood, Float> {
        val signals = mutableMapOf<TrackMood, Float>()
        val avg = (energy + bass) / 2f
        when {
            avg < 0.25f -> signals[TrackMood.CALM] = 0.5f
            avg in 0.25f..0.45f -> signals[TrackMood.NOSTALGIC] = 0.3f
            avg in 0.45f..0.65f -> signals[TrackMood.ENERGETIC] = 0.3f
            avg > 0.65f -> {
                signals[TrackMood.ENERGETIC] = 0.4f
                if (bass > 0.7f) signals[TrackMood.AGGRESSIVE] = 0.3f
                else signals[TrackMood.CELEBRATORY] = 0.3f
            }
        }
        return signals
    }

    private fun valenceFromMood(mood: TrackMood, energy: Float): Float = when (mood) {
        TrackMood.CALM -> 0.5f
        TrackMood.ENERGETIC -> 0.7f
        TrackMood.DARK -> 0.2f
        TrackMood.NOSTALGIC -> 0.4f
        TrackMood.ROMANTIC -> 0.7f
        TrackMood.AGGRESSIVE -> 0.2f
        TrackMood.CELEBRATORY -> 0.9f
        TrackMood.UNKNOWN -> 0.5f + (energy - 0.5f) * 0.3f
    }

    fun moodToOrbPalette(mood: TrackMood): MoodOrbPalette = when (mood) {
        TrackMood.CALM -> MoodOrbPalette(
            Color(0xFF89CFF0), Color(0xFFB0E0E6), Color(0xFFFFFFFF), Color(0xFFADD8E6)
        )
        TrackMood.ENERGETIC -> MoodOrbPalette(
            Color(0xFFFF8C00), Color(0xFFFFA500), Color(0xFFFFD700), Color(0xFFFF6347)
        )
        TrackMood.DARK -> MoodOrbPalette(
            Color(0xFF4A0E4E), Color(0xFF6B2FA0), Color(0xFF8B5CF6), Color(0xFF2D1B69)
        )
        TrackMood.NOSTALGIC -> MoodOrbPalette(
            Color(0xFFD4A017), Color(0xFFF5D76E), Color(0xFFFFE8C8), Color(0xFFC4843E)
        )
        TrackMood.ROMANTIC -> MoodOrbPalette(
            Color(0xFFFFB6C1), Color(0xFFFF69B4), Color(0xFFFFC0CB), Color(0xFFDB7093)
        )
        TrackMood.AGGRESSIVE -> MoodOrbPalette(
            Color(0xFFFF4500), Color(0xFFFF0000), Color(0xFFFF6347), Color(0xFF8B0000)
        )
        TrackMood.CELEBRATORY -> MoodOrbPalette(
            Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFFF8DC), Color(0xFFFF4500)
        )
        TrackMood.UNKNOWN -> MoodOrbPalette(
            Color(0xFFE8B87A), Color(0xFFDDB892), Color(0xFFFFE8C8), Color(0xFFC4843E)
        )
    }

    fun seasonalOverride(baseMood: TrackMood, seasonId: String?): TrackMood {
        if (seasonId == "july4") return TrackMood.CELEBRATORY
        return baseMood
    }
}
