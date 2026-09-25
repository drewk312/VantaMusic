package com.audiophile.musicplayer.search

import com.google.gson.JsonParser

/**
 * Turns a real music-catalog response into short, relevance-ordered typeahead.
 * Suggestions are labels only: selecting one still goes through the normal
 * multi-provider search and playback validation path.
 */
object MusicTypeahead {
    fun suggestions(
        query: String,
        catalogResponse: String?,
        libraryLabels: List<String> = emptyList(),
        graphArtistLabels: List<String> = emptyList(),
        graphAlbumLabels: List<String> = emptyList(),
        limit: Int = 5
    ): List<String> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < 2 || limit <= 0) return emptyList()

        val ranked = mutableListOf<RankedLabel>()
        // Canonical graph artists/albums outrank ephemeral catalog strings for partial queries.
        graphArtistLabels.forEachIndexed { index, label ->
            addIfMatching(
                destination = ranked,
                query = normalizedQuery,
                label = label,
                isLibrary = true,
                isTrackTitle = false,
                popularity = Long.MAX_VALUE - index,
                catalogIndex = index,
                matchBoost = 2
            )
        }
        graphAlbumLabels.forEachIndexed { index, label ->
            addIfMatching(
                destination = ranked,
                query = normalizedQuery,
                label = label,
                isLibrary = true,
                isTrackTitle = true,
                popularity = Long.MAX_VALUE / 2 - index,
                catalogIndex = index,
                matchBoost = 1
            )
        }
        libraryLabels.forEachIndexed { index, label ->
            addIfMatching(
                destination = ranked,
                query = normalizedQuery,
                label = label,
                isLibrary = true,
                isTrackTitle = true,
                popularity = Long.MAX_VALUE - index,
                catalogIndex = index
            )
        }

        parseCatalog(catalogResponse).forEachIndexed { index, candidate ->
            addIfMatching(
                destination = ranked,
                query = normalizedQuery,
                label = candidate.title,
                isLibrary = false,
                isTrackTitle = true,
                popularity = candidate.popularity,
                catalogIndex = index
            )
            addIfMatching(
                destination = ranked,
                query = normalizedQuery,
                label = candidate.artist,
                isLibrary = false,
                isTrackTitle = false,
                popularity = candidate.popularity,
                catalogIndex = index
            )
        }

        return ranked
            .sortedWith(
                compareByDescending<RankedLabel> { it.matchQuality }
                    .thenByDescending { it.isLibrary }
                    .thenByDescending { it.isTrackTitle }
                    .thenByDescending { it.popularity }
                    .thenBy { it.catalogIndex }
                    .thenBy { it.label.length }
            )
            .distinctBy { normalize(it.label) }
            .take(limit)
            .map { it.label }
    }

    private fun parseCatalog(body: String?): List<CatalogCandidate> {
        if (body.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JsonParser.parseString(body).asJsonObject
            buildList {
                addCatalogArray(this, root.getAsJsonArray("data"))
                addCatalogArray(this, root.getAsJsonObject("tracks")?.getAsJsonArray("data"))
                addCatalogArray(this, root.getAsJsonObject("albums")?.getAsJsonArray("data"))
                root.getAsJsonObject("artists")?.getAsJsonArray("data")?.forEach { element ->
                    val item = element.asJsonObject
                    val name = item.get("name")?.asString?.trim().orEmpty()
                    if (name.isBlank()) return@forEach
                    add(
                        CatalogCandidate(
                            title = name,
                            artist = name,
                            popularity = item.get("nb_fan")?.asLong ?: item.get("rank")?.asLong ?: 0L
                        )
                    )
                }
                root.getAsJsonArray("results")?.forEach { element ->
                    val item = element.asJsonObject
                    val title = item.get("trackName")?.asString?.trim()
                        ?: item.get("collectionName")?.asString?.trim()
                        ?: item.get("artistName")?.asString?.trim()
                        ?: return@forEach
                    val artist = item.get("artistName")?.asString?.trim().orEmpty()
                    add(CatalogCandidate(title = title, artist = artist, popularity = 0L))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun addCatalogArray(
        destination: MutableList<CatalogCandidate>,
        data: com.google.gson.JsonArray?
    ) {
        if (data == null) return
        data.forEach { element ->
            val item = element.asJsonObject
            val title = item.get("title")?.asString?.trim().orEmpty()
            val artist = item.getAsJsonObject("artist")
                ?.get("name")
                ?.asString
                ?.trim()
                .orEmpty()
            if (title.isBlank() || artist.isBlank()) return@forEach
            destination += CatalogCandidate(
                title = title,
                artist = artist,
                popularity = item.get("rank")?.asLong ?: 0L
            )
        }
    }

    private fun addIfMatching(
        destination: MutableList<RankedLabel>,
        query: String,
        label: String,
        isLibrary: Boolean,
        isTrackTitle: Boolean,
        popularity: Long,
        catalogIndex: Int,
        matchBoost: Int = 0
    ) {
        val cleanLabel = label.trim()
        val quality = matchQuality(query, normalize(cleanLabel)) + matchBoost
        if (cleanLabel.isNotBlank() && quality > 0) {
            destination += RankedLabel(
                label = cleanLabel,
                matchQuality = quality,
                isLibrary = isLibrary,
                isTrackTitle = isTrackTitle,
                popularity = popularity,
                catalogIndex = catalogIndex
            )
        }
    }

    private fun matchQuality(query: String, label: String): Int = when {
        label == query -> 5
        label.startsWith(query) -> 4
        label.split(' ').any { it.startsWith(query) } -> 3
        label.contains(query) -> 2
        else -> 0
    }

    /**
     * Fold accents so "beyonce" completes "Beyoncé".
     */
    fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun matchesPrefix(label: String, query: String): Boolean {
        val foldedQuery = normalize(query)
        val foldedLabel = normalize(label)
        if (foldedQuery.isEmpty() || foldedLabel.isBlank()) return false
        return foldedLabel == foldedQuery ||
            foldedLabel.startsWith(foldedQuery) ||
            foldedLabel.split(' ').any { it.startsWith(foldedQuery) }
    }

    /**
     * Gray remainder for inline prediction. Only when the suggestion continues
     * the typed prefix with the same letters (accents may differ).
     */
    fun completionRemainder(query: String, suggestion: String): String? {
        val typed = query.trimStart()
        if (typed.isBlank() || suggestion.isBlank()) return null
        if (suggestion.regionMatches(0, typed, 0, typed.length, ignoreCase = true)) {
            return suggestion.substring(typed.length).takeIf { it.isNotEmpty() }
        }
        val foldedQuery = normalize(typed)
        val foldedSuggestion = normalize(suggestion)
        if (foldedQuery.isEmpty() || !foldedSuggestion.startsWith(foldedQuery)) return null
        if (foldedQuery.length >= foldedSuggestion.length) return null
        // Consume suggestion characters until the folded typed prefix is matched,
        // then return the visible leftover (handles Beyoncé-style accents).
        var foldedConsumed = 0
        var index = 0
        while (index < suggestion.length && foldedConsumed < foldedQuery.length) {
            val foldedChar = normalize(suggestion[index].toString())
            if (foldedChar.isNotEmpty()) {
                foldedConsumed += foldedChar.length
            }
            index += 1
        }
        return suggestion.substring(index).takeIf { it.isNotEmpty() }
    }

    private data class CatalogCandidate(
        val title: String,
        val artist: String,
        val popularity: Long
    )

    private data class RankedLabel(
        val label: String,
        val matchQuality: Int,
        val isLibrary: Boolean,
        val isTrackTitle: Boolean,
        val popularity: Long,
        val catalogIndex: Int
    )
}
