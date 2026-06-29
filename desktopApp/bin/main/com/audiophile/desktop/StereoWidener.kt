package com.audiophile.desktop

// ─────────────────────────────────────────────────────────────────────────────
//  StereoWidener — Mid/Side (M/S) stereo width processor.
//
//  Theory:
//    Mid  = 0.5 * (L + R)   — the "mono" centre
//    Side = 0.5 * (L − R)   — the stereo difference
//
//    To widen: boost Side by widthFactor before decoding:
//    L' = Mid + Side * widthFactor
//    R' = Mid − Side * widthFactor
//
//  widthFactor = 1.0 → identity (no change)
//  widthFactor > 1.0 → wider stereo field
//  widthFactor < 1.0 → narrower (approaching mono at 0.0)
// ─────────────────────────────────────────────────────────────────────────────

object StereoWidener {

    /**
     * Processes a PCM frame buffer in-place using M/S widening.
     *
     * @param left         Mutable float array for the left channel samples (range −1..1)
     * @param right        Mutable float array for the right channel samples (range −1..1)
     * @param widthFactor  Stereo width scalar.  1.0 = no change.
     */
    fun process(
        left: FloatArray,
        right: FloatArray,
        widthFactor: Float
    ) {
        require(left.size == right.size) { "L/R buffers must be the same length" }

        // Short-circuit: nothing to do at unity gain
        if (widthFactor == 1.0f) return

        for (i in left.indices) {
            val l = left[i]
            val r = right[i]

            val mid  = 0.5f * (l + r)
            val side = 0.5f * (l - r) * widthFactor  // scaled Side channel

            left[i]  = (mid + side).coerceIn(-1f, 1f)
            right[i] = (mid - side).coerceIn(-1f, 1f)
        }
    }
}
