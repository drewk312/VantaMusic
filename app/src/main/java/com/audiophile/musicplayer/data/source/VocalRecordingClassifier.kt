package com.audiophile.musicplayer.data.source

/**
 * Studio-vocal vs non-vocal variant detection for search, playback, lyrics, and gateway parity.
 *
 * Always evaluate **title + artist + album** — many junk FLAC rows have clean titles but
 * betray themselves in album or performer fields (lullaby renditions, orchestras, piano uploaders).
 */
object VocalRecordingClassifier {

    fun haystack(title: String?, artist: String?, album: String? = null): String =
        "${title.orEmpty()} ${artist.orEmpty()} ${album.orEmpty()}"
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Known tribute / cover / instrumental performer brands — not the credited original artist. */
    private val tributeArtistMarkers = listOf(
        "rockabye baby",
        "symphony orchestra",
        "light orchestra",
        "guitar revival",
        "dreamy sugar",
        "ambient light",
        "acoustic guitar revival",
        "roma symphony",
        "soft serenades",
        "pancadão",
        "boanerges",
        "fredy'sam",
        "japanese jenn",
        "venkij palys",
        "tetrismouth",
        "party tyme",
        "ultimate tribute",
        "tribute stars",
        "tribute of honor",
        "sound-a-like",
        "cover hits",
        "sing along",
        "karaoke",
        "the backing tracks",
        "backing tracks",
        "backing track"
    )

    /** Album names that almost always mean non-vocal or tribute inventory. */
    private val nonVocalAlbumMarkers = listOf(
        "lullaby rendition",
        "lullaby renditions",
        "lullaby version",
        "baby lullaby",
        "baby lullabies",
        "renditions",
        "rendition",
        "translations",
        "translation",
        "performs",
        "instrumental version",
        "instrumental",
        "karaoke",
        "piano music",
        "acoustic renditions",
        "acoustic rendition",
        "sleep all",
        "ambient translation",
        "cover version",
        "cover versions",
        "in the style of",
        "made famous by",
        "originally performed",
        "as made famous",
        "backing track",
        "tribute to",
        "tabata",
        "hiit mix",
        "workout mix"
    )

    private val instrumentalTitleMarkers = listOf(
        "instrumental",
        "karaoke",
        "backing track",
        "minus one",
        "minus-one",
        "no vocal",
        "no vocals"
    )

    fun isTributeOrNonVocalArtist(artist: String?): Boolean {
        val normalized = artist.orEmpty().lowercase().trim()
        if (normalized.isBlank()) return false
        return tributeArtistMarkers.any { marker -> marker in normalized }
    }

    fun hasNonVocalAlbumSignals(album: String?): Boolean {
        val normalized = album.orEmpty().lowercase().trim()
        if (normalized.isBlank()) return false
        return nonVocalAlbumMarkers.any { marker -> marker in normalized }
    }

    fun hasPianoVariantInTitle(title: String?): Boolean {
        val normalized = title.orEmpty().lowercase()
        if (normalized.isBlank()) return false
        // Avoid aggressive piano filter for tracks like "Piano Man"
        if (normalized.contains("piano man")) return false
        return normalized.contains("piano version") ||
            normalized.contains("piano cover") ||
            normalized.endsWith(" piano") ||
            normalized.startsWith("piano ")
    }

    fun hasInstrumentalTitleSignals(title: String?): Boolean {
        val normalized = title.orEmpty().lowercase()
        if (normalized.isBlank()) return false
        return instrumentalTitleMarkers.any { marker -> marker in normalized }
    }

    fun userRequestedNonVocal(userQuery: String?): Boolean {
        if (userQuery.isNullOrBlank()) return false
        val q = userQuery.lowercase()
        return listOf("instrumental", "karaoke", "piano", "lullaby version", "baby lullaby", "acoustic", "live", "remix")
            .any { term -> term in q }
    }

    /**
     * Whether synced/plain lyrics should be fetched for this recording.
     * Instrumental and tribute rows waste provider quota and confuse Now Playing.
     */
    fun lyricsExpected(
        title: String?,
        artist: String?,
        album: String? = null,
        userQuery: String? = null
    ): Boolean {
        if (userRequestedNonVocal(userQuery)) return false
        // Only reject strictly non-vocal recordings for lyrics.
        // Do NOT call shouldAllowInCatalog — that rejects live/remix/acoustic
        // which are valid vocal recordings that should have lyrics.
        if (isTributeOrNonVocalArtist(artist)) return false
        if (hasInstrumentalTitleSignals(title)) return false
        val stack = haystack(title, artist, album)
        if (stack.contains("orchestra") || stack.contains("symphony") || stack.contains("ensemble")) {
            return false
        }
        if (stack.contains("karaoke") || stack.contains("backing track")) {
            return false
        }
        return true
    }

    /** Catalog ingest, gateway parse, and search purity gate. */
    fun shouldAllowInCatalog(
        title: String?,
        artist: String?,
        album: String? = null,
        userQuery: String? = null
    ): Boolean {
        if (userRequestedNonVocal(userQuery)) return true
        if (isTributeOrNonVocalArtist(artist)) return false
        if (hasNonVocalAlbumSignals(album)) return false
        if (hasPianoVariantInTitle(title) && !userRequestedNonVocal(userQuery)) return false
        if (hasInstrumentalTitleSignals(title) && !userRequestedNonVocal(userQuery)) return false

        val (rejected, _) = VariantClassifier.isRejectedForStudioIntent(title, artist, album, userQuery)
        return !rejected
    }
}
