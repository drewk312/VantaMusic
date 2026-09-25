package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.data.source.ResolvedStream

/**
 * Quality the user/player asked a source to supply. Playback itself stays
 * source-agnostic: adapters either return a truthful [ResolvedStream] or a
 * structured failure. Missing catalog tokens never block local/direct files.
 */
enum class RequestedAudioQuality {
    AUTO_SPATIAL,
    IAMF,
    ATMOS,
    SONY_360,
    HI_RES_24,
    LOSSLESS_16,
    ANY
}

enum class PlaybackSourceErrorCode {
    AUTH_REQUIRED,
    QUALITY_UNAVAILABLE,
    ATMOS_UNAVAILABLE,
    SOURCE_OFFLINE,
    UNSUPPORTED,
    NOT_FOUND
}

data class PlaybackSourceFailure(
    val code: PlaybackSourceErrorCode,
    val sourceLabel: String,
    val message: String,
    val adapterId: String? = null
) {
    fun userMessage(): String = when (code) {
        PlaybackSourceErrorCode.AUTH_REQUIRED -> "This source requires authentication."
        PlaybackSourceErrorCode.ATMOS_UNAVAILABLE -> "No configured source returned Atmos media."
        else -> message
    }

    companion object {
        fun of(
            code: PlaybackSourceErrorCode,
            sourceLabel: String,
            adapterId: String? = null,
            detail: String? = null
        ): PlaybackSourceFailure = PlaybackSourceFailure(
            code = code,
            sourceLabel = sourceLabel,
            adapterId = adapterId,
            message = messageFor(code, sourceLabel, detail)
        )

        fun messageFor(
            code: PlaybackSourceErrorCode,
            sourceLabel: String,
            detail: String? = null
        ): String {
            val suffix = detail?.trim()?.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
            return when (code) {
                PlaybackSourceErrorCode.AUTH_REQUIRED ->
                    "This source requires authentication.$suffix"
                PlaybackSourceErrorCode.QUALITY_UNAVAILABLE ->
                    "$sourceLabel cannot supply the requested quality.$suffix"
                PlaybackSourceErrorCode.ATMOS_UNAVAILABLE ->
                    "Dolby Atmos is not available from $sourceLabel. No configured source returned Atmos media.$suffix"
                PlaybackSourceErrorCode.SOURCE_OFFLINE ->
                    "$sourceLabel did not return a playable stream.$suffix"
                PlaybackSourceErrorCode.UNSUPPORTED ->
                    "$sourceLabel cannot play this media type.$suffix"
                PlaybackSourceErrorCode.NOT_FOUND ->
                    "No playable stream found from $sourceLabel.$suffix"
            }
        }
    }
}

sealed class PlaybackSourceOutcome {
    data class Ready(
        val stream: ResolvedStream,
        val qualityShortfall: PlaybackSourceErrorCode? = null
    ) : PlaybackSourceOutcome()

    data class Failed(val failure: PlaybackSourceFailure) : PlaybackSourceOutcome()
}

data class PlaybackSourceRequest(
    val title: String,
    val artist: String,
    val localUri: String? = null,
    val directUrl: String? = null,
    val catalogProviderId: String? = null,
    val catalogExternalId: String? = null,
    val requestedQuality: RequestedAudioQuality = RequestedAudioQuality.HI_RES_24,
    val bitrateKbps: Int = 0,
    val expiresAtMs: Long? = null
)

fun RequestedAudioQuality.toGatewayQuality(): String = when (this) {
    RequestedAudioQuality.AUTO_SPATIAL -> "auto"
    RequestedAudioQuality.IAMF -> "iamf"
    RequestedAudioQuality.ATMOS -> "atmos"
    RequestedAudioQuality.SONY_360 -> "360"
    RequestedAudioQuality.HI_RES_24 -> "24"
    RequestedAudioQuality.LOSSLESS_16 -> "16"
    RequestedAudioQuality.ANY -> "24"
}

fun requestedAudioQualityFromPreference(value: String?): RequestedAudioQuality =
    when (value?.trim()?.lowercase()) {
        "auto" -> RequestedAudioQuality.AUTO_SPATIAL
        "iamf", "eclipsa" -> RequestedAudioQuality.IAMF
        "atmos", "dolby_atmos", "eac3", "eac3_joc" -> RequestedAudioQuality.ATMOS
        "360", "360ra", "sony360", "sony_360", "360_reality_audio" -> RequestedAudioQuality.SONY_360
        "16" -> RequestedAudioQuality.LOSSLESS_16
        else -> RequestedAudioQuality.HI_RES_24
    }
