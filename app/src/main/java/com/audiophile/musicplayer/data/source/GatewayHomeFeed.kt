package com.audiophile.musicplayer.data.source

data class GatewayHomePlaylist(
    val id: String,
    val name: String,
    val curator: String,
    val artworkUrl: String?,
    val description: String?,
    val source: String = "apple"
) {
    fun isDeezerCatalog(): Boolean =
        source.equals("deezer", ignoreCase = true) || (id.isNotBlank() && id.all { it.isDigit() })
}

fun com.audiophile.musicplayer.data.canonical.CanonicalPlaylist.toGatewayHomePlaylist(): GatewayHomePlaylist =
    GatewayHomePlaylist(
        id = id.orEmpty(),
        name = title,
        curator = curator?.takeIf { it.isNotBlank() } ?: "Playlist",
        artworkUrl = artworkUrl,
        description = description,
        source = source
    )

fun GatewayHomePlaylist.toCanonicalPlaylist(): com.audiophile.musicplayer.data.canonical.CanonicalPlaylist =
    com.audiophile.musicplayer.data.canonical.CanonicalPlaylist(
        title = name,
        id = id,
        curator = curator,
        artworkUrl = artworkUrl,
        description = description,
        source = source
    )

private val CURATOR_BRAND_PATTERNS = listOf(
    Regex("(?i)apple\\s*music"),
    Regex("(?i)apple\\s+radio"),
    Regex("(?i)itunes"),
    Regex("(?i)deezer"),
    Regex("(?i)spotify"),
    Regex("(?i)amazon\\s*music"),
    Regex("(?i)tidal"),
    Regex("(?i)qobuz")
)

/** Strip provider branding from curated labels so suggestions never advertise a service. */
fun debrandCuratorLabel(curator: String): String {
    val cleaned = CURATOR_BRAND_PATTERNS.fold(curator) { current, pattern -> pattern.replace(current, "") }
        .trim()
        .trim(' ')
        .trimStart(':', ' ', '-')
        .trim()
        .trimEnd(':', ' ', '-')
        .trim()
    return cleaned.ifBlank { "Curated" }
}

fun parseGatewayPlaylistCards(array: com.google.gson.JsonArray?): List<GatewayHomePlaylist> {
    if (array == null) return emptyList()
    return buildList {
        for (item in array) {
            val card = item.asJsonObject ?: continue
            val id = card.get("id")?.asString?.trim().orEmpty()
            val name = (card.get("name")?.asString ?: card.get("title")?.asString)?.trim().orEmpty()
            if (id.isBlank() || name.isBlank()) continue
            add(
                GatewayHomePlaylist(
                    id = id,
                    name = name,
                    curator = card.get("curator")?.asString?.takeIf { it.isNotBlank() } ?: "Playlist",
                    artworkUrl = card.get("artworkURL")?.asString ?: card.get("artworkUrl")?.asString,
                    description = card.get("description")?.asString,
                    source = card.get("source")?.asString?.takeIf { it.isNotBlank() }
                        ?: if (id.all { it.isDigit() }) "deezer" else "apple"
                )
            )
        }
    }
}

data class GatewayHomeFeed(
    val storefront: String,
    val updatedAt: Long,
    val playlists: List<GatewayHomePlaylist>,
    val freshDrops: List<SourceSearchResult>,
    val popularTracks: List<SourceSearchResult>,
    val trendingNow: List<SourceSearchResult>
) {
    fun isEmpty(): Boolean = playlists.isEmpty() && freshDrops.isEmpty() &&
        popularTracks.isEmpty() && trendingNow.isEmpty()
}
