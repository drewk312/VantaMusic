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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.importer.CollectionResolver
import com.audiophile.musicplayer.data.importer.CollectionResult
import com.audiophile.musicplayer.data.importer.ParsedPlaylistLine
import com.audiophile.musicplayer.data.importer.SoundiizTextParser

@Composable
fun ImportPreviewScreen(
    pastedText: String,
    onBack: () -> Unit,
    onContinueToMatch: () -> Unit
) {
    val isEclipsePlaylist = SoundiizTextParser.isEclipsePlaylistUrl(pastedText)
    val isCollectionUrl = !isEclipsePlaylist && CollectionResolver.isCollectionUrl(pastedText.trim())
    var collectionResult by remember { mutableStateOf<CollectionResult?>(null) }
    var resolvingCollection by remember { mutableStateOf(false) }

    LaunchedEffect(pastedText) {
        if (isCollectionUrl) {
            resolvingCollection = true
            collectionResult = CollectionResolver().resolve(pastedText.trim())
            resolvingCollection = false
        }
    }

    val rows = when {
        isEclipsePlaylist -> emptyList()
        collectionResult != null -> emptyList()
        else -> SoundiizTextParser.parseBlock(pastedText)
    }
    val detectedFormat = collectionResult?.let { cr ->
        "${cr.platform} · ${cr.tracks.size} tracks"
    } ?: SoundiizTextParser.detectFormat(pastedText)
    val warningCount = rows.count { it.preserved }

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
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back", color = AppAccent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Preview Import", color = AppText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(detectedFormat, color = AppTextSecondary, fontSize = 13.sp)
            }
        }

        if (isEclipsePlaylist) {
            EclipsePlaylistPreview(pastedText = pastedText, onImport = onContinueToMatch)
        } else if (isCollectionUrl) {
            CollectionPreview(
                result = collectionResult,
                isResolving = resolvingCollection,
                onImport = onContinueToMatch
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewMetric("Rows", rows.size.toString(), Modifier.weight(1f))
                PreviewMetric("Warnings", warningCount.toString(), Modifier.weight(1f))
            }

            VantaCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rows.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("Paste a song list to preview parsed rows before matching.", color = AppTextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        rows.forEachIndexed { index, row ->
                            PreviewRow(row)
                            if (index < rows.size - 1) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp).height(0.5.dp)
                                        .background(AppOutline.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onContinueToMatch,
                enabled = rows.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Continue to Match")
            }
        }
    }
}

@Composable
private fun PreviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.glassSurface(shape = RoundedCornerShape(18.dp)).padding(14.dp)) {
        Column {
            Text(label, color = AppTextSecondary, fontSize = 12.sp)
            Text(value, color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewRow(row: ParsedPlaylistLine) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.preserved) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AppWarning, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(row.rawLine, color = AppText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
        }
        if (row.preserved) {
            Text("Could not parse this line — it will be preserved for review.", color = AppWarning, fontSize = 11.sp, modifier = Modifier.padding(start = 22.dp))
        } else {
            Text(
                listOfNotNull(row.title, row.artist, row.album).joinToString(" · "),
                color = AppTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp)
            )
            if (!row.sourcePlatform.isNullOrBlank() || !row.sourceUrl.isNullOrBlank()) {
                Text(
                    listOfNotNull(row.sourcePlatform, row.sourceUrl?.take(64)).joinToString(" · "),
                    color = AppAccent,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EclipsePlaylistPreview(pastedText: String, onImport: () -> Unit) {
    val url = pastedText.trim()
    VantaCard {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(48.dp)
            )
            Text("Playlist Link Detected", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "VANTA will fetch all tracks from the shared playlist, search your library and online sources, and add every playable match to your collection.",
                color = AppTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().glassSurface(shape = RoundedCornerShape(12.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = AppTextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    url.take(80),
                    color = AppTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import Playlist")
            }
        }
    }
}

@Composable
private fun CollectionPreview(
    result: CollectionResult?,
    isResolving: Boolean,
    onImport: () -> Unit
) {
    VantaCard {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isResolving) {
                CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(36.dp))
                Text("Resolving playlist...", color = AppTextSecondary, fontSize = 14.sp)
            } else if (result != null) {
                Icon(
                    Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    tint = AppAccent,
                    modifier = Modifier.size(48.dp)
                )
                Text("${result.platform} Playlist", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    result.collectionTitle,
                    color = AppTextSecondary,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!result.collectionArtist.isNullOrBlank()) {
                    Text(
                        result.collectionArtist,
                        color = AppAccent,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AppAccent.copy(alpha = 0.2f)))
                Text(
                    "${result.tracks.size} tracks will be imported and matched against your library.",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Playlist")
                }
            } else {
                Text("Could not resolve this playlist URL.", color = AppWarning, fontSize = 14.sp)
            }
        }
    }
}
