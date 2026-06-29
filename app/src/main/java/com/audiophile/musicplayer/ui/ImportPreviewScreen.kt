package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.importer.ParsedPlaylistLine
import com.audiophile.musicplayer.data.importer.SoundiizTextParser

@Composable
fun ImportPreviewScreen(
    pastedText: String,
    onBack: () -> Unit,
    onContinueToMatch: () -> Unit
) {
    val rows = SoundiizTextParser.parseBlock(pastedText)
    val detectedFormat = SoundiizTextParser.detectFormat(pastedText)
    val warningCount = rows.count { it.preserved }

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
                Text("Preview Import", color = AppText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("$detectedFormat · ${rows.size} parsed row(s)", color = AppTextSecondary, fontSize = 14.sp)
            }
            TextButton(onClick = onBack) { Text("Back") }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewMetric("Rows", rows.size.toString(), Modifier.weight(1f))
            PreviewMetric("Warnings", warningCount.toString(), Modifier.weight(1f))
        }

        Box(modifier = Modifier.fillMaxWidth().glassSurface().padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rows.isEmpty()) {
                    Text("Paste a song list to preview parsed rows before matching.", color = AppTextSecondary, fontSize = 14.sp)
                } else {
                    rows.forEachIndexed { index, row ->
                        PreviewRow(row)
                    }
                }
            }
        }

        Button(
            onClick = onContinueToMatch,
            enabled = rows.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue to Match")
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(row.rawLine, color = AppText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (row.preserved) {
            Text("Warning: could not parse this line. It will be preserved for review.", color = AppWarning, fontSize = 12.sp)
        } else {
            Text(
                listOfNotNull(row.title, row.artist, row.album).joinToString(" · "),
                color = AppTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!row.sourcePlatform.isNullOrBlank() || !row.sourceUrl.isNullOrBlank()) {
                Text(
                    listOfNotNull(row.sourcePlatform, row.sourceUrl?.take(64)).joinToString(" · "),
                    color = AppAccent,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
