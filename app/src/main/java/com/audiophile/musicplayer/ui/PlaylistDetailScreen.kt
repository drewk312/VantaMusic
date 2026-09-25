package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.ui.NetworkArtwork
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    playlistDescription: String?,
    playlistArtworkUrl: String?,
    tracks: List<LocalSongEntity>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (LocalSongEntity) -> Unit,
    onDeletePlaylist: () -> Unit,
    playlists: List<com.audiophile.musicplayer.data.local.entities.PlaylistEntity> = emptyList(),
    onAddSelected: (List<Long>, Long?) -> Unit = { _, _ -> },
    onImportCsv: (String) -> Unit = {},
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false
) {
    var selecting by remember(playlistName) { mutableStateOf(false) }
    var selected by remember(playlistName) { mutableStateOf(emptySet<Long>()) }
    var showDestinations by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= 2_000_000) { "Choose a CSV smaller than 2 MB." }
                                output.write(buffer, 0, count)
                            }
                            output.toString("UTF-8")
                        }.orEmpty().also { require(it.isNotBlank()) { "This file is empty." } }
                    }
                }
                result.onSuccess(onImportCsv).onFailure { importError = it.message ?: "Could not read this file. Try another export." }
            }
        }
    }
    if (showDeleteConfirmation) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false },
        title = { Text("Delete this playlist?") },
        text = { Text("$playlistName will be removed. Your songs and Liked Songs stay in your library.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { showDeleteConfirmation = false; onDeletePlaylist() }) { Text("Delete playlist", color = AppDestructive) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { showDeleteConfirmation = false }) { Text("Keep playlist") } }
    )
    importError?.let { message -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { importError = null }, title = { Text("Import needs attention") }, text = { Text(message) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { importError = null }) { Text("OK") } }
    ) }
    if (showDestinations) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showDestinations = false },
        title = { Text("Add ${selected.size} songs") },
        text = {
            LazyColumn {
                item {
                    androidx.compose.material3.TextButton(onClick = {
                        onAddSelected(selected.toList(), null); showDestinations = false
                    }) { Text("Liked Songs") }
                }
                itemsIndexed(playlists) { _, playlist ->
                    androidx.compose.material3.TextButton(onClick = {
                        onAddSelected(selected.toList(), playlist.id); showDestinations = false
                    }) { Text(playlist.name) }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { showDestinations = false }) { Text("Done") } }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AppText,
                modifier = Modifier.size(32.dp).clickable(onClick = onBack).padding(4.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete playlist",
                tint = AppWarning,
                modifier = Modifier.size(48.dp).clickable { showDeleteConfirmation = true }.padding(12.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(
                bottom = appOverlayBottomPadding(
                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = bottomNavVisible
                )
            )
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")) }) { Text("Import CSV") }
                    androidx.compose.material3.TextButton(onClick = { selecting = !selecting; selected = emptySet() }) { Text(if (selecting) "Cancel" else "Select") }
                }
                if (selecting) Row(Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.material3.TextButton(onClick = { selected = tracks.map { it.id }.toSet() }) { Text("Select all") }
                    androidx.compose.material3.TextButton(enabled = selected.isNotEmpty(), onClick = { showDestinations = true }) { Text("Add ${selected.size} to…") }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlistArtworkUrl != null) {
                        NetworkArtwork(artworkUrl = playlistArtworkUrl, seed = playlistName, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = AppAccent,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        playlistName,
                        color = AppText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!playlistDescription.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            playlistDescription,
                            color = AppTextSecondary,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                        color = AppTextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = onPlayAll,
                            colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All")
                        }
                        OutlinedButton(onClick = onShuffle) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            itemsIndexed(tracks) { index, song ->
                PlaylistTrackRow(
                    song = song,
                    index = index + 1,
                    selection = if (selecting) song.id in selected else null,
                    onClick = {
                        if (selecting) selected = if (song.id in selected) selected - song.id else selected + song.id
                        else onPlayTrack(song)
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    song: LocalSongEntity,
    index: Int,
    selection: Boolean? = null,
    onClick: () -> Unit
) {
    val cleanTitle = remember(song.title, song.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(song.title, song.artist, null).title
    }
    val cleanArtist = remember(song.artist) {
        DisplayMetadataCleaner.cleanArtistName(song.artist) ?: song.artist
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selection == null) "$index" else if (selection) "✓" else "○",
            color = AppTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                cleanTitle,
                color = AppText,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                cleanArtist,
                color = AppTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!song.streamUrl.isNullOrBlank()) {
            Icon(
                Icons.Filled.PlayCircleOutline,
                contentDescription = "Playable",
                tint = AppSuccess,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
