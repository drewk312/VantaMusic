package com.audiophile.musicplayer.radio.genome

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SourceSearchResult
import java.util.Locale

/**
 * Extracts and synthesizes a 24-dimensional [MusicGenomeVector] from track metadata,
 * genre tags, title/album acoustic markers, release era, and artist profiles.
 */
object MusicGenomeExtractor {

    fun extract(
        title: String,
        artist: String,
        album: String? = null,
        genre: String? = null,
        durationMs: Long? = null,
        releaseYear: Int? = null,
        bpmHint: Float? = null
    ): MusicGenomeVector {
        val cleanTitle = title.lowercase(Locale.ROOT)
        val cleanArtist = artist.lowercase(Locale.ROOT)
        val cleanAlbum = album?.lowercase(Locale.ROOT).orEmpty()
        val cleanGenre = genre?.lowercase(Locale.ROOT).orEmpty()
        val combined = "$cleanTitle $cleanArtist $cleanAlbum $cleanGenre"

        // 1. Base vector from genre / artist baseline
        var v = artistBaseline(cleanArtist)
            ?: genreBaseline(cleanGenre)
            ?: inferFromTokens(combined)

        // 2. Adjust BPM if hint exists
        if (bpmHint != null && bpmHint > 40f && bpmHint < 220f) {
            v = v.copy(tempoBpmNorm = MusicGenomeVector.bpmToNorm(bpmHint))
        }

        // 3. Adjust Era
        if (releaseYear != null && releaseYear in 1940..2030) {
            val era = MusicGenomeVector.yearToDecadeNorm(releaseYear)
            val warmth = when {
                releaseYear < 1980 -> 0.90f
                releaseYear < 1995 -> 0.75f
                releaseYear < 2010 -> 0.55f
                else -> 0.40f
            }
            v = v.copy(eraDecade = era, productionWarmth = warmth)
        }

        // 4. Acoustic and title modifiers
        when {
            combined.contains("acoustic") || combined.contains("unplugged") || combined.contains("piano version") -> {
                v = v.copy(
                    acousticWeight = 0.92f,
                    electronicWeight = 0.08f,
                    subBassWeight = 0.20f,
                    rockElectricWeight = 0.05f,
                    energyLevel = (v.energyLevel * 0.65f).coerceAtLeast(0.15f),
                    productionWarmth = 0.85f
                )
            }
            combined.contains("remix") || combined.contains("club mix") || combined.contains("extended mix") || combined.contains("dance mix") -> {
                v = v.copy(
                    electronicWeight = 0.88f,
                    acousticWeight = 0.10f,
                    danceability = 0.88f,
                    energyLevel = (v.energyLevel * 1.30f).coerceAtMost(0.95f),
                    subBassWeight = 0.80f
                )
            }
            combined.contains("live at") || combined.contains("live in") || combined.contains("in concert") || combined.contains("(live)") -> {
                v = v.copy(
                    organicPercussionWeight = 0.85f,
                    ambientReverbWeight = 0.75f,
                    dynamicRange = 0.80f
                )
            }
            combined.contains("instrumental") || combined.contains("karaoke") || combined.contains("backing track") -> {
                v = v.copy(vocalPresence = 0.02f)
            }
            combined.contains("lofi") || combined.contains("lo-fi") || combined.contains("chillhop") || combined.contains("study beats") -> {
                v = v.copy(
                    lofiAesthetic = 0.88f,
                    productionWarmth = 0.90f,
                    energyLevel = 0.22f,
                    dynamicRange = 0.40f,
                    ambientReverbWeight = 0.60f
                )
            }
            combined.contains("orchestral") || combined.contains("symphony") || combined.contains("philharmonic") -> {
                v = v.copy(
                    orchestralWeight = 0.90f,
                    acousticWeight = 0.85f,
                    dynamicRange = 0.90f,
                    electronicWeight = 0.05f
                )
            }
        }

        // 5. Short duration / Interlude adjustment
        if (durationMs != null && durationMs > 0 && durationMs < 90_000L) {
            v = v.copy(energyLevel = (v.energyLevel * 0.8f).coerceAtLeast(0.1f))
        }

        return v
    }

    fun extract(track: UnifiedTrackWithSources): MusicGenomeVector {
        return extract(
            title = track.track.title,
            artist = track.track.artist,
            album = track.track.albumName,
            genre = track.track.genre,
            durationMs = track.track.durationMs
        )
    }

    fun extract(result: SourceSearchResult): MusicGenomeVector {
        val year = result.releaseDate?.take(4)?.toIntOrNull()
        return extract(
            title = result.title,
            artist = result.artist,
            album = result.album,
            genre = null,
            durationMs = result.durationMs,
            releaseYear = year
        )
    }

    private fun genreBaseline(genre: String): MusicGenomeVector? {
        if (genre.isBlank()) return null
        val g = genre.lowercase(Locale.ROOT)
        return when {
            g.contains("hip hop") || g.contains("hip-hop") || g.contains("rap") || g.contains("trap") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.45f,
                    grooveSyncopation = 0.75f,
                    halfTimeFeel = 0.70f,
                    harmonicMode = 0.35f,
                    harmonicComplexity = 0.25f,
                    acousticWeight = 0.15f,
                    electronicWeight = 0.80f,
                    rockElectricWeight = 0.05f,
                    subBassWeight = 0.88f,
                    vocalPresence = 0.85f,
                    vocalStyleRap = 0.90f,
                    energyLevel = 0.70f,
                    danceability = 0.75f,
                    emotionalValence = 0.45f,
                    eraDecade = 0.92f
                )
            }
            g.contains("r&b") || g.contains("soul") || g.contains("neo-soul") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.38f,
                    grooveSyncopation = 0.65f,
                    swingFeel = 0.40f,
                    harmonicMode = 0.50f,
                    harmonicComplexity = 0.70f,
                    acousticWeight = 0.45f,
                    electronicWeight = 0.45f,
                    subBassWeight = 0.65f,
                    vocalPresence = 0.90f,
                    vocalStyleRap = 0.05f,
                    energyLevel = 0.45f,
                    danceability = 0.60f,
                    emotionalValence = 0.55f,
                    productionWarmth = 0.75f
                )
            }
            g.contains("rock") || g.contains("grunge") || g.contains("punk") || g.contains("alt-rock") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.55f,
                    grooveSyncopation = 0.35f,
                    harmonicMode = 0.50f,
                    acousticWeight = 0.20f,
                    electronicWeight = 0.10f,
                    rockElectricWeight = 0.88f,
                    organicPercussionWeight = 0.85f,
                    subBassWeight = 0.35f,
                    vocalPresence = 0.80f,
                    vocalTextureRasp = 0.65f,
                    energyLevel = 0.80f,
                    dynamicRange = 0.65f,
                    danceability = 0.35f,
                    productionWarmth = 0.70f
                )
            }
            g.contains("metal") || g.contains("heavy metal") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.68f,
                    rockElectricWeight = 0.98f,
                    vocalTextureRasp = 0.90f,
                    energyLevel = 0.95f,
                    emotionalValence = 0.15f,
                    harmonicMode = 0.15f,
                    subBassWeight = 0.30f,
                    organicPercussionWeight = 0.90f
                )
            }
            g.contains("electronic") || g.contains("edm") || g.contains("house") || g.contains("techno") || g.contains("dance") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.55f,
                    grooveSyncopation = 0.40f,
                    acousticWeight = 0.05f,
                    electronicWeight = 0.95f,
                    subBassWeight = 0.85f,
                    danceability = 0.95f,
                    energyLevel = 0.85f,
                    vocalPresence = 0.40f,
                    ambientReverbWeight = 0.60f
                )
            }
            g.contains("jazz") || g.contains("bop") || g.contains("fusion") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.48f,
                    grooveSyncopation = 0.85f,
                    swingFeel = 0.85f,
                    harmonicMode = 0.55f,
                    harmonicComplexity = 0.95f,
                    acousticWeight = 0.88f,
                    electronicWeight = 0.10f,
                    organicPercussionWeight = 0.90f,
                    dynamicRange = 0.85f,
                    vocalPresence = 0.30f,
                    productionWarmth = 0.85f
                )
            }
            g.contains("folk") || g.contains("acoustic") || g.contains("singer-songwriter") || g.contains("americana") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.38f,
                    acousticWeight = 0.92f,
                    electronicWeight = 0.05f,
                    rockElectricWeight = 0.05f,
                    vocalPresence = 0.92f,
                    organicPercussionWeight = 0.70f,
                    energyLevel = 0.35f,
                    dynamicRange = 0.75f,
                    productionWarmth = 0.85f
                )
            }
            g.contains("pop") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.52f,
                    harmonicMode = 0.75f,
                    harmonicComplexity = 0.30f,
                    vocalPresence = 0.90f,
                    danceability = 0.75f,
                    energyLevel = 0.68f,
                    electronicWeight = 0.60f,
                    acousticWeight = 0.30f,
                    subBassWeight = 0.65f,
                    emotionalValence = 0.70f
                )
            }
            g.contains("classical") || g.contains("soundtrack") || g.contains("score") -> {
                MusicGenomeVector(
                    tempoBpmNorm = 0.40f,
                    harmonicComplexity = 0.80f,
                    orchestralWeight = 0.95f,
                    acousticWeight = 0.90f,
                    electronicWeight = 0.05f,
                    vocalPresence = 0.10f,
                    dynamicRange = 0.95f,
                    danceability = 0.10f,
                    ambientReverbWeight = 0.75f
                )
            }
            else -> null
        }
    }

    private fun artistBaseline(artist: String): MusicGenomeVector? {
        val a = artist.lowercase(Locale.ROOT)
        return when {
            // Hip Hop / Rap Icons
            a.contains("travis scott") -> MusicGenomeVector(
                tempoBpmNorm = 0.50f, halfTimeFeel = 0.80f, grooveSyncopation = 0.75f,
                harmonicMode = 0.25f, subBassWeight = 0.92f, electronicWeight = 0.85f,
                ambientReverbWeight = 0.70f, vocalPresence = 0.80f, vocalStyleRap = 0.75f,
                vocalStyleAutotune = 0.85f, energyLevel = 0.80f, danceability = 0.75f,
                emotionalValence = 0.35f, eraDecade = 0.95f
            )
            a.contains("kendrick lamar") -> MusicGenomeVector(
                tempoBpmNorm = 0.46f, grooveSyncopation = 0.85f, swingFeel = 0.30f,
                harmonicComplexity = 0.60f, subBassWeight = 0.80f, acousticWeight = 0.30f,
                vocalPresence = 0.92f, vocalStyleRap = 0.95f, energyLevel = 0.75f,
                emotionalValence = 0.40f, dynamicRange = 0.70f, eraDecade = 0.92f
            )
            a.contains("drake") -> MusicGenomeVector(
                tempoBpmNorm = 0.45f, grooveSyncopation = 0.70f, harmonicMode = 0.40f,
                subBassWeight = 0.85f, electronicWeight = 0.75f, vocalPresence = 0.88f,
                vocalStyleRap = 0.60f, vocalStyleAutotune = 0.35f, danceability = 0.75f,
                energyLevel = 0.65f, emotionalValence = 0.45f, eraDecade = 0.92f
            )
            a.contains("kanye west") -> MusicGenomeVector(
                tempoBpmNorm = 0.50f, grooveSyncopation = 0.70f, harmonicComplexity = 0.55f,
                subBassWeight = 0.85f, electronicWeight = 0.70f, acousticWeight = 0.25f,
                vocalPresence = 0.88f, vocalStyleRap = 0.85f, energyLevel = 0.80f,
                dynamicRange = 0.65f, eraDecade = 0.88f
            )
            // Alt R&B / Pop
            a.contains("the weeknd") -> MusicGenomeVector(
                tempoBpmNorm = 0.52f, grooveSyncopation = 0.65f, harmonicMode = 0.35f,
                electronicWeight = 0.80f, subBassWeight = 0.75f, ambientReverbWeight = 0.65f,
                vocalPresence = 0.92f, vocalTextureRasp = 0.20f, energyLevel = 0.72f,
                danceability = 0.75f, emotionalValence = 0.38f, eraDecade = 0.95f
            )
            a.contains("sza") || a.contains("frank ocean") -> MusicGenomeVector(
                tempoBpmNorm = 0.38f, grooveSyncopation = 0.70f, swingFeel = 0.35f,
                harmonicComplexity = 0.70f, acousticWeight = 0.45f, electronicWeight = 0.45f,
                subBassWeight = 0.65f, ambientReverbWeight = 0.60f, vocalPresence = 0.92f,
                energyLevel = 0.45f, dynamicRange = 0.75f, emotionalValence = 0.45f
            )
            // Pop Titans
            a.contains("taylor swift") -> MusicGenomeVector(
                tempoBpmNorm = 0.50f, harmonicMode = 0.75f, harmonicComplexity = 0.35f,
                acousticWeight = 0.45f, electronicWeight = 0.45f, vocalPresence = 0.92f,
                vocalStyleRap = 0.0f, energyLevel = 0.65f, danceability = 0.68f,
                emotionalValence = 0.65f, eraDecade = 0.90f
            )
            a.contains("billie eilish") -> MusicGenomeVector(
                tempoBpmNorm = 0.42f, harmonicMode = 0.30f, subBassWeight = 0.88f,
                ambientReverbWeight = 0.75f, vocalPresence = 0.90f, vocalTextureRasp = 0.05f,
                energyLevel = 0.45f, dynamicRange = 0.80f, emotionalValence = 0.30f,
                eraDecade = 0.98f
            )
            // Rock Legends
            a.contains("pink floyd") || a.contains("led zeppelin") || a.contains("queen") -> MusicGenomeVector(
                tempoBpmNorm = 0.48f, harmonicComplexity = 0.75f, rockElectricWeight = 0.85f,
                acousticWeight = 0.35f, organicPercussionWeight = 0.88f, ambientReverbWeight = 0.70f,
                vocalPresence = 0.75f, vocalTextureRasp = 0.60f, energyLevel = 0.75f,
                dynamicRange = 0.90f, eraDecade = 0.30f, productionWarmth = 0.90f
            )
            a.contains("fleetwood mac") || a.contains("eagles") || a.contains("steely dan") -> MusicGenomeVector(
                tempoBpmNorm = 0.46f, harmonicComplexity = 0.65f, acousticWeight = 0.50f,
                rockElectricWeight = 0.50f, organicPercussionWeight = 0.80f, vocalPresence = 0.88f,
                energyLevel = 0.55f, dynamicRange = 0.85f, eraDecade = 0.25f, productionWarmth = 0.92f
            )
            // Electronic / Dance
            a.contains("daft punk") -> MusicGenomeVector(
                tempoBpmNorm = 0.55f, grooveSyncopation = 0.60f, electronicWeight = 0.95f,
                subBassWeight = 0.80f, danceability = 0.95f, energyLevel = 0.82f,
                vocalStyleAutotune = 0.70f, eraDecade = 0.80f, productionWarmth = 0.75f
            )
            else -> null
        }
    }

    private fun inferFromTokens(combined: String): MusicGenomeVector {
        return MusicGenomeVector.DEFAULT
    }
}
