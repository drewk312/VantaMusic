package com.audiophile.musicplayer.data.resolution

import com.audiophile.musicplayer.data.local.entities.AddonProvider
import com.audiophile.musicplayer.data.remote.ResolverRetrofitFactory

object ResolverBootstrap {

    fun createCommunityAddon(
        providerId: String,
        displayName: String,
        baseUrl: String
    ): CommunityInstanceResolverAddon {
        val provider = AddonProvider(
            providerId = providerId,
            displayName = displayName,
            baseUrl = baseUrl,
            providerKind = "community_instance",
            capabilitySearch = true,
            capabilityPlayback = true,
            capabilityDownload = false,
            capabilityRadio = true
        )
        return CommunityInstanceResolverAddon(
            provider = provider,
            api = ResolverRetrofitFactory.createCatalogApi(baseUrl)
        )
    }

    fun createTorBoxAddon(
        displayName: String = "TorBox Cache",
        client: TorBoxMetadataResolverClient
    ): TorBoxCacheResolverAddon {
        val provider = AddonProvider(
            providerId = "torbox_cache",
            displayName = displayName,
            providerKind = "owned_backend",
            capabilitySearch = true,
            capabilityPlayback = true,
            capabilityDownload = true,
            capabilityRadio = false
        )
        return TorBoxCacheResolverAddon(provider = provider, client = client)
    }
}
