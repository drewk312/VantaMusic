@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback.dsp

data class VantaEqualizerConfig(
    val eqEnabled: Boolean = false,
    val eqBands: List<Float> = List(BAND_COUNT) { 0f },
    val eqBypassEnabled: Boolean = false,
    val loudnessNormalizationEnabled: Boolean = false,
    val replayGainDb: Float = 0f,
    val autoHeadroomEnabled: Boolean = true,
    val spatialEnabled: Boolean = false,
    val immersiveMode: VantaImmersiveMode = VantaImmersiveMode.OFF,
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

        /** Bundled impulse responses shipped in assets for the convolver. */
        val BUNDLED_IR_PACK = mapOf(
            "small_club" to "ir/small_club.wav",
            "medium_hall" to "ir/medium_hall.wav",
            "large_hall" to "ir/large_hall.wav",
            "plate" to "ir/plate.wav",
            "studio_a" to "ir/studio_a.wav",
            "vintage_room" to "ir/vintage_room.wav"
        )
    }
}

enum class VantaImmersiveMode(val label: String, val badge: String, val isRendered: Boolean) {
    OFF("Off", "Stereo", true),
    STEREO("Stereo Widened", "Rendered", true),
    RENDERED("Immersive Rendered", "Rendered", true),
    NATIVE_ATMOS("Dolby Atmos", "Verified Atmos", false);
}

enum class VantaEqualizerPreset(val label: String, val gains: List<Float>) {
    FLAT("Flat", List(31) { 0f }),
    BASS_BOOST("Bass Boost", listOf(3f,3f,3f,3f,3f,2.5f,2f,1.5f,1f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    DEEP_BASS("Deep Bass", listOf(4f,4f,4f,3.5f,3.5f,3f,2.5f,2f,1.5f,1f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    TREBLE_BOOST("Treble Boost", listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0.5f,1f,1.5f,2f,2.5f,3f,3f,3f,3f,3f,3f,3f)),
    STUDIO("Studio", listOf(0f,0f,0f,0f,0f,0f,0f,1f,1f,1.5f,1.5f,1f,1f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    CONCERT_HALL("Concert Hall", listOf(1f,1f,1f,1f,1f,0.5f,0.5f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0.5f,0.5f,0.5f,1f,1f,1f,1f,1f,1f)),
    CINEMA("Cinema", listOf(1f,1f,1f,1f,1f,1f,1f,0.5f,0.5f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0.5f,1f,1.5f,2f)),
    INTIMATE("Intimate", listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0.5f,1f,1.5f,2f,2f,2f,1.5f,1f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    VOCAL("Intimate Vocal", listOf(0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0.5f,1f,1.5f,2f,2.5f,2f,1.5f,1f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f)),
    ELECTRONIC("Electronic Pulse", listOf(2f,2f,2f,1.5f,1.5f,1f,1f,0.5f,0.5f,0f,-0.5f,-0.5f,0f,0f,0f,0f,0f,0f,0.5f,1f,1.5f,2f,2f,2f,1.5f,1f,1f,0.5f,0.5f,0f,0f)),
    HIP_HOP("Hip-Hop", listOf(2.5f,2.5f,2.5f,2f,2f,1.5f,1.5f,1f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1f,1f,0.5f,0.5f,0f,0f,0f,0f,0f)),
    ROCK("Rock", listOf(2f,2f,2f,1.5f,1.5f,1f,1f,0.5f,0f,0f,-0.5f,-0.5f,-0.5f,0f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1.5f,1.5f,1.5f,1.5f,1.5f,1f,1f,1f,1f)),
    JAZZ("Jazz Lounge", listOf(1f,1f,1f,1f,0.5f,0.5f,0.5f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1f,1f,0.5f,0.5f,0f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1f,0.5f,0.5f)),
    CLASSICAL("Classical", listOf(1.5f,1.5f,1.5f,1f,1f,0.5f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,-0.5f,-0.5f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1.5f,1.5f,1.5f,1f,1f)),
    RNB("Velvet R&B", listOf(2f,2f,2f,1.5f,1.5f,1f,1f,0.5f,0.5f,0f,0f,0f,0.5f,0.5f,1f,1f,1f,0.5f,0.5f,0f,0f,0f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0f,0f,0f)),
    ACOUSTIC("Acoustic", listOf(1f,1f,1f,1f,0.5f,0.5f,0.5f,0.5f,0f,0f,0f,0f,0.5f,0.5f,0.5f,0.5f,1f,1f,1f,0.5f,0.5f,0.5f,1f,1f,1f,1f,1f,0.5f,0.5f,0.5f,0.5f)),
    PODCAST("Podcast Voice", listOf(-2f,-2f,-2f,-1.5f,-1.5f,-1f,-1f,-0.5f,0f,0f,0.5f,1f,1.5f,2f,2f,2f,2f,2f,1.5f,1.5f,1f,1f,0.5f,0.5f,0f,0f,-0.5f,-0.5f,-1f,-1f,-1.5f)),
    LOFI("Lo-Fi Cozy", listOf(1.5f,1.5f,1.5f,1.5f,1f,1f,1f,0.5f,0.5f,0.5f,0.5f,0f,0f,0f,0f,0f,0f,0f,-0.5f,-0.5f,-1f,-1f,-1.5f,-1.5f,-2f,-2f,-2.5f,-2.5f,-3f,-3.5f,-4f)),
    WARM("Warm Analog", listOf(1.5f,1.5f,1.5f,1f,1f,1f,0.5f,0.5f,0.5f,0.5f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-1f,-1f)),
    LOUDNESS("Loudness", listOf(3f,3f,2.5f,2.5f,2f,2f,1.5f,1f,0.5f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1.5f,1.5f,2f,2f,2.5f,2.5f,2.5f)),
}

object VantaEqualizerHolder {
    @Volatile
    var processor: VantaEqualizerProcessor? = null
}
