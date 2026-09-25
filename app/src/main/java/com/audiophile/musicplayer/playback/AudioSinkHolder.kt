@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import androidx.media3.exoplayer.audio.AudioSink

/**
 * Holds the live DefaultAudioSink built by [VantaSpatialRenderersFactory] so route
 * controllers (output picker) can re-apply a stored selection whenever the sink or
 * the audio route changes.
 */
object AudioSinkHolder {
    @Volatile
    var primarySink: AudioSink? = null
        private set

    fun register(sink: AudioSink) {
        primarySink = sink
    }
}