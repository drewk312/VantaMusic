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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = appTopContentPadding(extra = 16.dp))
            .padding(bottom = appBottomWindowInsets()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Import Music", color = AppText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste links, exported playlists, CSV, JSON, XML, M3U, or plain song lists.",
                    color = AppTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            TextButton(onClick = onBack) { Text("Back", color = AppAccent) }
        }

        VantaCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = importName,
                    onValueChange = {
                        importName = it
                        onTextChanged(importName, pastedText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Import name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppOutline,
                        cursorColor = AppAccent,
                        focusedLabelColor = AppAccent,
                        unfocusedLabelColor = AppTextSecondary
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = {
                        pastedText = it
                        onTextChanged(importName, pastedText)
                    },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    label = { Text("Paste playlist or song list") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppOutline,
                        cursorColor = AppAccent,
                        focusedLabelColor = AppAccent,
                        unfocusedLabelColor = AppTextSecondary
                    ),
                    minLines = 8
                )
                VantaCard {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Supported formats:", color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Apple Music / Spotify / Tidal share links", color = AppTextSecondary, fontSize = 13.sp)
                        Text("• Eclipse Music playlist links", color = AppTextSecondary, fontSize = 13.sp)
                        Text("• Song Title - Artist / CSV / JSON / XML / M3U", color = AppTextSecondary, fontSize = 13.sp)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPreview(importName, pastedText) },
                        enabled = pastedText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Preview Import")
                    }
                    OutlinedButton(
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
