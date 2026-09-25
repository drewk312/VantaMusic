package com.audiophile.musicplayer.radio.sonic

import android.util.Log
import com.audiophile.musicplayer.data.local.TrackFeatureDao
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.TrackFeatureEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service coordinating audio feature extraction, database vector persistence,
 * and RadioEngine session creation.
 */
class SonicRadioService(
    private val trackFeatureDao: TrackFeatureDao,
    private val featureExtractor: AudioFeatureExtractor = AudioFeatureExtractor()
) {
    private val TAG = "SonicRadioService"

    /**
     * Resolves or generates the sonic DNA for a [UnifiedTrackWithSources].
     */
    suspend fun resolveSonicTrack(unified: UnifiedTrackWithSources): SonicTrack = withContext(Dispatchers.IO) {
        val trackId = unified.track.trackId.toString()
        val cached = trackFeatureDao.getByTrackId(trackId)
        if (cached != null) {
            return@withContext cached.toSonicTrack().copy(rawUnifiedTrack = unified)
        }

        // Estimate initial features from metadata
        val genres = unified.track.genre?.split(",", ";", "/")?.map { it.trim() } ?: emptyList()
        val vector = featureExtractor.estimateFromMetadata(
            title = unified.track.title,
            artist = unified.track.artist,
            genres = genres
        )

        val sonicTrack = SonicTrack.fromUnifiedTrack(unified, vector)
        val entity = TrackFeatureEntity.fromSonicTrack(sonicTrack)
        trackFeatureDao.upsert(entity)
        Log.d(TAG, "Indexed sonic features for '${unified.track.title}': $vector")
        sonicTrack
    }

    /**
     * Resolves or generates the sonic DNA for a [LocalSongEntity].
     */
    suspend fun resolveSonicTrack(song: LocalSongEntity): SonicTrack = withContext(Dispatchers.IO) {
        val trackId = song.id.toString()
        val cached = trackFeatureDao.getByTrackId(trackId)
        if (cached != null) {
            return@withContext cached.toSonicTrack().copy(rawLocalSong = song)
        }

        val vector = featureExtractor.estimateFromMetadata(
            title = song.title,
            artist = song.artist,
            genres = song.genres
        )

        val sonicTrack = SonicTrack.fromLocalSong(song, vector)
        val entity = TrackFeatureEntity.fromSonicTrack(sonicTrack)
        trackFeatureDao.upsert(entity)
        Log.d(TAG, "Indexed sonic features for '${song.title}': $vector")
        sonicTrack
    }

    /**
     * Creates a new [RadioSessionManager] for a seed track by querying the vector database
     * and supplementing from the provided memory pool if needed.
     */
    suspend fun createRadioSession(
        seed: SonicTrack,
        fallbackLibrary: List<SonicTrack> = emptyList()
    ): RadioSessionManager = withContext(Dispatchers.IO) {
        val dbCandidates = trackFeatureDao.getNearbyCandidateTracks(
            excludeId = seed.id,
            targetEnergy = seed.features.energy,
            targetValence = seed.features.valence,
            targetDanceability = seed.features.danceability,
            targetAcousticness = seed.features.acousticness,
            window = 0.40,
            limit = 50
        ).map { it.track.toSonicTrack() }

        // Combine DB candidates with fallback library items
        val combinedLibrary = (dbCandidates + fallbackLibrary)
            .distinctBy { it.id }
            .filter { it.id != seed.id }

        Log.d(TAG, "Starting radio session for '${seed.title}' with ${combinedLibrary.size} candidate tracks")
        val engine = RadioEngine(combinedLibrary)
        RadioSessionManager(seed, engine)
    }

    /**
     * Indexes a batch of library tracks into the vector database.
     */
    suspend fun indexLibrary(tracks: List<UnifiedTrackWithSources>) = withContext(Dispatchers.IO) {
        val entities = tracks.map { track ->
            val genres = track.track.genre?.split(",", ";", "/")?.map { it.trim() } ?: emptyList()
            val vector = featureExtractor.estimateFromMetadata(
                title = track.track.title,
                artist = track.track.artist,
                genres = genres
            )
            TrackFeatureEntity(
                trackId = track.track.trackId.toString(),
                title = track.track.title,
                artist = track.track.artist,
                subGenres = genres.joinToString(","),
                energy = vector.energy,
                valence = vector.valence,
                danceability = vector.danceability,
                acousticness = vector.acousticness
            )
        }
        trackFeatureDao.upsertAll(entities)
        Log.d(TAG, "Indexed ${entities.size} tracks into track_features")
    }
}
