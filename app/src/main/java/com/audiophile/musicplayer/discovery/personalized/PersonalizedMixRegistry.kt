package com.audiophile.musicplayer.discovery.personalized

import com.audiophile.musicplayer.discovery.personalized.generators.DiscoveryWeeklyGenerator
import com.audiophile.musicplayer.discovery.personalized.generators.ReleaseRadarGenerator

data class PersonalizedMixKindSpec(
    val kind: PersonalizedMixKind,
    val displayName: String,
    val subtitle: String,
    val defaultConfig: PersonalizedMixConfig,
    val generator: PersonalizedMixGenerator,
    val requiresVariant: Boolean = false,
    val tags: Set<String> = emptySet()
)

/**
 * Registry of personalized mix kinds — SoulSync-inspired `specs.py` pattern.
 */
class PersonalizedMixRegistry {
    private val specs = mutableMapOf<PersonalizedMixKind, PersonalizedMixKindSpec>()

    init {
        register(
            PersonalizedMixKindSpec(
                kind = PersonalizedMixKind.DISCOVERY_WEEKLY,
                displayName = "Discovery Weekly",
                subtitle = "Fresh picks based on your taste",
                defaultConfig = PersonalizedMixConfig(
                    limit = 45,
                    maxPerAlbum = 2,
                    maxPerArtist = 3,
                    excludeRecentDays = 7,
                    seed = null
                ),
                generator = DiscoveryWeeklyGenerator(),
                tags = setOf("discovery", "curated")
            )
        )
        register(
            PersonalizedMixKindSpec(
                kind = PersonalizedMixKind.RELEASE_RADAR,
                displayName = "Release Radar",
                subtitle = "New music from artists you love",
                defaultConfig = PersonalizedMixConfig(
                    limit = 40,
                    maxPerAlbum = 2,
                    maxPerArtist = 4,
                    recencyDays = 21,
                    excludeRecentDays = 7
                ),
                generator = ReleaseRadarGenerator(),
                tags = setOf("discovery", "curated")
            )
        )
    }

    fun get(kind: PersonalizedMixKind): PersonalizedMixKindSpec? = specs[kind]

    fun all(): List<PersonalizedMixKindSpec> = specs.values.toList()

    fun homeKinds(): List<PersonalizedMixKind> =
        listOf(PersonalizedMixKind.DISCOVERY_WEEKLY, PersonalizedMixKind.RELEASE_RADAR)

    private fun register(spec: PersonalizedMixKindSpec) {
        specs[spec.kind] = spec
    }
}
