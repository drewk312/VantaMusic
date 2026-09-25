package com.audiophile.musicplayer.radio.sonic

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Holds the raw sonic characteristics of a track (scored 0.0 to 1.0).
 */
data class FeatureVector(
    val energy: Double,
    val valence: Double,       // Musical happiness/positivity
    val danceability: Double,
    val acousticness: Double
) {
    /**
     * Calculates the geometric Euclidean distance between two songs.
     * Lower score = closer sonic match.
     */
    fun distanceTo(other: FeatureVector): Double {
        return sqrt(
            (this.energy - other.energy).pow(2) +
            (this.valence - other.valence).pow(2) +
            (this.danceability - other.danceability).pow(2) +
            (this.acousticness - other.acousticness).pow(2)
        )
    }

    /**
     * Linearly interpolates (lerp) towards a target vector with the given learning rate/weight.
     * Used for Thumbs Up steering.
     */
    fun nudgeTowards(target: FeatureVector, weight: Double = 0.28): FeatureVector {
        val alpha = weight.coerceIn(0.01, 0.90)
        return FeatureVector(
            energy = (energy + (target.energy - energy) * alpha).coerceIn(0.0, 1.0),
            valence = (valence + (target.valence - valence) * alpha).coerceIn(0.0, 1.0),
            danceability = (danceability + (target.danceability - danceability) * alpha).coerceIn(0.0, 1.0),
            acousticness = (acousticness + (target.acousticness - acousticness) * alpha).coerceIn(0.0, 1.0)
        )
    }

    /**
     * Repels the current vector away from the specified vector with the given weight.
     * Used for Thumbs Down avoidance.
     */
    fun pushAway(target: FeatureVector, weight: Double = 0.22): FeatureVector {
        val beta = weight.coerceIn(0.01, 0.90)
        return FeatureVector(
            energy = (energy - (target.energy - energy) * beta).coerceIn(0.0, 1.0),
            valence = (valence - (target.valence - valence) * beta).coerceIn(0.0, 1.0),
            danceability = (danceability - (target.danceability - danceability) * beta).coerceIn(0.0, 1.0),
            acousticness = (acousticness - (target.acousticness - acousticness) * beta).coerceIn(0.0, 1.0)
        )
    }

    companion object {
        val NEUTRAL = FeatureVector(0.5, 0.5, 0.5, 0.5)
        val HIGH_ENERGY_DANCE = FeatureVector(0.85, 0.80, 0.85, 0.08)
        val CHILL_ACOUSTIC = FeatureVector(0.25, 0.40, 0.35, 0.85)
        val MELANCHOLIC_AMBIENT = FeatureVector(0.20, 0.20, 0.20, 0.70)
        val DRIVING_ROCK = FeatureVector(0.90, 0.60, 0.50, 0.05)
    }
}
