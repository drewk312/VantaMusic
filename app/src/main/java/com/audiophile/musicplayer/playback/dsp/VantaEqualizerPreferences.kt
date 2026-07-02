package com.audiophile.musicplayer.playback.dsp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class VantaEqualizerPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("vanta_equalizer", Context.MODE_PRIVATE)

    fun load(): VantaEqualizerConfig {
        return VantaEqualizerConfig(
            eqEnabled = prefs.getBoolean(PREF_EQ_ENABLED, false),
            eqBands = (0 until VantaEqualizerConfig.BAND_COUNT).map { i ->
                prefs.getFloat("eq_band_${i}_gain", 0f).coerceIn(
                    VantaEqualizerConfig.MIN_GAIN_DB.toFloat(),
                    VantaEqualizerConfig.MAX_GAIN_DB.toFloat()
                )
            },
            spatialEnabled = prefs.getBoolean(PREF_SPATIAL_ENABLED, false),
            stereoWidenLevel = prefs.getFloat(PREF_STEREO_WIDEN, 0f).coerceIn(0f, 1f),
            crossfeedEnabled = prefs.getBoolean(PREF_CROSSFEED_ENABLED, false),
            crossfeedMode = prefs.getInt(PREF_CROSSFEED_MODE, 0),
            reverbEnabled = prefs.getBoolean(PREF_REVERB_ENABLED, false),
            reverbPreset = prefs.getInt(PREF_REVERB_PRESET, 0),
            convolverEnabled = prefs.getBoolean(PREF_CONVOLVER_ENABLED, false),
            convolverIrAssetPath = prefs.getString(PREF_CONVOLVER_IR_PATH, null),
            tubeEnabled = prefs.getBoolean(PREF_TUBE_ENABLED, false),
            tubeDrive = prefs.getFloat(PREF_TUBE_DRIVE, 0.5f).coerceIn(0f, 1f),
            bassCannonEnabled = prefs.getBoolean(PREF_BASS_CANNON_ENABLED, false),
            bassCannonAmount = prefs.getFloat(PREF_BASS_CANNON_AMOUNT, 0.5f).coerceIn(0f, 1f),
            trebleEnabled = prefs.getBoolean(PREF_TREBLE_ENABLED, false),
            trebleBoostAmount = prefs.getFloat(PREF_TREBLE_AMOUNT, 0.5f).coerceIn(0f, 1f),
            autoEqEnabled = prefs.getBoolean(PREF_AUTO_EQ_ENABLED, false),
            autoEqProfileName = prefs.getString(PREF_AUTO_EQ_PROFILE, null),
            limiterEnabled = prefs.getBoolean(PREF_LIMITER_ENABLED, true),
            preset = loadPreset(),
        )
    }

    fun save(config: VantaEqualizerConfig) {
        prefs.edit {
                putBoolean(PREF_EQ_ENABLED, config.eqEnabled)
                putBoolean(PREF_SPATIAL_ENABLED, config.spatialEnabled)
                putFloat(PREF_STEREO_WIDEN, config.stereoWidenLevel)
                putBoolean(PREF_CROSSFEED_ENABLED, config.crossfeedEnabled)
                putInt(PREF_CROSSFEED_MODE, config.crossfeedMode)
                putBoolean(PREF_REVERB_ENABLED, config.reverbEnabled)
                putInt(PREF_REVERB_PRESET, config.reverbPreset)
                putBoolean(PREF_CONVOLVER_ENABLED, config.convolverEnabled)
                putString(PREF_CONVOLVER_IR_PATH, config.convolverIrAssetPath)
                putBoolean(PREF_TUBE_ENABLED, config.tubeEnabled)
                putFloat(PREF_TUBE_DRIVE, config.tubeDrive)
                putBoolean(PREF_BASS_CANNON_ENABLED, config.bassCannonEnabled)
                putFloat(PREF_BASS_CANNON_AMOUNT, config.bassCannonAmount)
                putBoolean(PREF_TREBLE_ENABLED, config.trebleEnabled)
                putFloat(PREF_TREBLE_AMOUNT, config.trebleBoostAmount)
                putBoolean(PREF_AUTO_EQ_ENABLED, config.autoEqEnabled)
                putString(PREF_AUTO_EQ_PROFILE, config.autoEqProfileName)
                putBoolean(PREF_LIMITER_ENABLED, config.limiterEnabled)
                putString(PREF_PRESET, config.preset.name)
            }
        config.eqBands.forEachIndexed { i, gain ->
            prefs.edit {
                    putFloat("eq_band_${i}_gain", gain)
                }
        }
    }

    fun saveEqBand(index: Int, gainDb: Float) {
        prefs.edit {
                putFloat("eq_band_${index}_gain", gainDb.coerceIn(
            VantaEqualizerConfig.MIN_GAIN_DB.toFloat(),
            VantaEqualizerConfig.MAX_GAIN_DB.toFloat()
        ))
            }
    }

    fun saveEqEnabled(enabled: Boolean) {
        prefs.edit {
                putBoolean(PREF_EQ_ENABLED, enabled)
            }
    }

    fun saveSpatialEnabled(enabled: Boolean) {
        prefs.edit {
                putBoolean(PREF_SPATIAL_ENABLED, enabled)
            }
    }

    fun saveBassCannon(enabled: Boolean, amount: Float = 0.5f) {
        prefs.edit {
                putBoolean(PREF_BASS_CANNON_ENABLED, enabled)
                putFloat(PREF_BASS_CANNON_AMOUNT, amount)
            }
    }

    fun saveTube(enabled: Boolean, drive: Float = 0.5f) {
        prefs.edit {
                putBoolean(PREF_TUBE_ENABLED, enabled)
                putFloat(PREF_TUBE_DRIVE, drive)
            }
    }

    fun saveConvolver(enabled: Boolean, irPath: String? = null) {
        prefs.edit {
                putBoolean(PREF_CONVOLVER_ENABLED, enabled)
                putString(PREF_CONVOLVER_IR_PATH, irPath)
            }
    }

    fun saveAutoEq(enabled: Boolean, profileName: String? = null) {
        prefs.edit {
                putBoolean(PREF_AUTO_EQ_ENABLED, enabled)
                putString(PREF_AUTO_EQ_PROFILE, profileName)
            }
    }

    private fun loadPreset(): VantaEqualizerPreset {
        val stored = prefs.getString(PREF_PRESET, null)
        if (stored != null) {
            return runCatching { VantaEqualizerPreset.valueOf(stored) }
                .getOrDefault(VantaEqualizerPreset.FLAT)
        }
        return VantaEqualizerPreset.FLAT
    }

    fun lyricsPipelineLeadMs(): Long {
        val config = load()
        if (!config.spatialEnabled) return 0L
        return (config.stereoWidenLevel * 8f + if (config.reverbEnabled) 30f else 0f).toLong().coerceIn(0L, 100L)
    }

    companion object {
        private const val PREF_EQ_ENABLED = "eq_enabled"
        private const val PREF_SPATIAL_ENABLED = "spatial_enabled"
        private const val PREF_STEREO_WIDEN = "stereo_widen"
        private const val PREF_CROSSFEED_ENABLED = "crossfeed_enabled"
        private const val PREF_CROSSFEED_MODE = "crossfeed_mode"
        private const val PREF_REVERB_ENABLED = "reverb_enabled"
        private const val PREF_REVERB_PRESET = "reverb_preset"
        private const val PREF_CONVOLVER_ENABLED = "convolver_enabled"
        private const val PREF_CONVOLVER_IR_PATH = "convolver_ir_path"
        private const val PREF_TUBE_ENABLED = "tube_enabled"
        private const val PREF_TUBE_DRIVE = "tube_drive"
        private const val PREF_BASS_CANNON_ENABLED = "bass_cannon_enabled"
        private const val PREF_BASS_CANNON_AMOUNT = "bass_cannon_amount"
        private const val PREF_TREBLE_ENABLED = "treble_enabled"
        private const val PREF_TREBLE_AMOUNT = "treble_amount"
        private const val PREF_AUTO_EQ_ENABLED = "auto_eq_enabled"
        private const val PREF_AUTO_EQ_PROFILE = "auto_eq_profile"
        private const val PREF_LIMITER_ENABLED = "limiter_enabled"
        private const val PREF_PRESET = "equalizer_preset"
    }
}