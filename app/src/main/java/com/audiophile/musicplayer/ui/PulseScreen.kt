package com.audiophile.musicplayer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PulseScreen(
    onPlayTrack: (String, String) -> Unit = { _, _ -> },
    onOpenMoodMix: (String) -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = false)),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Text(
            "Radio",
            style = VantaType.pageTitle,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .shadow(28.dp, CircleShape, spotColor = AppAccentSecondary.copy(alpha = glowAlpha * 0.7f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AppAccent, AppAccent.copy(alpha = 0.3f), AppAccent.copy(alpha = 0.05f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Radio,
                    contentDescription = "VANTA Radio",
                    tint = AppBackgroundBottom,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Text(
            "VANTA Radio",
            color = AppText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )
        Text(
            "AI-powered radio that learns your taste.",
            color = AppTextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Tune into a mood", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            val chips = listOf("Chill Vibes", "Late Night", "Workout", "Happy", "Focus", "Deep Cuts")
            val rows = chips.chunked(3)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(AppAccent.copy(alpha = 0.12f))
                                .border(0.5.dp, AppAccent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                .clickable { onOpenMoodMix(label) }
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
                Spacer(Modifier.height(8.dp))
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            VantaSectionHeader("Start a Mood Mix")
            Spacer(Modifier.height(12.dp))
            val moods = listOf("Chill", "Focus", "Energy", "Late Night", "Happy", "Deep Focus")
            val rows = moods.chunked(2)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { mood ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .vantaCard()
                                .clickable { onOpenMoodMix(mood) }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(mood, color = AppAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (row.size < 2) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
