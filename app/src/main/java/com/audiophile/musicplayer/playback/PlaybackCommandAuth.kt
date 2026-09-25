package com.audiophile.musicplayer.playback

import android.content.Context
import android.content.Intent
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Process-local capability for commands sent to the exported MediaLibraryService.
 * MediaSession/Android Auto binding remains public; private startService actions do not.
 */
object PlaybackCommandAuth {
    private const val EXTRA_COMMAND_CAPABILITY =
        "com.audiophile.musicplayer.extra.INTERNAL_COMMAND_CAPABILITY"
    private val processCapability = UUID.randomUUID().toString()

    fun createIntent(context: Context, action: String): Intent =
        Intent(context.applicationContext, PlaybackService::class.java)
            .setAction(action)
            .putExtra(EXTRA_COMMAND_CAPABILITY, processCapability)

    fun isTrusted(intent: Intent): Boolean {
        val supplied = intent.getStringExtra(EXTRA_COMMAND_CAPABILITY).orEmpty()
        return MessageDigest.isEqual(
            supplied.toByteArray(StandardCharsets.UTF_8),
            processCapability.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
