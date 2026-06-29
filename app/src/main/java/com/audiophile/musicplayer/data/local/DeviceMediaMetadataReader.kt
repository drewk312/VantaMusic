package com.audiophile.musicplayer.data.local

import android.os.Build
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** Reads YEAR and GENRE from MediaStore for device library tracks (localLibraryId = media _ID). */
class DeviceMediaMetadataReader(private val context: Context) {
    private val appContext = context.applicationContext

    fun yearByMediaId(mediaId: Long): Int? {
        if (mediaId <= 0L) return null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
        return queryColumn(uri, MediaStore.Audio.Media.YEAR)?.toIntOrNull()?.takeIf { it in 1950..2030 }
    }

    fun genreByMediaId(mediaId: Long): String? {
        if (mediaId <= 0L) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
        return queryColumn(uri, MediaStore.Audio.Media.GENRE)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun queryColumn(uri: android.net.Uri, column: String): String? {
        return try {
            appContext.contentResolver.query(
                uri,
                arrayOf(column),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val index = cursor.getColumnIndex(column)
                if (index < 0) return null
                cursor.getString(index)
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
