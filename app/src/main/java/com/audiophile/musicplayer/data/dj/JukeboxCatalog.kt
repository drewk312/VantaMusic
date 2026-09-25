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
    val seedTrackIds: List<Long> = emptyList(),
    val genomeMode: com.audiophile.musicplayer.radio.genome.PandoraStationMode? = null,
    val forbiddenGenres: List<String> = emptyList(),
    val isOneHitWonderOnly: Boolean = false
) {
    fun toSonicStationSpec(): com.audiophile.musicplayer.radio.sonic.SonicStationSpec? {
        val start = decadeStart ?: return null
        val end = decadeEnd ?: (start + 9)
        return com.audiophile.musicplayer.radio.sonic.SonicStationSpec(
            id = id,
            title = name,
            eraStart = start,
            eraEnd = end,
            seeds = seedArtists,
            forbiddenGenres = forbiddenGenres,
            isOneHitWonderOnly = isOneHitWonderOnly
        )
    }
}

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
        JukeboxStation(
            id = "golden_oldies",
            name = "Golden Oldies (50s-60s)",
            description = "The definitive '50s–'60s Rock, Pop, and Doo-Wop classics",
            genreKeywords = listOf("oldies", "golden oldies", "doo-wop", "doo wop", "motown", "rock and roll", "golden"),
            decadeStart = 1950,
            decadeEnd = 1969,
            emoji = "📻",
            seedArtists = listOf("Elvis Presley", "The Supremes", "Roy Orbison", "Sam Cooke", "Fats Domino", "Chuck Berry", "Buddy Holly", "The Everly Brothers", "Dion", "Little Richard", "The Beach Boys"),
            forbiddenGenres = listOf("Synthwave", "Modern-Pop", "Hip-Hop", "Electronic", "Trap")
        ),
        JukeboxStation(
            id = "fifties_rock_roll",
            name = "50s Rock 'n' Roll & Doo-Wop",
            description = "Rockabilly, street-corner doo-wop, and the birth of rock 'n' roll",
            genreKeywords = listOf("rock and roll", "50s", "doo-wop", "rockabilly", "early rock"),
            decadeStart = 1950,
            decadeEnd = 1959,
            emoji = "⚡",
            seedArtists = listOf("Chuck Berry", "Little Richard", "Gene Vincent", "Jerry Lee Lewis", "Buddy Holly", "Bo Diddley", "Fats Domino", "Bill Haley & His Comets"),
            forbiddenGenres = listOf("Disco", "Funk", "Punk", "Synthwave", "Electronic")
        ),
        JukeboxStation(
            id = "sixties_invasion",
            name = "60s British Invasion & Pop",
            description = "Merseybeat, mod rock, and swingin' sixties pop anthems",
            genreKeywords = listOf("60s", "british invasion", "pop", "mod rock", "classic pop"),
            decadeStart = 1960,
            decadeEnd = 1969,
            emoji = "🇬🇧",
            seedArtists = listOf("The Beatles", "The Kinks", "The Animals", "The Hollies", "The Beach Boys", "The Rolling Stones", "Herman's Hermits", "The Who", "The Dave Clark Five"),
            forbiddenGenres = listOf("Synth-Pop", "Grunge", "EDM", "Hip-Hop")
        ),
        JukeboxStation(
            id = "motown_soul",
            name = "Motown & Classic Soul",
            description = "Detroit rhythm, Stax horn sections, and timeless soul mastery",
            genreKeywords = listOf("motown", "soul", "r&b", "classic soul", "stax"),
            decadeStart = 1959,
            decadeEnd = 1975,
            emoji = "🏙️",
            seedArtists = listOf("Marvin Gaye", "The Temptations", "Stevie Wonder", "Otis Redding", "Aretha Franklin", "Smokey Robinson", "The Supremes", "Four Tops", "Martha & The Vandellas"),
            forbiddenGenres = listOf("Rap", "Trap", "Techno", "Metal")
        ),
        JukeboxStation(
            id = "doo_wop_classics",
            name = "Doo-Wop Street Corners",
            description = "Golden street-corner vocal harmonies of the '50s & early '60s",
            genreKeywords = listOf("doo-wop", "doo wop", "vocal harmony", "oldies", "street corner"),
            decadeStart = 1953,
            decadeEnd = 1964,
            emoji = "🎙️",
            seedArtists = listOf("The Platters", "The Drifters", "Dion & The Belmonts", "The Five Satins", "The Flamingos", "The Del-Vikings", "The Silhouettes", "The Clovers"),
            forbiddenGenres = listOf("EDM", "Synthwave", "Modern Pop", "Hip-Hop")
        ),
        JukeboxStation(
            id = "rockabilly_stomp",
            name = "Rockabilly & Early Stomp",
            description = "Sun Records twang, upright slap bass, and raw early jukebox rhythm",
            genreKeywords = listOf("rockabilly", "sun records", "early rock", "twang", "rock and roll"),
            decadeStart = 1952,
            decadeEnd = 1962,
            emoji = "🎸",
            seedArtists = listOf("Carl Perkins", "Wanda Jackson", "Johnny Cash", "Gene Vincent", "Link Wray", "Eddie Cochran", "Dale Hawkins"),
            forbiddenGenres = listOf("Disco", "Hip-Hop", "Electronic", "Synth-Pop")
        ),
        JukeboxStation(
            id = "yacht_rock",
            name = "Yacht Rock Premium",
            description = "Smooth, breezy West Coast soft rock & studio royalty",
            genreKeywords = listOf("yacht rock", "soft rock", "west coast", "smooth", "aor"),
            decadeStart = 1973,
            decadeEnd = 1984,
            emoji = "⛵",
            seedArtists = listOf("Steely Dan", "Toto", "Christopher Cross", "The Doobie Brothers", "Michael McDonald", "Boz Scaggs", "Kenny Loggins", "Ambrosia", "Hall & Oates", "Robbie Dupree", "Player", "Rupert Holmes", "Pages"),
            forbiddenGenres = listOf("Grunge", "Heavy-Metal", "Punk", "Trap", "EDM")
        ),
        JukeboxStation(
            id = "one_hit_wonders",
            name = "One-Hit Wonders",
            description = "Songs everyone knows once, from '60s to '90s",
            genreKeywords = listOf("one hit", "one hit wonder", "pop", "classics", "anthems"),
            decadeStart = 1960,
            decadeEnd = 1999,
            emoji = "💫",
            seedArtists = listOf("Norman Greenbaum", "Dexys Midnight Runners", "Tommy Tutone", "The Knack", "Soft Cell", "a-ha", "Steam", "Lipps Inc.", "Men Without Hats", "Taco", "Dead or Alive", "Cutting Crew", "Gotye", "Chumbawamba"),
            isOneHitWonderOnly = true,
            forbiddenGenres = emptyList()
        ),
        JukeboxStation("hindi_bollywood", "Hindi Bollywood", "Big-screen favorites and Hindi hits, from timeless to today.", listOf("hindi", "bollywood"), emoji = "🎬", seedArtists = listOf("Arijit Singh", "Shreya Ghoshal", "Sonu Nigam", "Sunidhi Chauhan")),
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
        JukeboxStation("deep_cuts", "Deep Cuts", "Album tracks and hidden gems", listOf("rock", "indie", "alternative", "soul", "jazz"), null, null, "🔍")
    )

    val sonicStationSpecs: Map<String, com.audiophile.musicplayer.radio.sonic.SonicStationSpec> by lazy {
        stations.mapNotNull { station ->
            station.toSonicStationSpec()?.let { spec -> station.id to spec }
        }.toMap()
    }

    val eras: List<EraChip> = listOf(
        EraChip("50s", "50s", 1950, 1959),
        EraChip("60s", "60s", 1960, 1969),
        EraChip("70s", "70s", 1970, 1979),
        EraChip("80s", "80s", 1980, 1989),
        EraChip("90s", "90s", 1990, 1999),
        EraChip("2000s", "2000s", 2000, 2009),
        EraChip("2010s", "2010s", 2010, 2019)
    )

    val genomeStations: List<JukeboxStation> = listOf(
        JukeboxStation(
            id = "pop_hits",
            name = "Crowd Faves",
            description = "Top hits & celebrated anthems sharing this acoustic DNA",
            genreKeywords = listOf("pop hits", "dance pop", "top hits", "chart anthems"),
            emoji = "🔥",
            stationType = JukeboxStationType.GENOME_MODE,
            seedArtists = listOf("Taylor Swift", "The Weeknd", "Drake", "Dua Lipa", "Bruno Mars", "Billie Eilish"),
            genomeMode = com.audiophile.musicplayer.radio.genome.PandoraStationMode.CROWD_FAVES
        ),
        JukeboxStation(
            id = "discovery_weekly",
            name = "Discovery",
            description = "Fresh independent artists & rising talent with matching musical genes",
            genreKeywords = listOf("indie", "alternative", "bedroom pop", "indie folk"),
            emoji = "🔭",
            stationType = JukeboxStationType.GENOME_MODE,
            seedArtists = listOf("Phoebe Bridgers", "Clairo", "Boygenius", "Lucy Dacus", "Big Thief", "Samia"),
            genomeMode = com.audiophile.musicplayer.radio.genome.PandoraStationMode.DISCOVERY
        ),
        JukeboxStation(
            id = "deep_cuts",
            name = "Deep Cuts",
            description = "Rare discography gems, B-sides & deep album tracks",
            genreKeywords = listOf("rock", "indie", "alternative", "soul", "art rock"),
            emoji = "💎",
            stationType = JukeboxStationType.GENOME_MODE,
            seedArtists = listOf("Radiohead", "Pink Floyd", "Steely Dan", "David Bowie", "Fleetwood Mac"),
            genomeMode = com.audiophile.musicplayer.radio.genome.PandoraStationMode.DEEP_CUTS
        ),
        JukeboxStation(
            id = "late_night_chill",
            name = "Chill & Acoustic",
            description = "Mellow acoustic warmth, relaxed tempo & late-night serenity",
            genreKeywords = listOf("acoustic", "indie folk", "quiet storm", "lo-fi", "chill"),
            emoji = "🌙",
            stationType = JukeboxStationType.GENOME_MODE,
            seedArtists = listOf("Bon Iver", "Norah Jones", "Jack Johnson", "Iron & Wine", "Sufjan Stevens"),
            genomeMode = com.audiophile.musicplayer.radio.genome.PandoraStationMode.CHILL
        ),
        JukeboxStation(
            id = "workout_energy",
            name = "Energy Up",
            description = "Driving syncopation, danceable BPM & high-octane groove",
            genreKeywords = listOf("electronic", "dance", "house", "synthwave", "techno"),
            emoji = "⚡",
            stationType = JukeboxStationType.GENOME_MODE,
            seedArtists = listOf("Daft Punk", "The Prodigy", "Justice", "Calvin Harris", "Fred again.."),
            genomeMode = com.audiophile.musicplayer.radio.genome.PandoraStationMode.UPBEAT
        )
    )

    val genres: List<GenreChip> = listOf(
        GenreChip("oldies", "Oldies", listOf("oldies", "golden oldies", "doo-wop", "motown", "50s", "60s", "rock and roll")),
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

    fun findStation(id: String): JukeboxStation? = stations.find { it.id == id } ?: genomeStations.find { it.id == id }

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
    val hintKeywords: List<String> = emptyList(),
    val seedArtists: List<String> = emptyList(),
    val genomeMode: String? = null
)

fun JukeboxStation.toStreamingSeed(): StreamingSeedParams {
    val allKeywords = (genreKeywords + seedArtists).distinct()

    return when (stationType) {
        JukeboxStationType.GENOME_MODE -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = "GENRE",
                hintKeywords = genreKeywords,
                seedArtists = seedArtists,
                genomeMode = genomeMode?.name
            )
        }
        JukeboxStationType.ERA, JukeboxStationType.PRESET -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = if (decadeStart != null) "ERA" else "GENRE",
                eraStart = decadeStart,
                eraEnd = decadeEnd,
                hintKeywords = allKeywords,
                seedArtists = seedArtists,
                genomeMode = genomeMode?.name
            )
        }
        JukeboxStationType.GENRE -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = "GENRE",
                hintKeywords = allKeywords,
                seedArtists = seedArtists,
                genomeMode = genomeMode?.name
            )
        }
        JukeboxStationType.ARTIST_SEED -> {
            val artist = seedArtists.firstOrNull() ?: name
            StreamingSeedParams(
                seedText = artist,
                seedKind = "ARTIST",
                artistName = artist,
                hintKeywords = allKeywords,
                seedArtists = seedArtists
            )
        }
        JukeboxStationType.SONG_SEED -> {
            val artist = seedArtists.firstOrNull().orEmpty()
            StreamingSeedParams(
                seedText = "songs like $name by $artist",
                seedKind = "TRACK",
                artistName = artist,
                trackTitle = name.removePrefix("Like \"").removeSuffix("\""),
                hintKeywords = allKeywords,
                seedArtists = seedArtists
            )
        }
        JukeboxStationType.MULTI_ARTIST -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = "ARTIST",
                artistName = seedArtists.joinToString(", "),
                eraStart = decadeStart,
                eraEnd = decadeEnd,
                hintKeywords = allKeywords,
                seedArtists = seedArtists
            )
        }
        else -> {
            StreamingSeedParams(
                seedText = name,
                seedKind = "GENRE",
                hintKeywords = allKeywords,
                seedArtists = seedArtists
            )
        }
    }
}
