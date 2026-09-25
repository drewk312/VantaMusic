package com.audiophile.musicplayer.radio.sonic

class StationSearchResolver {
    fun resolveForSearch(query: String): List<SonicStationSpec> {
        val sanitized = query.lowercase().trim()
        val stations = com.audiophile.musicplayer.data.dj.JukeboxCatalog.sonicStationSpecs
        return when {
            sanitized.contains("oldies") || sanitized == "50s" || sanitized == "60s" || sanitized == "golden oldies" -> {
                listOfNotNull(
                    stations["golden_oldies"],
                    stations["fifties_rock_roll"],
                    stations["sixties_invasion"],
                    stations["motown_soul"],
                    stations["doo_wop_classics"],
                    stations["rockabilly_stomp"]
                )
            }
            sanitized.contains("yacht") -> listOfNotNull(stations["yacht_rock"])
            sanitized.contains("wonder") || sanitized.contains("one-hit") || sanitized.contains("one hit") -> listOfNotNull(stations["one_hit_wonders"])
            else -> {
                com.audiophile.musicplayer.data.dj.StationSearchResolver.resolveForSearch(query)
                    .mapNotNull { it.toSonicStationSpec() }
            }
        }
    }
}
