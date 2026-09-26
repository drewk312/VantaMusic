package com.audiophile.musicplayer.data.brain

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.search.UnifiedSearchEngine

object IntentParser {
    fun parse(
        query: String,
        exactUserTap: Boolean = false,
        catalogCandidates: List<CanonicalTrack> = emptyList()
    ): CatalogIntent {
        val parsed = UnifiedSearchEngine.resolveIntent(query, catalogCandidates)
        val normalized = normalize(query)
        val requestedVariant = requestedVariant(normalized)
        val intentType = when {
            normalized.startsWith("artist ") -> VantaIntentType.ARTIST
            normalized.startsWith("album ") -> VantaIntentType.ALBUM
            normalized.contains(" radio") || normalized.startsWith("radio ") -> VantaIntentType.RADIO
            parsed.hasExpectedTitle -> VantaIntentType.SONG
            else -> VantaIntentType.GENERAL
        }
        val repaired = repairFeaturedArtistSplit(normalized, parsed.songTitle, parsed.primaryArtist, parsed.featuredArtists)
        val intent = CatalogIntent(
            rawQuery = query,
            title = repaired.first,
            primaryArtist = repaired.second,
            featuredArtists = repaired.third,
            requestedVariant = requestedVariant,
            intentType = intentType,
            exactUserTap = exactUserTap
        )
        Log.d(
            "VANTA_BRAIN_INTENT",
            "query=${intent.rawQuery} title=${intent.title ?: ""} primaryArtist=${intent.primaryArtist ?: ""} " +
                "featuredArtists=${intent.featuredArtists.joinToString("|")} requestedVariant=${intent.requestedVariant}"
        )
        return intent
    }

    private fun requestedVariant(normalizedQuery: String): RequestedVariant = when {
        "instrumental" in normalizedQuery || "inst " in normalizedQuery -> RequestedVariant.INSTRUMENTAL
        "karaoke" in normalizedQuery -> RequestedVariant.KARAOKE
        "piano" in normalizedQuery -> RequestedVariant.PIANO
        "lullaby version" in normalizedQuery || "lullaby rendition" in normalizedQuery || "baby lullaby" in normalizedQuery -> RequestedVariant.LULLABY
        "orchestra" in normalizedQuery || "symphony" in normalizedQuery -> RequestedVariant.ORCHESTRA
        "tribute" in normalizedQuery -> RequestedVariant.TRIBUTE
        "cover" in normalizedQuery -> RequestedVariant.COVER
        "live" in normalizedQuery -> RequestedVariant.LIVE
        "remix" in normalizedQuery -> RequestedVariant.REMIX
        "acoustic" in normalizedQuery || "unplugged" in normalizedQuery -> RequestedVariant.ACOUSTIC
        "slowed" in normalizedQuery || "sped" in normalizedQuery || "nightcore" in normalizedQuery -> RequestedVariant.SLOWED_SPED
        normalizedQuery.isBlank() -> RequestedVariant.NONE
        else -> RequestedVariant.NONE
    }

    private fun repairFeaturedArtistSplit(
        normalizedQuery: String,
        title: String?,
        primaryArtist: String?,
        featuredArtists: List<String>
    ): Triple<String?, String?, List<String>> {
        if (title.isNullOrBlank() || primaryArtist.isNullOrBlank() || featuredArtists.isNotEmpty()) {
            return Triple(title, primaryArtist, featuredArtists)
        }
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val artistTokens = primaryArtist.split(" ").filter { it.isNotBlank() }
        if (queryTokens.size >= 4 && artistTokens.size >= 3 && title.split(" ").size <= 2) {
            val repairedPrimary = artistTokens.take(2).joinToString(" ")
            val repairedFeatured = listOf(artistTokens.drop(2).joinToString(" ")).filter { it.isNotBlank() }
            return Triple(title, repairedPrimary, repairedFeatured)
        }
        return Triple(title, primaryArtist, featuredArtists)
    }
}
