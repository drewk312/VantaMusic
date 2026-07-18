package com.audiophile.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity

@Composable
fun AddToPlaylistSheet(
    playlists: List<PlaylistEntity>,
    onCreatePlaylist: (String) -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewPlaylistInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    VantaBottomSheet(visible = true, onDismiss = onDismiss) {
        Text(
            "Add to Playlist",
            color = AppText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(20.dp))
        VantaSheetDivider()

        if (showNewPlaylistInput) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Playlist name", color = AppTextMuted) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = AppText, fontSize = 14.sp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppOutline,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        cursorColor = AppAccent
                    )
                )
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Create",
                    tint = if (newPlaylistName.isNotBlank()) AppAccent else AppTextMuted,
                    modifier = Modifier.size(28.dp).clickable(enabled = newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName.trim())
                        showNewPlaylistInput = false
                        newPlaylistName = ""
                    }
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cancel",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(28.dp).clickable {
                        showNewPlaylistInput = false
                        newPlaylistName = ""
                    }
                )
            }
            VantaSheetDivider()
        } else {
            VantaSheetAction(
                icon = Icons.Filled.Add,
                label = "New Playlist",
                onClick = { showNewPlaylistInput = true }
            )
            VantaSheetDivider()
        }

        if (playlists.isEmpty() && !showNewPlaylistInput) {
            Spacer(Modifier.height(32.dp))
            Text(
                "No playlists yet",
                color = AppTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(32.dp))
        } else {
            playlists.forEach { playlist ->
                VantaSheetAction(
                    icon = Icons.Filled.PlaylistPlay,
                    label = DisplayMetadataCleaner.cleanDisplayName(playlist.name).ifBlank { playlist.name },
                    subtitle = if (playlist.description != null) playlist.description else null,
                    onClick = {
                        onAddToPlaylist(playlist.id)
                        onDismiss()
                    }
                )
            }
        }
    }
}
