package com.audiophile.musicplayer

import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Opt-in diagnostic: compare range delivery on the actual playback network. */
class GoogleVideoTransportProbeTest {
    @Test
    fun compareProviderClients() {
        val videoId = InstrumentationRegistry.getArguments().getString("videoId")
        assumeTrue("Requires an explicit video ID", !videoId.isNullOrBlank())
        val provider = com.audiophile.musicplayer.data.source.YouTubeMusicSourceProvider()
        val clientsField = provider.javaClass.getDeclaredField("innerTubeClients").apply { isAccessible = true }
        val clients = clientsField.get(provider) as List<*>
        val requestPlayer = provider.javaClass.declaredMethods.first { it.name == "requestInnerTubePlayer" }.apply { isAccessible = true }
        val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        for (client in clients.filterNotNull()) {
            fun field(name: String) = client.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(client) as String
            val label = field("label")
            val stream = requestPlayer.invoke(provider, videoId, client, field("apiKey"), null)
                as? com.audiophile.musicplayer.data.source.ResolvedStream
            if (stream == null) {
                Log.i("VANTA_RANGE_PROBE", "client=$label resolved=false")
                continue
            }
            for (start in listOf(0L, 1_048_576L, 2_097_152L)) {
                val request = Request.Builder().url(stream.streamUrl)
                    .header("Range", "bytes=$start-${start + 65_535}")
                    .apply { stream.requestHeaders.forEach { (key, value) -> header(key, value) } }.build()
                http.newCall(request).execute().use { response ->
                    Log.i("VANTA_RANGE_PROBE", "client=$label start=$start status=${response.code} length=${response.body?.contentLength()}")
                }
            }
        }
    }

    @Test
    fun compareRangeDelivery() {
        val encoded = InstrumentationRegistry.getArguments().getString("streamUrlBase64")
        assumeTrue("Requires an explicit temporary stream URL", !encoded.isNullOrBlank())
        val url = String(Base64.decode(encoded, Base64.DEFAULT)).toHttpUrl()
        require(url.host == "googlevideo.com" || url.host.endsWith(".googlevideo.com"))
        val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        for (queryRange in listOf(false, true)) {
            for (start in listOf(0L, 1_048_576L, 2_097_152L)) {
                val range = "$start-${start + 65_535}"
                val target = if (queryRange) url.newBuilder().setQueryParameter("range", range).build() else url
                val request = Request.Builder().url(target)
                    .apply { if (!queryRange) header("Range", "bytes=$range") }
                    .header("User-Agent", "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)")
                    .build()
                client.newCall(request).execute().use { response ->
                    Log.i("VANTA_RANGE_PROBE", "queryRange=$queryRange start=$start status=${response.code} contentRange=${response.header("Content-Range")} length=${response.body?.contentLength()}")
                }
            }
        }
    }
}
