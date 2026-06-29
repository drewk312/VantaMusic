package com.audiophile.musicplayer.data.dj

/** Text-first companion personality — controls talk frequency and queue bias. */
enum class DjCompanionMode(
    val label: String,
    val talkLevel: Float,
    val mappedDjMode: AiDjMode
) {
    SILENT("Silent DJ", 0f, AiDjMode.DAILY_DJ),
    CHILL_FRIEND("Chill Friend", 0.45f, AiDjMode.CHILL_VIBES),
    HYPE("Hype Mode", 0.7f, AiDjMode.WORKOUT),
    LATE_NIGHT("Late Night", 0.5f, AiDjMode.LATE_NIGHT),
    COMFORT("Comfort Mode", 0.4f, AiDjMode.DAILY_DJ),
    DISCOVERY("Discovery", 0.55f, AiDjMode.DISCOVER_NEW),
    FOCUS("Focus Mode", 0.15f, AiDjMode.CHILL_VIBES);

    fun shouldShowMoment(): Boolean = talkLevel > 0.1f
}
