package com.audiophile.musicplayer.data.source.playback

/**
 * Maps gateway / catalog HTTP bodies onto structured playback errors.
 * Legacy `no_stream_source` is not treated as AUTH_REQUIRED unless the
 * payload actually talks about tokens, secrets, or licensed sessions.
 */
object GatewayStreamErrorClassifier {
    fun classify(
        httpCode: Int?,
        error: String?,
        message: String?
    ): PlaybackSourceErrorCode {
        val code = error?.trim()?.uppercase().orEmpty().replace('-', '_')
        val text = message?.lowercase().orEmpty()
        when (code) {
            "AUTH_REQUIRED", "UNAUTHORIZED", "PROVIDER_NOT_CONFIGURED" ->
                return PlaybackSourceErrorCode.AUTH_REQUIRED
            "ATMOS_UNAVAILABLE" ->
                return PlaybackSourceErrorCode.ATMOS_UNAVAILABLE
            "QUALITY_UNAVAILABLE" ->
                return PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
            "SOURCE_OFFLINE", "STREAM_EXPIRED" ->
                return PlaybackSourceErrorCode.SOURCE_OFFLINE
            "UNSUPPORTED", "UNSUPPORTED_URL" ->
                return PlaybackSourceErrorCode.UNSUPPORTED
            "NOT_FOUND", "MISSING_PARAMETERS", "MISSING_ID" ->
                return PlaybackSourceErrorCode.NOT_FOUND
            "NO_STREAM_SOURCE" -> {
                return if (mentionsLicensedAuth(text)) {
                    PlaybackSourceErrorCode.AUTH_REQUIRED
                } else {
                    PlaybackSourceErrorCode.SOURCE_OFFLINE
                }
            }
        }
        return when (httpCode) {
            401, 403 -> PlaybackSourceErrorCode.AUTH_REQUIRED
            404 -> PlaybackSourceErrorCode.NOT_FOUND
            503, 502, 504, 410 -> PlaybackSourceErrorCode.SOURCE_OFFLINE
            else -> PlaybackSourceErrorCode.SOURCE_OFFLINE
        }
    }

    private fun mentionsLicensedAuth(message: String): Boolean =
        "token" in message ||
            "auth" in message ||
            "secret" in message ||
            "licensed" in message ||
            "qobuz_app_id" in message ||
            "account" in message
}
