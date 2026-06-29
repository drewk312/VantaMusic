package com.audiophile.musicplayer.data.source.external

data class ExternalSourceConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val enabled: Boolean,
    val priority: Int = 0,
    /** addon | qobuz | tidal — controls SpotiFLAC-style gateway routing. */
    val providerKind: String = PlaybackProviderKind.ADDON,
    /** Optional catalog/search gateway; defaults to [baseUrl]. */
    val searchBaseUrl: String? = null,
    /** Optional community/custom stream endpoint (SpotiFLAC /api/dl pattern). */
    val streamEndpointUrl: String? = null,
    val disabledByDefault: Boolean = false,
    val lastTestedAt: Long? = null,
    val lastStatus: String? = null,
    val healthStatus: String? = "unknown",
    val lastError: String? = null,
    val avgSearchMs: Long? = null,
    val lastSearchMs: Long? = null,
    val capabilities: List<String>? = null,
    val canSearch: Boolean? = true,
    val canStream: Boolean? = true,
    val canBrowse: Boolean? = false,
    val canAlbum: Boolean? = false,
    val canArtist: Boolean? = false,
    val canPlaylist: Boolean? = false
)
