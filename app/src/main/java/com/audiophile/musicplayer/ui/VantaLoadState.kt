package com.audiophile.musicplayer.ui

enum class VantaLoadFailureReason {
    NONE,
    INITIAL_TRACKS_FAILED,
    INITIAL_CONTAINERS_FAILED,
    INITIAL_METADATA_FAILED,
    INITIAL_LYRICS_FAILED,
    INITIAL_LIBRARY_STATE_FAILED,
    PROVIDER_SEARCH_FAILED,
    PROVIDER_SEARCH_EMPTY,
    SOURCE_RESOLVE_FAILED,
    STREAM_VALIDATION_FAILED,
    RESOURCES_FAILED,
    ARTWORK_FAILED,
    LOCAL_DATABASE_EMPTY,
    LOCAL_DATABASE_ERROR,
    MISSING_PROVIDER_IDENTITY,
    UNSUPPORTED_SOURCE,
    CONFIG_DISABLED,
    NETWORK_UNAVAILABLE,
    UNKNOWN
}

data class VantaLoadState(
    val isLoading: Boolean = false,
    val hasData: Boolean = false,
    val failureReason: VantaLoadFailureReason = VantaLoadFailureReason.NONE,
    val message: String? = null,
    val retryable: Boolean = false
)
