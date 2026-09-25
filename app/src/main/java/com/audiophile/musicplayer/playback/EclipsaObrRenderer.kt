@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.audiophile.musicplayer.playback

/** OBR is linked into the bundled IAMF decoder; no optional plug-in is required. */
object EclipsaObrRenderer {
    val isAvailable: Boolean get() = androidx.media3.decoder.iamf.IamfLibrary.isAvailable()
}
