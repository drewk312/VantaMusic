package com.audiophile.desktop

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

// ─────────────────────────────────────────────────────────────────────────────
//  DspPanelSheet — Glassmorphic DSP control panel.
//
//  Slides up from the bottom of the screen as an AnimatedVisibility overlay.
//  Reads/writes DspState directly — no extra state hoisting needed.
// ─────────────────────────────────────────────────────────────────────────────

private val DspAccent     = Color(0xFF66D9EF)
private val DspAccentGlow = Color(0x3366D9EF)
private val GlassCard     = Color(0xFF121A24)
private val GlassDeep     = Color(0xFF0C1219)
private val ChipActive    = Color(0xFF193345)
private val ChipInactive  = Color(0xFF141D28)

@Composable
fun DspPanelSheet() {
    AnimatedVisibility(
        visible = DspState.panelOpen,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300, easing = EaseInCubic)
        ) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {

            // Scrim — tap outside to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { DspState.panelOpen = false }
            )

            // ── Panel Card ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .wrapContentHeight()
                    .shadow(20.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF131B25), Color(0xFF0E141C))
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .clickable(enabled = false) {}  // block pass-through clicks
            ) {

                // ── Handle + Title ────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "DSP Effects",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 24.sp,
                            color      = Color.White
                        )
                        Text(
                            "Spatial · Reverb · Crossfeed · HRTF",
                            fontSize = 12.sp,
                            color    = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    // Global active indicator dot
                    val anyActive = DspState.widenerEnabled || DspState.reverbEnabled ||
                            DspState.crossfeedEnabled || DspState.hrtfEnabled
                    val dotScale by animateFloatAsState(if (anyActive) 1f else 0.6f, spring())
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                            .clip(CircleShape)
                            .background(if (anyActive) DspAccent else Color.White.copy(alpha = 0.2f))
                    )
                }

                Spacer(Modifier.height(24.dp))

                // ── Preset Row ────────────────────────────────────────────
                Text("Presets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DspPresets.forEach { preset ->
                        PresetChip(preset)
                    }
                }

                Spacer(Modifier.height(28.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(24.dp))

                // ── Effect Sections ────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1 — Stereo Widener
                    EffectSection(
                        title      = "Stereo Widener",
                        subtitle   = "Mid/Side stereo expansion",
                        iconVector = Icons.Filled.Favorite,
                        enabled    = DspState.widenerEnabled,
                        onToggle   = { DspState.widenerEnabled = it; DspState.activePreset = "Custom" }
                    ) {
                        DspSlider(
                            label    = "Width",
                            value    = DspState.widthFactor,
                            range    = 0.5f..2.0f,
                            format   = { "%.1fx".format(it) },
                            onValue  = { DspState.widthFactor = it; DspState.activePreset = "Custom" }
                        )
                        Spacer(Modifier.height(4.dp))
                        WidthVisualizer(DspState.widthFactor)
                    }

                    // 2 — Concert Hall Reverb
                    EffectSection(
                        title      = "Concert Hall Reverb",
                        subtitle   = "Freeverb room simulation",
                        iconVector = Icons.Filled.Build,
                        enabled    = DspState.reverbEnabled,
                        onToggle   = { DspState.reverbEnabled = it; DspState.activePreset = "Custom" }
                    ) {
                        DspSlider("Room Size", DspState.reverbRoomSize, 0f..1f,
                            format = { val s = when { it < 0.3f -> "Small" ; it < 0.6f -> "Medium" ; else -> "Large" }; s },
                            onValue = { DspState.reverbRoomSize = it; DspState.activePreset = "Custom" }
                        )
                        DspSlider("Damping",   DspState.reverbDamping,  0f..1f,
                            format = { val s = when { it < 0.35f -> "Bright" ; it < 0.65f -> "Neutral" ; else -> "Warm" }; s },
                            onValue = { DspState.reverbDamping = it; DspState.activePreset = "Custom" }
                        )
                        DspSlider("Wet / Dry", DspState.reverbWetDry,   0f..1f,
                            format = { "${(it * 100).toInt()}%" },
                            onValue = { DspState.reverbWetDry = it; DspState.activePreset = "Custom" }
                        )
                    }

                    // 3 — Crossfeed
                    EffectSection(
                        title      = "Headphone Crossfeed",
                        subtitle   = "Bauer BS2B headphone surround",
                        iconVector = Icons.Filled.Notifications,
                        enabled    = DspState.crossfeedEnabled,
                        onToggle   = { DspState.crossfeedEnabled = it; DspState.activePreset = "Custom" }
                    ) {
                        DspSlider("Feed Level", DspState.crossfeedLevel, 0f..1f,
                            format = { "${(it * 100).toInt()}%" },
                            onValue = { DspState.crossfeedLevel = it; DspState.activePreset = "Custom" }
                        )
                        DspSlider("Head Delay", DspState.crossfeedDelayMs, 0.1f..2.0f,
                            format = { "%.1f ms".format(it) },
                            onValue = { DspState.crossfeedDelayMs = it; DspState.activePreset = "Custom" }
                        )
                    }

                    // 4 — HRTF
                    EffectSection(
                        title      = "HRTF Elevation",
                        subtitle   = "Head-related transfer function",
                        iconVector = Icons.Filled.AccountCircle,
                        enabled    = DspState.hrtfEnabled,
                        onToggle   = { DspState.hrtfEnabled = it; DspState.activePreset = "Custom" }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HrtfPreset.entries.forEachIndexed { idx, preset ->
                                val selected = DspState.hrtfPresetIndex == idx
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            DspState.hrtfPresetIndex = idx
                                            DspState.activePreset = "Custom"
                                        },
                                    color = if (selected) ChipActive else ChipInactive
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            preset.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // ── Reset All ─────────────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).clickable {
                        DspState.applyPreset(DspPresets.first()) // "Flat"
                    },
                    color = GlassDeep
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Reset All Effects", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PresetChip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetChip(preset: DspPreset) {
    val active = DspState.activePreset == preset.label
    val scale by animateFloatAsState(if (active) 1.05f else 1f, spring(stiffness = Spring.StiffnessMedium))

    Surface(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(34.dp)
            .clip(CircleShape)
            .clickable { DspState.applyPreset(preset) },
        color = if (active) DspAccent else ChipInactive
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                preset.label,
                fontSize   = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color      = Color.White
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EffectSection — expandable card per effect
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EffectSection(
    title: String,
    subtitle: String,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val glowAlpha by animateFloatAsState(if (enabled) 1f else 0f, tween(400))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.04f + glowAlpha * 0.06f),
                        DspAccentGlow.copy(alpha = glowAlpha * 0.12f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f + glowAlpha * 0.25f),
                        DspAccent.copy(alpha = glowAlpha * 0.4f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    iconVector, title,
                    tint     = if (enabled) DspAccent else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                }
            }
            Switch(
                checked  = enabled,
                onCheckedChange = onToggle,
                colors   = SwitchDefaults.colors(
                    checkedThumbColor  = Color.White,
                    checkedTrackColor  = DspAccent,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
        }

        AnimatedVisibility(
            visible = enabled,
            enter   = expandVertically(spring()) + fadeIn(tween(200)),
            exit    = shrinkVertically(tween(200)) + fadeOut(tween(150))
        ) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DspSlider — labelled slider row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DspSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValue: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Text(format(value), fontSize = 12.sp, color = DspAccent, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value          = value,
            onValueChange  = onValue,
            valueRange     = range,
            modifier       = Modifier.fillMaxWidth(),
            colors         = SliderDefaults.colors(
                thumbColor         = Color.White,
                activeTrackColor   = DspAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  WidthVisualizer — simple stereo-field indicator that animates with widthFactor
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WidthVisualizer(widthFactor: Float) {
    val normalized = ((widthFactor - 0.5f) / 1.5f).coerceIn(0f, 1f)   // map 0.5–2.0 → 0–1
    val barWidth by animateFloatAsState(normalized, spring(stiffness = Spring.StiffnessLow))

    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Left wing
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barWidth)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, DspAccent.copy(alpha = 0.8f))
                        )
                    )
            )
        }
        // Centre dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(DspAccent)
        )
        // Right wing
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barWidth)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(DspAccent.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  EaseInCubic easing (not in current Compose stdlib by name)
// ─────────────────────────────────────────────────────────────────────────────
private val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
