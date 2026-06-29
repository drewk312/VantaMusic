package com.audiophile.musicplayer.data.voice

import android.content.Context
import java.io.File
import java.security.MessageDigest

class PulseVoiceCache(private val context: Context) {
    private val dir = File(context.applicationContext.cacheDir, "pulse_voice").apply { mkdirs() }

    fun keyFor(text: String, engine: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest("$engine|$text".encodeToByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun read(key: String): ByteArray? {
        val file = File(dir, "$key.mp3")
        if (!file.exists() || file.length() == 0L) return null
        return file.readBytes()
    }

    fun write(key: String, audio: ByteArray, extension: String = "mp3") {
        val file = File(dir, "$key.$extension")
        file.writeBytes(audio)
    }

    fun pathFor(key: String): String? {
        return listOf("wav", "mp3")
            .map { File(dir, "$key.$it") }
            .firstOrNull { it.exists() && it.length() > 0L }
            ?.absolutePath
    }
}
