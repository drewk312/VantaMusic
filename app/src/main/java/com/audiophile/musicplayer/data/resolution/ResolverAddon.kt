package com.audiophile.musicplayer.data.resolution

import com.audiophile.musicplayer.data.local.entities.AddonProvider

/**
 * Provider adapters implement metadata resolution and can be composed behind
 * the same cache and scoring layer.
 */
interface ResolverAddon {
    val provider: AddonProvider

    suspend fun resolve(query: TrackLookupQuery): ProviderResolutionRecord?
}
