package com.audiophile.musicplayer.common

import android.util.Log

/**
 * Thin, tag-enforced logging abstraction.
 *
 * Rules:
 * - All tags must be uppercase snake_case and ≤ 23 chars (Logcat limit).
 * - Use structured key=value pairs in messages for grep-ability.
 * - Debug logs are no-ops in release builds.
 *
 * Example:
 *   VantaLogger.d(Tag.SEARCH, "query='$q' results=${list.size}")
 *   VantaLogger.e(Tag.PLAYBACK, "stream_failed url='$url'", cause)
 */
object VantaLogger {

    /** Canonical log tags — add new ones here rather than in call sites. */
    enum class Tag(val value: String) {
        APP("VANTA_APP"),
        SEARCH("VANTA_SEARCH"),
        PLAYBACK("VANTA_PLAYBACK"),
        STREAM("VANTA_STREAM"),
        QUEUE("VANTA_QUEUE"),
        LIBRARY("VANTA_LIBRARY"),
        STATION("VANTA_STATION"),
        SOURCE("VANTA_SOURCE"),
        SETTINGS("VANTA_SETTINGS"),
        IMPORT("VANTA_IMPORT"),
        AI_DJ("VANTA_AI_DJ"),
        LYRICS("VANTA_LYRICS"),
        CAST("VANTA_CAST"),
        NETWORK("VANTA_NETWORK"),
        DI("VANTA_DI"),
        STARTUP("VANTA_STARTUP"),
    }

    fun v(tag: Tag, msg: String) = Log.v(tag.value, msg)

    fun d(tag: Tag, msg: String) = Log.d(tag.value, msg)

    fun i(tag: Tag, msg: String) = Log.i(tag.value, msg)

    fun w(tag: Tag, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(tag.value, msg, tr) else Log.w(tag.value, msg)
    }

    fun e(tag: Tag, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag.value, msg, tr) else Log.e(tag.value, msg)
    }
}
