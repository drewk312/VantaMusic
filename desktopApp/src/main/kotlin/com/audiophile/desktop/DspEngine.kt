package com.audiophile.desktop

// ─────────────────────────────────────────────────────────────────────────────
//  DspEngine — Chains StereoWidener → ReverbProcessor → CrossfeedFilter
//              in a single pass per audio buffer.
//
//  Usage (audio thread, NOT the UI thread):
//
//    // At startup / sample-rate known:
//    val engine = DspEngine(sampleRate = 44100)
//
//    // In your audio callback (e.g. javax.sound.sampled write loop):
//    engine.process(leftSamples, rightSamples)
//
//  The engine reads all parameters directly from DspState, so the UI can
//  change sliders in real-time and the next buffer call picks them up
//  automatically.  No locking needed — float reads/writes are atomic on JVM.
// ─────────────────────────────────────────────────────────────────────────────

class DspEngine(sampleRate: Int = 44100) {

    private val reverb    = ReverbProcessor(sampleRate)
    private val crossfeed = CrossfeedFilter(sampleRate)

    /**
     * Processes [left] and [right] PCM float buffers in-place.
     * Call this on every audio buffer before writing to the output device.
     *
     * Effects are applied in this order:
     *  1. Stereo Widener  (M/S)
     *  2. Concert Hall Reverb  (Freeverb)
     *  3. Crossfeed + HRTF
     */
    fun process(left: FloatArray, right: FloatArray) {
        val state = DspState

        // ── 1. Stereo Widener ──────────────────────────────────────────────
        if (state.widenerEnabled) {
            StereoWidener.process(left, right, state.widthFactor)
        }

        // ── 2. Concert Hall Reverb ─────────────────────────────────────────
        if (state.reverbEnabled) {
            reverb.process(
                left, right,
                roomSize = state.reverbRoomSize,
                damping  = state.reverbDamping,
                wetDry   = state.reverbWetDry
            )
        }

        // ── 3. Crossfeed / HRTF ────────────────────────────────────────────
        if (state.crossfeedEnabled || state.hrtfEnabled) {
            val hrtfPreset = if (state.hrtfEnabled)
                HrtfPreset.entries[state.hrtfPresetIndex]
            else
                null

            crossfeed.process(
                left, right,
                feedLevel  = if (state.crossfeedEnabled) state.crossfeedLevel else 0f,
                delayMs    = state.crossfeedDelayMs,
                hrtfPreset = hrtfPreset
            )
        }
    }

    /** Reset all internal filter state — e.g. on track change or preset jump. */
    fun reset() {
        reverb.reset()
        crossfeed.reset()
    }
}
