package com.audiophile.musicplayer.data.metadata.apple

data class AppleMusicConfig(
    val developerToken: String? = null,
    val storefront: String = "us"
) {
    val isEnabled: Boolean
        get() = !developerToken.isNullOrBlank()

    companion object {
        fun disabled(storefront: String = "us"): AppleMusicConfig {
            return AppleMusicConfig(developerToken = null, storefront = storefront)
        }
    }
}
