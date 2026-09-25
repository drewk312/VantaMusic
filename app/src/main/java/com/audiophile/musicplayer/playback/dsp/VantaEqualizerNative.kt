package com.audiophile.musicplayer.playback.dsp

import android.util.Log

class VantaEqualizerNative private constructor(private val handle: Long) : VantaDspEngine {

    fun setSampleRate(sampleRate: Float, forceRefresh: Boolean = false) {
        nativeSetSampleRate(handle, sampleRate, forceRefresh)
    }

    fun ensureBlockSize(blockSize: Int) {
        nativeEnsureBlockSize(handle, blockSize)
    }

    override fun applyConfig(config: VantaEqualizerConfig) {
        nativeConfigureSpatial(
            handle,
            config.spatialEnabled,
            config.stereoWidenLevel,
            config.crossfeedEnabled,
            config.crossfeedMode,
            config.reverbEnabled,
            config.reverbPreset
        )
        if (config.eqEnabled && config.eqBands.size == VantaEqualizerConfig.BAND_COUNT) {
            val freqAxis = VantaEqualizerConfig.EQ_FREQUENCIES
            val gainDb = config.eqBands.map { it.toDouble() }.toDoubleArray()
            nativeConfigureEQ(handle, freqAxis, gainDb, true)
        } else {
            nativeConfigureEQ(handle, doubleArrayOf(), doubleArrayOf(), false)
        }
        nativeConfigureBassBoost(handle, config.bassCannonEnabled, config.bassCannonAmount.toDouble())
        nativeConfigureTube(handle, config.tubeEnabled, config.tubeDrive.toDouble())
        nativeConfigureAutoEq(handle, config.autoEqEnabled, config.autoEqProfileName ?: "")
        nativeConfigureLimiter(handle, config.limiterEnabled)
    }

    fun applyConvolver(enable: Boolean, irAssetPath: String?) {
        nativeConfigureConvolver(handle, enable, irAssetPath ?: "")
    }

    override fun getSpectrumMagnitudes(): FloatArray? {
        return nativeGetSpectrum(handle)
    }

    override fun processDeinterleaved(left: FloatArray, right: FloatArray, offset: Int, frameCount: Int) {
        nativeProcessDeinterleaved(handle, left, right, offset, frameCount)
    }

    override fun destroy() {
        if (handle != 0L) {
            nativeDestroy(handle)
        }
    }

    companion object {
        private const val TAG = "VantaEqualizerNative"
        private var libraryLoaded = false

        val isAvailable: Boolean = run {
            try {
                System.loadLibrary("vanta_james_dsp")
                libraryLoaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native equalizer library unavailable", e)
                false
            }
        }

        fun create(blockSize: Int, sampleRate: Float): VantaEqualizerNative? {
            if (!libraryLoaded) return null
            val handle = nativeCreate(blockSize, sampleRate)
            if (handle == 0L) return null
            return VantaEqualizerNative(handle)
        }

        @JvmStatic private external fun nativeCreate(blockSize: Int, sampleRate: Float): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeSetSampleRate(handle: Long, sampleRate: Float, forceRefresh: Boolean)
        @JvmStatic private external fun nativeEnsureBlockSize(handle: Long, blockSize: Int)
        @JvmStatic private external fun nativeConfigureSpatial(
            handle: Long,
            spatialEnabled: Boolean,
            stereoWidenLevel: Float,
            crossfeedEnabled: Boolean,
            crossfeedMode: Int,
            reverbEnabled: Boolean,
            reverbPreset: Int
        )
        @JvmStatic private external fun nativeConfigureEQ(
            handle: Long,
            freqAxis: DoubleArray,
            gainDb: DoubleArray,
            enable: Boolean
        )
        @JvmStatic private external fun nativeConfigureBassBoost(
            handle: Long, enabled: Boolean, amount: Double
        )
        @JvmStatic private external fun nativeConfigureTube(
            handle: Long, enabled: Boolean, drive: Double
        )
        @JvmStatic private external fun nativeConfigureAutoEq(
            handle: Long, enabled: Boolean, profileData: String
        )
        @JvmStatic private external fun nativeConfigureLimiter(
            handle: Long, enabled: Boolean
        )
        @JvmStatic private external fun nativeConfigureConvolver(
            handle: Long, enabled: Boolean, irAssetPath: String
        )
        @JvmStatic private external fun nativeGetSpectrum(handle: Long): FloatArray?
        @JvmStatic private external fun nativeProcessDeinterleaved(
            handle: Long, left: FloatArray, right: FloatArray, offset: Int, frameCount: Int
        )
    }
}
