package com.audiophile.musicplayer.data.source.external

/**
 * Source families (gateway Goal B). The gateway is one source family among
 * several; a candidate carries its family so failover can reason about
 * independent fallbacks (gateway -> community -> public -> compressed -> local).
 */
enum class ProviderFamily {
    GATEWAY_ZARZ,
    QOBUZ,
    TIDAL,
    AMAZON,
    DEEZER,
    COMMUNITY,
    PUBLIC,
    LOCAL
}

/** Maps a provider id reported by a candidate or resolved stream to its family. */
fun familyForProvider(providerId: String?): ProviderFamily = when {
    providerId == null -> ProviderFamily.COMMUNITY
    providerId == "cloudflare_gateway" ||
        providerId == "qobuz" ||
        providerId == "tidal" ||
        providerId == "deezer" ||
        providerId == "amazon" -> ProviderFamily.GATEWAY_ZARZ
    providerId == "local" || providerId == "direct" -> ProviderFamily.LOCAL
    providerId.endsWith("_library") -> ProviderFamily.COMMUNITY
    else -> ProviderFamily.PUBLIC
}
