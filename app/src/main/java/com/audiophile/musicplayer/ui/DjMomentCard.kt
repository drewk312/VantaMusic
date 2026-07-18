package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
/** Compact DJ moment — text-first, woven above the mini player. */
@Composable
fun DjMomentCard(
    message: String,
    modifier: Modifier = Modifier,
    accentLabel: String = "VANTA DJ",
    onOpenDj: (() -> Unit)? = null,
    onCycleMode: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E).copy(alpha = 0.92f))
            .then(if (onOpenDj != null) Modifier.clickable(onClick = onOpenDj) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppAccent.copy(alpha = 0.15f),
                            AppAccent.copy(alpha = 0.55f),
                            AppAccent.copy(alpha = 0.15f)
                        )
                    )
                )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = accentLabel,
                color = AppTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
        if (onCycleMode != null) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Change DJ mode",
                tint = AppAccent,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onCycleMode)
            )
        }
        if (onDismiss != null) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = AppTextMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}
