package com.audiophile.musicplayer.data.source

import com.google.gson.JsonObject
import java.net.URI

internal object GatewayStreamResponseParser {
    fun locationOrBody(code: Int, location: String?, body: String?): String? {
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            val loc = location?.trim().orEmpty()
            if (loc.startsWith("http://", ignoreCase = true) || loc.startsWith("https://", ignoreCase = true)) {
                return loc
            }
        }
        if (code in 200..299) return body?.trim()?.takeIf { it.isNotEmpty() }
        return null
    }

    fun drmConfiguration(payload: JsonObject): StreamDrmConfiguration? {
        val drm = payload.get("drm")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null
        val scheme = drm.stringValue("scheme")?.lowercase() ?: return null
        if (scheme != "widevine") return null
        val licenseUrl = drm.stringValue("licenseUrl")?.takeIf(::isSafeHttpsUrl) ?: return null
        val requestHeaders = drm.get("licenseRequestHeaders")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.entrySet()
            ?.asSequence()
            ?.take(MAX_DRM_HEADERS)
            ?.mapNotNull { (name, value) ->
                if (!SAFE_HEADER_NAME.matches(name) || !value.isJsonPrimitive) return@mapNotNull null
                val headerValue = runCatching { value.asString }.getOrNull()?.trim().orEmpty()
                if (headerValue.isEmpty() || headerValue.length > MAX_HEADER_VALUE_LENGTH ||
                    '\r' in headerValue || '\n' in headerValue
                ) {
                    return@mapNotNull null
                }
                name to headerValue
            }
            ?.toMap()
            .orEmpty()
        val forceDefault = drm.get("forceDefaultLicenseUri")
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asBoolean }.getOrNull() }
            ?: true
        return StreamDrmConfiguration(
            scheme = scheme,
            licenseUrl = licenseUrl,
            licenseRequestHeaders = requestHeaders,
            forceDefaultLicenseUri = forceDefault,
        )
    }

    /**
     * Amazon CloudFront / MPEG-H CENC without a license or decrypt proxy will
     * fail in ExoPlayer as "Source unavailable". Skip so stereo FLAC can play.
     */
    fun streamUrlLooksUnplayableWithoutDrm(
        url: String,
        format: String?,
        drmPresent: Boolean
    ): Boolean {
        if (drmPresent) return false
        val lowerUrl = url.lowercase()
        if ("/api/decrypt" in lowerUrl || "/audio/" in lowerUrl) return false
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.contains("workers.dev") || host.contains("vanta-music-gateway")) return false
        val blob = "${format.orEmpty()} $lowerUrl".lowercase()
        // MPEG-H (mha1/mhm1) is intrinsically CENC-encrypted: without a license or
        // our decrypt proxy it can never decode, on any CDN host. Gate on codec, not host.
        if ("mha1" in blob || "mhm1" in blob || "mpeg-h" in blob || "mpegh" in blob) {
            return true
        }
        // Encrypted Amazon MPEG-H/FLAC on CloudFront needs a license or our decrypt
        // proxy. Worker-proxied `/audio/` URLs are already allowed above.
        return host.contains("cloudfront") ||
            host.contains("amazonaavn") ||
            host.contains("aiv-cdn.net")
    }

    private fun JsonObject.stringValue(key: String): String? =
        get(key)
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asString }.getOrNull() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun isSafeHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)

    private val SAFE_HEADER_NAME = Regex("^[A-Za-z0-9-]{1,64}$")
    private const val MAX_DRM_HEADERS = 16
    private const val MAX_HEADER_VALUE_LENGTH = 2048
}
