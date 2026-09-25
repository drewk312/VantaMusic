package com.audiophile.musicplayer.data.source

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints
import com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Resolves a **picture-only** music-video companion for TV.
 *
 * Audio stays on the already-resolved FLAC / Dolby Atmos stream. Qobuz has no
 * native music-video API (embeds YouTube/Vimeo only); Tidal has first-party MVs.
 * YouTube is last-resort video picture — never the listening path.
 */
class MusicVideoCompanionResolver(
    private val sourceRegistry: SourceRegistry,
    private val gatewayBaseUrl: String = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(GatewayApiKeyInterceptor)
        .build()
) {
    suspend fun resolve(track: UnifiedTrackWithSources): ResolvedStream? = withContext(Dispatchers.IO) {
        val title = track.track.title.orEmpty().trim()
        val artist = track.track.artist.orEmpty().trim()
        if (title.isBlank()) return@withContext null

        resolveTidalGateway(title, artist)?.let { return@withContext it }

        val youtubeId = track.sources
            .firstOrNull {
                it.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.YOUTUBE_MUSIC ||
                    it.externalProviderId.equals("youtube_music", ignoreCase = true)
            }
            ?.externalTrackId
            ?.takeIf { it.isNotBlank() }
            ?: findYouTubeId(title, artist)

        if (!youtubeId.isNullOrBlank()) {
            sourceRegistry.resolveVideoStream("youtube_music", youtubeId)?.let { return@withContext it }
        }

        Log.w("VANTA_TV_VIDEO", "no companion video for '$artist - $title'")
        null
    }

    private fun resolveTidalGateway(title: String, artist: String): ResolvedStream? {
        val base = gatewayBaseUrl.trimEnd('/')
        val q = URLEncoder.encode("$artist $title", Charsets.UTF_8.name())
        val url = "$base/video?q=$q&provider=tidal"
        return runCatching {
            httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d("VANTA_TV_VIDEO", "tidal gateway video HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                val root = JsonParser.parseString(body)?.asJsonObject ?: return null
                val streamUrl = root.get("streamUrl")?.asString
                    ?: root.get("url")?.asString
                    ?: return null
                if (streamUrl.isBlank()) return null
                ResolvedStream(
                    streamUrl = streamUrl,
                    bitrateKbps = root.get("bitrateKbps")?.asInt ?: 0,
                    mimeType = root.get("mimeType")?.asString ?: "application/x-mpegURL",
                    qualityLabel = root.get("quality")?.asString ?: "Tidal Music Video",
                    format = "video",
                    providerId = "tidal",
                    sourceLabel = "Tidal Music Video",
                    container = "hls"
                )
            }
        }.onFailure { err ->
            Log.d("VANTA_TV_VIDEO", "tidal gateway video error: ${err.message}")
        }.getOrNull()
    }

    private suspend fun findYouTubeId(title: String, artist: String): String? {
        val hits = runCatching {
            sourceRegistry.searchSingle("youtube_music", "$artist $title official music video")
        }.getOrNull().orEmpty()
        return hits.firstOrNull { candidate ->
            val hay = "${candidate.title} ${candidate.artist}".lowercase()
            title.lowercase().split(' ').filter { it.length > 2 }.any { it in hay } &&
                !hay.contains("lyrics") &&
                !hay.contains("audio only")
        }?.id
    }
}
