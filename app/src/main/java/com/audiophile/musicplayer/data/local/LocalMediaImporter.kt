package com.audiophile.musicplayer.data.local

import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class LocalImportResult(
    val scannedCount: Int,
    val importedCount: Int
)

private data class LocalAudioMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val genre: String?,
    val mimeType: String?,
    val bitrateKbps: Int?,
    val artworkUrl: String?
)

class LocalMediaImporter(
    private val context: Context,
    private val trackRepository: TrackRepository
) {
    private val appContext = context.applicationContext

    suspend fun importDeviceLibrary(
        limit: Int = 500,
        onProgress: ((scanned: Int, imported: Int) -> Unit)? = null
    ): LocalImportResult = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
            }
            add(MediaStore.Audio.Media.YEAR)
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        var scanned = 0
        var imported = 0

        val cursor = try {
            queryAudioMedia(resolver, collection, projection, selection, limit)
        } catch (e: SecurityException) {
            Log.e("LocalMediaImporter", "MediaStore access denied during device scan", e)
            return@withContext LocalImportResult(scannedCount = 0, importedCount = 0)
        } catch (e: Exception) {
            Log.e("LocalMediaImporter", "MediaStore query failed during device scan", e)
            return@withContext LocalImportResult(scannedCount = 0, importedCount = 0)
        } ?: return@withContext LocalImportResult(scannedCount = 0, importedCount = 0)

        cursor.use {
            val idIndex = it.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleIndex = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIndex = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumIndex = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationIndex = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val mimeTypeIndex = it.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
            val genreIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) it.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            val yearIndex = it.getColumnIndex(MediaStore.Audio.Media.YEAR)
            if (idIndex < 0 || titleIndex < 0) {
                Log.e("LocalMediaImporter", "MediaStore cursor missing required columns")
                return@withContext LocalImportResult(scannedCount = 0, importedCount = 0)
            }

            while (it.moveToNext()) {
                scanned += 1
                try {
                    val mediaId = it.getLong(idIndex)
                    val title = it.getString(titleIndex)?.trim().orEmpty()
                    val artist = it.getString(artistIndex)?.trim().orEmpty().ifBlank { "Unknown Artist" }
                    val album = if (albumIndex >= 0) {
                        it.getString(albumIndex)?.trim().orEmpty().ifBlank { null }
                    } else {
                        null
                    }
                    val durationMs = if (durationIndex >= 0) it.getLong(durationIndex) else 0L
                    val mimeType = if (mimeTypeIndex >= 0) it.getString(mimeTypeIndex)?.trim() else null
                    val genre = if (genreIndex >= 0) {
                        if (genreIndex >= 0) it.getString(genreIndex) else null?.trim()?.takeIf { value -> value.isNotBlank() }
                    } else {
                        null
                    }

                    if (title.isBlank()) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(collection, mediaId)
                    // Bulk scan uses MediaStore metadata only. Opening MediaMetadataRetriever
                    // (and loading embedded artwork) per track can OOM-kill the process.
                    val trackId = trackRepository.addTrackSource(
                        title = title,
                        artist = artist,
                        album = album,
                        coverArtUrl = null,
                        sourceType = SourceType.LOCAL,
                        streamUrl = contentUri.toString(),
                        bitrate = 0,
                        localLibraryId = mediaId,
                        genre = genre,
                        durationMs = durationMs.takeIf { value -> value > 0 }
                    )
                    if (trackId > 0L) {
                        imported += 1
                    }
                    if (scanned == 1 || scanned % 25 == 0) {
                        onProgress?.invoke(scanned, imported)
                    }
                } catch (e: Exception) {
                    Log.w("LocalMediaImporter", "Skipped track during device scan", e)
                }
            }
        }

        onProgress?.invoke(scanned, imported)
        LocalImportResult(scannedCount = scanned, importedCount = imported)
    }

    private fun queryAudioMedia(
        resolver: ContentResolver,
        collection: Uri,
        projection: Array<String>,
        selection: String,
        limit: Int
    ): android.database.Cursor? {
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Audio.Media.DATE_ADDED)
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        return resolver.query(collection, projection, queryArgs, null)
    }

    suspend fun importSingleUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadata = readAudioMetadata(
                uri = uri,
                fallbackTitle = getFileName(uri),
                fallbackArtist = "Unknown Artist",
                fallbackAlbum = null,
                fallbackDurationMs = null,
                fallbackMimeType = null
            )

            trackRepository.addTrackSource(
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                coverArtUrl = metadata.artworkUrl,
                sourceType = SourceType.LOCAL,
                streamUrl = uri.toString(),
                bitrate = metadata.bitrateKbps ?: 0,
                genre = metadata.genre,
                durationMs = metadata.durationMs
            )
            return@withContext true
        } catch (e: Exception) {
            Log.e("LocalMediaImporter", "Failed to import URI: $uri", e)
            return@withContext false
        }
    }

    private fun readAudioMetadata(
        uri: Uri,
        fallbackTitle: String?,
        fallbackArtist: String?,
        fallbackAlbum: String?,
        fallbackDurationMs: Long?,
        fallbackMimeType: String?
    ): LocalAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val title = cleanTag(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                ?: cleanTag(fallbackTitle)
                ?: getFileName(uri)
            val artist = cleanTag(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                ?: cleanTag(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                ?: cleanTag(fallbackArtist)
                ?: "Unknown Artist"
            val album = cleanTag(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                ?: cleanTag(fallbackAlbum)
            val durationMs = parsePositiveLong(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ) ?: fallbackDurationMs?.takeIf { it > 0L }
            val genre = cleanTag(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE))
            val mimeType = cleanTag(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
                ?: cleanTag(fallbackMimeType)
            val bitrateKbps = parseBitrateKbps(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            )
            LocalAudioMetadata(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                genre = genre,
                mimeType = mimeType,
                bitrateKbps = bitrateKbps,
                artworkUrl = writeEmbeddedArtwork(uri, retriever.embeddedPicture)
            )
        } catch (e: Exception) {
            Log.w("LocalMediaImporter", "Falling back to MediaStore metadata for: $uri", e)
            LocalAudioMetadata(
                title = cleanTag(fallbackTitle) ?: getFileName(uri),
                artist = cleanTag(fallbackArtist) ?: "Unknown Artist",
                album = cleanTag(fallbackAlbum),
                durationMs = fallbackDurationMs?.takeIf { it > 0L },
                genre = null,
                mimeType = cleanTag(fallbackMimeType),
                bitrateKbps = null,
                artworkUrl = null
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun writeEmbeddedArtwork(uri: Uri, picture: ByteArray?): String? {
        if (picture == null || picture.isEmpty()) return null
        return try {
            val rawName = uri.lastPathSegment ?: "temp"
            val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
            val file = File(appContext.cacheDir, "art_${safeName}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos -> fos.write(picture) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("LocalMediaImporter", "Failed to write embedded artwork for: $uri", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Imported Track"
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    name = cursor.getString(nameIdx)
                }
            }
        }
        // strip extension
        return if (name.contains(".")) {
            name.substringBeforeLast(".")
        } else {
            name
        }
    }

    private fun estimateBitrate(mimeType: String?): Int {
        return 0
    }

    private fun cleanTag(value: String?): String? {
        val cleaned = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalized = cleaned.lowercase(Locale.US)
        return when (normalized) {
            "<unknown>", "unknown", "unknown artist", "unknown album" -> null
            else -> cleaned
        }
    }

    private fun parsePositiveLong(value: String?): Long? =
        value?.trim()?.toLongOrNull()?.takeIf { it > 0L }

    private fun parseBitrateKbps(value: String?): Int? {
        val raw = value?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val kbps = if (raw > 10_000L) raw / 1_000L else raw
        return kbps.takeIf { it in 1L..10_000L }?.toInt()
    }
}
