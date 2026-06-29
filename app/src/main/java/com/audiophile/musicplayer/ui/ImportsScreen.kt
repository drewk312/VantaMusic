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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.audiophile.musicplayer.permissions.MediaPermissions
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(bottom = appBottomWindowInsets()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Imports", color = AppText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Local music & tracklists", color = AppTextSecondary, fontSize = 14.sp)
            }
            TextButton(onClick = onBack) { Text("Back") }
        }

        // Actions Card
        Box(modifier = Modifier.fillMaxWidth().glassSurface().padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ingest Music", color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                Button(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            onScanDevice()
                        } else {
                            permissionLauncher.launch(permissionToRequest)
                        }
                    },
                    enabled = !isScanningDeviceLibrary,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = Color.Black)
                ) {
                    Text(if (isScanningDeviceLibrary) "Scanning..." else "Scan Device for Music")
                }

                if (isScanningDeviceLibrary) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 2.dp),
                            color = AppAccent,
                            strokeWidth = 2.dp
                        )
                        Text(
                            deviceScanProgress ?: "Scanning device library...",
                            color = AppTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(message, color = AppTextSecondary, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("audio/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AppCardRaised, contentColor = AppText)
                ) {
                    Text("Import Audio File (SAF)")
                }

                Button(
                    onClick = onNewImport,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AppCard, contentColor = AppText)
                ) {
                    Text("Paste Links or Tracklist")
                }

                Button(
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
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AppSurfaceRaised, contentColor = AppText)
                ) {
                    Text("Import Tracklist File")
                }
            }
        }

        // Import Batch History
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import History", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.fillMaxWidth().glassSurface().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (imports.isEmpty()) {
                        Text("No past pasted tracklists found.", color = AppTextSecondary)
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
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
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
