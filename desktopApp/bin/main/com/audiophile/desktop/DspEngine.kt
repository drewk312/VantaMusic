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

// ─────────────────────────────────────────────────────────────────────────────
//  ── CONNECT YOUR AUDIO PIPELINE HERE ─────────────────────────────────────────
//
//  Example skeleton (javax.sound.sampled):
//
//    val engine = DspEngine(sampleRate = 44100)
//    val format = AudioFormat(44100f, 16, 2, true, false)
//    val line   = AudioSystem.getSourceDataLine(format)
//    line.open(format, 4096)
//    line.start()
//
//    val pcm   = ShortArray(2048)   // interleaved L/R 16-bit samples from your decoder
//    val left  = FloatArray(1024)
//    val right = FloatArray(1024)
//
//    // Deinterleave → float
//    for (i in left.indices) {
//        left[i]  = pcm[i * 2    ] / 32768f
//        right[i] = pcm[i * 2 + 1] / 32768f
//    }
//
//    engine.process(left, right)    // <─ apply all DSP here
//
//    // Interleave → short → write
//    for (i in left.indices) {
//        val li = (left[i]  * 32767f).toInt().coerceIn(-32768, 32767).toShort()
//        val ri = (right[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
//        pcm[i * 2    ] = li
//        pcm[i * 2 + 1] = ri
//    }
//    val bytes = ByteArray(pcm.size * 2) { i ->
//        val s = pcm[i / 2]; if (i % 2 == 0) s.toByte() else (s.toInt() shr 8).toByte()
//    }
//    line.write(bytes, 0, bytes.size)
//
// ─────────────────────────────────────────────────────────────────────────────
