package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AudioQualityPreferenceDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val current = when (selected.trim().lowercase()) {
        "atmos", "dolby_atmos", "360" -> "atmos"
        "auto", "iamf" -> "auto"
        "24" -> "24"
        "16" -> "16"
        else -> "auto"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppBackgroundTop,
        title = { Text("Streaming Resolution Preference", color = AppText, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Select your preferred audio master format. When spatial audio is preferred, Dolby Atmos and Sony 360 get #1 priority.",
                    color = AppTextSecondary,
                    lineHeight = 18.sp,
                    fontSize = 13.sp
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("atmos", "Dolby Atmos & Sony 360", "#1 Priority · Dolby Atmos & Sony 360 Reality Audio bitstream first, 24-bit FLAC fallback."),
                        Triple("24", "24-bit Hi-Res Studio FLAC", "Max stereo fidelity · 24-bit / 96–192 kHz lossless FLAC. Skips spatial mixes."),
                        Triple("auto", "Smart Spatial + Lossless", "Balanced · Tries Dolby Atmos & Sony 360, then studio master 24-bit FLAC."),
                        Triple("16", "16-bit Lossless CD Quality", "Standard 16-bit / 44.1 kHz FLAC · Lower mobile data bandwidth.")
                    ).forEach { (value, title, description) ->
                        val isSelected = current == value
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) AppAccent.copy(alpha = .14f) else AppSurfaceRaised)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = AppAccent, unselectedColor = AppTextMuted)
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(title, color = if (isSelected) AppAccent else AppText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(description, color = AppTextSecondary, fontSize = 11.sp, lineHeight = 15.sp,
                                    modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = AppAccent) } }
    )
}
