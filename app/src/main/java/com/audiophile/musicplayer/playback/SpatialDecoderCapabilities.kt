package com.audiophile.musicplayer.playback

import android.media.MediaCodecList
import android.content.Context
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioAttributes
import android.os.Build
import com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality

object SpatialDecoderCapabilities {
    private var appContext: Context? = null
    fun initialize(context: Context) { appContext = context.applicationContext }

    /** Precisely what this device/route can do with each spatial format. */
    enum class SpatialPlaybackCapability {
        NO_OUTPUT,
        PCM_STEREO_FALLBACK,
        PCM_MULTICHANNEL,
        JOC_PASSTHROUGH,
        JOC_SOFTWARE_RENDER,
        IAMF_RENDER
    }

    /** Ability per explicit spatial format, not a boolean switch. */
    fun spatialPlaybackCapabilityFor(format: com.audiophile.musicplayer.data.display.SpatialFormat): SpatialPlaybackCapability {
        return when (format) {
            com.audiophile.musicplayer.data.display.SpatialFormat.DOLBY_ATMOS -> when {
                supportsAtmosOutput() -> SpatialPlaybackCapability.JOC_PASSTHROUGH
                supportsSoftwareAtmos() -> SpatialPlaybackCapability.JOC_SOFTWARE_RENDER
                else -> SpatialPlaybackCapability.PCM_STEREO_FALLBACK
            }
            com.audiophile.musicplayer.data.display.SpatialFormat.ECLIPSA_AUDIO -> if (supportsIamf())
                SpatialPlaybackCapability.IAMF_RENDER else SpatialPlaybackCapability.PCM_STEREO_FALLBACK
            com.audiophile.musicplayer.data.display.SpatialFormat.SONY_360_REALITY_AUDIO ->
                if (supportsMpegh()) SpatialPlaybackCapability.PCM_MULTICHANNEL
                else SpatialPlaybackCapability.PCM_STEREO_FALLBACK
            com.audiophile.musicplayer.data.display.SpatialFormat.UNKNOWN_SPATIAL,
            com.audiophile.musicplayer.data.display.SpatialFormat.NONE -> SpatialPlaybackCapability.PCM_STEREO_FALLBACK
        }
    }

    fun canPlaySpatial(format: com.audiophile.musicplayer.data.display.SpatialFormat): Boolean {
        val capability = spatialPlaybackCapabilityFor(format)
        return capability == SpatialPlaybackCapability.JOC_PASSTHROUGH ||
            capability == SpatialPlaybackCapability.JOC_SOFTWARE_RENDER ||
            capability == SpatialPlaybackCapability.IAMF_RENDER ||
            capability == SpatialPlaybackCapability.PCM_MULTICHANNEL
    }

    /** Re-evaluated for the current route; an E-AC-3 decoder alone is not Atmos. */
    @Suppress("DEPRECATION")
    fun supportsAtmosOutput(): Boolean = runCatching {
        val context = appContext ?: return false
        if (Build.VERSION.SDK_INT < 29) return false
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val joc = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_E_AC3_JOC)
            .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).build()
        if (android.media.AudioTrack.isDirectPlaybackSupported(joc, attributes)) return true
        if (Build.VERSION.SDK_INT < 32 || !platformSupports("audio/eac3-joc")) return false
        val spatializer = context.getSystemService(AudioManager::class.java).spatializer
        val pcm = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).build()
        spatializer.isAvailable && spatializer.isEnabled && spatializer.canBeSpatialized(attributes, pcm)
    }.getOrDefault(false)
    private fun platformSupports(mime: String): Boolean = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { codec ->
            !codec.isEncoder && codec.supportedTypes.any { it.equals(mime, true) }
        }
    }.getOrDefault(false)
    fun supportsIamf(): Boolean = platformSupports("audio/iamf") || runCatching {
        Class.forName("androidx.media3.decoder.iamf.IamfLibrary").getMethod("isAvailable").invoke(null) == true
    }.getOrDefault(false)
    fun supportsEac3(): Boolean = platformSupports("audio/eac3-joc") || platformSupports("audio/eac3") || runCatching {
        val library = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegLibrary")
        library.getMethod("isAvailable").invoke(null) == true &&
            library.getMethod("supportsFormat", String::class.java).invoke(null, "audio/eac3") == true
    }.getOrDefault(false)
    fun supportsSoftwareAtmos(): Boolean = com.audiophile.musicplayer.playback.spatial.NativeSpatial.isAvailable()
    fun supportsMpegh(): Boolean = platformSupports("audio/mha1") ||
        platformSupports("audio/mhm1") ||
        platformSupports("audio/mpegh") ||
        runCatching {
            Class.forName("androidx.media3.decoder.mpegh.MpeghLibrary").getMethod("isAvailable").invoke(null) == true
        }.getOrDefault(false) ||
        runCatching {
            Class.forName("androidx.media3.decoder.mpegh.MpeghAudioRenderer")
            true
        }.getOrDefault(false)
    fun supportsAtmosStream(stream: com.audiophile.musicplayer.data.source.ResolvedStream): Boolean {
        if (!stream.isDolbyAtmos || supportsAtmosOutput()) return true
        val codec = listOfNotNull(stream.codec, stream.mimeType, stream.format).joinToString(" ").lowercase()
        return supportsSoftwareAtmos() && ("eac3" in codec || "e-ac-3" in codec || "ec-3" in codec)
    }
    fun supportsAtmosPlayback(): Boolean = supportsAtmosOutput() || supportsSoftwareAtmos()
    fun playableQuality(requested: RequestedAudioQuality): RequestedAudioQuality =
        selectSpatialQuality(
            requested,
            supportsAtmosOutput(),
            supportsIamf(),
            supportsSoftwareAtmos(),
            supportsMpegh()
        )
}
