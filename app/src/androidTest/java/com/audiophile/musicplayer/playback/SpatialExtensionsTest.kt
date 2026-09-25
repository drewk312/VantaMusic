@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.audiophile.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class SpatialExtensionsTest {
    @Test fun fraunhoferHeadphonesPlayAndSeek() = exercise(2, "mpeg-h.mp4")
    @Test fun ittiamHeadphonesPlayAndSeek() = exercise(1, "mpeg-h.mp4")
    @Test fun openJocHeadphonesPlayAndSeek() = exercise(0, "joc.mp4")
    @Test fun integratedMpeghPlayAndSeek() = exercise(3, "mpeg-h.mp4")
    @Test fun integratedJocPlayAndSeek() = exercise(3, "joc.mp4")
    private fun exercise(mode: Int, asset: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Native Spatial library must be packaged", com.audiophile.musicplayer.playback.spatial.NativeSpatial.isAvailable())
        val file = java.io.File(context.cacheDir, "spatial-device-test.mp4")
        instrumentation.context.assets.open("spatial/$asset").use { input ->
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
                val factory = androidx.media3.exoplayer.RenderersFactory { handler, _, audio, _, _ ->
                    val sink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(pcm)).build()
                    val renderer: androidx.media3.exoplayer.Renderer = if (mode == 2)
                        androidx.media3.decoder.mpegh.MpeghAudioRenderer(handler, audio, sink)
                    else com.audiophile.musicplayer.playback.spatial.SpatialAudioRenderer(mode, handler, audio, sink)
                    arrayOf(renderer)
                }
                player = ExoPlayer.Builder(context, if (mode == 3) VantaSpatialRenderersFactory(context, iamfProcessors = arrayOf(pcm)) else factory).build()
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
            assertTrue("Spatial failed to become ready", ready.await(20, TimeUnit.SECONDS))
            assertNull(failure.get()?.stackTraceToString(), failure.get())
            instrumentation.runOnMainSync {
                assertTrue("Spatial container must expose a seek map", player.isCurrentMediaItemSeekable)
                player.seekTo(2500)
            }
            assertTrue("Spatial failed to finish after seek", ended.await(90, TimeUnit.SECONDS))
            assertNull(failure.get()?.stackTraceToString(), failure.get())
            assertEquals("Binaural output must be two channels", 2, channels.get().toInt())
            assertTrue("Must decode more than two seconds of PCM", bytes.get() > 384000)
            assertTrue("Decoded PCM must not be silent: bytes=${bytes.get()}, nonzero=${nonZero.get()}", nonZero.get() > 10000)
        } finally {
            instrumentation.runOnMainSync { if (created) player.release() }
            file.delete()
        }
    }
}
