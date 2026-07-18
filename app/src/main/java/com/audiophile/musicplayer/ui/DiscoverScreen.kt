package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiscoverScreen(
    onOpenSearch: () -> Unit,
    onOpenImports: () -> Unit,
    onOpenMoodMix: (String) -> Unit = {},
    onOpenPulse: () -> Unit = {}
) {
    val moodMixes = listOf(
        "Chill" to "Relax and unwind",
        "Focus" to "Stay in the zone",
        "Energy" to "Power through",
        "Late Night" to "After hours",
        "Happy" to "Feel good tunes",
        "Deep Focus" to "Deep concentration"
    )
    val moodColors = listOf(
        Color(0xFF2E4A6A), Color(0xFF3D2E6A), Color(0xFF6A2E2E),
        Color(0xFF1A1A3A), Color(0xFF6A5A2E), Color(0xFF2E4A3A)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = false)),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text("Discover", style = VantaType.pageTitle,
            modifier = Modifier.padding(horizontal = 24.dp))

        // Search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppSurface)
                .border(0.5.dp, AppOutline, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = AppTextSecondary, modifier = Modifier.size(24.dp))
            Text("Search for music", color = AppTextSecondary, fontSize = 16.sp)
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            VantaSectionHeader("Mood Mixes")
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(moodMixes.size) { index ->
                    val (mood, desc) = moodMixes[index]
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(moodColors[index % moodColors.size])
                            .border(0.5.dp, AppOutline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .clickable { onOpenMoodMix(mood) }
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                            Text(mood, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Column {
                                Text(desc, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
