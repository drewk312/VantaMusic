package com.audiophile.desktop

import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  ReverbProcessor — Freeverb-style stereo reverb engine.
//
//  Architecture (per channel):
//    input → [8 parallel Comb filters] → sum → [4 serial Allpass filters] → output
//
//  The 8 comb filter delay lengths (in samples at 44100 Hz) are the classic
//  Freeverb tuning values, with a small stereo spread added to the right channel.
//
//  Call `reset()` whenever sample rate or room params change significantly to
//  avoid glitching artefacts.
// ─────────────────────────────────────────────────────────────────────────────

class ReverbProcessor(private val sampleRate: Int = 44100) {

    // ── Freeverb comb filter lengths (samples @ 44100 Hz) ─────────────────
    private val combLengthsL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val combLengthsR = intArrayOf(1139, 1211, 1300, 1379, 1445, 1514, 1580, 1640) // +23 spread

    // ── Freeverb allpass filter lengths ────────────────────────────────────
    private val allpassLengths = intArrayOf(556, 441, 341, 225)

    // Scaled for non-44100 sample rates
    private fun scaled(len: Int) = (len * sampleRate / 44100.0).roundToInt().coerceAtLeast(1)

    // ── Comb filter state ──────────────────────────────────────────────────
    private val combBufL = Array(8) { i -> FloatArray(scaled(combLengthsL[i])) }
    private val combBufR = Array(8) { i -> FloatArray(scaled(combLengthsR[i])) }
    private val combPosL = IntArray(8)
    private val combPosR = IntArray(8)
    private val combFilterL = FloatArray(8) // per-comb low-pass state
    private val combFilterR = FloatArray(8)

    // ── Allpass filter state ───────────────────────────────────────────────
    private val allpassBufL = Array(4) { i -> FloatArray(scaled(allpassLengths[i])) }
    private val allpassBufR = Array(4) { i -> FloatArray(scaled(allpassLengths[i])) }
    private val allpassPosL = IntArray(4)
    private val allpassPosR = IntArray(4)

    // ── Allpass feedback coefficient (fixed at Freeverb default) ──────────
    private val allpassFeedback = 0.5f

    // ── Process a stereo buffer ────────────────────────────────────────────

    /**
     * Applies Freeverb stereo reverb to [left] / [right] sample buffers.
     *
     * @param roomSize  0.0 (tiny) – 1.0 (massive hall).  Internally mapped to feedback range 0.70–0.98.
     * @param damping   0.0 (bright) – 1.0 (dark/warm).
     * @param wetDry    0.0 (dry) – 1.0 (fully wet).  Wet is blended additively.
     */
    fun process(
        left: FloatArray,
        right: FloatArray,
        roomSize: Float,
        damping: Float,
        wetDry: Float
    ) {
        require(left.size == right.size) { "L/R buffers must be same length" }

        // Map roomSize 0–1 → feedback 0.70–0.98
        val feedback = 0.70f + roomSize * 0.28f
        val damp1 = damping * 0.4f          // low-pass coefficient
        val damp2 = 1.0f - damp1

        for (n in left.indices) {
            val inL = left[n]
            val inR = right[n]

            // ── 8 parallel Comb filters ────────────────────────────────────
            var outL = 0f
            var outR = 0f

            for (c in 0..7) {
                // Left comb
                val bufL = combBufL[c]
                val posL = combPosL[c]
                val delayL = bufL[posL]
                combFilterL[c] = delayL * damp2 + combFilterL[c] * damp1
                bufL[posL] = inL + combFilterL[c] * feedback
                combPosL[c] = (posL + 1) % bufL.size
                outL += delayL

                // Right comb
                val bufR = combBufR[c]
                val posR = combPosR[c]
                val delayR = bufR[posR]
                combFilterR[c] = delayR * damp2 + combFilterR[c] * damp1
                bufR[posR] = inR + combFilterR[c] * feedback
                combPosR[c] = (posR + 1) % bufR.size
                outR += delayR
            }

            // Scale comb output
            outL *= 0.015f
            outR *= 0.015f

            // ── 4 serial Allpass filters ───────────────────────────────────
            for (a in 0..3) {
                // Left allpass
                val apBufL = allpassBufL[a]
                val apPosL = allpassPosL[a]
                val bufOutL = apBufL[apPosL]
                val apOutL = -outL + bufOutL
                apBufL[apPosL] = outL + bufOutL * allpassFeedback
                allpassPosL[a] = (apPosL + 1) % apBufL.size
                outL = apOutL

                // Right allpass
                val apBufR = allpassBufR[a]
                val apPosR = allpassPosR[a]
                val bufOutR = apBufR[apPosR]
                val apOutR = -outR + bufOutR
                apBufR[apPosR] = outR + bufOutR * allpassFeedback
                allpassPosR[a] = (apPosR + 1) % apBufR.size
                outR = apOutR
            }

            // ── Wet/Dry blend ──────────────────────────────────────────────
            val dry = 1.0f - wetDry
            left[n]  = (inL * dry + outL * wetDry).coerceIn(-1f, 1f)
            right[n] = (inR * dry + outR * wetDry).coerceIn(-1f, 1f)
        }
    }

    /** Clears all delay lines — call on track change or param reset. */
    fun reset() {
        combBufL.forEach { it.fill(0f) }
        combBufR.forEach { it.fill(0f) }
        combFilterL.fill(0f)
        combFilterR.fill(0f)
        combPosL.fill(0)
        combPosR.fill(0)
        allpassBufL.forEach { it.fill(0f) }
        allpassBufR.forEach { it.fill(0f) }
        allpassPosL.fill(0)
        allpassPosR.fill(0)
    }
}
