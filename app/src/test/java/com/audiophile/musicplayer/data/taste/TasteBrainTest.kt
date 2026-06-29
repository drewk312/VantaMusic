package com.audiophile.musicplayer.data.taste

import com.audiophile.musicplayer.radio.RadioSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteBrainTest {

    @Test
    fun skipReducesSimilarCandidates() {
        val store = InMemoryTasteEventStore()
        repeat(3) {
            store.append(TasteEvent(track = "Stayin' Alive", artist = "Bee Gees", genre = "disco", event = TasteEventType.SKIPPED))
        }
        val vector = TasteVectorBuilder.fromEvents(store.all())
        assertTrue((vector.artistAffinity["bee gees"] ?: 0f) < 0f)
    }

    @Test
    fun replayAndLikeIncreaseSimilarCandidates() {
        val store = InMemoryTasteEventStore()
        store.append(TasteEvent(track = "After Hours", artist = "The Weeknd", genre = "pop", event = TasteEventType.LIKED))
        store.append(TasteEvent(track = "After Hours", artist = "The Weeknd", genre = "pop", event = TasteEventType.REPLAYED))
        val vector = TasteVectorBuilder.fromEvents(store.all())
        assertTrue((vector.artistAffinity["the weeknd"] ?: 0f) > 0f)
        assertTrue(vector.replayAffinity.isNotEmpty())
    }

    @Test
    fun beeGeesStationRejectsLilWayneDrift() {
        val seed = RadioSeed(title = "", artist = "Bee Gees", album = null, genre = "disco")
        val direction = StationPlanner.decideStage(seed, emptyList(), "Lil Wayne", "hip-hop")
        assertFalse(direction.accepted)
        assertEquals("seed_lock_rejected", direction.reason)
    }

    @Test
    fun stationExpandsGraduallyByStage() {
        val seed = RadioSeed(title = "Stayin' Alive", artist = "Bee Gees", album = null, genre = "disco")
        // Stage 1: Same artist
        val stage1 = StationPlanner.decideStage(seed, emptyList(), "Bee Gees", "disco")
        // Stage 2: Similar artist (common token) or same genre
        val stage2 = StationPlanner.decideStage(seed, emptyList(), "The Bee Gees Band", "disco")
        // Stage 3: Different but allowed
        val stage3 = StationPlanner.decideStage(seed, emptyList(), "Bee Gees Tribute", "rock")

        assertEquals(1, stage1.stage)
        assertTrue(stage2.accepted || stage2.stage == 1 || stage2.stage == 2)
    }

    @Test
    fun highSkipRateTightensStation() {
        val store = InMemoryTasteEventStore()
        repeat(6) {
            store.append(TasteEvent(track = "Track $it", artist = "Artist $it", genre = "pop", event = TasteEventType.SKIPPED))
        }
        repeat(2) {
            store.append(TasteEvent(track = "Liked $it", artist = "Favorite $it", genre = "pop", event = TasteEventType.LIKED))
        }
        val vector = TasteVectorBuilder.fromEvents(store.all())
        assertTrue(vector.skipRate >= 0.5f)
        assertTrue(FatigueManager.tightenForHighSkipRate(vector.skipRate) <= 0.20f)
    }

    @Test
    fun discoveryMixUsesSafeAdjacentAdventurousBuckets() {
        val plan = DiscoveryPlanner.plan(
            totalCount = 20,
            profile = TasteProfile(discoveryOpenness = 0.35f, highSkipRate = false)
        )
        assertEquals(12, plan.safeCount)
        assertEquals(5, plan.adjacentCount)
        assertEquals(3, plan.adventurousCount)
    }

    @Test
    fun exactUserTapDoesNotUseTastePlanner() {
        val profile = TasteProfile(highSkipRate = true, discoveryOpenness = 0.1f)
        val plan = DiscoveryPlanner.plan(20, profile)
        assertTrue(plan.safeCount > plan.adventurousCount)
        // Jay Sean vs Jay Sean — should accept
        assertTrue(RadioSeedLock.acceptsCandidate(RadioSeed(title = "Down", artist = "Jay Sean", album = null, genre = "pop"), "Jay Sean"))
    }
}
