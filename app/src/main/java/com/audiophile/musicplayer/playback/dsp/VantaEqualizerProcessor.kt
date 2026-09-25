package com.audiophile.musicplayer.playback.dsp

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

private const val BLOCK_SIZE = 4096

interface VantaDspEngine {
    fun applyConfig(config: VantaEqualizerConfig)
    fun processDeinterleaved(left: FloatArray, right: FloatArray, offset: Int, frameCount: Int)
    fun getSpectrumMagnitudes(): FloatArray?
    fun destroy()
}

@UnstableApi
class VantaEqualizerProcessor(
    private val engineFactory: (Int, Float) -> VantaDspEngine? = { size, rate -> VantaEqualizerNative.create(size, rate) }
) : BaseAudioProcessor() {
    @Volatile var config = VantaEqualizerConfig()
        set(value) { field = value; configDirty = true }
    @Volatile var configDirty = false
    @Volatile var spectrumListener: ((FloatArray) -> Unit)? = null
    /**
     * Set per-track by the playback engine. When the active track is genuine
     * Dolby Atmos / spatial / multi-channel, the stereo DSP chain must NOT
     * touch the mix: processing it corrupts or silences playback. The stream
     * is routed to the platform Dolby/spatializer path untouched instead.
     */
    @Volatile var spatialTrackBypass = false
    @Volatile var isCurrentTrackSpatial = false
    private var encoding = C.ENCODING_INVALID
    private var sampleRate = 44_100
    private var channels = 2
    private var supported = false
    private val nativeLock = Any()
    private var native: VantaDspEngine? = null
    private var appliedConfig: VantaEqualizerConfig? = null
    private var nativeFailed = false
    private val left = FloatArray(BLOCK_SIZE)
    private val right = FloatArray(BLOCK_SIZE)

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        synchronized(nativeLock) {
            // Filter histories and sample-rate converters belong to one format only.
            if (sampleRate != format.sampleRate || encoding != format.encoding || channels != format.channelCount) releaseEngine()
            encoding = format.encoding; sampleRate = format.sampleRate; channels = format.channelCount
            supported = channels in 1..2 && isVantaPcmEncoding(encoding)
            nativeFailed = false
        }
        return format
    }

    override fun queueInput(input: ByteBuffer) = synchronized(nativeLock) {
        if (!input.hasRemaining()) return@synchronized
        val snapshot = config
        val effective = snapshot.forAudioProcessing()
        val isSpatial = isCurrentTrackSpatial
        if (!supported || spatialTrackBypass || snapshot.eqBypassEnabled || !effective.hasEnabledEffects(isSpatial)) {
            passthrough(input)
            return@synchronized
        }
        // Initialize here, not just in onConfigure: users can enable EQ mid-song.
        if (native == null && !nativeFailed) {
            native = try { engineFactory(BLOCK_SIZE, sampleRate.toFloat()) }
            catch (error: Exception) {
                Log.e("VANTA_DSP", "Effects unavailable; preserving original PCM", error)
                null
            }
            nativeFailed = native == null
            appliedConfig = null
        }
        val engine = native
        if (engine == null || nativeFailed) {
            passthrough(input)
            return@synchronized
        }
        val start = input.position()
        val bytes = input.remaining()
        val bytesPerFrame = bytesPerSample() * channels
        if (bytes % bytesPerFrame != 0) {
            passthrough(input)
            return@synchronized
        }
        val output = replaceOutputBuffer(bytes).order(ByteOrder.LITTLE_ENDIAN)
        try {
            if (effective != appliedConfig) {
                engine.applyConfig(effective)
                appliedConfig = effective
                Log.i("VANTA_DSP", "effects_applied rate=$sampleRate encoding=$encoding eq=${effective.eqEnabled} spatial=${effective.spatialEnabled}")
            }
            configDirty = false
            val gain = effective.inputHeadroomGain(isSpatial)
            input.order(ByteOrder.LITTLE_ENDIAN)
            var frames = bytes / bytesPerFrame
            while (frames > 0) {
                val count = minOf(BLOCK_SIZE, frames)
                for (i in 0 until count) {
                    left[i] = readSample(input) * gain
                    right[i] = if (channels == 2) readSample(input) * gain else left[i]
                }
                engine.processDeinterleaved(left, right, 0, count)
                for (i in 0 until count) {
                    check(left[i].isFinite() && right[i].isFinite()) { "Non-finite DSP output" }
                    writeSample(output, left[i])
                    if (channels == 2) writeSample(output, right[i])
                }
                frames -= count
            }
            output.flip()
            engine.getSpectrumMagnitudes()?.let { spectrumListener?.invoke(it) }
        } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException) throw error
            Log.e("VANTA_DSP", "Effects failed; preserving original PCM", error)
            input.position(start)
            output.clear(); output.put(input); output.flip()
            releaseEngine(); nativeFailed = true
        }
    }

    private fun bytesPerSample(): Int = when (encoding) {
        C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_32BIT -> 4
        C.ENCODING_PCM_24BIT -> 3
        else -> 2
    }
    private fun readSample(input: ByteBuffer): Float = when (encoding) {
        C.ENCODING_PCM_FLOAT -> input.float
        C.ENCODING_PCM_32BIT -> input.int / 2147483648f
        C.ENCODING_PCM_24BIT -> readPcm24(input)
        else -> input.short / 32768f
    }
    private fun writeSample(output: ByteBuffer, sample: Float) {
        val bounded = sample.coerceIn(-1f, 1f)
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> output.putFloat(bounded)
            C.ENCODING_PCM_32BIT -> output.putInt(
                (bounded * 2147483648f).roundToInt().coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
            )
            C.ENCODING_PCM_24BIT -> writePcm24(output, bounded)
            else -> output.putShort((bounded * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
    }
    private fun passthrough(input: ByteBuffer) {
        replaceOutputBuffer(input.remaining()).apply { put(input); flip() }
    }
    // UI changes only publish configuration. Native work stays on the audio thread.
    fun flushAndApplyConfig() { forceApply() }
    fun forceApply() { configDirty = true }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() = synchronized(nativeLock) { releaseEngine(); nativeFailed = false }
    override fun onReset() = synchronized(nativeLock) { releaseEngine(); encoding = C.ENCODING_INVALID; supported = false }
    fun release() = synchronized(nativeLock) { releaseEngine(); nativeFailed = false }
    fun getSpectrum(): FloatArray? = synchronized(nativeLock) { native?.getSpectrumMagnitudes() }
    private fun releaseEngine() { native?.destroy(); native = null; appliedConfig = null }
}

internal fun isVantaPcmEncoding(encoding: Int): Boolean =
    encoding == C.ENCODING_PCM_16BIT ||
        encoding == C.ENCODING_PCM_24BIT ||
        encoding == C.ENCODING_PCM_32BIT ||
        encoding == C.ENCODING_PCM_FLOAT

private fun readPcm24(input: ByteBuffer): Float {
    val b0 = input.get().toInt() and 0xFF
    val b1 = input.get().toInt() and 0xFF
    val b2 = input.get().toInt() and 0xFF
    var packed = b0 or (b1 shl 8) or (b2 shl 16)
    if (packed and 0x800000 != 0) packed = packed or -0x1000000
    return packed / 8_388_608f
}

private fun writePcm24(output: ByteBuffer, sample: Float) {
    val packed = (sample.coerceIn(-1f, 1f) * 8_388_608f).roundToInt().coerceIn(-8_388_608, 8_388_607)
    output.put((packed and 0xFF).toByte())
    output.put(((packed shr 8) and 0xFF).toByte())
    output.put(((packed shr 16) and 0xFF).toByte())
}

internal fun VantaEqualizerConfig.forAudioProcessing(): VantaEqualizerConfig {
    val gains = List(VantaEqualizerConfig.BAND_COUNT) { index ->
        val base = if (eqEnabled) eqBands.getOrElse(index) { 0f }.takeIf { it.isFinite() } ?: 0f else 0f
        val treble = if (trebleEnabled && index >= 20) minOf(index - 19, 6) * trebleBoostAmount.coerceIn(0f, 1f) else 0f
        (base + treble).coerceIn(-12f, 12f)
    }
    return copy(eqEnabled = eqEnabled || trebleEnabled, eqBands = gains,
        crossfeedEnabled = spatialEnabled && crossfeedEnabled,
        reverbEnabled = spatialEnabled && reverbEnabled)
}

/**
 * Phone speakers (Fold / thin dual-speaker phones) distort when Immersive widening,
 * bass cannon, tube drive, or hot EQ boosts hit the tiny amps. Keep the user's
 * headphone settings stored, but process a safer profile while the built-in
 * speaker is the active route.
 */
fun VantaEqualizerConfig.forBuiltInSpeaker(): VantaEqualizerConfig {
    val softenedBands = eqBands.mapIndexed { index, gain ->
        val capped = gain.coerceIn(-12f, 3f)
        // Sub/bass energy is what Fold speakers clip on first.
        if (index <= 6) minOf(capped, 1.5f) else capped
    }
    return copy(
        spatialEnabled = false,
        immersiveMode = VantaImmersiveMode.OFF,
        stereoWidenLevel = 0f,
        crossfeedEnabled = false,
        reverbEnabled = false,
        convolverEnabled = false,
        tubeEnabled = false,
        bassCannonEnabled = false,
        trebleEnabled = false,
        limiterEnabled = true,
        autoHeadroomEnabled = true,
        eqBands = softenedBands,
        // Extra fixed headroom so hot lossless masters don't slam the amp.
        replayGainDb = (if (loudnessNormalizationEnabled) replayGainDb else 0f) - 6f,
        loudnessNormalizationEnabled = true
    )
}

/**
 * USB DACs: skip VANTA's stereo DSP so PCM is not re-EQed / widened before the
 * device. Android still may resample to the USB endpoint rate — true WASAPI-style
 * exclusive bit-perfect is not available — but this is the closest app-side path.
 */
fun VantaEqualizerConfig.forUsbPassthrough(): VantaEqualizerConfig =
    copy(eqBypassEnabled = true)

internal fun VantaEqualizerConfig.hasEnabledEffects(isSpatialTrack: Boolean = false) =
    eqEnabled || spatialEnabled || tubeEnabled || bassCannonEnabled ||
    autoEqEnabled || convolverEnabled || loudnessNormalizationEnabled || isSpatialTrack

internal fun VantaEqualizerConfig.inputHeadroomGain(isSpatialTrack: Boolean = false): Float {
    val boost = if (autoHeadroomEnabled && eqEnabled) eqBands.maxOrNull()?.coerceAtLeast(0f) ?: 0f else 0f
    val replay = if (loudnessNormalizationEnabled) {
        if (isSpatialTrack) {
            // Dolby Atmos / Spatial tracks are mixed lower (~-18 LUFS) per broadcast standard.
            // Sound Check matches them with normalized stereo FLAC by applying +2.5 dB makeup headroom.
            2.5f
        } else {
            // Commercial stereo masters are hot (-9 to -12 LUFS). Pull them down to target level.
            replayGainDb.takeIf { it.isFinite() }?.coerceIn(-30f, 6f) ?: -7.5f
        }
    } else {
        if (isSpatialTrack) {
            // When Sound Check is disabled, still provide +3.0 dB clean makeup gain so Atmos
            // doesn't sound excessively quiet next to commercial stereo tracks.
            3.0f
        } else {
            0f
        }
    }
    return 10f.pow((replay - boost) / 20f)
}
