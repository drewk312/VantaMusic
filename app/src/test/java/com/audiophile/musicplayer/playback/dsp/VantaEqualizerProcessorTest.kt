package com.audiophile.musicplayer.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

@UnstableApi
class VantaEqualizerProcessorTest {
    @Test
    fun bypass_preservesFloatPcmBytes() {
        val input = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0.25f)
            .putFloat(-0.5f)
            .putFloat(1.0f)
            .putFloat(-1.0f)
            .array()

        assertArrayEquals(
            input,
            processBypassed(
                encoding = C.ENCODING_PCM_FLOAT,
                bytes = input
            )
        )
    }

    @Test
    fun bypass_preservesTwentyFourBitPcmBytes() {
        val input = byteArrayOf(
            0x01, 0x02, 0x03,
            0x04, 0x05, 0x06,
            0x7f, 0x00, 0x40,
            0x11, 0x22, 0x33
        )

        assertArrayEquals(
            input,
            processBypassed(
                encoding = C.ENCODING_PCM_24BIT,
                bytes = input
            )
        )
    }

    @Test fun enablingEffectsMidTrackStartsEngineAndBypassRestoresExactBytes() {
        val rates = mutableListOf<Float>()
        val processor = VantaEqualizerProcessor { _, rate -> rates += rate; FakeEngine() }
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(.25f).putFloat(-.25f).array()
        assertArrayEquals(bytes, process(processor, bytes))
        org.junit.Assert.assertTrue(rates.isEmpty())
        processor.config = VantaEqualizerConfig(eqEnabled = true)
        val changed = ByteBuffer.wrap(process(processor, bytes)).order(ByteOrder.LITTLE_ENDIAN)
        org.junit.Assert.assertEquals(.125f, changed.float, .00001f)
        org.junit.Assert.assertEquals(listOf(44100f), rates)
        processor.config = processor.config.copy(eqBypassEnabled = true)
        assertArrayEquals(bytes, process(processor, bytes))
    }

    @Test
    fun sampleRateChangeRecreatesNativeState() {
        val rates = mutableListOf<Float>()
        val engines = mutableListOf<FakeEngine>()
        val processor = VantaEqualizerProcessor { _, rate -> rates += rate; FakeEngine().also(engines::add) }
        processor.config = VantaEqualizerConfig(eqEnabled = true)
        for (rate in listOf(44100, 96000, 48000)) {
            processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
            processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            process(processor, ByteArray(16))
        }
        org.junit.Assert.assertEquals(listOf(44100f, 96000f, 48000f), rates)
        org.junit.Assert.assertTrue(engines.dropLast(1).all { it.destroyed })
        processor.release()
        org.junit.Assert.assertTrue(engines.last().destroyed)
    }

    @Test
    fun builtInSpeakerProfileDisablesImmersiveAndAddsHeadroom() {
        val stored = VantaEqualizerConfig(
            spatialEnabled = true,
            stereoWidenLevel = 0.8f,
            bassCannonEnabled = true,
            tubeEnabled = true,
            eqEnabled = true,
            eqBands = List(31) { if (it < 5) 6f else 0f },
            limiterEnabled = false
        )
        val speaker = stored.forBuiltInSpeaker()
        org.junit.Assert.assertFalse(speaker.spatialEnabled)
        org.junit.Assert.assertFalse(speaker.bassCannonEnabled)
        org.junit.Assert.assertFalse(speaker.tubeEnabled)
        org.junit.Assert.assertTrue(speaker.limiterEnabled)
        org.junit.Assert.assertTrue(speaker.loudnessNormalizationEnabled)
        org.junit.Assert.assertEquals(-6f, speaker.replayGainDb, 0.01f)
        org.junit.Assert.assertTrue(speaker.eqBands.take(7).all { it <= 1.5f })
    }

    @Test
    fun usbPassthroughForcesExactBypass() {
        val stored = VantaEqualizerConfig(eqEnabled = true, spatialEnabled = true)
        val usb = stored.forUsbPassthrough()
        org.junit.Assert.assertTrue(usb.eqBypassEnabled)
        org.junit.Assert.assertTrue(usb.spatialEnabled)
    }

    @Test fun invalidDspSamplesFallBackToTheOriginalBuffer() {
        val processor = VantaEqualizerProcessor { _, _ -> FakeEngine(invalid = true) }
        processor.config = VantaEqualizerConfig(eqEnabled = true)
        processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(.3f).putFloat(-.3f).array()
        assertArrayEquals(bytes, process(processor, bytes))
        assertArrayEquals(bytes, process(processor, bytes))
    }

    @Test fun trebleWorksIndependentlyAndDisabledEqDoesNotAttenuate() {
        val config = VantaEqualizerConfig(eqBands = List(31) { 12f })
        org.junit.Assert.assertEquals(1f, config.forAudioProcessing().inputHeadroomGain(), 0f)
        val treble = config.copy(trebleEnabled = true).forAudioProcessing()
        org.junit.Assert.assertTrue(treble.eqEnabled)
        org.junit.Assert.assertEquals(3f, treble.eqBands.last(), 0f)
        org.junit.Assert.assertFalse(config.copy(crossfeedEnabled = true, reverbEnabled = true).forAudioProcessing().hasEnabledEffects())
    }

    @Test fun twentyFourBitEqKeepsFrameSizeAndAttenuates() {
        val processor = VantaEqualizerProcessor { _, _ -> FakeEngine() }
        processor.config = VantaEqualizerConfig(eqEnabled = true)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_24BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = byteArrayOf(0x00, 0x00, 0x40, 0x00, 0x00, 0x40)
        val output = process(processor, input)
        org.junit.Assert.assertEquals(input.size, output.size)
        org.junit.Assert.assertFalse(input.contentEquals(output))
    }

    private fun process(processor: VantaEqualizerProcessor, bytes: ByteArray): ByteArray {
        val input = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).put(bytes)
        input.flip(); processor.queueInput(input)
        val output = processor.output
        return ByteArray(output.remaining()).also(output::get)
    }
    private class FakeEngine(private val invalid: Boolean = false) : VantaDspEngine {
        var destroyed = false
        override fun applyConfig(config: VantaEqualizerConfig) {}
        override fun processDeinterleaved(left: FloatArray, right: FloatArray, offset: Int, frameCount: Int) {
            for (i in offset until offset + frameCount) {
                left[i] = if (invalid) Float.NaN else left[i] * .5f
                right[i] *= .5f
            }
        }
        override fun getSpectrumMagnitudes(): FloatArray? = null
        override fun destroy() { destroyed = true }
    }

    private fun processBypassed(encoding: Int, bytes: ByteArray): ByteArray {
        val processor = VantaEqualizerProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, encoding))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        val input = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(bytes)
        input.flip()
        processor.queueInput(input)

        val output = processor.output
        return ByteArray(output.remaining()).also(output::get)
    }
}
