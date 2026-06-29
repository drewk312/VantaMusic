package com.audiophile.musicplayer.data.metadata

enum class MetadataMergePolicy {
    /** Overwrites local data with the provider's data, regardless of local state. */
    OVERWRITE_LOCAL,
    
    /** Preserves local data; only fills fields that are currently empty/null. */
    FILL_EMPTY_ONLY,
    
    /** Intelligently merges based on confidence score. Will overwrite if provider confidence is very high. */
    SMART_MERGE
}
