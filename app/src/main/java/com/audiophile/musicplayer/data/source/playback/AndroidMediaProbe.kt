package com.audiophile.musicplayer.data.source.playback

import android.content.ContentResolver
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class AndroidMediaProbe(
    private val contentResolver: ContentResolver
) : MediaProbe {
    override fun readHeader(uri: String, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(maxBytes.coerceAtLeast(1))
        val read = try {
            when {
                uri.startsWith("content:", ignoreCase = true) ||
                    uri.startsWith("file:", ignoreCase = true) -> {
                    contentResolver.openInputStream(uri.toUri())?.use { input ->
                        readFully(input, buffer)
                    } ?: return null
                }
                else -> {
                    val file = File(uri)
                    if (!file.isFile) return FileMediaProbe.readHeader(uri, maxBytes)
                    file.inputStream().use { input -> readFully(input, buffer) }
                }
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: SecurityException) {
            return null
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (read <= 0) return null
        return buffer.copyOf(read)
    }

    override fun lengthBytes(uri: String): Long? = try {
        when {
            uri.startsWith("content:", ignoreCase = true) ||
                uri.startsWith("file:", ignoreCase = true) -> {
                contentResolver.openAssetFileDescriptor(uri.toUri(), "r")?.use { descriptor ->
                    descriptor.length.takeIf { it > 0L }
                        ?: descriptor.parcelFileDescriptor.statSize.takeIf { it > 0L }
                }
            }
            else -> File(uri).takeIf { it.isFile }?.length()?.takeIf { it > 0L }
        }
    } catch (_: FileNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count <= 0) break
            total += count
        }
        return total
    }
}
