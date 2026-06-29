package com.audiophile.musicplayer.data.resolution

class CachedResolverService(
    private val cacheRepository: ResolutionCacheRepository,
    addons: List<ResolverAddon>
) {
    private val addonsByProviderId = addons.associateBy { it.provider.providerId }

    suspend fun registerConfiguredProviders() {
        addonsByProviderId.values.forEach { addon ->
            cacheRepository.upsertProvider(addon.provider)
        }
    }

    /**
     * Resolves from cache first, then configured addons, then stores the result.
     */
    suspend fun resolve(
        query: TrackLookupQuery,
        allowedProviderIds: Set<String>? = null
    ): ScoredCacheCandidate? {
        val cached = cacheRepository.findBestCachedMatch(query, allowedProviderIds)
        if (cached != null) {
            cacheRepository.markCacheHit(cached.entry.cacheEntryId)
            return cached
        }

        val addons = addonsByProviderId.values
            .asSequence()
            .filter { allowedProviderIds == null || it.provider.providerId in allowedProviderIds }
            .toList()

        for (addon in addons) {
            val record = addon.resolve(query) ?: continue
            cacheRepository.storeResolution(query, record)
        }

        val refreshed = cacheRepository.findBestCachedMatch(query, allowedProviderIds)
        refreshed?.let { cacheRepository.markCacheHit(it.entry.cacheEntryId) }
        return refreshed
    }
}
