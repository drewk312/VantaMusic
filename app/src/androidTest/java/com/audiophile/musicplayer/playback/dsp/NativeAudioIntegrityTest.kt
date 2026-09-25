package com.audiophile.musicplayer.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class NativeAudioIntegrityTest {
    @Test fun realEngineSurvivesFormatChangesAndProducesCleanNonSilentPcm() {
        assertTrue(VantaEqualizerNative.isAvailable)
        val processor = VantaEqualizerProcessor()
        try {
            for (rate in listOf(44100, 48000, 96000, 44100)) {
                processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
                processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
                for (enabled in listOf(false, true, false, true)) {
                    processor.config = VantaEqualizerConfig(eqEnabled = enabled,
                        eqBands = List(31) { -6f }, autoHeadroomEnabled = true)
                    var energy = 0.0
                    for (block in 0 until 12) {
                        val input = ByteBuffer.allocateDirect(1024 * 8).order(ByteOrder.LITTLE_ENDIAN)
                        repeat(1024) { index ->
                            val sample = (.2 * sin(2 * PI * 1000 * (block * 1024 + index) / rate)).toFloat()
                            input.putFloat(sample); input.putFloat(sample)
                        }
                        input.flip()
                        val original = ByteArray(input.remaining()); input.duplicate().get(original)
                        processor.queueInput(input)
                        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
                        assertEquals(original.size, output.remaining())
                        if (!enabled) {
                            val actual = ByteArray(output.remaining()); output.duplicate().get(actual)
                            assertArrayEquals(original, actual)
                        }
                        while (output.hasRemaining()) {
                            val value = output.float
                            assertTrue("finite PCM at $rate", value.isFinite())
                            assertTrue("No full-scale distortion at $rate: $value", abs(value) < .8f)
                            if (block >= 6) energy += value * value
                        }
                    }
                    val rms = sqrt(energy / (6 * 1024 * 2))
                    assertTrue("Audible PCM at $rate enabled=$enabled rms=$rms", rms > .01)
                    if (enabled) assertTrue("EQ cut must change PCM: $rms", rms < .12)
                }
            }
        } finally { processor.release() }
    }
    @Test fun upperEqBandsAndImmersiveSettingsProcessPcm16WithoutClipping() {
        val processor = VantaEqualizerProcessor()
        try {
            for (rate in listOf(44100, 48000, 96000)) {
                processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
                processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
                val settings = listOf(
                    VantaEqualizerConfig(),
                    VantaEqualizerConfig(eqEnabled = true, eqBands = List(31) { if (it >= 20) -12f else 0f }),
                    VantaEqualizerConfig(spatialEnabled = true, stereoWidenLevel = .35f),
                    VantaEqualizerConfig(spatialEnabled = true, stereoWidenLevel = .35f,
                        crossfeedEnabled = true, reverbEnabled = true, reverbPreset = 0),
                    VantaEqualizerConfig()
                )
                for ((mode, config) in settings.withIndex()) {
                    processor.config = config
                    var energy = 0.0
                    var measuredSamples = 0
                    var position = 0
                    for (block in 0 until 20) {
                        val frames = listOf(1024, 4096, 317)[block % 3]
                        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
                        repeat(frames) {
                            val sample = (.2 * sin(2 * PI * 8000 * position++ / rate) * 32767).toInt().toShort()
                            input.putShort(sample); input.putShort(sample)
                        }
                        input.flip()
                        processor.queueInput(input)
                        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
                        assertEquals(frames * 4, output.remaining())
                        while (output.hasRemaining()) {
                            val sample = output.short / 32768.0
                            assertTrue("No clipping rate=$rate mode=$mode sample=$sample", abs(sample) < .8)
                            if (block >= 10) { energy += sample * sample; measuredSamples++ }
                        }
                    }
                    val rms = sqrt(energy / measuredSamples)
                    assertTrue("Non-silent rate=$rate mode=$mode rms=$rms", rms > .001)
                    if (mode == 1) assertTrue("Upper EQ bands must attenuate 8kHz: $rms", rms < .06)
                }
            }
        } finally { processor.release() }
    }

}
