package com.audiophile.musicplayer.data.lyrics

data class LyricsWordTiming(
    val startTimeMs: Long,
    val text: String
)

data class LyricsLine(
    val startTimeMs: Long?,
    val endTimeMs: Long? = null,
    val text: String,
    val translatedText: String? = null,
    val wordTimings: List<LyricsWordTiming> = emptyList()
)

data class LyricsData(
    val trackKey: String,
    val isSynced: Boolean,
    val lines: List<LyricsLine>,
    val providerId: String,
    val sourceLabel: String? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
