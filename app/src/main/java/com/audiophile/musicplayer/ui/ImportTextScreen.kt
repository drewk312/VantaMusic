package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ImportTextScreen(
    onBack: () -> Unit,
    initialImportName: String,
    initialPastedText: String,
    onTextChanged: (String, String) -> Unit,
    onPreview: (String, String) -> Unit
) {
    var importName by remember { mutableStateOf(initialImportName.ifBlank { "Imported Playlist" }) }
    var pastedText by remember {
        mutableStateOf(initialPastedText)
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
            Column(modifier = Modifier.weight(1f)) {
                Text("Import Music", color = AppText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste links, exported playlists, CSV, JSON, XML, M3U, or plain song lists. VANTA will identify the tracks, resolve playable sources, and add them to your library.",
                    color = AppTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            TextButton(onClick = onBack) { Text("Back") }
        }

        Box(
            modifier = Modifier.fillMaxWidth().glassSurface().padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = importName,
                    onValueChange = {
                        importName = it
                        onTextChanged(importName, pastedText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Import name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = {
                        pastedText = it
                        onTextChanged(importName, pastedText)
                    },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    label = { Text("Paste playlist or song list") },
                    minLines = 10
                )
                Text(
                    "Examples:\nhttps://music.apple.com/us/song/stupid-song/1889992115\nhttps://open.spotify.com/track/...\nSong Title - Artist\nArtist - Song Title\nSong Title, Artist, Album\n{\"title\":\"Song Title\",\"artist\":\"Artist\"}",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPreview(importName, pastedText) },
                        enabled = pastedText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Preview Import")
                    }
                    TextButton(
                        onClick = {
                            pastedText = ""
                            onTextChanged(importName, pastedText)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}
