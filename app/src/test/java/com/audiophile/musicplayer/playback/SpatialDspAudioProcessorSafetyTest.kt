package com.audiophile.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
class SpatialDspAudioProcessorSafetyTest {

    @Test
    fun queueInput_withUnsupportedFormat_bypassesInsteadOfRejectingPlayback() {
        val processor = SpatialDspAudioProcessor()
        processor.config = ImmersiveAudioConfig(enabled = true)
        processor.configure(
            AudioProcessor.AudioFormat(
                44_100,
                1,
                C.ENCODING_PCM_16BIT
            )
        )

        val input = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        input.putShort(1_000)
        input.flip()

        processor.queueInput(input)

        assertEquals(input.limit(), input.position())
    }
}
*/
