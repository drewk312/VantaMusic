package com.audiophile.musicplayer.radio.genome

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 24-dimensional Musical Genome Vector replicating the core acoustic and stylistic
 * dimensions of Pandora's Music Genome Project.
 *
 * Each attribute is normalized between 0.0f and 1.0f.
 */
data class MusicGenomeVector(
    // 1. Rhythm & Meter
    val tempoBpmNorm: Float = 0.5f,          // 0.0 = 40 BPM, 1.0 = 200 BPM (0.5 ≈ 120 BPM)
    val grooveSyncopation: Float = 0.5f,     // 0.0 = straight/square, 1.0 = highly syncopated/funky
    val swingFeel: Float = 0.0f,             // 0.0 = straight 8ths/16ths, 1.0 = heavy shuffle/triplet swing
    val halfTimeFeel: Float = 0.0f,          // 0.0 = standard tempo, 1.0 = trap/dubstep half-time feel

    // 2. Tonality & Harmony
    val harmonicMode: Float = 0.5f,          // 0.0 = dark minor / Phrygian, 1.0 = bright major / Lydian
    val harmonicComplexity: Float = 0.3f,    // 0.0 = simple 3-chord diatonic, 1.0 = jazz/neo-soul extended 7ths/9ths/13ths

    // 3. Timbre, Instrumentation & Texture
    val acousticWeight: Float = 0.3f,        // 0.0 = 100% electronic/synth, 1.0 = 100% acoustic wood/strings/piano
    val electronicWeight: Float = 0.4f,      // 0.0 = acoustic/live band, 1.0 = synths, drum machines, digital FX
    val rockElectricWeight: Float = 0.2f,    // 0.0 = no electric guitar, 1.0 = heavy distorted guitars / live rock kit
    val subBassWeight: Float = 0.5f,         // 0.0 = lean bass, 1.0 = heavy 808 sub / club low-end
    val ambientReverbWeight: Float = 0.4f,   // 0.0 = bone dry intimate vocal, 1.0 = ethereal wash / cathedral reverb
    val organicPercussionWeight: Float = 0.3f, // 0.0 = quantized 808/909 ticks, 1.0 = live kit nuances, congas, shakers

    // 4. Vocal Aesthetics
    val vocalPresence: Float = 0.7f,         // 0.0 = pure instrumental, 1.0 = dominant vocal lead / acappella
    val vocalStyleRap: Float = 0.0f,         // 0.0 = melodic singing, 1.0 = rhythmic rap flow / spoken word
    val vocalStyleAutotune: Float = 0.1f,    // 0.0 = natural acoustic voice, 1.0 = hyperpop / heavy vocoder / Auto-Tune
    val vocalTextureRasp: Float = 0.2f,      // 0.0 = silky smooth / falsetto, 1.0 = gritty, raspy rock/blues vocal

    // 5. Dynamics, Energy & Mood
    val energyLevel: Float = 0.5f,           // 0.0 = whisper quiet / ambient, 1.0 = explosive stadium / peak dance
    val dynamicRange: Float = 0.5f,          // 0.0 = brickwalled radio master, 1.0 = wide audiophile dynamics
    val danceability: Float = 0.5f,          // 0.0 = non-dance rubato/ambient, 1.0 = four-on-the-floor infectious groove
    val emotionalValence: Float = 0.5f,      // 0.0 = melancholic, dark, angry, 1.0 = euphoric, celebratory, sunny

    // 6. Era & Production Aesthetic
    val eraDecade: Float = 0.8f,             // 0.0 = 1960s, 0.2 = 1970s, 0.4 = 1980s, 0.6 = 1990s, 0.8 = 2000s, 0.9 = 2010s, 1.0 = 2020s
    val productionWarmth: Float = 0.6f,      // 0.0 = clinical digital, 1.0 = warm tape saturation / vinyl warmth
    val lofiAesthetic: Float = 0.1f,         // 0.0 = clean studio, 1.0 = vinyl crackle, tape warble, lo-fi beats
    val orchestralWeight: Float = 0.1f       // 0.0 = no orchestral elements, 1.0 = symphonic strings, brass, cinematic
) {

    fun toFloatArray(): FloatArray = floatArrayOf(
        tempoBpmNorm, grooveSyncopation, swingFeel, halfTimeFeel,
        harmonicMode, harmonicComplexity,
        acousticWeight, electronicWeight, rockElectricWeight, subBassWeight, ambientReverbWeight, organicPercussionWeight,
        vocalPresence, vocalStyleRap, vocalStyleAutotune, vocalTextureRasp,
        energyLevel, dynamicRange, danceability, emotionalValence,
        eraDecade, productionWarmth, lofiAesthetic, orchestralWeight
    )

    /**
     * Cosine similarity between this genome vector and another (range: -1.0 .. 1.0, typical 0.0 .. 1.0).
     */
    fun cosineSimilarity(other: MusicGenomeVector): Float {
        val a = toFloatArray()
        val b = other.toFloatArray()
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-6f) (dot / denom).coerceIn(-1.0f, 1.0f) else 0f
    }

    /**
     * Euclidean distance between two genome vectors.
     */
    fun distance(other: MusicGenomeVector): Float {
        val a = toFloatArray()
        val b = other.toFloatArray()
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Pandora Thumbs Up Steering: Nudges the current station profile towards the liked track.
     */
    fun nudgeTowards(target: MusicGenomeVector, weight: Float = 0.25f): MusicGenomeVector {
        val clampedWeight = weight.coerceIn(0.01f, 0.75f)
        fun lerp(a: Float, b: Float) = (a + (b - a) * clampedWeight).coerceIn(0f, 1f)

        return MusicGenomeVector(
            tempoBpmNorm = lerp(tempoBpmNorm, target.tempoBpmNorm),
            grooveSyncopation = lerp(grooveSyncopation, target.grooveSyncopation),
            swingFeel = lerp(swingFeel, target.swingFeel),
            halfTimeFeel = lerp(halfTimeFeel, target.halfTimeFeel),
            harmonicMode = lerp(harmonicMode, target.harmonicMode),
            harmonicComplexity = lerp(harmonicComplexity, target.harmonicComplexity),
            acousticWeight = lerp(acousticWeight, target.acousticWeight),
            electronicWeight = lerp(electronicWeight, target.electronicWeight),
            rockElectricWeight = lerp(rockElectricWeight, target.rockElectricWeight),
            subBassWeight = lerp(subBassWeight, target.subBassWeight),
            ambientReverbWeight = lerp(ambientReverbWeight, target.ambientReverbWeight),
            organicPercussionWeight = lerp(organicPercussionWeight, target.organicPercussionWeight),
            vocalPresence = lerp(vocalPresence, target.vocalPresence),
            vocalStyleRap = lerp(vocalStyleRap, target.vocalStyleRap),
            vocalStyleAutotune = lerp(vocalStyleAutotune, target.vocalStyleAutotune),
            vocalTextureRasp = lerp(vocalTextureRasp, target.vocalTextureRasp),
            energyLevel = lerp(energyLevel, target.energyLevel),
            dynamicRange = lerp(dynamicRange, target.dynamicRange),
            danceability = lerp(danceability, target.danceability),
            emotionalValence = lerp(emotionalValence, target.emotionalValence),
            eraDecade = lerp(eraDecade, target.eraDecade),
            productionWarmth = lerp(productionWarmth, target.productionWarmth),
            lofiAesthetic = lerp(lofiAesthetic, target.lofiAesthetic),
            orchestralWeight = lerp(orchestralWeight, target.orchestralWeight)
        )
    }

    /**
     * Pandora Thumbs Down Steering: Pushes the current station profile away from the disliked track.
     */
    fun pushAway(disliked: MusicGenomeVector, weight: Float = 0.20f): MusicGenomeVector {
        val clampedWeight = weight.coerceIn(0.01f, 0.50f)
        fun push(a: Float, b: Float): Float {
            val delta = (a - b) * clampedWeight
            return (a + delta).coerceIn(0f, 1f)
        }

        return MusicGenomeVector(
            tempoBpmNorm = push(tempoBpmNorm, disliked.tempoBpmNorm),
            grooveSyncopation = push(grooveSyncopation, disliked.grooveSyncopation),
            swingFeel = push(swingFeel, disliked.swingFeel),
            halfTimeFeel = push(halfTimeFeel, disliked.halfTimeFeel),
            harmonicMode = push(harmonicMode, disliked.harmonicMode),
            harmonicComplexity = push(harmonicComplexity, disliked.harmonicComplexity),
            acousticWeight = push(acousticWeight, disliked.acousticWeight),
            electronicWeight = push(electronicWeight, disliked.electronicWeight),
            rockElectricWeight = push(rockElectricWeight, disliked.rockElectricWeight),
            subBassWeight = push(subBassWeight, disliked.subBassWeight),
            ambientReverbWeight = push(ambientReverbWeight, disliked.ambientReverbWeight),
            organicPercussionWeight = push(organicPercussionWeight, disliked.organicPercussionWeight),
            vocalPresence = push(vocalPresence, disliked.vocalPresence),
            vocalStyleRap = push(vocalStyleRap, disliked.vocalStyleRap),
            vocalStyleAutotune = push(vocalStyleAutotune, disliked.vocalStyleAutotune),
            vocalTextureRasp = push(vocalTextureRasp, disliked.vocalTextureRasp),
            energyLevel = push(energyLevel, disliked.energyLevel),
            dynamicRange = push(dynamicRange, disliked.dynamicRange),
            danceability = push(danceability, disliked.danceability),
            emotionalValence = push(emotionalValence, disliked.emotionalValence),
            eraDecade = push(eraDecade, disliked.eraDecade),
            productionWarmth = push(productionWarmth, disliked.productionWarmth),
            lofiAesthetic = push(lofiAesthetic, disliked.lofiAesthetic),
            orchestralWeight = push(orchestralWeight, disliked.orchestralWeight)
        )
    }

    companion object {
        val DEFAULT = MusicGenomeVector()

        fun bpmToNorm(bpm: Float): Float {
            return ((bpm - 40f) / 160f).coerceIn(0f, 1f)
        }

        fun normToBpm(norm: Float): Float {
            return (40f + norm.coerceIn(0f, 1f) * 160f)
        }

        fun yearToDecadeNorm(year: Int): Float {
            return when {
                year <= 1965 -> 0.05f
                year <= 1975 -> 0.20f
                year <= 1985 -> 0.40f
                year <= 1995 -> 0.60f
                year <= 2005 -> 0.75f
                year <= 2015 -> 0.88f
                else -> 1.0f
            }
        }
    }
}
