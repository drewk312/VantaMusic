package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

data class JukeboxStation(
    val id: String,
    val name: String,
    val description: String,
    val genreKeywords: List<String> = emptyList(),
    val decadeStart: Int? = null,
    val decadeEnd: Int? = null,
    val emoji: String = "🎵",
    val stationType: JukeboxStationType = JukeboxStationType.PRESET,
    val seedArtists: List<String> = emptyList(),
    val seedTrackIds: List<Long> = emptyList()
)

data class EraChip(
    val id: String,
    val label: String,
    val decadeStart: Int,
    val decadeEnd: Int
)

data class GenreChip(
    val id: String,
    val label: String,
    val keywords: List<String>
)

object JukeboxCatalog {
    val stations: List<JukeboxStation> = listOf(
        JukeboxStation("golden_oldies", "Golden Oldies", "Classic hits from the '50s–'60s", listOf("oldies", "doo-wop", "doo wop", "motown", "rock and roll", "golden"), 1950, 1969, "📻"),
        JukeboxStation(
            "yacht_rock",
            "Yacht Rock",
            "Smooth, breezy soft rock",
            listOf("yacht rock", "soft rock", "west coast", "smooth"),
            1975,
            1985,
            "⛵",
            seedArtists = listOf(
                "Steely Dan", "Toto", "Christopher Cross", "The Doobie Brothers",
                "Michael McDonald", "Hall & Oates", "Boz Scaggs", "Ambrosia"
            )
        ),
        JukeboxStation("classic_soul", "Classic Soul", "Warm soul and R&B grooves", listOf("soul", "r&b", "rnb", "funk"), 1960, 1989, "🎤"),
        JukeboxStation("80s_night", "'80s Night", "Synths, drums, and big hooks", listOf("80s", "pop", "rock", "new wave", "synth"), 1980, 1989, "🌃"),
        JukeboxStation("90s_alt", "'90s Alternative", "Grunge, alt-rock, and college rock", listOf("alternative", "grunge", "rock", "indie"), 1990, 1999, "🎸"),
        JukeboxStation(
            "classic_country",
            "Classic Country",
            "Story songs and twang",
            listOf("country", "americana", "folk"),
            1960,
            1999,
            "🤠",
            seedArtists = listOf(
                "Johnny Cash", "Dolly Parton", "Willie Nelson", "George Strait",
                "Merle Haggard", "Patsy Cline", "Waylon Jennings", "Garth Brooks",
                "Chris Stapleton", "Luke Combs"
            )
        ),
        JukeboxStation("motown", "Motown", "Detroit soul and pop perfection", listOf("motown", "soul", "r&b"), 1960, 1979, "🏙️"),
        JukeboxStation("disco", "Disco", "Four-on-the-floor dance floor", listOf("disco", "funk", "dance"), 1970, 1979, "🪩"),
        JukeboxStation("quiet_storm", "Quiet Storm", "Late-night slow jams", listOf("quiet storm", "r&b", "soul", "slow"), 1970, 1999, "🌙"),
        JukeboxStation("southern_rock", "Southern Rock", "Grit, groove, and open roads", listOf("southern rock", "rock", "blues rock"), 1970, 1989, "🛣️"),
        JukeboxStation("one_hit_wonders", "One-Hit Wonders", "Songs everyone knows once", listOf("pop", "rock", "one hit"), 1960, 1999, "💫"),
        JukeboxStation("deep_cuts", "Deep Cuts", "Album tracks and hidden gems", listOf("rock", "indie", "alternative", "soul", "jazz"), null, null, "🔍")
    )

    val eras: List<EraChip> = listOf(
        EraChip("60s", "60s", 1960, 1969),
        EraChip("70s", "70s", 1970, 1979),
        EraChip("80s", "80s", 1980, 1989),
        EraChip("90s", "90s", 1990, 1999),
        EraChip("2000s", "2000s", 2000, 2009),
        EraChip("2010s", "2010s", 2010, 2019)
    )

    val genres: List<GenreChip> = listOf(
        GenreChip("rock", "Rock", listOf("rock", "classic rock", "hard rock")),
        GenreChip("pop", "Pop", listOf("pop", "dance pop")),
        GenreChip("soul", "Soul", listOf("soul", "r&b", "rnb")),
        GenreChip("funk", "Funk", listOf("funk", "disco")),
        GenreChip("jazz", "Jazz", listOf("jazz", "smooth jazz")),
        GenreChip("country", "Country", listOf("country", "americana")),
        GenreChip("electronic", "Electronic", listOf("electronic", "edm", "house", "techno")),
        GenreChip("hip_hop", "Hip-Hop", listOf("hip-hop", "hip hop", "rap")),
        GenreChip("indie", "Indie", listOf("indie", "alternative")),
        GenreChip("metal", "Metal", listOf("metal", "heavy metal"))
    )

    fun findStation(id: String): JukeboxStation? = stations.find { it.id == id }

    fun resolveStation(id: String, customStore: CustomStationStore? = null): JukeboxStation? {
        return when {
            id.startsWith("era_") -> stationFromEra(id.removePrefix("era_"))
            id.startsWith("genre_") -> stationFromGenre(id.removePrefix("genre_"))
            id.startsWith("mood_") -> MoodStationCatalog.stationFromMood(id.removePrefix("mood_"))
            id.startsWith("custom_") -> customStore?.find(id)
            else -> findStation(id) ?: customStore?.find(id)
        }
    }

    fun stationLabel(id: String, customStore: CustomStationStore? = null): String =
        resolveStation(id, customStore)?.name ?: id

    fun stationFromEra(eraId: String): JukeboxStation? {
        val era = eras.find { it.id == eraId } ?: return null
        val decadeStr = era.decadeStart.toString()
        val eraKeywords = listOf(
            era.label, "${decadeStr}s",
            "${era.label} music", "${era.label} hits",
            "${decadeStr}s music"
        )
        return JukeboxStation(
            id = "era_$eraId",
            name = eraDisplayName(era),
            description = eraDescription(era),
            genreKeywords = eraKeywords,
            decadeStart = era.decadeStart,
            decadeEnd = era.decadeEnd,
            emoji = eraEmoji(era),
            stationType = JukeboxStationType.ERA
        )
    }

    fun stationFromGenre(genreId: String): JukeboxStation? {
        val genre = genres.find { it.id == genreId } ?: return null
        val seedArtists = com.audiophile.musicplayer.radio.GenreStationSeeds
            .seedArtistsFor(genreId)
            .ifEmpty { genre.keywords.flatMap { com.audiophile.musicplayer.radio.GenreStationSeeds.seedArtistsFor(it) } }
            .distinct()
        return JukeboxStation(
            id = "genre_$genreId",
            name = "${genre.label} Radio",
            description = "Endless ${genre.label.lowercase()} from your library — deep cuts, classics, and hidden gems.",
            genreKeywords = genre.keywords,
            emoji = genreEmoji(genre.id),
            stationType = JukeboxStationType.GENRE,
            seedArtists = seedArtists
        )
    }

    private fun eraDisplayName(era: EraChip): String = when (era.id) {
        "60s" -> "'60s Hits"
        "70s" -> "'70s Hits"
        "80s" -> "80s Hits"
        "90s" -> "'90s Hits"
        "2000s" -> "2000s Hits"
        "2010s" -> "2010s Hits"
        else -> "${era.label} Hits"
    }

    private fun eraDescription(era: EraChip): String = when (era.id) {
        "60s" -> "British Invasion, Motown, and the birth of modern pop — a flowing set from the swinging sixties."
        "70s" -> "Disco floors, prog epics, and soul deep cuts from the decade that defined the album era."
        "80s" -> "Iconic synth-pop, new wave, and arena rock from the decade of big hair and bigger hooks."
        "90s" -> "Grunge, hip-hop golden age, and alt-rock anthems from a decade that changed everything."
        "2000s" -> "Pop crossover, indie breakthroughs, and early digital-era hits from the 2000s."
        "2010s" -> "Streaming-era pop, EDM crossover, and genre-blurring hits from the 2010s."
        else -> "A flowing set from the ${era.label} — curated from your library."
    }

    private fun eraEmoji(era: EraChip): String = when (era.id) {
        "60s" -> "☮️"
        "70s" -> "🕺"
        "80s" -> "🌃"
        "90s" -> "🎸"
        "2000s" -> "💿"
        "2010s" -> "📱"
        else -> "📅"
    }

    private fun genreEmoji(genreId: String): String = when (genreId) {
        "rock" -> "🎸"
        "pop" -> "🎤"
        "soul" -> "🎷"
        "funk" -> "🕺"
        "jazz" -> "🎺"
        "country" -> "🤠"
        "electronic" -> "🎛️"
        "hip_hop" -> "🎧"
        "indie" -> "🌿"
        "metal" -> "🤘"
        else -> "🎶"
    }

    fun buildArtistStation(artist: String): JukeboxStation {
        val trimmed = artist.trim()
        val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(48).trim('_')
        return JukeboxStation(
            id = "custom_artist_$slug",
            name = trimmed,
            description = "Endless $trimmed and similar sounds from your library",
            seedArtists = listOf(trimmed),
            emoji = "🎤",
            stationType = JukeboxStationType.ARTIST_SEED
        )
    }

    fun buildSongStation(track: UnifiedTrackWithSources): JukeboxStation {
        val title = track.track.title
        val artist = track.track.artist
        val genre = track.track.genre
        return JukeboxStation(
            id = "custom_song_${track.track.trackId}",
            name = "Like \"$title\"",
            description = "Tracks in the pocket of \"$title\" by $artist",
            genreKeywords = genre?.let { listOf(it) } ?: emptyList(),
            seedArtists = listOf(artist),
            seedTrackIds = listOf(track.track.trackId),
            emoji = "🎵",
            stationType = JukeboxStationType.SONG_SEED
        )
    }

    fun buildMultiArtistStation(
        name: String,
        artists: List<String>,
        eraId: String? = null,
        genreId: String? = null
    ): JukeboxStation {
        val cleanedArtists = artists.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val era = eraId?.let { eras.find { it.id == eraId } }
        val genre = genreId?.let { genres.find { it.id == genreId } }
        val id = "custom_mix_${System.currentTimeMillis()}"
        val artistLabel = cleanedArtists.take(3).joinToString(", ")
        val description = buildString {
            append("Your mix of $artistLabel")
            era?.let { append(" · ${it.label}") }
            genre?.let { append(" · ${it.label}") }
        }
        return JukeboxStation(
            id = id,
            name = name.trim().ifBlank { "Custom Mix" },
            description = description,
            genreKeywords = genre?.keywords ?: emptyList(),
            decadeStart = era?.decadeStart,
            decadeEnd = era?.decadeEnd,
            seedArtists = cleanedArtists,
            emoji = "✨",
            stationType = JukeboxStationType.MULTI_ARTIST
        )
    }
}

// ── Streaming Seed Bridge ──

data class StreamingSeedParams(
    val seedText: String,
    val seedKind: String = "GENRE",
    val eraStart: Int? = null,
    val eraEnd: Int? = null,
    val artistName: String? = null,
    val trackTitle: String? = null,
    val hintKeywords: List<String> = emptyList()
)

fun JukeboxStation.toStreamingSeed(): StreamingSeedParams {
    val eraString = if (decadeStart != null) "${decadeStart}s" else ""
    val allKeywords = (genreKeywords + seedArtists).distinct()

    return when (stationType) {
        JukeboxStationType.ERA, JukeboxStationType.PRESET -> {
            StreamingSeedParams(
                seedText = "$name $eraString ${allKeywords.joinToString(" ")}".trim(),
                seedKind = "ERA",
                eraStart = decadeStart,
                eraEnd = decadeEnd,
                hintKeywords = allKeywords
            )
        }
        JukeboxStationType.GENRE -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = "GENRE",
                hintKeywords = allKeywords
            )
        }
        JukeboxStationType.ARTIST_SEED -> {
            val artist = seedArtists.firstOrNull() ?: name
            StreamingSeedParams(
                seedText = artist,
                seedKind = "ARTIST",
                artistName = artist,
                hintKeywords = allKeywords
            )
        }
        JukeboxStationType.SONG_SEED -> {
            val artist = seedArtists.firstOrNull().orEmpty()
            StreamingSeedParams(
                seedText = "songs like $name by $artist",
                seedKind = "TRACK",
                artistName = artist,
                trackTitle = name.removePrefix("Like \"").removeSuffix("\""),
                hintKeywords = allKeywords
            )
        }
        JukeboxStationType.MULTI_ARTIST -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = "ARTIST",
                artistName = seedArtists.joinToString(", "),
                eraStart = decadeStart,
                eraEnd = decadeEnd,
                hintKeywords = allKeywords
            )
        }
        else -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = "GENRE",
                hintKeywords = allKeywords
            )
        }
    }
}
