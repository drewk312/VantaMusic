package com.audiophile.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SleepTimerSheet(
    activeMinutes: Int?,
    onSelectTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 15, 30, 45, 60)

    VantaBottomSheet(visible = true, onDismiss = onDismiss) {
        Text(
            "Sleep Timer",
            color = AppText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (activeMinutes != null) "Timer set for ${activeMinutes} minutes"
            else "Stop playback after a set time",
            color = AppTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(20.dp))
        VantaSheetDivider()

        if (activeMinutes != null) {
            VantaSheetAction(
                icon = Icons.Filled.Cancel,
                label = "Cancel Sleep Timer",
                tint = AppError,
                onClick = onCancelTimer
            )
            VantaSheetDivider()
        }

        options.forEach { minutes ->
            VantaSheetAction(
                icon = if (minutes == activeMinutes) Icons.Filled.CheckCircle else Icons.Filled.Timer,
                label = when (minutes) {
                    5 -> "5 minutes"
                    15 -> "15 minutes"
                    30 -> "30 minutes"
                    45 -> "45 minutes"
                    60 -> "1 hour"
                    else -> "${minutes} minutes"
                },
                subtitle = if (minutes == activeMinutes) "Active" else null,
                tint = if (minutes == activeMinutes) AppAccent else AppText,
                onClick = { onSelectTimer(minutes) }
            )
        }
    }
}
