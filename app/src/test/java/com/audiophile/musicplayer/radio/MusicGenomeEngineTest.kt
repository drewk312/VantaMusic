package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.radio.genome.MusicGenomeEngine
import com.audiophile.musicplayer.radio.genome.MusicGenomeExtractor
import com.audiophile.musicplayer.radio.genome.MusicGenomeVector
import com.audiophile.musicplayer.radio.genome.PandoraStationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicGenomeEngineTest {

    @Test
    fun testCosineSimilarityAndDistance() {
        val v1 = MusicGenomeVector(tempoBpmNorm = 0.5f, energyLevel = 0.8f, acousticWeight = 0.1f)
        val v2 = MusicGenomeVector(tempoBpmNorm = 0.5f, energyLevel = 0.8f, acousticWeight = 0.1f)
        val vOpposite = MusicGenomeVector(tempoBpmNorm = 0.1f, energyLevel = 0.1f, acousticWeight = 0.9f)

        val simIdentical = v1.cosineSimilarity(v2)
        assertEquals(1.0f, simIdentical, 0.001f)

        val simDistant = v1.cosineSimilarity(vOpposite)
        assertTrue("Expected similarity to be lower for distant genres: $simDistant", simDistant < simIdentical)

        val distIdentical = v1.distance(v2)
        assertEquals(0.0f, distIdentical, 0.001f)
    }

    @Test
    fun testMusicGenomeExtractorAcousticModifier() {
        val studio = MusicGenomeExtractor.extract(
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California"
        )
        val acoustic = MusicGenomeExtractor.extract(
            title = "Hotel California (Acoustic Live)",
            artist = "Eagles",
            album = "Hell Freezes Over"
        )

        assertTrue(
            "Acoustic version must have higher acousticWeight",
            acoustic.acousticWeight > studio.acousticWeight
        )
        assertTrue(
            "Live version must have higher organicPercussionWeight",
            acoustic.organicPercussionWeight >= studio.organicPercussionWeight
        )
    }

    @Test
    fun testThumbsUpAndDownSteering() {
        val engine = MusicGenomeEngine(
            seedTrackTitle = "FE!N",
            seedTrackArtist = "Travis Scott",
            seedGenre = "Hip-Hop"
        )

        val initialGenome = engine.currentStationGenome

        // Thumb up an electronic high-energy synth track
        val electronicTrack = MusicGenomeExtractor.extract("Around the World", "Daft Punk", genre = "Electronic")
        val simBefore = engine.currentStationGenome.cosineSimilarity(electronicTrack)

        engine.onThumbsUp(
            com.audiophile.musicplayer.data.source.SourceSearchResult(
                id = "deezer:123",
                providerId = "deezer",
                title = "Around the World",
                artist = "Daft Punk",
                album = "Homework",
                coverSeed = "",
                durationMs = 240_000L,
                status = com.audiophile.musicplayer.data.source.SearchItemStatus.VALIDATED_PLAYABLE,
                qualityLabel = "16-bit FLAC"
            )
        )

        val simAfter = engine.currentStationGenome.cosineSimilarity(electronicTrack)
        assertTrue("Thumbs Up must bring station genome closer to the liked track", simAfter > simBefore)

        // Thumb down an acoustic folk track
        val folkTrack = MusicGenomeExtractor.extract("Blowin' in the Wind", "Bob Dylan", genre = "Folk")
        engine.onThumbsDown(
            com.audiophile.musicplayer.data.source.SourceSearchResult(
                id = "deezer:456",
                providerId = "deezer",
                title = "Blowin' in the Wind",
                artist = "Bob Dylan",
                album = "The Freewheelin' Bob Dylan",
                coverSeed = "",
                durationMs = 180_000L,
                status = com.audiophile.musicplayer.data.source.SearchItemStatus.VALIDATED_PLAYABLE,
                qualityLabel = "16-bit FLAC"
            )
        )

        // Candidate score for the thumbed down track must be strongly negative (banned)
        val score = engine.scoreCandidate("Blowin' in the Wind", "Bob Dylan")
        assertTrue("Disliked track must receive disqualifying negative score", score < 0.0)
    }

    @Test
    fun testPandoraStationModesModulateScoring() {
        val engine = MusicGenomeEngine(
            seedTrackTitle = "Blinding Lights",
            seedTrackArtist = "The Weeknd",
            seedGenre = "Synth-Pop"
        )

        // Balanced mode
        engine.setMode(PandoraStationMode.BALANCED)
        val weekndScoreBalanced = engine.scoreCandidate("Save Your Tears", "The Weeknd")
        val duaLipaScoreBalanced = engine.scoreCandidate("Levitating", "Dua Lipa", candidateGenre = "Pop")

        // Discovery mode: penalizes the seed artist to explore other artists with same genome
        engine.setMode(PandoraStationMode.DISCOVERY)
        val weekndScoreDiscovery = engine.scoreCandidate("Save Your Tears", "The Weeknd")
        val duaLipaScoreDiscovery = engine.scoreCandidate("Levitating", "Dua Lipa", candidateGenre = "Pop")

        assertTrue(
            "Discovery mode should reduce score for the seed artist",
            weekndScoreDiscovery < weekndScoreBalanced
        )
        assertTrue(
            "Discovery mode should favor discovery of other genome-matching artists",
            duaLipaScoreDiscovery > weekndScoreDiscovery
        )

        // Artist Only mode: rejects other artists
        engine.setMode(PandoraStationMode.ARTIST_ONLY)
        val otherArtistScore = engine.scoreCandidate("Levitating", "Dua Lipa")
        assertTrue("Artist Only mode must reject different artists", otherArtistScore < 0.0)
    }

    @Test
    fun testArtistSeparationRule() {
        val engine = MusicGenomeEngine(
            seedTrackTitle = "Starboy",
            seedTrackArtist = "The Weeknd"
        )

        val score1 = engine.scoreCandidate("God's Plan", "Drake", candidateGenre = "Hip-Hop")

        // Record that Drake just played
        engine.recordTrackPlayed("God's Plan", "Drake")

        val scoreImmediateNext = engine.scoreCandidate("One Dance", "Drake", candidateGenre = "Hip-Hop")
        assertTrue(
            "Immediate repeat of same artist should be heavily penalized by artist separation rule (score1=$score1, next=$scoreImmediateNext)",
            scoreImmediateNext < score1 - 40.0
        )
    }
}
