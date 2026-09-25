package com.audiophile.musicplayer.playback.dsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqProfileValidatorTest {

    private val validProfile = """
        Preamp: -3.0 dB
        SR_44100,1.0,0.0,0.0,0.0,0.0,0.9,-1.7,0.85,1.7,-0.85
        SR_48000,1.0,0.0,0.0,0.0,0.0,0.9,-1.7,0.85,1.7,-0.85
    """.trimIndent()

    @Test
    fun validDdcProfile_isAccepted() {
        assertTrue(AutoEqProfileValidator.isValidDdcProfile(validProfile))
    }

    @Test
    fun missing48kSection_isRejected() {
        val missing48 = """
            SR_44100,1.0,0.0,0.0,0.0,0.0
        """.trimIndent()
        assertFalse(AutoEqProfileValidator.isValidDdcProfile(missing48))
    }

    @Test
    fun mismatchedSectionCounts_isRejected() {
        val mismatched = """
            SR_44100,1.0,0.0,0.0,0.0,0.0
            SR_48000,1.0,0.0,0.0,0.0,0.0,0.5,0.6,0.7,0.8,0.9,0.3
        """.trimIndent()
        assertFalse(AutoEqProfileValidator.isValidDdcProfile(mismatched))
    }

    @Test
    fun nonNumericTerms_isRejected() {
        val badTerm = """
            SR_44100,1.0,0.0,0.0,0.0,abc
            SR_48000,1.0,0.0,0.0,0.0,0.0
        """.trimIndent()
        assertFalse(AutoEqProfileValidator.isValidDdcProfile(badTerm))
    }

    @Test
    fun blankOrNull_isRejected() {
        assertFalse(AutoEqProfileValidator.isValidDdcProfile(null))
        assertFalse(AutoEqProfileValidator.isValidDdcProfile(""))
        assertFalse(AutoEqProfileValidator.isValidDdcProfile("   "))
    }

    @Test
    fun sectionNotConstituentOfFive_isRejected() {
        val fourTerms = """
            SR_44100,1.0,0.0,0.0,0.0
            SR_48000,1.0,0.0,0.0,0.0,0.0
        """.trimIndent()
        assertFalse(AutoEqProfileValidator.isValidDdcProfile(fourTerms))
    }

    @Test
    fun orderReversed_isRejected() {
        val reversed = """
            SR_48000,1.0,0.0,0.0,0.0,0.0
            SR_44100,1.0,0.0,0.0,0.0,0.0
        """.trimIndent()
        assertFalse(AutoEqProfileValidator.isValidDdcProfile(reversed))
    }
}