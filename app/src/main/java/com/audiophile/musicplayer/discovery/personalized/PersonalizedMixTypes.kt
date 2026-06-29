package com.audiophile.musicplayer.discovery.personalized

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PersonalizedMixConfig(
    val limit: Int = 50,
    val maxPerAlbum: Int = 2,
    val maxPerArtist: Int = 3,
    val popularityMin: Int? = null,
    val popularityMax: Int? = null,
    val excludeRecentDays: Int = 0,
    val recencyDays: Int? = null,
    val seed: Long? = null,
    val extra: Map<String, Any?> = emptyMap()
) {
    fun merged(overrides: PersonalizedMixConfig): PersonalizedMixConfig {
        val mergedExtra = extra.toMutableMap()
        mergedExtra.putAll(overrides.extra)
        return copy(
            limit = overrides.limit.takeIf { overrides.limit != limit } ?: limit,
            maxPerAlbum = if (overrides.maxPerAlbum != maxPerAlbum) overrides.maxPerAlbum else maxPerAlbum,
            maxPerArtist = if (overrides.maxPerArtist != maxPerArtist) overrides.maxPerArtist else maxPerArtist,
            popularityMin = overrides.popularityMin ?: popularityMin,
            popularityMax = overrides.popularityMax ?: popularityMax,
            excludeRecentDays = if (overrides.excludeRecentDays != excludeRecentDays) {
                overrides.excludeRecentDays
            } else {
                excludeRecentDays
            },
            recencyDays = overrides.recencyDays ?: recencyDays,
            seed = overrides.seed ?: seed,
            extra = mergedExtra
        )
    }

    companion object {
        private val gson = Gson()

        fun toJson(config: PersonalizedMixConfig): String = gson.toJson(config)

        fun fromJson(json: String): PersonalizedMixConfig {
            if (json.isBlank()) return PersonalizedMixConfig()
            return runCatching {
                gson.fromJson(json, PersonalizedMixConfig::class.java)
            }.getOrDefault(PersonalizedMixConfig())
        }
    }
}

/** Pre-resolution candidate from generator search/scoring. */
data class MixCandidate(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val providerId: String? = null,
    val externalTrackId: String? = null,
    val isrc: String? = null,
    val releaseYear: Int? = null,
    val releaseDate: String? = null,
    val normKey: String = TrackNormKey.normalize(title, artist),
    val score: Double = 0.0,
    val source: String? = null
)

data class PersonalizedMixRecord(
    val id: Long,
    val kind: PersonalizedMixKind,
    val variant: String,
    val name: String,
    val config: PersonalizedMixConfig,
    val trackCount: Int,
    val lastGeneratedAt: Long?,
    val lastGenerationError: String?,
    val isStale: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class PersonalizedMixTrackRow(
    val position: Int,
    val trackId: Long?,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val providerId: String?,
    val externalTrackId: String?,
    val normKey: String
)

data class PersonalizedMixSnapshot(
    val record: PersonalizedMixRecord,
    val tracks: List<PersonalizedMixTrackRow>
)

data class PersonalizedMixPlaybackBundle(
    val record: PersonalizedMixRecord,
    val playableTracks: List<UnifiedTrackWithSources>
)
