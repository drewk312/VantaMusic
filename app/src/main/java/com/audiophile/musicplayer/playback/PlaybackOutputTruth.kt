@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Holds what the audio pipeline actually hands to the platform sink: the real
 * sample rate, PCM encoding, and channel count from `AudioSink.configure()`.
 *
 * This is the Qobuz "bz7" pattern: the mobile app wraps the ExoPlayer sink to
 * log the post-pipeline format so the UI can show the true output depth/rate
 * (e.g. "24-bit / 192 kHz") instead of only the stream's claimed format.
 */
object PlaybackOutputTruth {

    data class SinkOutputTruth(
        val sampleRateHz: Int? = null,
        val pcmEncodingName: String? = null,
        val bitDepth: Int? = null,
        val channels: Int? = null
    ) {
        val isUseful: Boolean get() = sampleRateHz != null || bitDepth != null || channels != null
    }

    @Volatile
    var latest: SinkOutputTruth = SinkOutputTruth()
        private set

    @Volatile
    var listener: ((SinkOutputTruth) -> Unit)? = null

    fun capture(format: Format) {
        val truth = SinkOutputTruth(
            sampleRateHz = format.sampleRate.takeIf { it > 0 },
            pcmEncodingName = Media3AudioFormatReader.pcmEncodingName(format.pcmEncoding),
            bitDepth = Media3AudioFormatReader.bitDepthFromPcmEncoding(format.pcmEncoding),
            channels = format.channelCount.takeIf { it > 0 }
        )
        latest = truth
        if (truth.isUseful) listener?.invoke(truth)
    }
}

/**
 * Transparently forwards to the real [DefaultAudioSink] (so `AudioSinkHolder`
 * and [OutputSwitchController] keep their direct casts working) while capturing
 * the configured output format for the truth chip.
 */
class VantaAudioSinkProxy(delegate: AudioSink) : ForwardingAudioSink(delegate) {

    override fun configure(inputFormat: Format, specifiedBufferSizeUs: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSizeUs, outputChannels)
        PlaybackOutputTruth.capture(inputFormat)
    }
}