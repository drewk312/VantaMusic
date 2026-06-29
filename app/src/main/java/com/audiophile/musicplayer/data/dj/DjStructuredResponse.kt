package com.audiophile.musicplayer.data.dj

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

enum class DjActionType {
    @SerializedName("PLAY_TRACK") PLAY_TRACK,
    @SerializedName("START_RADIO") START_RADIO,
    @SerializedName("ADJUST_QUEUE") ADJUST_QUEUE,
    @SerializedName("EXPLAIN_CURRENT_SONG") EXPLAIN_CURRENT_SONG,
    @SerializedName("FIND_SIMILAR") FIND_SIMILAR,
    @SerializedName("SKIP") SKIP,
    @SerializedName("PAUSE") PAUSE,
    @SerializedName("RESUME") RESUME,
    @SerializedName("SAVE_TO_LIBRARY") SAVE_TO_LIBRARY,
    @SerializedName("ADD_TO_PLAYLIST") ADD_TO_PLAYLIST,
    @SerializedName("CHANGE_MOOD") CHANGE_MOOD,
    @SerializedName("REDUCE_TALKING") REDUCE_TALKING,
    @SerializedName("INCREASE_TALKING") INCREASE_TALKING,
    @SerializedName("NO_ACTION") NO_ACTION,
    UNKNOWN
}

data class DjStructuredAction(
    val type: DjActionType = DjActionType.NO_ACTION,
    val direction: String? = null,
    val artist: String? = null,
    val title: String? = null
)

data class DjDisplayHint(
    @SerializedName("cardType") val cardType: String? = "dj_moment",
    val accent: String? = null,
    @SerializedName("durationMs") val durationMs: Long? = 6000L
)

data class DjStructuredResponse(
    val message: String = "",
    val mood: String? = null,
    val energy: Float? = null,
    val actions: List<DjStructuredAction> = emptyList(),
    val display: DjDisplayHint? = null
) {
    val primaryAction: DjStructuredAction?
        get() = actions.firstOrNull { it.type != DjActionType.NO_ACTION && it.type != DjActionType.UNKNOWN }
}

object DjStructuredResponseParser {
    private val gson = Gson()

    fun parse(raw: String?): DjStructuredResponse? {
        if (raw.isNullOrBlank()) return null
        val json = extractJsonObject(raw) ?: return null
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val actions = root.getAsJsonArray("actions")?.mapNotNull { element ->
                runCatching {
                    val obj = element.asJsonObject
                    val typeName = obj.get("type")?.asString?.uppercase()?.replace(" ", "_")
                    val type = runCatching {
                        DjActionType.valueOf(typeName ?: "NO_ACTION")
                    }.getOrDefault(DjActionType.UNKNOWN)
                    DjStructuredAction(
                        type = type,
                        direction = obj.get("direction")?.asString,
                        artist = obj.get("artist")?.asString,
                        title = obj.get("title")?.asString
                    )
                }.getOrNull()
            }.orEmpty()
            DjStructuredResponse(
                message = root.get("message")?.asString.orEmpty(),
                mood = root.get("mood")?.asString,
                energy = root.get("energy")?.asFloat,
                actions = actions,
                display = root.getAsJsonObject("display")?.let {
                    gson.fromJson(it, DjDisplayHint::class.java)
                }
            )
        }.getOrNull()
    }

    private fun extractJsonObject(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return null
    }
}
