package com.audiophile.musicplayer.auto

import com.audiophile.musicplayer.data.lyrics.LyricsLine
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Small bounded cache used by Android Auto callbacks, which may arrive out of order. */
class AutoLruCache<K, V>(private val maxEntries: Int) {
    init {
        require(maxEntries > 0)
    }

    private val values = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxEntries
    }

    @Synchronized fun put(key: K, value: V) {
        values[key] = value
    }

    @Synchronized fun get(key: K): V? = values[key]

    @Synchronized fun putAll(entries: Map<K, V>) {
        values.putAll(entries)
    }

    @Synchronized fun clear() = values.clear()

    @Synchronized fun size(): Int = values.size
}

internal object AutoMediaIdCodec {
    data class RemoteTrack(val providerId: String, val externalTrackId: String)

    fun remoteTrackId(prefix: String, providerId: String, externalTrackId: String): String =
        "$prefix${encode(providerId)}|${encode(externalTrackId)}"

    fun parseRemoteTrackId(prefix: String, mediaId: String): RemoteTrack? {
        if (!mediaId.startsWith(prefix)) return null
        val parts = mediaId.removePrefix(prefix).split('|', limit = 2)
        if (parts.size != 2) return null
        val provider = decode(parts[0]).trim()
        val track = decode(parts[1]).trim()
        return if (provider.isBlank() || track.isBlank()) null else RemoteTrack(provider, track)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.toString()) }.getOrDefault("")
}

internal object KaraokeTimeline {
    fun lineAt(lines: List<LyricsLine>, positionMs: Long): String? =
        lines.lastOrNull { line ->
            val start = line.startTimeMs ?: return@lastOrNull false
            val end = line.endTimeMs
            start <= positionMs && (end == null || positionMs < end)
        }?.text?.trim()?.takeIf { it.isNotBlank() }
            ?: lines.lastOrNull { (it.startTimeMs ?: Long.MAX_VALUE) <= positionMs }
                ?.text?.trim()?.takeIf { it.isNotBlank() }
}
