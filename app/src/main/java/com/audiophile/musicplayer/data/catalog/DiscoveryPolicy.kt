package com.audiophile.musicplayer.data.catalog

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import java.util.Locale

object DiscoveryPolicy {
    private val neighbors = mapOf(
        "Pop" to listOf("Bedroom Pop", "Indie", "R&B"),
        "Rock" to listOf("Alternative", "Indie", "Metal"),
        "Indie" to listOf("Dream Pop", "Bedroom Pop", "Shoegaze"),
        "Alternative" to listOf("Indie", "Shoegaze", "Dream Pop"),
        "Hip-Hop" to listOf("R&B", "Neo Soul", "Soul"),
        "R&B" to listOf("Neo Soul", "Soul", "Jazz"),
        "Country" to listOf("Soul", "Rock", "Indie"),
        "Jazz" to listOf("Neo Soul", "Soul", "Classical"),
        "Electronic" to listOf("Dance", "Dark Ambient", "Lo-Fi"),
        "Dance" to listOf("Electronic", "Pop", "Afrobeats")
    )
    fun categories(seed: String, openness: Int, otherLanguages: Boolean): List<String> {
        val base = BrowseCatalog.find(seed)?.title ?: seed
        if (openness == 0 || (!otherLanguages && BrowseCatalog.find(base)?.group == "Around the world")) return listOf(base)
        val adjacent = neighbors[base].orEmpty().ifEmpty { listOf(base) }
        return if (openness == 1) (listOf(base) + adjacent).distinct()
        else (listOf(base) + adjacent + listOf("Jazz", "Electronic", "Indie", "Soul")).distinct()
    }
    fun key(track: CanonicalTrack) = "${track.title.trim()}|${track.artist.trim()}".lowercase(Locale.ROOT)
    fun select(tracks: List<CanonicalTrack>, excluded: Set<String>, seed: Int, limit: Int = 12): List<CanonicalTrack> {
        val counts = mutableMapOf<String, Int>()
        return tracks.distinctBy(::key).filter { key(it) !in excluded }
            .shuffled(kotlin.random.Random(seed)).filter {
                val artist = it.artist.lowercase(Locale.ROOT)
                val count = counts[artist] ?: 0
                if (count >= 2) false else { counts[artist] = count + 1; true }
            }.take(limit)
    }
}
