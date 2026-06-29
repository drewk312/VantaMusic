package com.audiophile.musicplayer.discovery.personalized

interface PersonalizedMixGenerator {
    suspend fun generate(
        deps: PersonalizedMixDeps,
        variant: String,
        config: PersonalizedMixConfig
    ): List<MixCandidate>
}
