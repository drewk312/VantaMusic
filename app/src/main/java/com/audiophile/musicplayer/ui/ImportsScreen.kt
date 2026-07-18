package com.audiophile.musicplayer.ui

import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.audiophile.musicplayer.permissions.MediaPermissions
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.importer.SpotifyExportImporter
import com.audiophile.musicplayer.data.importer.readBoundedText
import java.text.DateFormat
import java.util.Date

@Composable
fun ImportsScreen(
    imports: List<ImportBatchEntity>,
    isScanningDeviceLibrary: Boolean = false,
    deviceScanProgress: String? = null,
    statusMessage: String? = null,
    onBack: () -> Unit,
    onNewImport: () -> Unit,
    onScanDevice: () -> Unit,
    onImportFile: (android.net.Uri) -> Unit,
    onImportTracklistFile: (String, String) -> Unit
) {
    val context = LocalContext.current

    val permissionToRequest = MediaPermissions.requiredAudioPermission()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onScanDevice()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportFile(uri)
        }
    }

    val tracklistFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = readTextDocument(context, uri)
            if (text.isNotBlank()) {
                onImportTracklistFile(displayNameForUri(context, uri) ?: "Imported Tracklist", text)
            }
        }
    }

    val spotifyExportPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val tracklist = runCatching {
                context.contentResolver.openInputStream(uri)?.use(SpotifyExportImporter::extractTracklist).orEmpty()
            }.getOrDefault("")
            if (tracklist.isNotBlank()) onImportTracklistFile("Spotify Library", tracklist)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(horizontal = 24.dp)
            .padding(bottom = appBottomWindowInsets() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Library intake", color = AppText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Bring the music you already love into VANTA.", color = AppTextSecondary, fontSize = 14.sp)
            }
            TextButton(onClick = onBack) { Text("Close", color = AppAccent) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CHOOSE A SOURCE", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text("Your music stays yours. VANTA reads these files on this device.", color = AppTextSecondary, fontSize = 13.sp)
            ImportActionRow(
                icon = Icons.Filled.LibraryMusic,
                title = "Spotify library export",
                subtitle = "Import the privacy ZIP Spotify gives you",
                onClick = { spotifyExportPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) }
            )
            ImportActionRow(
                icon = Icons.Filled.QueueMusic,
                title = "Apple Music or iTunes library",
                subtitle = "Import an exported XML or text library file",
                onClick = {
                    tracklistFilePickerLauncher.launch(
                        arrayOf("text/*", "application/xml", "text/xml", "application/octet-stream")
                    )
                }
            )
            ImportActionRow(
                icon = Icons.Filled.LibraryMusic,
                title = if (isScanningDeviceLibrary) "Scanning your device" else "Scan device music",
                subtitle = deviceScanProgress ?: "Find audio already stored on this phone",
                emphasized = true,
                enabled = !isScanningDeviceLibrary,
                onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            onScanDevice()
                        } else {
                            permissionLauncher.launch(permissionToRequest)
                        }
                    }
            )

            ImportActionRow(
                icon = Icons.Filled.QueueMusic,
                title = "Import an audio file",
                subtitle = "Choose a song or album from your files",
                onClick = { filePickerLauncher.launch(arrayOf("audio/*")) }
            )
            ImportActionRow(
                icon = Icons.Filled.Link,
                title = "Paste links or a tracklist",
                subtitle = "Bring in a playlist from a link or text",
                onClick = onNewImport
            )
            ImportActionRow(
                icon = Icons.Filled.QueueMusic,
                title = "Other library file",
                subtitle = "CSV, JSON, XML, or a plain text list",
                onClick = {
                    tracklistFilePickerLauncher.launch(
                            arrayOf(
                                "text/*",
                                "text/csv",
                                "application/json",
                                "application/xml",
                                "text/xml",
                                "application/octet-stream"
                            )
                    )
                }
            )
        }

        statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(message, color = AppTextSecondary, fontSize = 13.sp)
        }

        // Import Batch History
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Recent intake", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.fillMaxWidth().glassSurface().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (imports.isEmpty()) {
                        Text("Your completed imports will appear here.", color = AppTextSecondary)
                    } else {
                        imports.forEachIndexed { index, batch ->
                            ImportBatchRow(batch)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val container = if (emphasized) AppAccent.copy(alpha = 0.12f) else AppSurface.copy(alpha = 0.62f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (emphasized) AppAccent else AppTextSecondary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (enabled) AppText else AppTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1)
        }
        if (!enabled) CircularProgressIndicator(modifier = Modifier.padding(4.dp), color = AppAccent, strokeWidth = 2.dp)
        else Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AppTextMuted)
    }
}

@Composable
private fun ImportBatchRow(batch: ImportBatchEntity) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(batch.name, color = AppText, fontWeight = FontWeight.SemiBold)
            Text(importStatus(batch), color = AppAccent, fontSize = 12.sp)
        }
        Text(
            "${batch.sourceLabel} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(batch.importedAt))}",
            color = AppTextSecondary,
            fontSize = 12.sp
        )
        Text(
            "${batch.totalTracks} tracks · ${batch.playableCount} Playable · ${batch.metadataOnlyCount} Matched · ${batch.notFoundCount} Not Found",
            color = AppTextMuted,
            fontSize = 12.sp
        )
    }
}

private fun importStatus(batch: ImportBatchEntity): String {
    return when {
        batch.notFoundCount == 0 && batch.needsReviewCount == 0 -> "Ready"
        batch.playableCount > 0 || batch.metadataOnlyCount > 0 -> "Review"
        else -> "Needs Work"
    }
}

private fun readTextDocument(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readBoundedText() }.orEmpty()
    }.getOrDefault("")
}

private fun displayNameForUri(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }.getOrNull()
}
