package com.audiophile.musicplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class RadioMode { FAMILIAR, DISCOVER, DEEP_CUTS }

@Composable
fun RadioScreen(
    onStartStation: (String, String) -> Unit = { _, _ -> },
    onOpenAiDj: () -> Unit = {}
) {
    var radioMode by remember { mutableStateOf(RadioMode.FAMILIAR) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = false)),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Text(
            "Radio",
            style = VantaType.pageTitle,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Hero: AI Radio
        RadioHeroCard(onOpenAiDj = onOpenAiDj)

        // Mode selector
        RadioModeSelector(
            currentMode = radioMode,
            onModeSelected = { radioMode = it }
        )

        // Song Radio
        VantaSectionHeader("Song Radio", modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        RadioMoodRow(
            items = listOf(
                "Start from a Song" to Icons.Filled.MusicNote,
                "Start from an Artist" to Icons.Filled.Person,
                "Start from a Genre" to Icons.Filled.Category
            ),
            onItemClick = { label, _ -> onStartStation("song", label) },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Mood Radio
        VantaSectionHeader("Mood Radio", modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        RadioChipGrid(
            items = listOf("Chill Vibes", "Late Night", "Workout", "Happy", "Focus", "Deep Cuts"),
            onItemClick = { onStartStation("mood", it) },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Era Radio
        VantaSectionHeader("Era Radio", modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        EraSelector(
            onEraSelected = { onStartStation("era", it) },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        // Activity Radio
        VantaSectionHeader("Activity Radio", modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        ActivityStrip(
            onActivitySelected = { onStartStation("activity", it) },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RadioHeroCard(onOpenAiDj: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1A3A), Color(0xFF0D0D2E), Color(0xFF000000))
                )
            )
            .border(0.5.dp, AppAccent.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenAiDj)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(64.dp)
                .clip(CircleShape)
                .background(AppAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Radio,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(32.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "AI Radio",
                color = AppAccent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Endless, personalized radio that learns your taste",
                color = AppTextSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppAccent)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Listen Now", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RadioModeSelector(currentMode: RadioMode, onModeSelected: (RadioMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface.copy(alpha = 0.3f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RadioMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val bg by animateColorAsState(
                targetValue = if (selected) AppAccent.copy(alpha = 0.2f) else Color.Transparent,
                animationSpec = tween(300),
                label = "modeBg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (mode) {
                        RadioMode.FAMILIAR -> "Familiar"
                        RadioMode.DISCOVER -> "Discover"
                        RadioMode.DEEP_CUTS -> "Deep Cuts"
                    },
                    color = if (selected) AppAccent else AppTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RadioMoodRow(
    items: List<Pair<String, ImageVector>>,
    onItemClick: (String, ImageVector) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.size) { index ->
            val (label, icon) = items[index]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface.copy(alpha = 0.3f))
                    .border(0.5.dp, AppAccent.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .clickable { onItemClick(label, icon) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = AppAccent, modifier = Modifier.size(20.dp))
                    Text(label, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RadioChipGrid(
    items: List<String>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppAccent.copy(alpha = 0.08f))
                            .border(0.5.dp, AppAccent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .clickable { onItemClick(label) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = AppAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (row.size < 3) {
                    Spacer(Modifier.weight(3f - row.size))
                }
            }
        }
    }
}

@Composable
private fun EraSelector(onEraSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val eras = listOf("60s", "70s", "80s", "90s", "00s", "10s", "2020s")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        eras.forEach { era ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppAccent.copy(alpha = 0.06f))
                    .clickable { onEraSelected(era) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(era, color = AppTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ActivityStrip(onActivitySelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val activities = listOf(
        "Workout" to Icons.Filled.FitnessCenter,
        "Focus" to Icons.Filled.Psychology,
        "Sleep" to Icons.Filled.NightlightRound,
        "Party" to Icons.Filled.Celebration,
        "Commute" to Icons.Filled.DirectionsCar
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(activities.size) { index ->
            val (label, icon) = activities[index]
            Column(
                modifier = Modifier
                    .width(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface.copy(alpha = 0.3f))
                    .clickable { onActivitySelected(label) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = AppAccent, modifier = Modifier.size(24.dp))
                Text(label, color = AppText, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
