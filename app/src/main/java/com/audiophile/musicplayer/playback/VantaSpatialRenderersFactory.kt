@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.audiophile.musicplayer.playback

import android.content.Context
import android.os.Handler
import androidx.media3.decoder.iamf.LibiamfAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerProcessor
import android.os.Build
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Binaural IAMF has its own sink so the stereo widening/EQ chain cannot render it twice. */
class VantaSpatialRenderersFactory(context: Context, private val equalizer: VantaEqualizerProcessor? = null,
    private val iamfProcessors: Array<androidx.media3.common.audio.AudioProcessor> = emptyArray()) :
    DefaultRenderersFactory(context) {
    init {
        setEnableDecoderFallback(true)
    }
    @Suppress("DEPRECATION")
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        // Qobuz enables float output so hi-res decode stays in float right up to
        // the AudioTrack write. Default off here preserves the existing PCM16
        // behavior; turn it on in Settings → Audio for float-tracks if wanted.
        val floatOutput = PlaybackOutputPreferences(context).floatOutputEnabled()
        val sink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(floatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .apply { equalizer?.let { setAudioProcessors(arrayOf(it)) } }
            .build()
        AudioSinkHolder.register(sink)
        OutputSwitchController.applySelection(context, sink)
        // Return the truth proxy to Media3; the real sink stays registered so
        // OutputSwitchController's DefaultAudioSink casts keep working.
        return VantaAudioSinkProxy(sink)
    }

    override fun buildAudioRenderers(context: Context, extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean, audioSink: AudioSink, eventHandler: Handler,
        eventListener: AudioRendererEventListener, out: ArrayList<Renderer>) {
        val headphoneProcessors: Array<androidx.media3.common.audio.AudioProcessor> =
            if (equalizer != null) arrayOf(equalizer, *iamfProcessors) else iamfProcessors
        out.add(LibiamfAudioRenderer(context, eventHandler, eventListener,
            DefaultAudioSink.Builder(context).setAudioProcessors(headphoneProcessors).build()))
        // LC streams use Ittiam; baseline/unspecified MPEG-H uses Fraunhofer.
        out.add(com.audiophile.musicplayer.playback.spatial.SpatialAudioRenderer(1, eventHandler, eventListener,
            DefaultAudioSink.Builder(context).setAudioProcessors(headphoneProcessors).build()))
        out.add(androidx.media3.decoder.mpegh.MpeghAudioRenderer(eventHandler, eventListener,
            DefaultAudioSink.Builder(context).setAudioProcessors(headphoneProcessors).build()))
        if (!SpatialDecoderCapabilities.supportsAtmosOutput()) {
            out.add(com.audiophile.musicplayer.playback.spatial.SpatialAudioRenderer(0, eventHandler, eventListener,
                DefaultAudioSink.Builder(context).setAudioProcessors(headphoneProcessors).build()))
        }
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector,
            enableDecoderFallback, audioSink, eventHandler, eventListener, out)
        val pixelAtmosGuard = Build.MANUFACTURER.equals("Google", ignoreCase = true) && Build.MODEL.startsWith("Pixel")
        val fiioLegacyFlac = Build.VERSION.SDK_INT <= 29 && Build.MODEL.startsWith("FiiO", ignoreCase = true)
        if (pixelAtmosGuard || fiioLegacyFlac) {
            for (index in out.indices) {
                if (out[index] !is androidx.media3.exoplayer.audio.MediaCodecAudioRenderer) continue
                out[index] = object : androidx.media3.exoplayer.audio.MediaCodecAudioRenderer(
                    context, getCodecAdapterFactory(), mediaCodecSelector, enableDecoderFallback,
                    eventHandler, eventListener, audioSink
                ) {
                    override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
                        // The M11 Plus Android 10 OMX decoder drops STREAMINFO on flush.
                        // Recreate it so MediaCodec receives its codec-specific data again.
                        if (fiioLegacyFlac && sampleStreamIsResetToKeyFrame &&
                            codecInfo?.name == "OMX.google.flac.decoder") {
                            releaseCodec()
                        }
                        super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame)
                    }

                    override fun supportsFormat(selector: MediaCodecSelector, format: androidx.media3.common.Format): Int {
                        // Guard the original format: Media3's soft match queries
                        // E-AC-3 separately, so a selector-only filter is insufficient.
                        if (pixelAtmosGuard && com.audiophile.musicplayer.playback.spatial.SpatialMimeSupport.isOpenJocInput(format) &&
                            !SpatialDecoderCapabilities.supportsAtmosOutput()) {
                            return androidx.media3.exoplayer.RendererCapabilities.create(
                                androidx.media3.common.C.FORMAT_UNSUPPORTED_SUBTYPE)
                        }
                        return super.supportsFormat(selector, format)
                    }
                }
            }
        }
    }
}
