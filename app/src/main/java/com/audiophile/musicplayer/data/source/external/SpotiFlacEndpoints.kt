package com.audiophile.musicplayer.data.source.external

import java.net.URLEncoder

object SpotiFlacEndpoints {
    /**
     * Your Cloudflare Worker gateway (workers/music-gateway).
     * Deploy with: cd workers/music-gateway && npm install && npx wrangler deploy
     * Then paste the workers.dev URL here and in Settings → External Sources.
     */
    const val DEFAULT_GATEWAY_BASE_URL = "https://vanta-music-gateway.16drewk.workers.dev/"

    /** @deprecated use [DEFAULT_GATEWAY_BASE_URL] */
    const val DEFAULT_ADDON_BASE_URL: String = DEFAULT_GATEWAY_BASE_URL

    /** Public zero-config Qobuz stream relay (no API key). */
    const val QOBUZ_WJHE_STREAM_API_URL = ""

    val GDSTUDIO_API_URLS = listOf(
        "https://music.gdstudio.xyz/api.php",
        "https://music.gdstudio.org/api.php",
        "https://music-api.gdstudio.org/api.php"
    )

    /**
     * Preferred stream quality: 24 = hi-res FLAC, 16 = CD FLAC, atmos = request Atmos media.
     * Mutable so the Settings screen can change it at runtime; loaded from prefs at startup.
     */
    @Volatile
    var PREFERRED_STREAM_QUALITY: String = "auto"

    fun prefersSpatialMix(quality: String = PREFERRED_STREAM_QUALITY): Boolean {
        val q = quality.trim().lowercase()
        return q == "auto" || "atmos" in q || "360" in q || "spatial" in q || "iamf" in q
    }

    const val COMMUNITY_DOWNLOAD_PATH = "/api/dl"

    fun gatewayStreamEndpoint(baseUrl: String = DEFAULT_GATEWAY_BASE_URL): String =
        baseUrl.trim().trimEnd('/') + COMMUNITY_DOWNLOAD_PATH

    val AMAZON_COMMUNITY_API_URL: String = gatewayStreamEndpoint()
    val PANDORA_COMMUNITY_API_URL: String = gatewayStreamEndpoint()

    fun mapTidalQualityToCommunity(quality: String?): String =
        when (quality?.trim()?.uppercase()?.replace("-", "_")) {
            "ATMOS", "DOLBY_ATMOS", "DOLBY", "EAC3", "EAC3_JOC" -> "atmos"
            "360", "360RA", "SONY360", "SONY_360", "360_REALITY_AUDIO" -> "360"
            "AUTO" -> "auto"
            "HI_RES_LOSSLESS", "HI_RES", "24" -> "24"
            else -> "16"
        }

    fun mapQobuzQualityToCommunity(quality: String?): String =
        when (quality?.trim()?.uppercase()?.replace("-", "_")) {
            "27", "7", "24", "HI_RES", "HI_RES_LOSSLESS" -> "24"
            else -> "16"
        }

    fun mapQobuzWjheQuality(quality: String?): Pair<Int, String> =
        when (quality?.trim()) {
            "27", "7" -> 2000 to "flac"
            "", "6" -> 1000 to "flac"
            else -> 320 to "mp3"
        }

    fun buildQobuzWjheStreamUrl(trackId: String, quality: String? = "27"): String = ""

    fun buildAddonStreamUrls(baseUrl: String, trackId: String, preferTidal: Boolean = false): List<String> {
        val trimmedBase = baseUrl.trimEnd('/')
        val id = encode(trackId.removePrefix("tidal:").removePrefix("qobuz:"))
        val tidalSuffix = if (preferTidal) "&provider=tidal" else ""
        return listOf(
            "$trimmedBase/stream/$id?quality=${PREFERRED_STREAM_QUALITY}$tidalSuffix"
        )
    }

    fun buildCommunityDownloadPayload(trackId: String, quality: String?, service: String): String {
        val normalizedId = trackId.removePrefix("tidal:").removePrefix("qobuz:").removePrefix("deezer:").removePrefix("amazon:").trim()
        val mappedQuality = when (service.lowercase()) {
            "tidal" -> mapTidalQualityToCommunity(quality)
            "deezer" -> mapTidalQualityToCommunity(quality ?: PREFERRED_STREAM_QUALITY)
            "amazon" -> mapTidalQualityToCommunity(quality)
            else -> mapQobuzQualityToCommunity(quality ?: PREFERRED_STREAM_QUALITY)
        }
        return """{"id":"$normalizedId","quality":"$mappedQuality","service":"$service"}"""
    }

    /** Map prefixed catalog track ids to the gateway /api/dl service name. */
    fun inferStreamService(trackId: String): String = when {
        trackId.startsWith("tidal:", ignoreCase = true) -> "tidal"
        trackId.startsWith("qobuz:", ignoreCase = true) -> "qobuz"
        trackId.startsWith("amazon:", ignoreCase = true) -> "amazon"
        trackId.startsWith("pandora:", ignoreCase = true) -> "pandora"
        else -> "deezer"
    }

    fun buildQobuzOpenTrackUrl(trackId: String): String =
        "https://open.qobuz.com/track/${trackId.removePrefix("qobuz:").trim()}"

    fun buildSongLinkUrl(platformUrl: String): String =
        "https://api.odesli.co/matches?url=${encode(platformUrl)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
