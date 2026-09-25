package com.audiophile.musicplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.radio.RadioDiscoveryMode

@Composable
fun StationTuningSelector(
    selectedMode: RadioDiscoveryMode,
    onModeSelected: (RadioDiscoveryMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0D14))
            .border(1.dp, Color(0xFF221A0F), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioDiscoveryMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            val scale by animateFloatAsState(
                targetValue = if (isFocused) 1.05f else 1.0f,
                animationSpec = spring(stiffness = 500f),
                label = "scale"
            )

            val backgroundColor by animateColorAsState(
                targetValue = when {
                    isFocused -> Color(0xFF2E2416)
                    isSelected -> Color(0xFF1F1709)
                    else -> Color.Transparent
                },
                label = "bg"
            )

            val borderColor by animateColorAsState(
                targetValue = when {
                    isFocused -> Color(0xFF58A6FF)
                    isSelected -> Color(0xFFFFDF73)
                    else -> Color.Transparent
                },
                label = "border"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(10.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .focusable(interactionSource = interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        onModeSelected(mode)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = mode.icon,
                        color = if (isSelected || isFocused) Color(0xFFFFDF73) else Color(0xFF6B7280),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = mode.label,
                        color = when {
                            isSelected -> Color(0xFFFFDF73)
                            isFocused -> Color.White
                            else -> Color(0xFF9CA3AF)
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
