package com.audiophile.musicplayer.data.source

/**
 * Classifies recording variants so playback can prefer studio vocal identity over availability.
 */
object VariantClassifier {

    enum class VariantType {
        STUDIO_VOCAL,
        INSTRUMENTAL,
        KARAOKE,
        COVER,
        LIVE,
        REMIX,
        ACOUSTIC,
        PIANO,
        UNKNOWN
    }

    data class Classification(
        val variantType: VariantType,
        val confidence: Float,
        val rejectionReason: String?
    )

    private val instrumentalMarkers = listOf(
        "instrumental", "inst.", "inst ", "no vocals", "no vocal", "backing track",
        "performance track", "minus one", "minus-one", "tv track", "drumless",
        "lullaby", "lullabies"
    )
    private val karaokeMarkers = listOf("karaoke", "sing along", "singalong")
    private val pianoMarkers = listOf("piano version", "piano cover", "piano instrumental", "for piano")
    private val coverMarkers = listOf(
        "cover version", "cover by", "tribute to", "made famous by", "originally performed by",
        "in the style of", "as made famous", "renditions", "rendition", "translations",
        "symphony orchestra", "orchestra performs", "performs the"
    )
    private val liveMarkers = listOf(
        "live at", "live from", "live version", "live session", "mtv unplugged", "tiny desk",
        "performs live", "full concert", "live performance"
    )
    private val remixMarkers = listOf(
        "remix", "slowed", "sped up", "sped-up", "speed up", "nightcore", "reverb", "8d", "8 d",
        "lofi", "tiktok version", "workout remix", "dj mix"
    )
    private val acousticMarkers = listOf("acoustic version", "acoustic cover", "unplugged")

    fun classify(
        title: String?,
        artist: String?,
        album: String? = null,
        userQuery: String? = null
    ): Classification {
        val haystack = VocalRecordingClassifier.haystack(title, artist, album)
        if (haystack.isBlank()) {
            return Classification(VariantType.UNKNOWN, 0.2f, "missing_metadata")
        }

        if (VocalRecordingClassifier.isTributeOrNonVocalArtist(artist)) {
            return Classification(VariantType.COVER, 0.95f, "tribute_artist")
        }

        val type = when {
            matchesAny(haystack, instrumentalMarkers) -> VariantType.INSTRUMENTAL
            matchesAny(haystack, karaokeMarkers) -> VariantType.KARAOKE
            matchesAny(haystack, pianoMarkers) -> VariantType.PIANO
            matchesAny(haystack, coverMarkers) || haystack.contains(" tribute") -> VariantType.COVER
            matchesAny(haystack, liveMarkers) || haystack.contains(" live") -> VariantType.LIVE
            matchesAny(haystack, remixMarkers) -> VariantType.REMIX
            matchesAny(haystack, acousticMarkers) -> VariantType.ACOUSTIC
            else -> VariantType.STUDIO_VOCAL
        }

        val confidence = when (type) {
            VariantType.STUDIO_VOCAL -> 0.75f
            VariantType.UNKNOWN -> 0.2f
            else -> 0.9f
        }
        val rejection = if (type == VariantType.STUDIO_VOCAL || type == VariantType.UNKNOWN) {
            null
        } else {
            "variant_${type.name.lowercase()}"
        }
        return Classification(type, confidence, rejection)
    }

    fun userAllowsVariant(userQuery: String?, marker: String): Boolean {
        if (userQuery.isNullOrBlank()) return false
        return marker.lowercase() in userQuery.lowercase()
    }

    fun userAllowsVariantType(userQuery: String?, type: VariantType): Boolean {
        if (userQuery.isNullOrBlank()) return false
        val q = userQuery.lowercase()
        return when (type) {
            VariantType.INSTRUMENTAL -> "instrumental" in q || "inst" in q
            VariantType.KARAOKE -> "karaoke" in q
            VariantType.PIANO -> "piano" in q
            VariantType.COVER -> "cover" in q || "tribute" in q
            VariantType.LIVE -> "live" in q
            VariantType.REMIX -> "remix" in q || "slowed" in q || "sped" in q || "nightcore" in q
            VariantType.ACOUSTIC -> "acoustic" in q || "unplugged" in q
            VariantType.STUDIO_VOCAL, VariantType.UNKNOWN -> true
        }
    }

    fun isRejectedForStudioIntent(
        title: String?,
        artist: String?,
        album: String?,
        userQuery: String?
    ): Pair<Boolean, String?> {
        val classification = classify(title, artist, album, userQuery)
        if (classification.variantType == VariantType.STUDIO_VOCAL || classification.variantType == VariantType.UNKNOWN) {
            return false to null
        }
        if (userAllowsVariantType(userQuery, classification.variantType)) {
            return false to null
        }
        return true to (classification.rejectionReason ?: "variant_not_requested")
    }

    private fun matchesAny(haystack: String, markers: List<String>): Boolean =
        markers.any { marker -> marker in haystack }
}
