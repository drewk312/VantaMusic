@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.audiophile.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.decoder.iamf.IamfLibrary
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class IamfPlaybackTest {
    @Test fun bundledBinauralDecoderPlaysMultichannelIamfAndSeeks() = exercise(true)
    @Test fun bundledBinauralDecoderProducesAudioFromStart() = exercise(false)
    @Test fun ambisonicRecordingUsesBinauralRenderer() = exercise(false, "ambisonic-binaural.mp4")
    private fun exercise(seek: Boolean, asset: String = "multichannel-opus.mp4") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Native IAMF library must be packaged", IamfLibrary.isAvailable())
        val file = java.io.File(context.cacheDir, "iamf-device-test.mp4")
        instrumentation.context.assets.open("iamf/$asset").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val bytes = AtomicLong()
        val nonZero = AtomicLong()
        val channels = AtomicLong()
        val pcm = object : BaseAudioProcessor() {
            override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
                channels.set(inputAudioFormat.channelCount.toLong())
                return inputAudioFormat
            }
            override fun queueInput(inputBuffer: ByteBuffer) {
                if (!inputBuffer.hasRemaining()) return
                bytes.addAndGet(inputBuffer.remaining().toLong())
                val view = inputBuffer.duplicate()
                while (view.hasRemaining()) if (view.get().toInt() != 0) nonZero.incrementAndGet()
                replaceOutputBuffer(inputBuffer.remaining()).apply { put(inputBuffer); flip() }
            }
        }
        val ready = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException>()
        lateinit var player: ExoPlayer
        var created = false
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context, VantaSpatialRenderersFactory(context, iamfProcessors = arrayOf(pcm))).build()
                created = true
                // A quiet test level leaves the user's system volume setting unchanged.
                player.volume = 0.08f
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready.countDown()
                        if (state == Player.STATE_ENDED) ended.countDown()
                    }
                    override fun onPlayerError(error: PlaybackException) { failure.set(error); ready.countDown(); ended.countDown() }
                })
                player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                player.prepare()
                player.play()
            }
            assertTrue("IAMF failed to become ready", ready.await(20, TimeUnit.SECONDS))
            assertNull(failure.get())
            instrumentation.runOnMainSync {
                assertTrue("IAMF container must expose a seek map", player.isCurrentMediaItemSeekable)
                if (seek) player.seekTo(2500)
            }
            assertTrue("IAMF failed to finish after seek", ended.await(20, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertEquals("Binaural output must be two channels", 2, channels.get().toInt())
            assertTrue("Must decode more than two seconds of PCM", bytes.get() > 384000)
            assertTrue("Decoded PCM must not be silent: bytes=${bytes.get()}, nonzero=${nonZero.get()}", nonZero.get() > 10000)
        } finally {
            instrumentation.runOnMainSync { if (created) player.release() }
            file.delete()
        }
    }
}
