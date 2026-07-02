@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback.dsp

data class VantaEqualizerConfig(
    val eqEnabled: Boolean = false,
    val eqBands: List<Float> = List(BAND_COUNT) { 0f },
    val spatialEnabled: Boolean = false,
    val stereoWidenLevel: Float = 0f,
    val crossfeedEnabled: Boolean = false,
    val crossfeedMode: Int = 0,
    val reverbEnabled: Boolean = false,
    val reverbPreset: Int = 0,
    val convolverEnabled: Boolean = false,
    val convolverIrAssetPath: String? = null,
    val tubeEnabled: Boolean = false,
    val tubeDrive: Float = 0.5f,
    val bassCannonEnabled: Boolean = false,
    val bassCannonAmount: Float = 0.5f,
    val trebleEnabled: Boolean = false,
    val trebleBoostAmount: Float = 0.5f,
    val autoEqEnabled: Boolean = false,
    val autoEqProfileName: String? = null,
    val limiterEnabled: Boolean = true,
    val preset: VantaEqualizerPreset = VantaEqualizerPreset.FLAT,
) {
    companion object {
        val EQ_FREQUENCIES = doubleArrayOf(
            20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0,
            125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
            800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0,
            5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0
        )
        const val BAND_COUNT = 31
        const val MIN_GAIN_DB = -12.0
        const val MAX_GAIN_DB = 12.0
    }
}

enum class VantaEqualizerPreset(val label: String, val gains: List<Float>) {
    FLAT("Flat", List(31) { 0f }),
    BASS_BOOST("Bass Boost", listOf(6f,6f,6f,6f,5f,5f,4f,3f,2f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    TREBLE_BOOST("Treble Boost", listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,1f,2f,3f,4f,5f,6f,6f,6f,6f,6f,6f)),
    STUDIO("Studio", listOf(0f,0f,0f,0f,0f,0f,0f,2f,2f,3f,3f,2f,2f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    CONCERT_HALL("Concert Hall", listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    CINEMA("Cinema", listOf(2f,2f,2f,2f,2f,2f,2f,1f,1f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,1f,2f,3f,4f)),
    INTIMATE("Intimate", listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,1f,2f,3f,4f,4f,4f,3f,2f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    VOCAL("Intimate Vocal", listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,1f,2f,3f,4f,5f,4f,3f,2f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    ELECTRONIC("Electronic Pulse", listOf(4f,4f,4f,3f,3f,2f,2f,1f,1f,0f,-1f,-1f,0f,0f,0f,0f,0f,0f,1f,2f,3f,4f,4f,4f,3f,2f,2f,1f,1f,0f,0f)),
}

object VantaEqualizerHolder {
    @Volatile
    var processor: VantaEqualizerProcessor? = null
}
