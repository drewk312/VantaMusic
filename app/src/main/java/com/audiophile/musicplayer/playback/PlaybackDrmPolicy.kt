package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.source.StreamDrmConfiguration
import java.net.URI

internal object PlaybackDrmPolicy {
    fun validated(configuration: StreamDrmConfiguration?): StreamDrmConfiguration? {
        val drm = configuration ?: return null
        if (!drm.scheme.equals("widevine", ignoreCase = true)) return null
        val licenseUrl = drm.licenseUrl.trim().takeIf(::isSafeHttpsUrl) ?: return null
        val headers = drm.licenseRequestHeaders.entries
            .asSequence()
            .filter { (name, value) ->
                SAFE_HEADER_NAME.matches(name) &&
                    value.isNotBlank() &&
                    value.length <= MAX_HEADER_VALUE_LENGTH &&
                    '\r' !in value &&
                    '\n' !in value
            }
            .take(MAX_DRM_HEADERS)
            .associate { (name, value) -> name to value.trim() }
        return drm.copy(
            scheme = "widevine",
            licenseUrl = licenseUrl,
            licenseRequestHeaders = headers,
        )
    }

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
