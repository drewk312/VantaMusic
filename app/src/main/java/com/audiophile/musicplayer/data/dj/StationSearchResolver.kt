package com.audiophile.musicplayer.data.dj

object StationSearchResolver {
    private data class SearchableStation(
        val station: JukeboxStation,
        val aliases: List<String>,
        val baseScore: Int = 0
    )

    private val eraWordAliases = mapOf(
        "50s" to listOf("fifties", "1950s", "1950", "50's"),
        "60s" to listOf("sixties", "1960s", "1960", "60's"),
        "70s" to listOf("seventies", "1970s", "1970", "70's"),
        "80s" to listOf("eighties", "1980s", "1980", "80's"),
        "90s" to listOf("nineties", "1990s", "1990", "90's"),
        "2000s" to listOf("two thousands", "00s", "2000", "2000's", "aughts"),
        "2010s" to listOf("twenty tens", "10s", "2010", "2010's", "teens")
    )

    fun resolve(query: String): List<JukeboxStation> {
        val normalized = normalize(query)
        if (normalized.length < 2) return emptyList()

        if (normalized.contains("oldies") || normalized == "50s" || normalized == "60s" || normalized == "golden oldies") {
            val oldies = listOfNotNull(
                JukeboxCatalog.stations.find { it.id == "golden_oldies" },
                JukeboxCatalog.stations.find { it.id == "fifties_rock_roll" },
                JukeboxCatalog.stations.find { it.id == "sixties_invasion" },
                JukeboxCatalog.stations.find { it.id == "motown_soul" },
                JukeboxCatalog.stations.find { it.id == "doo_wop_classics" },
                JukeboxCatalog.stations.find { it.id == "rockabilly_stomp" }
            )
            if (oldies.isNotEmpty()) return oldies
        }
        if (normalized.contains("yacht")) {
            val yacht = listOfNotNull(JukeboxCatalog.stations.find { it.id == "yacht_rock" })
            if (yacht.isNotEmpty()) return yacht
        }
        if (normalized.contains("wonder") || normalized.contains("one hit") || normalized.contains("one-hit")) {
            val wonders = listOfNotNull(JukeboxCatalog.stations.find { it.id == "one_hit_wonders" })
            if (wonders.isNotEmpty()) return wonders
        }

        return rankedCandidates(normalized)
            .distinctBy { it.first.id }
            .take(8)
            .map { it.first }
    }

    /**
     * Strict station matching for the global music search box.
     */
    fun resolveForSearch(query: String): List<JukeboxStation> {
        val normalized = normalize(query)
        if (normalized.length < 2) return emptyList()

        return rankedCandidates(normalized)
            .filter { (_, score) -> score >= STRICT_SEARCH_SCORE }
            .distinctBy { it.first.id }
            .take(8)
            .map { it.first }
    }

    fun resolveSingle(query: String): JukeboxStation? = resolve(query).firstOrNull()

    private fun buildSearchableStations(): List<SearchableStation> = buildList {
        JukeboxCatalog.eras.forEach { era ->
            val station = JukeboxCatalog.stationFromEra(era.id) ?: return@forEach
            val aliases = buildList {
                add(era.label)
                add(station.name)
                add("${era.label} radio")
                add("${era.label} hits")
                addAll(eraWordAliases[era.id].orEmpty())
                addAll(station.genreKeywords)
            }
            add(SearchableStation(station, aliases, baseScore = 10))
        }

        JukeboxCatalog.genres.forEach { genre ->
            val station = JukeboxCatalog.stationFromGenre(genre.id) ?: return@forEach
            add(
                SearchableStation(
                    station = station,
                    aliases = buildList {
                        add(genre.label)
                        add(station.name)
                        addAll(genre.keywords)
                    },
                    baseScore = 8
                )
            )
        }

        MoodStationCatalog.moods.forEach { mood ->
            val station = MoodStationCatalog.stationFromMood(mood.id) ?: return@forEach
            add(
                SearchableStation(
                    station = station,
                    aliases = buildList {
                        add(mood.label)
                        add(station.name)
                        addAll(mood.aliases)
                    },
                    baseScore = 9
                )
            )
        }

        JukeboxCatalog.stations.forEach { station ->
            add(
                SearchableStation(
                    station = station,
                    aliases = buildList {
                        add(station.name)
                        addAll(station.genreKeywords)
                    },
                    baseScore = 6
                )
            )
        }
    }

    private fun scoreCandidate(normalizedQuery: String, candidate: SearchableStation): Int {
        var best = 0
        for (alias in candidate.aliases) {
            val normalizedAlias = normalize(alias)
            if (normalizedAlias.isBlank()) continue
            best = maxOf(best, scoreAlias(normalizedQuery, normalizedAlias))
        }
        return if (best > 0) best + candidate.baseScore else 0
    }

    private fun rankedCandidates(normalizedQuery: String): List<Pair<JukeboxStation, Int>> =
        buildSearchableStations()
            .mapNotNull { candidate ->
                val score = scoreCandidate(normalizedQuery, candidate)
                if (score <= 0) null else candidate.station to score
            }
            .sortedWith(
                compareByDescending<Pair<JukeboxStation, Int>> { it.second }
                    .thenBy { it.first.name.length }
            )

    private fun scoreAlias(query: String, alias: String): Int = when {
        query == alias -> 1000
        alias == query.removeSuffix(" radio") -> 950
        alias == query.removeSuffix(" hits") -> 940
        alias.startsWith(query) && query.length >= 2 -> 800 - (alias.length - query.length)
        query.startsWith(alias) && alias.length >= 3 -> 700 - (query.length - alias.length)
        query.contains(alias) && alias.length >= 3 -> 500
        alias.contains(query) && query.length >= 3 -> 450
        else -> 0
    }

    private fun normalize(raw: String): String =
        raw.trim()
            .lowercase()
            .replace("'", "")
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private const val STRICT_SEARCH_SCORE = 900
}
