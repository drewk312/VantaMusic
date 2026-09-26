package com.audiophile.musicplayer.ui

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import java.io.File
import java.util.Locale

@Composable
fun FileInfoSheet(
    title: String,
    artist: String?,
    album: String?,
    streamUrl: String?,
    qualityInfo: VantaQualityInfo?,
    durationMs: Long?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val resolvedFileName = remember(streamUrl) { resolveFileName(context, streamUrl) }
    val resolvedFileSize = remember(streamUrl) { resolveFileSize(context, streamUrl) }

    val audioFormat = qualityInfo?.format?.uppercase(Locale.US)
        ?: inferFormatFromUri(streamUrl)
        ?: "Unknown"

    val container = qualityInfo?.container?.uppercase(Locale.US)
        ?: inferContainerFromUri(streamUrl)
        ?: audioFormat

    val sampleRate = qualityInfo?.sampleRateHz?.let { formatSampleRate(it) } ?: "—"
    val bitDepth = qualityInfo?.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" } ?: "—"
    val bitrate = qualityInfo?.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" } ?: "—"
    val channels = qualityInfo?.channels?.let {
        when (it) {
            1 -> "1.0 Mono"
            2 -> "2.0 Stereo"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> "$it Channels"
        }
    } ?: "Stereo (2.0)"

    val spatialLabel = when {
        qualityInfo?.isDolbyAtmos == true -> "Dolby Atmos (E-AC-3 / JOC)"
        qualityInfo?.isSony360RealityAudio == true -> "360 Reality Audio"
        qualityInfo?.isSpatialAudio == true -> "Spatial Audio"
        else -> "Standard Stereo"
    }

    val losslessLabel = when {
        qualityInfo?.isLossless == true -> "Lossless (Bit-perfect)"
        audioFormat.equals("FLAC", ignoreCase = true) || audioFormat.equals("ALAC", ignoreCase = true) || audioFormat.equals("WAV", ignoreCase = true) -> "Lossless"
        qualityInfo?.isLossless == false -> "Lossy"
        else -> "Standard"
    }

    val isHiRes = qualityInfo?.isHiRes == true ||
        (qualityInfo?.bitDepth != null && qualityInfo.bitDepth > 16) ||
        (qualityInfo?.sampleRateHz != null && qualityInfo.sampleRateHz > 48000)

    val storageSource = when {
        streamUrl.isNullOrBlank() -> "Library Entry"
        streamUrl.startsWith("content://") -> "Local Device (MediaStore)"
        streamUrl.startsWith("file://") || streamUrl.startsWith("/") -> "Local Storage (Direct File)"
        streamUrl.startsWith("http://") || streamUrl.startsWith("https://") -> "Network Stream"
        else -> "Unknown Source"
    }

    VantaBottomSheet(visible = true, onDismiss = onDismiss, maxHeightFraction = 0.88f) {
        VantaSheetHeader("Audio & File Info", onDismiss = onDismiss)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Track Summary Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppOutline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title.ifBlank { "Unknown Title" },
                    color = AppText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!artist.isNullOrBlank()) {
                    Text(
                        text = artist,
                        color = AppAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!album.isNullOrBlank()) {
                    Text(
                        text = album,
                        color = AppTextMuted,
                        fontSize = 12.sp
                    )
                }
                if (durationMs != null && durationMs > 0) {
                    Text(
                        text = "Length: ${formatDuration(durationMs)}",
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Audio Specs Section
            Text(
                text = "AUDIO SPECIFICATIONS",
                color = AppTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface)
                    .border(0.5.dp, AppOutline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                InfoItemRow("Format / Codec", audioFormat)
                InfoItemRow("Container", container)
                InfoItemRow("MIME Type", qualityInfo?.mimeType ?: inferMimeFromUri(streamUrl) ?: "audio/*")
                InfoItemRow("Bit Depth", bitDepth)
                InfoItemRow("Sample Rate", sampleRate)
                InfoItemRow("Bitrate", bitrate)
                InfoItemRow("Channels", channels)
                InfoItemRow("Encoding Quality", losslessLabel)
                InfoItemRow("Hi-Res Audio", if (isHiRes) "Yes (Hi-Res)" else "Standard Resolution")
                InfoItemRow("Spatial Mix", spatialLabel)
            }

            // File & Storage Section
            Text(
                text = "FILE & STORAGE DETAILS",
                color = AppTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface)
                    .border(0.5.dp, AppOutline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                InfoItemRow("Source", storageSource)
                if (resolvedFileName != null) {
                    InfoItemRow("File Name", resolvedFileName)
                }
                if (resolvedFileSize != null) {
                    InfoItemRow("File Size", resolvedFileSize)
                }
            }

            // Path & URI with Copy Button
            if (!streamUrl.isNullOrBlank()) {
                Text(
                    text = "STORAGE URI / PATH",
                    color = AppTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppSurfaceRaised)
                        .border(0.5.dp, AppOutline, RoundedCornerShape(12.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(streamUrl))
                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = streamUrl,
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy URI/Path",
                        tint = AppAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = AppText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f).padding(start = 12.dp)
        )
    }
}

private fun formatSampleRate(sampleRateHz: Int?): String? {
    if (sampleRateHz == null || sampleRateHz <= 0) return null
    return if (sampleRateHz % 1000 == 0) {
        "${sampleRateHz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", sampleRateHz / 1000.0)
    }
}

private fun resolveFileName(context: Context, uriString: String?): String? {
    if (uriString.isNullOrBlank()) return null
    try {
        if (uriString.startsWith("file://") || uriString.startsWith("/")) {
            return File(uriString.removePrefix("file://")).name
        }
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
    } catch (_: Exception) {}
    return uriString.substringAfterLast('/')
}

private fun resolveFileSize(context: Context, uriString: String?): String? {
    if (uriString.isNullOrBlank()) return null
    try {
        if (uriString.startsWith("file://") || uriString.startsWith("/")) {
            val path = uriString.removePrefix("file://")
            val f = File(path)
            if (f.exists() && f.length() > 0) return formatBytes(f.length())
        }
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size > 0) return formatBytes(size)
            }
        }
    } catch (_: Exception) {}
    return null
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
}

private fun inferFormatFromUri(uriString: String?): String? {
    if (uriString.isNullOrBlank()) return null
    val lower = uriString.lowercase(Locale.ROOT)
    return when {
        lower.contains(".flac") -> "FLAC"
        lower.contains(".m4a") || lower.contains(".mp4") -> "M4A / AAC"
        lower.contains(".mp3") -> "MP3"
        lower.contains(".wav") -> "WAV"
        lower.contains(".ogg") -> "OGG"
        lower.contains(".opus") -> "OPUS"
        else -> null
    }
}

private fun inferContainerFromUri(uriString: String?): String? {
    if (uriString.isNullOrBlank()) return null
    val lower = uriString.lowercase(Locale.ROOT)
    return when {
        lower.contains(".flac") -> "FLAC"
        lower.contains(".m4a") -> "M4A"
        lower.contains(".mp4") -> "MP4"
        lower.contains(".mp3") -> "MP3"
        lower.contains(".wav") -> "WAV"
        lower.contains(".ogg") -> "OGG"
        lower.contains(".opus") -> "OPUS"
        else -> null
    }
}

private fun inferMimeFromUri(uriString: String?): String? {
    if (uriString.isNullOrBlank()) return null
    val lower = uriString.lowercase(Locale.ROOT)
    return when {
        lower.contains(".flac") -> "audio/flac"
        lower.contains(".m4a") || lower.contains(".mp4") -> "audio/mp4"
        lower.contains(".mp3") -> "audio/mpeg"
        lower.contains(".wav") -> "audio/wav"
        lower.contains(".ogg") -> "audio/ogg"
        lower.contains(".opus") -> "audio/opus"
        else -> null
    }
}
