package com.audiophile.musicplayer.data.dj

enum class AiDjMode(
    val displayName: String,
    val description: String,
    val emoji: String
) {
    DAILY_DJ("Daily DJ", "A fresh mix based on your current taste", "\uD83C\uDFA7"),
    LATE_NIGHT("Late Night", "Dark, atmospheric, and chill", "\uD83C\uDF19"),
    WORKOUT("Workout", "High energy to keep you moving", "\uD83C\uDFC3"),
    DISCOVER_NEW("Discover New", "Explore beyond your usual rotation", "\uD83D\uDD0D"),
    THROWBACKS("Throwbacks", "Your favorites from the past", "\uD83D\uDDC4\uFE0F"),
    SIMILAR_TO_SONG("Similar to This Song", "Find tracks like the current one", "\uD83C\uDFB5"),
    SIMILAR_TO_ARTIST("Similar to This Artist", "Explore similar artists", "\uD83C\uDFA4"),
    DEEP_CUTS("Deep Cuts", "Dive deeper into your library", "\uD83C\uDFB6"),
    OUTSIDE_COMFORT_ZONE("Outside Your Comfort Zone", "Try something different", "\uD83C\uDF0D"),
    VANTA_RADIO("VANTA Radio", "Endless personalized radio", "\uD83D\uDCFB"),
    CHILL_VIBES("Chill Vibes", "Low-energy, smooth, and relaxed", "\uD83E\uDDCA")
}
