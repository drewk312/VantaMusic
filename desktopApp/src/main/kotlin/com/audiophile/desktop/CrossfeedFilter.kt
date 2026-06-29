package com.audiophile.desktop

import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
//  CrossfeedFilter — Bauer stereophonic-to-binaural (BS2B-inspired) crossfeed
//  with optional simplified HRTF shelf EQ.
//
//  What it does:
//    When wearing headphones, your left ear hears ONLY the left channel.
//    In reality (speakers), your left ear also hears the right speaker — but
//    delayed by ~0.3ms and slightly muffled by your head (inter-aural crosstalk).
//    By re-adding that bleed, music sounds less "glued to the sides of your skull"
//    and more like it's playing in front of you.
//
//  Signal flow per output sample:
//    L_out = L_in * (1 - feedLevel) + LPF(R_in) * feedLevel  [delayed by delayMs]
//    R_out = R_in * (1 - feedLevel) + LPF(L_in) * feedLevel  [delayed by delayMs]
//
//  HRTF shelf EQ (simplified):
//    Applies a first-order high/low shelf to the output to approximate the
//    frequency response of the outer ear (pinna) for a chosen elevation angle.
//    This creates subtle front/back/above cues without needing convolution IRs.
// ─────────────────────────────────────────────────────────────────────────────

class CrossfeedFilter(private val sampleRate: Int = 44100) {

    // ── Low-pass filter state (one-pole IIR per channel) ──────────────────
    private var lpStateL = 0f
    private var lpStateR = 0f

    // ── Delay line buffers for inter-aural delay ───────────────────────────
    private var delayBufL = FloatArray(256)   // resized lazily
    private var delayBufR = FloatArray(256)
    private var delayPos = 0

    // ── HRTF shelf state (first-order) ────────────────────────────────────
    private var hShelfStateL = 0f
    private var hShelfStateR = 0f
    private var lShelfStateL = 0f
    private var lShelfStateR = 0f

    // ── Process ────────────────────────────────────────────────────────────

    /**
     * Applies crossfeed (and optional HRTF) to a stereo PCM buffer in-place.
     *
     * @param left          Left channel samples (−1..1)
     * @param right         Right channel samples (−1..1)
     * @param feedLevel     Cross-bleed amount 0.0 – 1.0  (recommend 0.2 – 0.45)
     * @param delayMs       Inter-aural delay in milliseconds (0.1 – 2.0 ms)
     * @param hrtfPreset    Optional elevation preset; null = HRTF bypass
     */
    fun process(
        left: FloatArray,
        right: FloatArray,
        feedLevel: Float,
        delayMs: Float,
        hrtfPreset: HrtfPreset?
    ) {
        require(left.size == right.size) { "L/R buffers must match" }

        val delaySamples = (delayMs * sampleRate.toFloat() / 1000.0f).toDouble().toInt().coerceAtLeast(1)

        // Resize delay buffers if needed
        if (delayBufL.size < delaySamples + 1) {
            delayBufL = FloatArray(delaySamples + 64)
            delayBufR = FloatArray(delaySamples + 64)
            delayPos  = 0
        }

        // Low-pass cutoff ~700 Hz  (simulates shadowing of the head)
        // Using one-pole IIR: y[n] = α*x[n] + (1−α)*y[n−1]
        val lpAlpha = 1.0f - exp(-2.0f * PI.toFloat() * 700f / sampleRate.toFloat())

        val direct = 1.0f - feedLevel

        for (n in left.indices) {
            val inL = left[n]
            val inR = right[n]

            // ── Low-pass filter the cross-channel ─────────────────────────
            lpStateL = lpAlpha * inL + (1f - lpAlpha) * lpStateL
            lpStateR = lpAlpha * inR + (1f - lpAlpha) * lpStateR

            // ── Write current LP'd samples to delay line ───────────────────
            val writePos = delayPos % delayBufL.size
            delayBufL[writePos] = lpStateL
            delayBufR[writePos] = lpStateR
            delayPos++

            // ── Read from delay ────────────────────────────────────────────
            val readPos: Int = ((delayPos - delaySamples - 1 + delayBufL.size * 2) % delayBufL.size)
            val crossL = delayBufR[readPos]  // right ear hears left speaker delayed
            val crossR = delayBufL[readPos]  // left ear hears right speaker delayed

            // ── Mix ────────────────────────────────────────────────────────
            left[n]  = (inL * direct + crossL * feedLevel).coerceIn(-1f, 1f)
            right[n] = (inR * direct + crossR * feedLevel).coerceIn(-1f, 1f)
        }

        // ── HRTF shelf EQ (applied after crossfeed) ────────────────────────
        hrtfPreset?.let { applyHrtfShelves(left, right, it) }
    }

    // ── Simplified first-order shelf EQ for HRTF elevation cues ──────────

    private fun applyHrtfShelves(
        left: FloatArray,
        right: FloatArray,
        preset: HrtfPreset
    ) {
        if (preset == HrtfPreset.FLAT) return

        // High shelf: boost/cut above ~6 kHz
        val hsCutoff = 6000f
        val hsAlpha  = exp(-2f * PI.toFloat() * hsCutoff / sampleRate.toFloat())
        val hsGain   = dbToLinear(preset.highShelfGain)

        // Low shelf: boost/cut below ~300 Hz
        val lsCutoff = 300f
        val lsAlpha  = exp(-2f * PI.toFloat() * lsCutoff / sampleRate.toFloat())
        val lsGain   = dbToLinear(preset.lowShelfGain)

        for (n in left.indices) {
            // High shelf (both channels equally — simplified pinna response)
            hShelfStateL = hsAlpha * hShelfStateL + (1f - hsAlpha) * left[n]
            hShelfStateR = hsAlpha * hShelfStateR + (1f - hsAlpha) * right[n]
            val hiL = left[n]  - hShelfStateL   // high-passed component
            val hiR = right[n] - hShelfStateR

            // Low shelf
            lShelfStateL = lsAlpha * lShelfStateL + (1f - lsAlpha) * left[n]
            lShelfStateR = lsAlpha * lShelfStateR + (1f - lsAlpha) * right[n]

            left[n]  = (hShelfStateL + hiL * hsGain + (lShelfStateL * lsGain - lShelfStateL)).coerceIn(-1f, 1f)
            right[n] = (hShelfStateR + hiR * hsGain + (lShelfStateR * lsGain - lShelfStateR)).coerceIn(-1f, 1f)
        }
    }

    fun reset() {
        lpStateL = 0f; lpStateR = 0f
        delayBufL.fill(0f); delayBufR.fill(0f); delayPos = 0
        hShelfStateL = 0f; hShelfStateR = 0f
        lShelfStateL = 0f; lShelfStateR = 0f
    }

    private fun dbToLinear(db: Float): Float = 10.0.pow((db / 20f).toDouble()).toFloat()
}


