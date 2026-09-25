package com.audiophile.musicplayer.playback.dsp

/**
 * Validates a JamesDSP DDC headphone-correction string before it is persisted
 * into [VantaEqualizerConfig.autoEqProfileName] and handed to the native
 * `DDCStringParser`. Mirrors the native contract (see `Effects/vdc.c`):
 *
 *  - must contain the `SR_44100` and `SR_48000` section markers;
 *  - each section must hold a comma-separated list of numbers whose count is a
 *    positive multiple of five (each biquad section is `b0,b1,b2,a1,a2`).
 */
object AutoEqProfileValidator {

    private const val SR_44100 = "SR_44100"
    private const val SR_48000 = "SR_48000"

    fun isValidDdcProfile(ddc: String?): Boolean {
        if (ddc.isNullOrBlank()) return false
        val fs44 = ddc.indexOf(SR_44100)
        val fs48 = ddc.indexOf(SR_48000)
        if (fs44 < 0 || fs48 < 0 || fs44 >= fs48) return false
        val fortyFourTerms = termsAfter(ddc, fs44 + SR_44100.length, fs48)
        val fortyEightTerms = termsAfter(ddc, fs48 + SR_48000.length, ddc.length)
        if (!isValidSection(fortyFourTerms)) return false
        if (!isValidSection(fortyEightTerms)) return false
        return fortyFourTerms == fortyEightTerms
    }

    private fun termsAfter(ddc: String, fromIndex: Int, toIndex: Int): List<String> {
        val segment = ddc.substring(fromIndex.coerceIn(0, ddc.length), toIndex.coerceIn(0, ddc.length))
        return segment.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun isValidSection(terms: List<String>): Boolean {
        if (terms.isEmpty() || terms.size % 5 != 0) return false
        return terms.all { it.toDoubleOrNull() != null }
    }
}