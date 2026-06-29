package com.audiophile.musicplayer.data.source.external

object PlaybackProviderKind {
    const val ADDON = "addon"
    const val QOBUZ = "qobuz"
    const val TIDAL = "tidal"
    const val DEEZER = "deezer"
    const val PANDORA = "pandora"
    const val AMAZON = "amazon"

    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        QOBUZ -> QOBUZ
        TIDAL -> TIDAL
        DEEZER -> DEEZER
        PANDORA -> PANDORA
        AMAZON -> AMAZON
        else -> ADDON
    }

    fun isBundled(id: String): Boolean =
        id in setOf(
            "qobuz_tidal",
            "qobuz_gateway",
            "tidal_gateway",
            "deezer_gateway",
            "pandora_gateway",
            "amazon_gateway"
        )
}
