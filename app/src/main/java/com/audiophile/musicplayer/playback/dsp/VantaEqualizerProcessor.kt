package com.audiophile.musicplayer.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

private const val BLOCK_SIZE = 4096

@UnstableApi
class VantaEqualizerProcessor : BaseAudioProcessor() {

    @Volatile
    var config: VantaEqualizerConfig = VantaEqualizerConfig()
        set(value) {
            field = value
            configDirty = true
        }

    @Volatile
    var configDirty = false

    @Volatile
    var spectrumListener: ((FloatArray) -> Unit)? = null

    private var currentEncoding = C.ENCODING_INVALID
    private var currentSampleRate = 44_100
    private var currentChannelCount = 2
    private var isSupportedFormat = true
    private val nativeLock = Any()
    @Volatile private var native: VantaEqualizerNative? = null
    @Volatile private var nativeFailed = false

    private val scratchL = FloatArray(BLOCK_SIZE)
    private val scratchR = FloatArray(BLOCK_SIZE)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        currentEncoding = inputAudioFormat.encoding
        currentSampleRate = inputAudioFormat.sampleRate
        currentChannelCount = inputAudioFormat.channelCount
        isSupportedFormat = (currentChannelCount == 1 || currentChannelCount == 2) &&
            (currentEncoding == C.ENCODING_PCM_16BIT || currentEncoding == C.ENCODING_PCM_FLOAT)
        if (!isSupportedFormat) return inputAudioFormat

        ensureNativeEngine()
        synchronized(nativeLock) {
            native?.applyConfig(config)
        }
        configDirty = false
        return AudioProcessor.AudioFormat(
            currentSampleRate, 2, C.ENCODING_PCM_FLOAT
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val inputStart = inputBuffer.position()

        if (!isSupportedFormat) {
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }

        if (configDirty) {
            synchronized(nativeLock) {
            native?.let { nativeEngine ->
                    native?.applyConfig(config)
                            } ?: Unit
            }
            configDirty = false
        }

        val enc = currentEncoding
        val channels = currentChannelCount
        val bytesPerFrame = (if (enc == C.ENCODING_PCM_FLOAT) 4 else 2) * channels
        val frameCount = remaining / bytesPerFrame
        if (frameCount <= 0) return

        val outputBytes = frameCount * 8
        val output = replaceOutputBuffer(outputBytes)

        try {
            val inOrder = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val outOrder = output.order(ByteOrder.LITTLE_ENDIAN)
            val nativeEngine = native

            var offset = 0
            while (offset < frameCount) {
                val chunk = minOf(scratchL.size, frameCount - offset)
                if (enc == C.ENCODING_PCM_FLOAT) {
                    if (channels == 2) {
                        for (i in 0 until chunk) {
                            scratchL[i] = inOrder.getFloat()
                            scratchR[i] = inOrder.getFloat()
                        }
                    } else {
                        for (i in 0 until chunk) {
                            val v = inOrder.getFloat()
                            scratchL[i] = v
                            scratchR[i] = v
                        }
                    }
                } else {
                    if (channels == 2) {
                        for (i in 0 until chunk) {
                            scratchL[i] = inOrder.getShort() / Short.MAX_VALUE.toFloat()
                            scratchR[i] = inOrder.getShort() / Short.MAX_VALUE.toFloat()
                        }
                    } else {
                        for (i in 0 until chunk) {
                            val v = inOrder.getShort() / Short.MAX_VALUE.toFloat()
                            scratchL[i] = v
                            scratchR[i] = v
                        }
                    }
                }
            native?.let { nativeEngine ->

                synchronized(nativeLock) {
                    try {
                        nativeEngine.processDeinterleaved(scratchL, scratchR, 0, chunk)
                    } catch (e: Exception) {
                        android.util.Log.e("VANTA_DSP", "Native processDeinterleaved failed", e)
                        nativeFailed = true
                    }
                }
            } ?: Unit
            for (i in 0 until chunk) {
                outOrder.putFloat(scratchL[i])
                outOrder.putFloat(scratchR[i])
            }
            offset += chunk
        }
        if (!nativeFailed) {
            spectrumListener?.let { listener ->
                synchronized(nativeLock) {
                    native?.getSpectrumMagnitudes()?.let { mags ->
                        listener.invoke(mags)
                    }
                }
            }
        }
            output.flip()
        } catch (e: Exception) {
            if (e is java.util.concurrent.CancellationException) throw e
            android.util.Log.e("VANTA_DSP", "VantaEqualizer failed; bypassing", e)
            output.clear()
            if (enc == C.ENCODING_PCM_FLOAT) {
                output.put(inputBuffer.duplicate().apply {
                    position(inputStart)
                    limit(inputStart + remaining)
                })
            } else {
                val dup = inputBuffer.duplicate()
                dup.position(inputStart)
                dup.limit(inputStart + remaining)
                while (dup.hasRemaining()) {
                    output.putFloat(dup.getShort() / Short.MAX_VALUE.toFloat())
                    output.putFloat(dup.getShort() / Short.MAX_VALUE.toFloat())
                }
            }
            output.flip()
            nativeFailed = true
            synchronized(nativeLock) {
                native?.destroy()
                native = null
            }
        }
        inputBuffer.position(inputBuffer.limit())
    }

    override fun onFlush() {
        if (nativeFailed) {
            ensureNativeEngine()
        }
        if (!nativeFailed) {
            synchronized(nativeLock) {
                native?.applyConfig(config)
            }
            configDirty = false
        }
    }

    override fun onReset() {
        currentEncoding = C.ENCODING_INVALID
    }

    fun release() {
        synchronized(nativeLock) {
            native?.destroy()
            native = null
        }
        nativeFailed = false
    }

    fun getSpectrum(): FloatArray? {
        synchronized(nativeLock) {
            return native?.getSpectrumMagnitudes()
        }
    }

    private fun ensureNativeEngine() {
        if (native == null && !nativeFailed) {
            try {
                val newNative = VantaEqualizerNative.create(BLOCK_SIZE, currentSampleRate.toFloat())
                synchronized(nativeLock) {
                    native = newNative
                }
                if (newNative == null) nativeFailed = true
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                android.util.Log.e("VANTA_DSP", "Failed to create native equalizer engine", e)
                nativeFailed = true
            }
        }
    }

    private fun toPcm16(value: Float): Short =
        (value.coerceIn(-1f, 1f) * Short.MAX_VALUE)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
}
