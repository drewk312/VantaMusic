package com.audiophile.musicplayer.data.lyrics

import com.google.gson.annotations.SerializedName

data class LyricsWordTiming(
    @SerializedName("startTimeMs") val startTimeMs: Long,
    @SerializedName("text") val text: String
)

data class LyricsLine(
    @SerializedName("startTimeMs") val startTimeMs: Long?,
    @SerializedName("endTimeMs") val endTimeMs: Long? = null,
    @SerializedName("text") val text: String,
    @SerializedName("translatedText") val translatedText: String? = null,
    @SerializedName("wordTimings") val wordTimings: List<LyricsWordTiming> = emptyList()
)

data class LyricsData(
    @SerializedName("trackKey") val trackKey: String,
    @SerializedName("isSynced") val isSynced: Boolean,
    @SerializedName("lines") val lines: List<LyricsLine>,
    @SerializedName("providerId") val providerId: String,
    @SerializedName("sourceLabel") val sourceLabel: String? = null,
    @SerializedName("lastUpdatedAt") val lastUpdatedAt: Long = System.currentTimeMillis()
)
