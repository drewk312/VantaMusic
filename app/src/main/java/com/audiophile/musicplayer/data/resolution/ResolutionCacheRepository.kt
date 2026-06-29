package com.audiophile.musicplayer.data.resolution

import com.audiophile.musicplayer.data.local.dao.ResolutionDao
import com.audiophile.musicplayer.data.local.entities.AddonProvider
import com.audiophile.musicplayer.data.local.entities.ResolutionCacheEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

class ResolutionCacheRepository(
    private val resolutionDao: ResolutionDao
) {

    suspend fun upsertProvider(provider: AddonProvider) = withContext(Dispatchers.IO) {
        resolutionDao.upsertProvider(provider)
    }

    suspend fun getEnabledProviders(): List<AddonProvider> = withContext(Dispatchers.IO) {
        resolutionDao.getEnabledProviders()
    }

    suspend fun storeResolution(
        query: TrackLookupQuery,
        record: ProviderResolutionRecord,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val entry = ResolutionCacheEntry(
            queryKey = query.queryKey,
            normalizedTitle = query.normalizedTitle,
            normalizedArtist = query.normalizedArtist,
            normalizedAlbum = query.normalizedAlbum.ifBlank { null },
            isrc = query.normalizedIsrc,
            durationMs = query.durationMs,
            linkedTrackId = record.linkedTrackId,
            providerId = record.providerId,
            providerTrackId = record.providerTrackId,
            providerAlbumId = record.providerAlbumId,
            displayTitle = record.displayTitle,
            displayArtist = record.displayArtist,
            displayAlbum = record.displayAlbum,
            availabilityStatus = record.availabilityStatus,
            qualityLabel = record.qualityLabel,
            bitrateKbps = record.bitrateKbps,
            confidenceScore = record.confidenceScore,
            negativeResult = record.negativeResult,
            streamEndpointHint = record.streamEndpointHint,
            payloadJson = record.payloadJson,
            resolvedAtEpochMs = nowEpochMs,
            expiresAtEpochMs = nowEpochMs + record.ttlMs,
            lastHitAtEpochMs = nowEpochMs
        )
        resolutionDao.upsertCacheEntry(entry)
    }

    suspend fun findBestCachedMatch(
        query: TrackLookupQuery,
        allowedProviderIds: Set<String>? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ScoredCacheCandidate? = withContext(Dispatchers.IO) {
        val providers = resolutionDao.getEnabledProviders()
        val providerMap = providers.associateBy { it.providerId }

        val candidates = linkedSetOf<ResolutionCacheEntry>()
        candidates.addAll(resolutionDao.getFreshEntriesForQueryKey(query.queryKey, nowEpochMs))
        query.normalizedIsrc?.let {
            candidates.addAll(resolutionDao.getFreshEntriesForIsrc(it, nowEpochMs))
        }

        candidates
            .asSequence()
            .filter { !it.negativeResult }
            .filter { allowedProviderIds == null || it.providerId in allowedProviderIds }
            .filter { providerMap[it.providerId]?.isEnabled != false }
            .map { entry ->
                ScoredCacheCandidate(
                    entry = entry,
                    provider = providerMap[entry.providerId],
                    score = scoreEntry(query, entry, nowEpochMs)
                )
            }
            .sortedWith(
                compareByDescending<ScoredCacheCandidate> { it.score }
                    .thenByDescending { it.entry.confidenceScore }
                    .thenByDescending { it.entry.hitCount }
                    .thenByDescending { it.entry.resolvedAtEpochMs }
            )
            .firstOrNull()
    }

    suspend fun markCacheHit(cacheEntryId: Long, nowEpochMs: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            resolutionDao.markCacheHit(cacheEntryId, nowEpochMs)
        }

    suspend fun purgeExpired(nowEpochMs: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) {
            resolutionDao.purgeExpired(nowEpochMs)
        }

    private fun scoreEntry(
        query: TrackLookupQuery,
        entry: ResolutionCacheEntry,
        nowEpochMs: Long
    ): Int {
        var score = 0

        if (query.normalizedIsrc != null && query.normalizedIsrc == entry.isrc) {
            score += 1000
        }
        if (query.normalizedTitle == entry.normalizedTitle) {
            score += 180
        }
        if (query.normalizedArtist == entry.normalizedArtist) {
            score += 180
        }
        if (query.normalizedAlbum.isNotBlank() && query.normalizedAlbum == entry.normalizedAlbum) {
            score += 50
        }
        if (entry.availabilityStatus == "available") {
            score += 60
        }
        score += min(entry.hitCount * 3, 45)
        score += min(entry.bitrateKbps ?: 0, 2000) / 40
        score += (entry.confidenceScore * 100).toInt()

        val durationDelta = if (query.durationMs != null && entry.durationMs != null) {
            abs(query.durationMs - entry.durationMs)
        } else {
            null
        }
        durationDelta?.let { delta ->
            score += when {
                delta <= 2_000 -> 90
                delta <= 10_000 -> 40
                delta <= 20_000 -> 10
                else -> -40
            }
        }

        val ageMs = (nowEpochMs - entry.resolvedAtEpochMs).coerceAtLeast(0L)
        score -= ((ageMs / 86_400_000L).coerceAtMost(30L)).toInt()

        return score.coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
    }
}
