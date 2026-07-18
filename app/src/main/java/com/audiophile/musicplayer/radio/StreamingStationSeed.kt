package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.dj.JukeboxStation
import com.audiophile.musicplayer.data.dj.StationSearchResolver
import com.audiophile.musicplayer.data.dj.StreamingSeedParams

/** How a station is sourced at playback time. */
enum class StationPlaybackMode {
    /** Stream from cloud/catalog providers; library is optional enrichment only. */
    STREAMING_STATION,
    /** Match and play only from the on-device library (legacy jukebox). */
    LIBRARY_ONLY
}

enum class StreamingStationKind {
    FREE_TEXT,
    GENRE,
    MOOD,
    ACTIVITY,
    ERA,
    ARTIST,
    SONG,
    SONG_SIMILAR,
    JUKEBOX_PRESET
}

/**
 * Canonical seed for Pandora-style streaming stations.
 * Supports genre, mood, activity, era, artist, song, and arbitrary free-text requests.
 */
data class StreamingStationSeed(
    val id: String,
    val displayName: String,
    val kind: StreamingStationKind,
    val queryPhrases: List<String> = emptyList(),
    val eraStart: Int? = null,
    val eraEnd: Int? = null,
    val seedArtist: String? = null,
    val seedTitle: String? = null,
    val seedArtists: List<String> = emptyList(),
    val hintKeywords: List<String> = emptyList()
) {
    val primaryQuery: String
        get() = StationQuerySanitizer.resolvePrimaryQuery(
            queryPhrases = queryPhrases,
            displayName = displayName,
            hintKeywords = hintKeywords,
            seedArtists = seedArtists
        )
}

/** Filters user-facing station labels (e.g. "Country Radio") out of catalog search queries. */
object StationQuerySanitizer {
    private val metaTitlePattern = Regex("""(?i)^(.+?)\s+(radio|hits|station)$""")

    fun isStationMetaPhrase(phrase: String): Boolean {
        val lower = phrase.lowercase().trim()
        if (lower.isBlank()) return true
        if (lower == "radio" || lower == "station" || lower == "hits" || lower == "music") return true
        if (lower.endsWith(" radio") || lower.endsWith(" station") || lower.endsWith(" hits") || lower.endsWith(" music")) return true
        if (lower.startsWith("radio ") || lower.startsWith("station ") || lower.startsWith("hits ") || lower.startsWith("music ")) return true
        return false
    }

    private val metaCleanPattern = Regex("""(?i)\b(radio|station|hits|music)\b""")
    fun cleanStationMeta(phrase: String): String {
        return phrase.replace(metaCleanPattern, "").replace(Regex("""\s+"""), " ").trim()
    }

    fun resolvePrimaryQuery(
        queryPhrases: List<String>,
        displayName: String,
        hintKeywords: List<String>,
        seedArtists: List<String>
    ): String {
        queryPhrases.firstOrNull { !isStationMetaPhrase(it) }?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        seedArtists.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        hintKeywords.firstOrNull { !isStationMetaPhrase(it) }?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        metaTitlePattern.matchEntire(displayName.trim())?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return displayName.trim()
    }

    fun filterSearchQueries(queries: Collection<String>): List<String> =
        queries.map { it.trim() }
            .filter { it.length >= 2 && !isStationMetaPhrase(it) }
            .distinct()
}

/** Canonical seed artists and conflict markers for genre stations. */
object GenreStationSeeds {
    private val artistsByGenre = mapOf(
        "country" to listOf(
            "Johnny Cash", "Dolly Parton", "Willie Nelson", "George Strait",
            "Merle Haggard", "Garth Brooks", "Patsy Cline", "Waylon Jennings",
            "Loretta Lynn", "Hank Williams", "Luke Combs", "Chris Stapleton"
        ),
        "americana" to listOf(
            "Johnny Cash", "Willie Nelson", "Emmylou Harris", "Lucinda Williams",
            "Steve Earle", "Townes Van Zandt", "Jason Isbell", "Brandi Carlile"
        ),
        "rock" to listOf(
            "Led Zeppelin", "The Rolling Stones", "Queen", "AC/DC",
            "Pink Floyd", "The Who", "Fleetwood Mac", "Eagles"
        ),
        "pop" to listOf(
            "Michael Jackson", "Madonna", "Taylor Swift", "Adele",
            "Britney Spears", "Prince", "Beyoncé", "Elton John"
        ),
        "soul" to listOf(
            "Aretha Franklin", "Marvin Gaye", "Stevie Wonder", "Otis Redding",
            "Sam Cooke", "Al Green", "Diana Ross", "The Temptations"
        ),
        "jazz" to listOf(
            "Miles Davis", "John Coltrane", "Ella Fitzgerald", "Louis Armstrong",
            "Billie Holiday", "Dave Brubeck", "Thelonious Monk", "Duke Ellington"
        ),
        "metal" to listOf(
            "Metallica", "Iron Maiden", "Black Sabbath", "Judas Priest",
            "Slayer", "Megadeth", "Pantera", "Ozzy Osbourne"
        ),
        "electronic" to listOf(
            "Daft Punk", "The Chemical Brothers", "Fatboy Slim", "Aphex Twin",
            "Deadmau5", "Calvin Harris", "Avicii", "Kraftwerk"
        ),
        "hip hop" to listOf(
            "Tupac", "The Notorious B.I.G.", "Jay-Z", "Nas",
            "Eminem", "OutKast", "Dr. Dre", "Kendrick Lamar"
        ),
        "indie" to listOf(
            "Arcade Fire", "The Strokes", "Radiohead", "Vampire Weekend",
            "Arctic Monkeys", "Bon Iver", "The National", "Tame Impala"
        ),
        "alternative" to listOf(
            "Nirvana", "Pearl Jam", "Radiohead", "R.E.M.",
            "Red Hot Chili Peppers", "Foo Fighters", "Soundgarden", "Beck"
        )
    )

    private val conflictMarkersByGenre = mapOf(
        "country" to listOf(
            "electronic", "edm", "techno", "house", "trance", "dubstep",
            "synthwave", "top electronic", "edm tribe", "dance mix", "eurodance"
        ),
        "americana" to listOf("electronic", "edm", "techno", "house", "dubstep", "top electronic"),
        "folk" to listOf("electronic", "edm", "techno", "house", "dubstep"),
        "rock" to listOf("edm tribe", "top electronic", "house mix", "techno mix"),
        "soul" to listOf("edm", "techno", "house", "trance", "dubstep"),
        "jazz" to listOf("edm", "techno", "house", "trance", "dubstep"),
        "blues" to listOf("edm", "techno", "house", "trance", "dubstep")
    )

    fun seedArtistsFor(genreKey: String): List<String> =
        artistsByGenre[genreKey.lowercase().trim()].orEmpty()

    fun conflictMarkersFor(hintKeywords: List<String>): List<String> =
        hintKeywords.flatMap { conflictMarkersByGenre[it.lowercase().trim()].orEmpty() }.distinct()
}

object StreamingStationSeedResolver {
    private val songsLikePattern = Regex(
        """(?i)^songs?\s+like\s+(.+?)\s+by\s+(.+)$"""
    )
    private val titleByArtistPattern = Regex(
        """^(.+?)\s+by\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )
    private val eraPattern = Regex("""(?i)(\d{2})s?\s*(.*)""")
    private val eraOnlyPattern = Regex("""(?i)^(\d{2})s?$""")

    fun fromUserInput(raw: String): StreamingStationSeed {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return StreamingStationSeed(
                id = "empty",
                displayName = "Station",
                kind = StreamingStationKind.FREE_TEXT
            )
        }

        JukeboxCatalog.resolveStation(trimmed)?.let { return fromJukeboxStation(it) }
        StationSearchResolver.resolveSingle(trimmed)?.let { return fromJukeboxStation(it) }

        songsLikePattern.matchEntire(trimmed)?.let { match ->
            val title = match.groupValues[1].trim()
            val artist = match.groupValues[2].trim()
            return StreamingStationSeed(
                id = slug("similar_${title}_$artist"),
                displayName = "Songs like $title",
                kind = StreamingStationKind.SONG_SIMILAR,
                queryPhrases = listOf("songs like $title $artist", "$title $artist similar songs"),
                seedTitle = title,
                seedArtist = artist
            )
        }

        titleByArtistPattern.matchEntire(trimmed)?.let { match ->
            val title = match.groupValues[1].trim()
            val artist = match.groupValues[2].trim()
            if (title.length >= 2 && artist.length >= 2) {
                return StreamingStationSeed(
                    id = slug("song_${title}_$artist"),
                    displayName = "$title Radio",
                    kind = StreamingStationKind.SONG,
                    queryPhrases = listOf("$title $artist", "songs like $title $artist"),
                    seedTitle = title,
                    seedArtist = artist
                )
            }
        }

        eraOnlyPattern.matchEntire(trimmed)?.let { match ->
            val decade = match.groupValues[1].toIntOrNull() ?: return@let
            val start = 1900 + decade
            val label = "${decade}s Hits"
            return StreamingStationSeed(
                id = slug("era_$decade"),
                displayName = label,
                kind = StreamingStationKind.ERA,
                queryPhrases = listOf(label, "${decade}s music", "${decade}s hits"),
                eraStart = start,
                eraEnd = start + 9
            )
        }

        eraPattern.matchEntire(trimmed)?.let { match ->
            val decade = match.groupValues[1].toIntOrNull() ?: return@let
            val rest = match.groupValues[2].trim()
            val start = 1900 + decade
            val label = if (rest.isNotBlank()) "${decade}s $rest" else "${decade}s Hits"
            return StreamingStationSeed(
                id = slug("era_${decade}_$rest"),
                displayName = label.replaceFirstChar { it.uppercase() },
                kind = if (rest.isBlank()) StreamingStationKind.ERA else StreamingStationKind.GENRE,
                queryPhrases = listOf(label, "$label radio", "$label classics"),
                eraStart = start,
                eraEnd = start + 9,
                hintKeywords = rest.split(Regex("""\s+""")).filter { it.length >= 3 }
            )
        }

        val activity = detectActivity(trimmed)
        if (activity != null) {
            return StreamingStationSeed(
                id = slug("activity_$trimmed"),
                displayName = activity.displayName,
                kind = StreamingStationKind.ACTIVITY,
                queryPhrases = activity.queries,
                hintKeywords = activity.keywords
            )
        }

        detectGenreStation(trimmed)?.let { return it }

        val identityText = StationQuerySanitizer.cleanStationMeta(trimmed)
        if (identityText.isNotBlank() &&
            !VibeTranslator.isPersonalTastePrompt(identityText) &&
            !VibeTranslator.isVibePrompt(identityText) &&
            looksLikeArtistOnly(identityText)
        ) {
            return artistSeed(identityText)
        }

        if (looksLikeArtistOnly(trimmed)) {
            return artistSeed(trimmed)
        }

        return StreamingStationSeed(
            id = slug("text_$trimmed"),
            displayName = trimmed.replaceFirstChar { it.uppercase() },
            kind = StreamingStationKind.FREE_TEXT,
            queryPhrases = listOf(trimmed)
        )
    }

    private val genreAliases = mapOf(
        "yacht rock" to listOf("yacht rock", "soft rock", "west coast"),
        "classic rock" to listOf("classic rock", "arena rock"),
        "country" to listOf("country", "americana"),
        "hip hop" to listOf("hip hop", "rap"),
        "hip-hop" to listOf("hip hop", "rap"),
        "r&b" to listOf("r&b", "soul"),
        "rnb" to listOf("r&b", "soul"),
        "jazz" to listOf("jazz", "smooth jazz"),
        "blues" to listOf("blues", "electric blues"),
        "metal" to listOf("metal", "heavy metal"),
        "punk" to listOf("punk", "punk rock"),
        "disco" to listOf("disco", "funk"),
        "soul" to listOf("soul", "motown"),
        "indie" to listOf("indie", "alternative"),
        "alternative" to listOf("alternative", "alt rock"),
        "electronic" to listOf("electronic", "house"),
        "lofi" to listOf("lofi", "chillhop"),
        "lo-fi" to listOf("lofi", "chillhop")
    )

    private fun detectGenreStation(text: String): StreamingStationSeed? {
        val lower = text.lowercase().trim()
        genreAliases.forEach { (key, hints) ->
            if (lower == key || lower.contains(key)) {
                val artists = GenreStationSeeds.seedArtistsFor(key)
                return StreamingStationSeed(
                    id = slug("genre_$key"),
                    displayName = text.replaceFirstChar { it.uppercase() },
                    kind = StreamingStationKind.GENRE,
                    queryPhrases = artists.take(4).flatMap { listOf(it, "$it greatest hits") },
                    seedArtists = artists,
                    hintKeywords = hints
                )
            }
        }
        return null
    }

    private fun artistSeed(artist: String): StreamingStationSeed =
        StreamingStationSeed(
            id = slug("artist_$artist"),
            displayName = "$artist Radio",
            kind = StreamingStationKind.ARTIST,
            queryPhrases = listOf("$artist songs", "$artist greatest hits"),
            seedArtist = artist
        )

    fun fromJukeboxStation(station: JukeboxStation): StreamingStationSeed {
        val genreKey = station.genreKeywords.firstOrNull().orEmpty()
        val artists = station.seedArtists.ifEmpty {
            GenreStationSeeds.seedArtistsFor(genreKey)
        }
        val phrases = buildList {
            artists.take(6).forEach { artist ->
                add(artist)
                add("$artist greatest hits")
            }
            station.genreKeywords.filter { it.isNotBlank() && !StationQuerySanitizer.isStationMetaPhrase(it) }
                .take(4)
                .forEach { keyword ->
                    add(keyword)
                    add("$keyword classics")
                    add("best $keyword songs")
                }
            if (!StationQuerySanitizer.isStationMetaPhrase(station.name)) {
                add(station.name)
            }
        }.distinct()
        val kind = when {
            station.decadeStart != null && station.genreKeywords.isNotEmpty() -> StreamingStationKind.GENRE
            station.decadeStart != null -> StreamingStationKind.ERA
            station.stationType.name == "MOOD" -> StreamingStationKind.MOOD
            station.stationType.name == "GENRE" -> StreamingStationKind.GENRE
            else -> StreamingStationKind.JUKEBOX_PRESET
        }
        return StreamingStationSeed(
            id = station.id,
            displayName = station.name,
            kind = kind,
            queryPhrases = phrases,
            eraStart = station.decadeStart,
            eraEnd = station.decadeEnd,
            seedArtists = artists,
            hintKeywords = station.genreKeywords
        )
    }

    private data class ActivitySeed(
        val displayName: String,
        val queries: List<String>,
        val keywords: List<String>
    )

    private fun detectActivity(text: String): ActivitySeed? {
        val lower = text.lowercase()
        return when {
            lower.contains("study") || lower.contains("studying") || lower.contains("focus") ->
                ActivitySeed(
                    "Study Music",
                    listOf("study music", "focus music", "instrumental study", "lofi study"),
                    listOf("study", "focus", "instrumental")
                )
            lower.contains("workout") || lower.contains("gym") || lower.contains("running") ->
                ActivitySeed(
                    "Workout Music",
                    listOf("workout music", "gym playlist", "high energy workout"),
                    listOf("workout", "gym", "energy")
                )
            lower.contains("sleep") || lower.contains("bedtime") ->
                ActivitySeed(
                    "Sleep Music",
                    listOf("sleep music", "ambient sleep", "calm bedtime"),
                    listOf("sleep", "ambient", "calm")
                )
            lower.contains("party") ->
                ActivitySeed(
                    "Party Music",
                    listOf("party hits", "dance party playlist", "party music"),
                    listOf("party", "dance")
                )
            lower.contains("chill") || lower.contains("relax") ->
                ActivitySeed(
                    "Chill Music",
                    listOf("chill music", "relaxing playlist", "mellow vibes"),
                    listOf("chill", "relax", "mellow")
                )
            else -> null
        }
    }

    private fun looksLikeArtistOnly(text: String): Boolean {
        val words = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size > 4) return false
        val lower = text.lowercase()
        if (lower.contains("rock") || lower.contains("pop") || lower.contains("country") ||
            lower.contains("jazz") || lower.contains("radio") || lower.contains("music")
        ) {
            return false
        }
        return words.size in 1..3 && text.none { it.isDigit() }
    }

    private fun slug(raw: String): String =
        raw.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), "")
            .replace(Regex("""\s+"""), "_")
            .take(48)
            .ifBlank { "station" }
}

fun StreamingSeedParams.toStationSeed(): StreamingStationSeed {
    val kind = when (seedKind) {
        "ERA" -> StreamingStationKind.ERA
        "GENRE" -> StreamingStationKind.GENRE
        "ARTIST" -> StreamingStationKind.ARTIST
        "TRACK" -> StreamingStationKind.SONG
        else -> StreamingStationKind.FREE_TEXT
    }
    val id = seedText.lowercase()
        .replace(Regex("""[^\p{L}\p{N}\s]+"""), "")
        .replace(Regex("""\s+"""), "_")
        .take(48)
        .trim('_')
        .ifBlank { "station" }
    val genreKey = hintKeywords.firstOrNull().orEmpty().ifBlank {
        seedText.replace(Regex("(?i)\\s+radio$"), "").trim()
    }
    val artists = when (kind) {
        StreamingStationKind.GENRE -> GenreStationSeeds.seedArtistsFor(genreKey)
        StreamingStationKind.ARTIST -> listOfNotNull(artistName?.trim()?.takeIf { it.isNotBlank() })
        else -> emptyList()
    }
    val phrases = buildList {
        when (kind) {
            StreamingStationKind.ARTIST -> {
                artistName?.trim()?.takeIf { it.isNotBlank() }?.let { artist ->
                    add(artist)
                    add("$artist greatest hits")
                    add("$artist top songs")
                }
            }
            StreamingStationKind.SONG -> {
                val title = trackTitle.orEmpty()
                val artist = artistName.orEmpty()
                if (title.isNotBlank() && artist.isNotBlank()) {
                    add("$artist $title")
                    add("songs like $title $artist")
                }
            }
            StreamingStationKind.GENRE -> {
                artists.take(6).forEach { artist ->
                    add(artist)
                    add("$artist greatest hits")
                }
                hintKeywords.filter { it.isNotBlank() && !StationQuerySanitizer.isStationMetaPhrase(it) }
                    .take(4)
                    .forEach { keyword ->
                        add(keyword)
                        add("$keyword classics")
                        add("best $keyword songs")
                    }
            }
            else -> {
                if (!StationQuerySanitizer.isStationMetaPhrase(seedText)) {
                    add(seedText)
                }
            }
        }
    }.distinct()
    return StreamingStationSeed(
        id = id,
        displayName = seedText,
        kind = kind,
        queryPhrases = phrases,
        eraStart = eraStart,
        eraEnd = eraEnd,
        seedArtist = artistName,
        seedTitle = trackTitle,
        seedArtists = artists,
        hintKeywords = hintKeywords
    )
}

fun StreamingStationSeed.toStreamingSeedParams(): StreamingSeedParams = StreamingSeedParams(
    seedText = displayName,
    seedKind = kind.name,
    eraStart = eraStart,
    eraEnd = eraEnd,
    artistName = seedArtist,
    trackTitle = seedTitle,
    hintKeywords = hintKeywords
)
