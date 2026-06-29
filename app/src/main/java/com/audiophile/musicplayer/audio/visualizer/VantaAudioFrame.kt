package com.audiophile.musicplayer.audio.visualizer

data class VantaAudioFrame(
    val bassEnergy: Float = 0f,
    val midEnergy: Float = 0f,
    val trebleEnergy: Float = 0f,
    val rms: Float = 0f,
    val peak: Float = 0f,
    val fftBuckets: List<Float> = emptyList(),
    val timestampMs: Long = 0L,
    val isLiveAudio: Boolean = false,
    val isBeat: Boolean = false,
    val beatIntensity: Float = 0f,
    val transientEnergy: Float = 0f
)
