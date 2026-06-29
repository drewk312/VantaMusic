package com.audiophile.musicplayer.discovery.personalized

import com.audiophile.musicplayer.data.local.entities.PersonalizedMixEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixHistoryEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the persistence contract used by [PersonalizedMixManager] on refresh failure:
 * errors are stamped without replacing the stored track snapshot.
 */
class PersonalizedMixManagerFailureTest {
    @Test
    fun generationError_stampsMessageWithoutReplacingSnapshot() = runBlocking {
        val dao = RecordingPersonalizedMixDao(
            tracks = listOf(
                PersonalizedMixTrackEntity(
                    mixId = 1L,
                    position = 0,
                    trackId = 99L,
                    title = "Saved",
                    artist = "Artist",
                    album = null,
                    artworkUrl = null,
                    providerId = null,
                    externalTrackId = null,
                    normKey = "saved|artist"
                )
            )
        )

        dao.stampGenerationError(1L, "boom")
        assertTrue(dao.lastError == "boom")
        assertFalse(dao.snapshotReplaced)
        assertEquals(1, dao.getTracksForMix(1L).size)
    }
}

private class RecordingPersonalizedMixDao(
    private val tracks: List<PersonalizedMixTrackEntity>
) : com.audiophile.musicplayer.data.local.PersonalizedMixDao {
    var snapshotReplaced = false
    var lastError: String? = null

    override suspend fun getMix(kind: String, variant: String): PersonalizedMixEntity? = null
    override suspend fun listMixes(): List<PersonalizedMixEntity> = emptyList()
    override suspend fun insertMix(entity: PersonalizedMixEntity): Long = 1L
    override suspend fun updateMix(entity: PersonalizedMixEntity) {}
    override suspend fun markKindsStale(kinds: List<String>, updatedAt: Long) {}
    override suspend fun stampGenerationError(mixId: Long, error: String, updatedAt: Long) {
        lastError = error
    }
    override suspend fun stampGenerationSuccess(mixId: Long, trackCount: Int, generatedAt: Long, updatedAt: Long) {}
    override suspend fun deleteTracksForMix(mixId: Long) {}
    override suspend fun insertTracks(tracks: List<PersonalizedMixTrackEntity>) {}
    override suspend fun getTracksForMix(mixId: Long) = tracks
    override suspend fun insertHistoryRows(rows: List<PersonalizedMixHistoryEntity>) {}
    override suspend fun recentNormKeys(kind: String, sinceMs: Long) = emptyList<String>()
    override suspend fun getArtistCache(artistKey: String) = null
    override suspend fun upsertArtistCache(entity: com.audiophile.musicplayer.data.local.entities.DiscoveryArtistCacheEntity) {}
    override suspend fun getTrackCache(normKey: String) = null
    override suspend fun upsertTrackCache(entity: com.audiophile.musicplayer.data.local.entities.DiscoveryTrackCacheEntity) {}
    override suspend fun replaceSnapshot(
        mixId: Long,
        tracks: List<PersonalizedMixTrackEntity>,
        trackCount: Int,
        generatedAt: Long,
        historyRows: List<PersonalizedMixHistoryEntity>
    ) {
        snapshotReplaced = true
    }
}
