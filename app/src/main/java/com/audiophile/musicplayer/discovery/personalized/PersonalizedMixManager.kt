package com.audiophile.musicplayer.discovery.personalized

import android.util.Log
import com.audiophile.musicplayer.data.local.PersonalizedMixDao
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixHistoryEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixTrackEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Sole owner of personalized mix persistence — architecture derived from SoulSync
 * `core/personalized/manager.py`. Atomic snapshot replace; failures preserve prior snapshot.
 */
class PersonalizedMixManager(
    private val dao: PersonalizedMixDao,
    private val registry: PersonalizedMixRegistry,
    private val deps: PersonalizedMixDeps,
    private val trackRepository: TrackRepository
) {
    companion object {
        private const val TAG = "PersonalizedMixManager"
        private const val MAX_ERROR_LEN = 500
    }

    suspend fun ensureMix(kind: PersonalizedMixKind, variant: String = ""): PersonalizedMixRecord =
        withContext(Dispatchers.IO) {
            val spec = registry.get(kind) ?: error("Unknown mix kind: $kind")
            val existing = dao.getMix(kind.id, variant)
            if (existing != null) return@withContext toRecord(existing, kind)
            val now = System.currentTimeMillis()
            val entity = PersonalizedMixEntity(
                kind = kind.id,
                variant = variant,
                name = spec.displayName,
                configJson = PersonalizedMixConfig.toJson(spec.defaultConfig),
                createdAt = now,
                updatedAt = now
            )
            val id = dao.insertMix(entity)
            dao.getMix(kind.id, variant)?.copy(id = id)?.let { toRecord(it, kind) }
                ?: toRecord(entity.copy(id = id), kind)
        }

    suspend fun getSnapshot(kind: PersonalizedMixKind, variant: String = ""): PersonalizedMixSnapshot? =
        withContext(Dispatchers.IO) {
            val entity = dao.getMix(kind.id, variant) ?: return@withContext null
            val tracks = dao.getTracksForMix(entity.id).map { row ->
                PersonalizedMixTrackRow(
                    position = row.position,
                    trackId = row.trackId,
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    artworkUrl = row.artworkUrl,
                    providerId = row.providerId,
                    externalTrackId = row.externalTrackId,
                    normKey = row.normKey
                )
            }
            PersonalizedMixSnapshot(toRecord(entity, kind), tracks)
        }

    suspend fun listSnapshots(): List<PersonalizedMixSnapshot> = withContext(Dispatchers.IO) {
        dao.listMixes().mapNotNull { entity ->
            val kind = PersonalizedMixKind.fromId(entity.kind) ?: return@mapNotNull null
            getSnapshot(kind, entity.variant)
        }
    }

    suspend fun markKindsStale(kinds: Collection<PersonalizedMixKind>) {
        withContext(Dispatchers.IO) {
            dao.markKindsStale(kinds.map { it.id })
        }
    }

    suspend fun refreshMix(
        kind: PersonalizedMixKind,
        variant: String = "",
        configOverrides: PersonalizedMixConfig? = null
    ): PersonalizedMixRecord = withContext(Dispatchers.IO) {
        val spec = registry.get(kind) ?: error("Unknown mix kind: $kind")
        val record = ensureMix(kind, variant)
        val effectiveConfig = record.config.let { base ->
            configOverrides?.let { base.merged(it) } ?: base
        }

        val candidates: List<MixCandidate>
        try {
            candidates = spec.generator.generate(deps, variant, effectiveConfig)
        } catch (e: Exception) {
            Log.e(TAG, "generator_failed kind=${kind.id}", e)
            val message = e.message?.take(MAX_ERROR_LEN) ?: e.javaClass.simpleName
            dao.stampGenerationError(record.id, message)
            return@withContext record.copy(lastGenerationError = message)
        }

        if (candidates.isEmpty()) {
            val message = "Generator returned no candidates"
            Log.w(TAG, "empty_candidates kind=${kind.id}")
            dao.stampGenerationError(record.id, message)
            return@withContext record.copy(lastGenerationError = message)
        }

        val excludeKeys = recentExcludeKeys(kind, effectiveConfig.excludeRecentDays)
        val filteredCandidates = applyExcludeRecent(candidates, excludeKeys)
        val playable = deps.candidatePipeline.process(filteredCandidates, effectiveConfig, excludeKeys)

        if (playable.isEmpty()) {
            val message = "No playable tracks resolved"
            Log.w(TAG, "no_playable kind=${kind.id}")
            dao.stampGenerationError(record.id, message)
            return@withContext record.copy(lastGenerationError = message)
        }

        val now = System.currentTimeMillis()
        val trackRows = playable.mapIndexed { index, track ->
            PersonalizedMixTrackEntity(
                mixId = record.id,
                position = index,
                trackId = track.track.trackId,
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                artworkUrl = track.track.coverArtUrl,
                providerId = track.sources.firstOrNull()?.externalProviderId,
                externalTrackId = track.sources.firstOrNull()?.externalTrackId,
                normKey = TrackNormKey.normalize(track.track.title, track.track.artist)
            )
        }
        val historyRows = trackRows.map { row ->
            PersonalizedMixHistoryEntity(kind = kind.id, normKey = row.normKey, servedAt = now)
        }

        dao.replaceSnapshot(record.id, trackRows, playable.size, now, historyRows)
        if (configOverrides != null) {
            dao.getMix(kind.id, variant)?.let { existing ->
                dao.updateMix(
                    existing.copy(
                        configJson = PersonalizedMixConfig.toJson(effectiveConfig),
                        updatedAt = now
                    )
                )
            }
        }

        Log.d(TAG, "refreshed kind=${kind.id} tracks=${playable.size}")
        dao.getMix(kind.id, variant)?.let { toRecord(it, kind) } ?: record
    }

    suspend fun loadPlayableTracks(kind: PersonalizedMixKind, variant: String = ""): List<UnifiedTrackWithSources> =
        withContext(Dispatchers.IO) {
            val snapshot = getSnapshot(kind, variant) ?: return@withContext emptyList()
            snapshot.tracks.mapNotNull { row ->
                row.trackId?.let { trackRepository.getTrackWithSources(it) }
            }
        }

    private suspend fun recentExcludeKeys(kind: PersonalizedMixKind, days: Int): Set<String> {
        if (days <= 0) return emptySet()
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return dao.recentNormKeys(kind.id, since).toSet()
    }

    private fun applyExcludeRecent(candidates: List<MixCandidate>, exclude: Set<String>): List<MixCandidate> {
        if (exclude.isEmpty()) return candidates
        return candidates.filter { it.normKey !in exclude }
    }

    private fun toRecord(entity: PersonalizedMixEntity, kind: PersonalizedMixKind): PersonalizedMixRecord =
        PersonalizedMixRecord(
            id = entity.id,
            kind = kind,
            variant = entity.variant,
            name = entity.name,
            config = PersonalizedMixConfig.fromJson(entity.configJson),
            trackCount = entity.trackCount,
            lastGeneratedAt = entity.lastGeneratedAt,
            lastGenerationError = entity.lastGenerationError,
            isStale = entity.isStale,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
}
