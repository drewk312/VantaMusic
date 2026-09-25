@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AtmosDecoderProbeTest {
    @Test
    fun compareDolbyMimeConfigurationsAndVerifyFlac() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.filesDir, "vanta-atmos-test.mp4")
        assumeTrue("Requires the existing Atmos test file", file.length() == 6_895_915L)
        assumeTrue("Requires the generated FLAC fixture", File(context.filesDir, "vanta-flac-test.flac").isFile)
        for (mode in listOf("joc", "eac3", "flac")) {
            val finished = CountDownLatch(1)
            var player: ExoPlayer? = null
            instrumentation.runOnMainSync {
                val delegate = MediaCodecAdapter.Factory.getDefault(context)
                val factory = object : DefaultRenderersFactory(context) {
                    override fun getCodecAdapterFactory() = MediaCodecAdapter.Factory { configuration ->
                        if (mode == "eac3") configuration.mediaFormat.setString("mime", "audio/eac3")
                        delegate.createAdapter(configuration)
                    }
                }.setEnableAudioFloatOutput(false)
                player = ExoPlayer.Builder(context, factory).build().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                Log.i("VANTA_ATMOS_PROBE", "mode=$mode playing=true format=$audioFormat")
                            }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            Log.i("VANTA_ATMOS_PROBE", "mode=$mode error=${error.errorCodeName}")
                            finished.countDown()
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) finished.countDown()
                        }
                    })
                    val selected = if (mode == "flac") File(context.filesDir, "vanta-flac-test.flac") else file
                    setMediaItem(MediaItem.fromUri(selected.toURI().toString()))
                    prepare()
                    play()
                }
            }
            val completed = finished.await(15, TimeUnit.SECONDS)
            instrumentation.runOnMainSync {
                Log.i("VANTA_ATMOS_PROBE", "mode=$mode completed=$completed state=${player?.playbackState} position=${player?.currentPosition}")
                val playedToEnd = completed && player?.playbackState == Player.STATE_ENDED &&
                    player.currentPosition >= 7_900L
                player?.release()
                if (mode == "flac") assertTrue("FLAC must play to completion", playedToEnd)
            }
        }
    }
}
