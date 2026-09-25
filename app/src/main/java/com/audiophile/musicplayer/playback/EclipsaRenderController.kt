package com.audiophile.musicplayer.playback

/** IAMF requires a real decoder; OBR alone and ordinary FFmpeg do not supply one. */
object EclipsaRenderController {
    fun selectRenderer(isEclipsa: Boolean, mimeType: String?): EclipsaRenderer = when {
        !isEclipsa && mimeType != "audio/iamf" -> EclipsaRenderer.NONE
        SpatialDecoderCapabilities.supportsIamf() -> EclipsaRenderer.MEDIA3_IAMF
        else -> EclipsaRenderer.UNAVAILABLE
    }

    // The IAMF renderer owns its output layout. Do not spatialize binaural output twice.
    fun shouldForceSpatial(renderer: EclipsaRenderer): Boolean = false
    enum class EclipsaRenderer { NONE, MEDIA3_IAMF, UNAVAILABLE }
}
