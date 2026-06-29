package com.audiophile.desktop

import androidx.compose.runtime.*

// ─────────────────────────────────────────────────────────────────────────────
//  DspState — singleton-style holder for all DSP parameters.
//  Uses Compose's `mutableStateOf` so any composable reading these fields will
//  automatically recompose when they change.
// ─────────────────────────────────────────────────────────────────────────────

object DspState {

    // ── Panel visibility ───────────────────────────────────────────────────
    var panelOpen: Boolean by mutableStateOf(false)

    // ── Active preset label ────────────────────────────────────────────────
    var activePreset: String by mutableStateOf("Flat")

    // ── Stereo Widener ─────────────────────────────────────────────────────
    var widenerEnabled: Boolean by mutableStateOf(false)
    /** 0.5 = narrowed, 1.0 = unchanged, 2.0 = very wide */
    var widthFactor: Float by mutableStateOf(1.0f)

    // ── Concert Hall Reverb ────────────────────────────────────────────────
    var reverbEnabled: Boolean by mutableStateOf(false)
    /** 0.0 = tiny room, 1.0 = massive hall */
    var reverbRoomSize: Float by mutableStateOf(0.5f)
    /** 0.0 = bright, 1.0 = dark/warm */
    var reverbDamping: Float by mutableStateOf(0.5f)
    /** 0.0 = dry, 1.0 = fully wet */
    var reverbWetDry: Float by mutableStateOf(0.33f)

    // ── Crossfeed (Headphone Surround) ─────────────────────────────────────
    var crossfeedEnabled: Boolean by mutableStateOf(false)
    /** 0.0 = no bleed, 1.0 = maximum crossfeed */
    var crossfeedLevel: Float by mutableStateOf(0.3f)
    /** Delay in milliseconds (0.1 – 2.0 ms, simulates head width) */
    var crossfeedDelayMs: Float by mutableStateOf(0.3f)

    // ── HRTF Elevation Preset ──────────────────────────────────────────────
    var hrtfEnabled: Boolean by mutableStateOf(false)
    /** Index into HrtfPreset.entries */
    var hrtfPresetIndex: Int by mutableStateOf(0)

    // ── Convenience: apply a named preset ─────────────────────────────────
    fun applyPreset(preset: DspPreset) {
        activePreset = preset.label

        widenerEnabled  = preset.widenerEnabled
        widthFactor     = preset.widthFactor

        reverbEnabled   = preset.reverbEnabled
        reverbRoomSize  = preset.reverbRoomSize
        reverbDamping   = preset.reverbDamping
        reverbWetDry    = preset.reverbWetDry

        crossfeedEnabled  = preset.crossfeedEnabled
        crossfeedLevel    = preset.crossfeedLevel
        crossfeedDelayMs  = preset.crossfeedDelayMs

        hrtfEnabled     = preset.hrtfEnabled
        hrtfPresetIndex = preset.hrtfPresetIndex
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DspPreset — immutable snapshot of a full DSP configuration
// ─────────────────────────────────────────────────────────────────────────────

data class DspPreset(
    val label: String,
    val widenerEnabled: Boolean = false,
    val widthFactor: Float = 1.0f,
    val reverbEnabled: Boolean = false,
    val reverbRoomSize: Float = 0.5f,
    val reverbDamping: Float = 0.5f,
    val reverbWetDry: Float = 0.33f,
    val crossfeedEnabled: Boolean = false,
    val crossfeedLevel: Float = 0.3f,
    val crossfeedDelayMs: Float = 0.3f,
    val hrtfEnabled: Boolean = false,
    val hrtfPresetIndex: Int = 0
)

val DspPresets = listOf(
    DspPreset(label = "Flat"),
    DspPreset(
        label = "Concert Hall",
        reverbEnabled = true, reverbRoomSize = 0.82f, reverbDamping = 0.4f, reverbWetDry = 0.45f,
        widenerEnabled = true, widthFactor = 1.35f
    ),
    DspPreset(
        label = "Viper Wide",
        widenerEnabled = true, widthFactor = 1.8f,
        crossfeedEnabled = true, crossfeedLevel = 0.25f, crossfeedDelayMs = 0.3f
    ),
    DspPreset(
        label = "Bedroom",
        reverbEnabled = true, reverbRoomSize = 0.25f, reverbDamping = 0.7f, reverbWetDry = 0.2f,
        crossfeedEnabled = true, crossfeedLevel = 0.35f, crossfeedDelayMs = 0.4f
    ),
    DspPreset(
        label = "Cinema",
        reverbEnabled = true, reverbRoomSize = 0.75f, reverbDamping = 0.3f, reverbWetDry = 0.4f,
        widenerEnabled = true, widthFactor = 1.5f,
        hrtfEnabled = true, hrtfPresetIndex = 1
    )
)

// ─────────────────────────────────────────────────────────────────────────────
//  HRTF elevation presets (simplified shelf-EQ descriptions used by CrossfeedFilter)
// ─────────────────────────────────────────────────────────────────────────────

enum class HrtfPreset(val label: String, val highShelfGain: Float, val lowShelfGain: Float) {
    FLAT   ("Flat",    0.0f,  0.0f),
    ABOVE  ("Above",   3.0f, -1.5f),
    BEHIND ("Behind", -2.0f,  2.0f),
    FRONT  ("Front",   1.5f,  1.0f)
}
