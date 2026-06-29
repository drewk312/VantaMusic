package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.userFacingLabel

// ============================================================
// VANTA Section Header
// ============================================================
@Composable
fun VantaSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = VantaType.sectionTitle
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AppAccent,
                            AppAccent.copy(alpha = 0.15f)
                        )
                    )
                )
        )
    }
}

// ============================================================
// VANTA Chip — pill-style chip for filters/tags
// ============================================================
@Composable
fun VantaChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            AppAccent.copy(alpha = 0.24f),
                            AppAccent.copy(alpha = 0.14f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(AppSurface, AppSurfaceRaised)
                    )
                }
            )
            .border(
                0.5.dp,
                if (selected) AppAccent.copy(alpha = 0.45f) else AppOutline,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = if (selected) AppAccent else AppTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

// ============================================================
// VANTA Status Badge — compact status indicator
// ============================================================
@Composable
fun VantaStatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ============================================================
// VANTA Explicit Badge — "E" badge for explicit content
// ============================================================
@Composable
fun VantaExplicitBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AppTextSecondary.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = "E",
            color = AppTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = "Explicit" }
        )
    }
}

// ============================================================
// VANTA Quality Badge — audio quality indicator
// ============================================================
@Composable
fun VantaCompactQualityChip(
    qualityInfo: VantaQualityInfo?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val label = qualityInfo?.bestQualityLabel()?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val lower = label.lowercase()
    val bitrate = Regex("""\d+\s*kbps""", RegexOption.IGNORE_CASE).find(label)?.value
    val rate = Regex("""\d+(?:\.\d+)?\s*kHz""", RegexOption.IGNORE_CASE).find(label)?.value
    val primary = when {
        "hi-res" in lower || "hi res" in lower -> "Hi-Res"
        "flac" in lower -> "FLAC"
        "lossless" in lower || "cd quality" in lower -> "Lossless"
        else -> label.substringBefore(" \u00B7 ").take(12)
    }
    val secondary = when {
        "hi-res" in lower || "hi res" in lower -> rate ?: bitrate
        primary.equals("FLAC", ignoreCase = true) -> bitrate
        else -> null
    }
    val chipText = if (secondary != null) "$primary \u2022 $secondary" else primary

    Box(
        modifier = modifier
            .semantics { contentDescription = "Audio quality: ${label.replace(" \u00B7 ", " ")}" }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = chipText,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VantaQualityBadge(
    label: String?,
    modifier: Modifier = Modifier
) {
    val cleanLabel = label?.trim()?.takeIf { it.isNotEmpty() } ?: return
    Box(
        modifier = modifier
            .semantics {
                contentDescription = "Audio quality: ${cleanLabel.replace(" \u00B7 ", " ")}"
            }
            .clip(RoundedCornerShape(6.dp))
            .background(AppAccent.copy(alpha = 0.11f))
            .border(0.5.dp, AppAccent.copy(alpha = 0.24f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = cleanLabel,
            color = AppAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================
// VANTA Card — solid surface card
// ============================================================
@Composable
fun VantaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .luxuryCard(RoundedCornerShape(VantaRadius.card))
            .then(clickable)
            .padding(18.dp),
        content = content
    )
}

// ============================================================
// VANTA Song Row — standard track list item
// ============================================================
@Composable
fun VantaSongRow(
    displayTitle: String,
    displayArtist: String,
    artworkUrl: String?,
    badgeText: String? = null,
    badgeColor: Color = AppAccent,
    onClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VantaRadius.card))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(VantaRadius.artwork))
                .border(0.5.dp, AppAccent.copy(alpha = 0.14f), RoundedCornerShape(VantaRadius.artwork))
        ) {
            NetworkArtwork(
                artworkUrl = artworkUrl,
                seed = displayTitle,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = VantaType.songTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = displayArtist,
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier
                )
                if (badgeText != null) {
                    VantaStatusBadge(text = badgeText, color = badgeColor)
                }
            }
        }
    }
}

// ============================================================
// VANTA Empty State
// ============================================================
@Composable
fun VantaEmptyState(
    title: String,
    description: String,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VantaSpacing.screenHorizontal, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppOutline, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppTextMuted,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Text(
            text = title,
            color = AppText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            color = AppTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(VantaRadius.button))
                    .background(AppAccentSoft)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    actionLabel,
                    color = AppAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
