package com.audiophile.musicplayer.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack

data class DownloadEnqueueResult(
    val downloadId: Long,
    val fileName: String
)

data class DownloadSnapshot(
    val downloadId: Long,
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val localUri: Uri?,
    val reason: Int
)

class AndroidTrackDownloadManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences = appContext.getSharedPreferences("vanta_managed_downloads", Context.MODE_PRIVATE)

    fun enqueueTrackDownload(
        track: UnifiedTrack,
        source: TrackSource,
        destinationSubdirectory: String = "VANTA Music"
    ): DownloadEnqueueResult {
        val fileName = buildFileName(track, source)
        val request = DownloadManager.Request(source.streamUrl.toUri())
            .setTitle(track.title)
            .setDescription(track.artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MUSIC,
                "$destinationSubdirectory/$fileName"
            )

        val downloadId = downloadManager.enqueue(request)
        val pending = preferences.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty().toMutableSet()
        pending.add(downloadId.toString())
        preferences.edit().putStringSet(KEY_PENDING_IDS, pending).apply()
        return DownloadEnqueueResult(downloadId = downloadId, fileName = fileName)
    }

    fun pendingDownloadIds(): Set<Long> =
        preferences.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()

    fun markDownloadHandled(downloadId: Long) {
        val pending = preferences.getStringSet(KEY_PENDING_IDS, emptySet()).orEmpty().toMutableSet()
        pending.remove(downloadId.toString())
        preferences.edit().putStringSet(KEY_PENDING_IDS, pending).apply()
    }

    fun getDownloadedFileUri(downloadId: Long): Uri? =
        downloadManager.getUriForDownloadedFile(downloadId) ?: getDownloadSnapshot(downloadId)?.localUri

    fun getDownloadSnapshot(downloadId: Long): DownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val localUriRaw = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

            return DownloadSnapshot(
                downloadId = downloadId,
                status = status,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                localUri = localUriRaw?.toUri(),
                reason = reason
            )
        }
    }

    private fun buildFileName(track: UnifiedTrack, source: TrackSource): String {
        val extension = when {
            source.streamUrl.contains(".flac", ignoreCase = true) -> "flac"
            source.streamUrl.contains(".wav", ignoreCase = true) -> "wav"
            source.streamUrl.contains(".m4a", ignoreCase = true) -> "m4a"
            source.streamUrl.contains(".mp3", ignoreCase = true) -> "mp3"
            source.bitrate >= 900 -> "flac"
            else -> "m4a"
        }
        val artist = sanitizeFileSegment(track.artist)
        val title = sanitizeFileSegment(track.title)
        return "$artist - $title.$extension"
    }

    private fun sanitizeFileSegment(value: String): String {
        return value.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
    }

    private companion object {
        const val KEY_PENDING_IDS = "pending_ids"
    }
}
