package com.audiophile.musicplayer.radio.sonic

import org.junit.Assert.*
import org.junit.Test

class SonicRadioEngineTest {

    @Test
    fun testEuclideanDistanceCalculation() {
        val v1 = FeatureVector(energy = 0.8, valence = 0.9, danceability = 0.8, acousticness = 0.1)
        val v2 = FeatureVector(energy = 0.8, valence = 0.9, danceability = 0.8, acousticness = 0.1)
        assertEquals(0.0, v1.distanceTo(v2), 1e-6)

        val v3 = FeatureVector(energy = 0.2, valence = 0.3, danceability = 0.4, acousticness = 0.8)
        val dist = v1.distanceTo(v3)
        // sqrt((0.6)^2 + (0.6)^2 + (0.4)^2 + (-0.7)^2) = sqrt(0.36 + 0.36 + 0.16 + 0.49) = sqrt(1.37) ≈ 1.17046999
        assertTrue(dist > 1.15 && dist < 1.20)
    }

    @Test
    fun testSlowTrapTrackIsSkippedByRadioEngine() {
        val mockLibrary = listOf(
            SonicTrack("1", "Levitating", "Dua Lipa", setOf("Synth-Pop", "Dance-Pop"), FeatureVector(0.8, 0.9, 0.8, 0.1)),
            SonicTrack("2", "As It Was", "Harry Styles", setOf("Indie-Pop", "Synth-Pop"), FeatureVector(0.75, 0.85, 0.78, 0.2)),
            SonicTrack("3", "Bad Habits", "Ed Sheeran", setOf("Dance-Pop"), FeatureVector(0.82, 0.7, 0.8, 0.05)),
            SonicTrack("4", "Cruel Summer", "Taylor Swift", setOf("Synth-Pop"), FeatureVector(0.78, 0.75, 0.75, 0.15)),
            // TRAP SONG: Tagged with "Pop" but sonically a slow acoustic ballad
            SonicTrack("5", "Pop Ballad Track 5", "Slow Artist", setOf("Traditional-Pop"), FeatureVector(0.2, 0.3, 0.4, 0.8)),
            SonicTrack("6", "Blinding Lights", "The Weeknd", setOf("Synth-Pop", "New Wave"), FeatureVector(0.85, 0.65, 0.75, 0.01))
        )

        val engine = RadioEngine(mockLibrary)
        val seedSong = mockLibrary[0] // Levitating

        val similarTracks = engine.getSimilarTracks(seedSong, limit = 4)

        // Seed song itself should never be returned
        assertFalse(similarTracks.any { it.id == "1" })

        // The slow trap song (id 5) must not be in the top 4 similar tracks
        assertFalse(similarTracks.any { it.id == "5" })

        // Ensure high-energy synth-pop / dance-pop matches occupy the top candidates
        assertEquals(4, similarTracks.size)
        val candidateIds = similarTracks.map { it.id }.toSet()
        assertTrue(candidateIds.containsAll(listOf("2", "3", "4", "6")))
    }

    @Test
    fun testSubgenreIntersectionDiscount() {
        // Track A and Track B have identical sonic features, but Track B shares a sub-genre with Seed
        val seed = SonicTrack("s", "Seed", "Artist S", setOf("Synth-Pop"), FeatureVector(0.5, 0.5, 0.5, 0.5))
        val trackA = SonicTrack("a", "Track A", "Artist A", setOf("Country"), FeatureVector(0.6, 0.6, 0.6, 0.6))
        val trackB = SonicTrack("b", "Track B", "Artist B", setOf("Synth-Pop"), FeatureVector(0.6, 0.6, 0.6, 0.6))

        val engine = RadioEngine(listOf(trackA, trackB))
        val results = engine.getSimilarTracks(seed, limit = 2)

        // Track B must be ranked ahead of Track A because of the 20% shared sub-genre boost
        assertEquals("b", results[0].id)
        assertEquals("a", results[1].id)
    }

    @Test
    fun testRadioSessionManagerRollingLookaheadAndAntiFatigue() {
        val mockLibrary = listOf(
            SonicTrack("1", "Levitating", "Dua Lipa", setOf("Synth-Pop"), FeatureVector(0.8, 0.9, 0.8, 0.1)),
            SonicTrack("2", "Physical", "Dua Lipa", setOf("Synth-Pop"), FeatureVector(0.82, 0.88, 0.82, 0.1)),
            SonicTrack("3", "Hallucinate", "Dua Lipa", setOf("Synth-Pop"), FeatureVector(0.81, 0.89, 0.81, 0.1)),
            SonicTrack("4", "As It Was", "Harry Styles", setOf("Synth-Pop"), FeatureVector(0.75, 0.85, 0.78, 0.2)),
            SonicTrack("5", "Bad Habits", "Ed Sheeran", setOf("Synth-Pop"), FeatureVector(0.82, 0.7, 0.8, 0.05)),
            SonicTrack("6", "Cruel Summer", "Taylor Swift", setOf("Synth-Pop"), FeatureVector(0.78, 0.75, 0.75, 0.15)),
            SonicTrack("7", "Blinding Lights", "The Weeknd", setOf("Synth-Pop"), FeatureVector(0.85, 0.65, 0.75, 0.01))
        )

        val engine = RadioEngine(mockLibrary)
        val seed = mockLibrary[0] // Dua Lipa - Levitating
        val session = RadioSessionManager(seed, engine)

        // Initial queue should start with the seed track, and lookahead top-up fills it
        val initialQueue = session.getUpcomingQueue()
        assertTrue(initialQueue.isNotEmpty())
        assertEquals(seed.id, initialQueue.first().id)

        // Pop 1st song (seed)
        val firstPlayed = session.nextTrack()
        assertNotNull(firstPlayed)
        assertEquals("Dua Lipa", firstPlayed?.artist)

        // As we advance through songs, verify artist fatigue prevents playing Dua Lipa consecutively
        val playedArtists = mutableListOf<String>()
        for (i in 0 until 4) {
            val track = session.nextTrack()
            if (track != null) {
                playedArtists.add(track.artist)
            }
        }

        // Played list should have diversity across artists
        val uniqueArtists = playedArtists.toSet()
        assertTrue("Expected artist variety, got: $playedArtists", uniqueArtists.size >= 2)
    }

    @Test
    fun testThumbsUpVectorNudge() {
        val initialVector = FeatureVector(energy = 0.8, valence = 0.9, danceability = 0.8, acousticness = 0.1)
        val likedTrackVector = FeatureVector(energy = 0.4, valence = 0.5, danceability = 0.4, acousticness = 0.7)

        val seed = SonicTrack("s", "Seed", "Artist S", setOf("Pop"), initialVector)
        val engine = RadioEngine(listOf(seed))
        val session = RadioSessionManager(seed, engine)

        assertEquals(0.8, session.currentTargetFeatures.energy, 1e-6)

        // Thumbs up the lower energy / higher acousticness track
        val likedTrack = SonicTrack("l", "Liked", "Artist L", setOf("Acoustic"), likedTrackVector)
        val steered = session.onThumbsUp(likedTrack, weight = 0.5)

        // Lerp with weight 0.5: 0.8 + (0.4 - 0.8)*0.5 = 0.6
        assertEquals(0.6, steered.energy, 1e-6)
        // Valence: 0.9 + (0.5 - 0.9)*0.5 = 0.7
        assertEquals(0.7, steered.valence, 1e-6)
        // Acousticness: 0.1 + (0.7 - 0.1)*0.5 = 0.4
        assertEquals(0.4, steered.acousticness, 1e-6)
        assertEquals(steered, session.currentTargetFeatures)
    }

    @Test
    fun testThumbsDownVectorPushAwayAndPurge() {
        val seed = SonicTrack("s", "Seed", "Artist S", setOf("Pop"), FeatureVector(0.7, 0.7, 0.7, 0.2))
        val disliked = SonicTrack("d", "Disliked", "Artist D", setOf("Pop"), FeatureVector(0.9, 0.9, 0.9, 0.1))
        val alternative = SonicTrack("a", "Alternative", "Artist A", setOf("Pop"), FeatureVector(0.6, 0.6, 0.6, 0.3))

        val engine = RadioEngine(listOf(seed, disliked, alternative))
        val session = RadioSessionManager(seed, engine)

        // Thumbs down disliked track
        val repelled = session.onThumbsDown(disliked, weight = 0.5)

        // Energy pushed away: 0.7 - (0.9 - 0.7)*0.5 = 0.6
        assertEquals(0.6, repelled.energy, 1e-6)

        // Ensure the disliked track is purged and not present in upcoming queue
        val upcoming = session.getUpcomingQueue()
        assertFalse(upcoming.any { it.id == disliked.id })
    }

    @Test
    fun testDynamicQueueReshapingAfterThumbs() {
        val seed = SonicTrack("s", "Seed", "Artist S", emptySet(), FeatureVector(0.8, 0.8, 0.8, 0.1))
        val danceTrack = SonicTrack("d", "Dance Track", "Artist D", emptySet(), FeatureVector(0.85, 0.85, 0.85, 0.05))
        val chillTrack1 = SonicTrack("c1", "Chill Track 1", "Artist C1", emptySet(), FeatureVector(0.3, 0.4, 0.3, 0.8))
        val chillTrack2 = SonicTrack("c2", "Chill Track 2", "Artist C2", emptySet(), FeatureVector(0.25, 0.35, 0.3, 0.85))

        val engine = RadioEngine(listOf(seed, danceTrack, chillTrack1, chillTrack2))
        val session = RadioSessionManager(seed, engine)

        // Initially seeded from high energy pop (0.8, 0.8) -> danceTrack is closer
        val initialSimilar = engine.getSimilarTracks(session.currentTargetFeatures, excludeIds = setOf(seed.id), limit = 1)
        assertEquals("d", initialSimilar.first().id)

        // User repeatedly thumbs up chill tracks, steering target vector towards chill acoustic
        session.onThumbsUp(chillTrack1, weight = 0.8)
        session.onThumbsUp(chillTrack2, weight = 0.8)

        // Target features must have morphed to chill/acoustic coordinates
        assertTrue(session.currentTargetFeatures.energy < 0.45)
        assertTrue(session.currentTargetFeatures.acousticness > 0.60)

        // Now chill tracks must be ranked higher than the high-energy dance track
        val reRanked = engine.getSimilarTracks(session.currentTargetFeatures, excludeIds = setOf(seed.id), limit = 2)
        val topIds = reRanked.map { it.id }
        assertTrue(topIds.contains("c1") || topIds.contains("c2"))
    }
}
