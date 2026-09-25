package com.audiophile.musicplayer.data.source

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object RemoteBitrateMeasurer {
    fun measureAverageKbps(
        streamUrl: String,
        durationMs: Long,
        requestHeaders: Map<String, String> = emptyMap()
    ): Int? {
        if (durationMs <= 0L || durationMs > 20 * 60 * 1000L) return null
        if (!streamUrl.startsWith("http://", ignoreCase = true) &&
            !streamUrl.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        val path = streamUrl.substringBefore('?').lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-0")
                setRequestProperty("Accept-Encoding", "identity")
                requestHeaders.forEach { (key, value) ->
                    if (key.isNotBlank() && value.isNotBlank()) {
                        setRequestProperty(key, value)
                    }
                }
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
            }
            connection.connect()
            val totalBytes = connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/')?.toLongOrNull()
                ?: connection.contentLengthLong.takeIf { it > 1L }
                ?: return null
            val kbps = ((totalBytes * 8.0) / (durationMs / 1000.0) / 1000.0).toInt()
                .takeIf { it in 16..10_000 }
            Log.d("VANTA_BITRATE_MEASURE", "bytes=$totalBytes durationMs=$durationMs averageKbps=${kbps ?: "invalid"}")
            kbps
        } catch (e: java.io.IOException) {
            Log.d("VANTA_BITRATE_MEASURE", "measurement_failed reason='${e.message}'")
            null
        } catch (e: IllegalArgumentException) {
            Log.d("VANTA_BITRATE_MEASURE", "measurement_failed reason='${e.message}'")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
