@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.audiophile.musicplayer.playback

import android.media.MediaCodecList
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DolbyPlaybackTest {
    @Test fun dolbyRecordingSelectsSupportedAudioAndSeeksOnCurrentRoute() = exercise(false)
    @Test fun liveGatewayAutoQualityPlaysAndSeeks() = exercise(true)

    private fun exercise(live: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        SpatialDecoderCapabilities.initialize(context)
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
            .filter { it.supportedTypes.any { type -> type.contains("ac3") } }
            .joinToString { "${it.name}:${it.supportedTypes.joinToString()}" }
        Log.i("VANTA_DOLBY_TEST", "codecs=$codecs atmosOutput=${SpatialDecoderCapabilities.supportsAtmosOutput()} eac3=${SpatialDecoderCapabilities.supportsEac3()}")
        val file = java.io.File(context.cacheDir, "dolby-device-test.mp4")
        val resolved = if (live) kotlinx.coroutines.runBlocking {
            com.audiophile.musicplayer.data.source.CloudflareGatewaySource().resolvePlayback(
                "deezer:2801558052", SpatialDecoderCapabilities.playableQuality(com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.AUTO_SPATIAL))
        } else null
        if (live) assertTrue("Live gateway resolution must succeed", resolved is com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Ready)
        val stream = (resolved as? com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Ready)?.stream
        if (!live) instrumentation.context.assets.open("dolby/atmos-with-fallback.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val ready = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException>()
        lateinit var player: ExoPlayer
        var created = false
        try {
            instrumentation.runOnMainSync {
                val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(stream?.requestHeaders.orEmpty() + mapOf("Referer" to "https://vanta-music-gateway.16drewk.workers.dev/"))
                player = ExoPlayer.Builder(context, VantaSpatialRenderersFactory(context)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON))
                    .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        androidx.media3.datasource.DefaultDataSource.Factory(context, http))).build()
                created = true
                player.volume = 0.08f
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                    override fun onPlayerError(error: PlaybackException) { failure.set(error); ready.countDown() }
                })
                player.setMediaItem(MediaItem.fromUri(stream?.streamUrl ?: android.net.Uri.fromFile(file).toString()))
                player.prepare()
                player.play()
            }
            assertTrue("Dolby recording did not become ready", ready.await(20, TimeUnit.SECONDS))
            assertNull(failure.get())
            Thread.sleep(if (live) 12000 else 3500)
            instrumentation.runOnMainSync {
                assertTrue("Dolby must keep advancing beyond two seconds", player.currentPosition > 2500)
                assertTrue(player.isPlaying)
                assertTrue(player.isCurrentMediaItemSeekable)
                Log.i("VANTA_DOLBY_TEST", "mime=${player.audioFormat?.sampleMimeType} channels=${player.audioFormat?.channelCount} position=${player.currentPosition}")
                if (!SpatialDecoderCapabilities.supportsAtmosPlayback()) {
                    assertEquals("Unsupported JOC must select the playable lossless alternative", "audio/flac", player.audioFormat?.sampleMimeType)
                }
                player.seekTo(if (live) 30000 else 1000)
            }
            // Allow device audio routing to settle, then require sustained progress.
            repeat(10) { second ->
                Thread.sleep(1000)
                instrumentation.runOnMainSync {
                    Log.i("VANTA_DOLBY_TEST", "afterSeek seconds=${second + 1} position=${player.currentPosition} state=${player.playbackState} playing=${player.isPlaying}")
                }
            }
            instrumentation.runOnMainSync {
                assertTrue("Audio must continue after seeking", player.currentPosition > if (live) 36000 else 7000)
                assertTrue(player.isPlaying)
            }
            assertNull(failure.get())
        } finally {
            instrumentation.runOnMainSync { if (created) player.release() }
            file.delete()
        }
    }
}
