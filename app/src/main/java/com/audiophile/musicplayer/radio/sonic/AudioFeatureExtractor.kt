package com.audiophile.musicplayer.radio.sonic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * DSP Feature Extractor converting raw PCM audio samples into a normalized [FeatureVector],
 * with an acoustic heuristic estimator when raw PCM is not yet decoded.
 */
class AudioFeatureExtractor {

    data class RawAudioStats(
        val rmsEnergy: Double,
        val spectralCentroid: Double,
        val zeroCrossingRate: Double,
        val beatRegularity: Double,
        val highFrequencyRatio: Double
    )

    /**
     * Converts raw PCM samples (float array in [-1.0, 1.0]) into a normalized [FeatureVector].
     */
    fun extract(pcmSamples: FloatArray, sampleRate: Int = 44100): FeatureVector {
        if (pcmSamples.isEmpty()) return FeatureVector.NEUTRAL

        val stats = analyzeAudio(pcmSamples, sampleRate)

        // 1. Energy: Root Mean Square (RMS) loudness mapped logarithmically (0.0 to 1.0)
        val energy = (stats.rmsEnergy * 2.5).coerceIn(0.0, 1.0)

        // 2. Acousticness: Smooth decay, lower high-freq distortion, lower compression/rms ratio
        val acousticness = ((1.0 - stats.highFrequencyRatio) * (1.0 - energy)).coerceIn(0.0, 1.0)

        // 3. Danceability: Strong periodic onsets + moderate-to-high tempo regularity
        val danceability = (stats.beatRegularity * 0.7 + (if (energy in 0.4..0.85) 0.3 else 0.1)).coerceIn(0.0, 1.0)

        // 4. Valence: Positivity/brightness. Correlated with bright spectral centroid and rhythmic drive
        val valence = (stats.spectralCentroid * 0.5 + stats.beatRegularity * 0.3 + energy * 0.2).coerceIn(0.0, 1.0)

        return FeatureVector(
            energy = (energy * 100).roundToInt() / 100.0,
            valence = (valence * 100).roundToInt() / 100.0,
            danceability = (danceability * 100).roundToInt() / 100.0,
            acousticness = (acousticness * 100).roundToInt() / 100.0
        )
    }

    private fun analyzeAudio(samples: FloatArray, sampleRate: Int): RawAudioStats {
        var sumSquares = 0.0
        var zeroCrossings = 0
        val windowSize = 2048

        for (i in samples.indices) {
            val s = samples[i].toDouble()
            sumSquares += s * s
            if (i > 0 && ((samples[i] >= 0f && samples[i - 1] < 0f) || (samples[i] < 0f && samples[i - 1] >= 0f))) {
                zeroCrossings++
            }
        }

        val rms = sqrt(sumSquares / samples.size)
        val zcr = zeroCrossings.toDouble() / samples.size

        // Sample beat onset / flux regularity (simplified autocorrelation on 2048-sample frames)
        val frameCount = samples.size / windowSize
        val frameEnergies = DoubleArray(frameCount)
        for (f in 0 until frameCount) {
            var fSum = 0.0
            for (j in 0 until windowSize) {
                val s = samples[f * windowSize + j]
                fSum += s * s
            }
            frameEnergies[f] = sqrt(fSum / windowSize)
        }

        val diffs = DoubleArray(max(0, frameCount - 1)) { i -> abs(frameEnergies[i + 1] - frameEnergies[i]) }
        val avgDiff = if (diffs.isNotEmpty()) diffs.average() else 0.0
        val regularity = (avgDiff * 4.0).coerceIn(0.0, 1.0)

        return RawAudioStats(
            rmsEnergy = rms,
            spectralCentroid = (zcr * 2.0).coerceIn(0.0, 1.0),
            zeroCrossingRate = zcr,
            beatRegularity = regularity,
            highFrequencyRatio = (zcr * 1.5).coerceIn(0.0, 1.0)
        )
    }

    /**
     * Estimates initial FeatureVector from metadata/genre tags when PCM audio has not yet been processed.
     */
    fun estimateFromMetadata(
        title: String,
        artist: String,
        genres: Collection<String>
    ): FeatureVector {
        val lowerText = (genres.joinToString(" ") + " " + title + " " + artist).lowercase()

        var energy = 0.5
        var valence = 0.5
        var danceability = 0.5
        var acousticness = 0.5

        when {
            lowerText.containsAny("edm", "dance", "electronic", "club", "house", "techno", "synth-pop") -> {
                energy = 0.85
                danceability = 0.82
                valence = 0.70
                acousticness = 0.08
            }
            lowerText.containsAny("rock", "metal", "punk", "hard rock", "alternative") -> {
                energy = 0.88
                danceability = 0.50
                valence = 0.55
                acousticness = 0.05
            }
            lowerText.containsAny("acoustic", "folk", "ballad", "unplugged", "piano", "classical") -> {
                energy = 0.25
                danceability = 0.35
                valence = 0.40
                acousticness = 0.85
            }
            lowerText.containsAny("hip hop", "rap", "trap", "r&b") -> {
                energy = 0.75
                danceability = 0.80
                valence = 0.60
                acousticness = 0.15
            }
            lowerText.containsAny("ambient", "chill", "lo-fi", "sleep", "meditation") -> {
                energy = 0.20
                danceability = 0.25
                valence = 0.35
                acousticness = 0.75
            }
            lowerText.containsAny("jazz", "blues", "soul") -> {
                energy = 0.45
                danceability = 0.60
                valence = 0.65
                acousticness = 0.60
            }
        }

        return FeatureVector(energy, valence, danceability, acousticness)
    }

    companion object {
        val DEFAULT = AudioFeatureExtractor()

        fun estimateFromMetadata(
            title: String,
            artist: String,
            genres: Collection<String> = emptyList()
        ): FeatureVector = DEFAULT.estimateFromMetadata(title, artist, genres)
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
