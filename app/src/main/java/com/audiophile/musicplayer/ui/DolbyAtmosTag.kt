package com.audiophile.musicplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.VantaQualityInfo

enum class SpatialTagSize { Compact, Standard, Hero }

/** Violet — Dolby Atmos identity (not the trademark logo). */
val DolbyAtmosAccent = Color(0xFFB8A7FF)
private val DolbyAtmosSurface = Color(0xFF0B0A0E)
private val DolbyAtmosBorder = Color(0xFF6E63A8)

/** Cyan — Sony 360 Reality Audio identity (not Sony’s trademark logo). */
val Sony360Accent = Color(0xFF5EEAD4)
private val Sony360Surface = Color(0xFF061016)
private val Sony360Border = Color(0xFF2A7A72)

enum class SpatialIdentityKind { DOLBY_ATMOS, SONY_360 }

fun spatialIdentityKind(info: VantaQualityInfo): SpatialIdentityKind? = when {
    info.isDolbyAtmos -> SpatialIdentityKind.DOLBY_ATMOS
    info.isSony360RealityAudio -> SpatialIdentityKind.SONY_360
    isDolbyAtmosLabel(info.bestQualityLabel()) -> SpatialIdentityKind.DOLBY_ATMOS
    isSony360Label(info.bestQualityLabel()) || isSony360Label(info.format) -> SpatialIdentityKind.SONY_360
    else -> null
}

fun isDolbyAtmosLabel(label: String?): Boolean {
    val n = label?.lowercase().orEmpty()
    if (n.isBlank()) return false
    // Prefer word-ish Atmos hits; avoid matching unrelated substrings.
    return Regex("""(?<![a-z0-9])(?:dolby\s+)?atmos(?![a-z0-9])""").containsMatchIn(n)
}

fun isSony360Label(label: String?): Boolean {
    val n = label?.lowercase()?.replace('_', ' ')?.replace('-', ' ')?.trim().orEmpty()
    if (n.isBlank()) return false
    val compact = n.replace(" ", "")
    return compact == "360ra" ||
        compact == "360" ||
        "360ra" in compact ||
        "360reality" in compact ||
        "sony360" in compact ||
        "mpegh" in compact ||
        "mpeg-h" in n ||
        ("360" in n && ("reality" in n || "sony" in n || Regex("""\b360\s*ra\b""").containsMatchIn(n)))
}

/** Compact title-row mark for Atmos / 360 — quiet enough for search lists. */
@Composable
fun TitleSpatialIndicator(
    qualityInfo: VantaQualityInfo?,
    modifier: Modifier = Modifier,
    atmosMixAvailable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val kind = qualityInfo?.let { spatialIdentityKind(it) }
    when (kind) {
        SpatialIdentityKind.DOLBY_ATMOS -> DolbyAtmosTag(
            modifier = modifier,
            size = SpatialTagSize.Compact,
            verified = qualityInfo.spatialEvidence.equals("verified", ignoreCase = true) ||
                qualityInfo.spatialEvidence.isNullOrBlank(),
            onClick = onClick
        )
        SpatialIdentityKind.SONY_360 -> Sony360Tag(
            modifier = modifier,
            size = SpatialTagSize.Compact,
            verified = qualityInfo.spatialEvidence.equals("verified", ignoreCase = true) ||
                qualityInfo.spatialEvidence.isNullOrBlank(),
            onClick = onClick
        )
        null -> if (atmosMixAvailable) {
            DolbyAtmosTag(
                modifier = modifier,
                size = SpatialTagSize.Compact,
                verified = false,
                onClick = onClick
            )
        }
    }
}

/** Picks Atmos or 360 Reality Audio tag from quality truth. */
@Composable
fun SpatialIdentityTag(
    qualityInfo: VantaQualityInfo,
    modifier: Modifier = Modifier,
    size: SpatialTagSize = SpatialTagSize.Compact,
    onClick: (() -> Unit)? = null
) {
    when (spatialIdentityKind(qualityInfo)) {
        SpatialIdentityKind.DOLBY_ATMOS -> DolbyAtmosTag(
            modifier = modifier,
            size = size,
            verified = qualityInfo.spatialEvidence.equals("verified", ignoreCase = true) ||
                qualityInfo.spatialEvidence.isNullOrBlank(),
            onClick = onClick
        )
        SpatialIdentityKind.SONY_360 -> Sony360Tag(
            modifier = modifier,
            size = size,
            verified = qualityInfo.spatialEvidence.equals("verified", ignoreCase = true) ||
                qualityInfo.spatialEvidence.isNullOrBlank(),
            onClick = onClick
        )
        null -> Unit
    }
}

@Composable
fun DolbyAtmosTag(
    modifier: Modifier = Modifier,
    size: SpatialTagSize = SpatialTagSize.Compact,
    verified: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    SpatialBrandTag(
        modifier = modifier,
        size = size,
        brand = "DOLBY",
        title = if (verified) "ATMOS" else "ATMOS · CATALOG",
        contentDescription = if (verified) "Dolby Atmos" else "Atmos available",
        accent = DolbyAtmosAccent,
        surfaceStart = DolbyAtmosSurface,
        surfaceEnd = Color(0xFF161225),
        border = DolbyAtmosBorder,
        quiet = size == SpatialTagSize.Compact || size == SpatialTagSize.Standard,
        onClick = onClick
    ) { markModifier ->
        AtmosIdentityMark(modifier = markModifier, color = DolbyAtmosAccent)
    }
}

@Composable
fun Sony360Tag(
    modifier: Modifier = Modifier,
    size: SpatialTagSize = SpatialTagSize.Compact,
    verified: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    SpatialBrandTag(
        modifier = modifier,
        size = size,
        brand = "SONY",
        title = if (verified) "360 RA" else "360 · CATALOG",
        contentDescription = if (verified) "Sony 360 Reality Audio" else "360 Reality Audio available",
        accent = Sony360Accent,
        surfaceStart = Sony360Surface,
        surfaceEnd = Color(0xFF0A1C22),
        border = Sony360Border,
        quiet = size == SpatialTagSize.Compact || size == SpatialTagSize.Standard,
        onClick = onClick
    ) { markModifier ->
        Sony360IdentityMark(modifier = markModifier, color = Sony360Accent)
    }
}

/** @deprecated Use [SpatialTagSize]. */
typealias DolbyAtmosTagSize = SpatialTagSize

@Composable
private fun SpatialBrandTag(
    modifier: Modifier,
    size: SpatialTagSize,
    brand: String,
    title: String,
    contentDescription: String,
    accent: Color,
    surfaceStart: Color,
    surfaceEnd: Color,
    border: Color,
    quiet: Boolean,
    onClick: (() -> Unit)?,
    mark: @Composable (Modifier) -> Unit
) {
    val shape = RoundedCornerShape(if (size == SpatialTagSize.Hero) 10.dp else 6.dp)
    val horizontal = when (size) {
        SpatialTagSize.Compact -> 6.dp
        SpatialTagSize.Standard -> 7.dp
        SpatialTagSize.Hero -> 12.dp
    }
    val vertical = when (size) {
        SpatialTagSize.Compact -> 2.dp
        SpatialTagSize.Standard -> 3.dp
        SpatialTagSize.Hero -> 7.dp
    }
    val markSize = when (size) {
        SpatialTagSize.Compact -> 9.dp
        SpatialTagSize.Standard -> 10.dp
        SpatialTagSize.Hero -> 17.dp
    }
    val titleSize = when (size) {
        SpatialTagSize.Compact -> 9.sp
        SpatialTagSize.Standard -> 10.sp
        SpatialTagSize.Hero -> 13.sp
    }
    Row(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (quiet) 18.dp else if (size == SpatialTagSize.Compact) 22.dp else 26.dp)
            .widthIn(max = if (size == SpatialTagSize.Hero) 220.dp else 120.dp)
            .clip(shape)
            .then(
                if (quiet) Modifier.background(Color.White.copy(alpha = 0.06f))
                else Modifier.background(Brush.horizontalGradient(listOf(surfaceStart, surfaceEnd)))
            )
            .border(0.5.dp, if (quiet) accent.copy(alpha = 0.35f) else border.copy(alpha = 0.9f), shape)
            .padding(horizontal = horizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        mark(Modifier.size(markSize))
        if (quiet) {
            Text(
                text = title,
                color = accent.copy(alpha = 0.92f),
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
                Text(
                    text = brand,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    maxLines = 1
                )
                Text(
                    text = title,
                    color = accent,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Abstract spatial mark — concentric arcs, not a Dolby trademark glyph. */
@Composable
fun AtmosIdentityMark(
    modifier: Modifier = Modifier,
    color: Color = DolbyAtmosAccent
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.11f)
        val pad = size.minDimension * 0.08f
        drawRoundRect(
            color = color.copy(alpha = 0.18f),
            topLeft = Offset(pad, pad),
            size = Size(this.size.width - pad * 2f, this.size.height - pad * 2f),
            cornerRadius = CornerRadius(this.size.minDimension * 0.22f)
        )
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r2 = this.size.minDimension * 0.32f
        drawCircle(color = color, radius = this.size.minDimension * 0.08f, center = Offset(cx, cy))
        drawArc(
            color = color.copy(alpha = 0.95f),
            startAngle = -70f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r2, cy - r2),
            size = Size(r2 * 2f, r2 * 2f),
            style = stroke
        )
        drawArc(
            color = color.copy(alpha = 0.55f),
            startAngle = 110f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - r2, cy - r2),
            size = Size(r2 * 2f, r2 * 2f),
            style = stroke
        )
    }
}

/** Abstract 360° sphere mark — not Sony’s trademark glyph. */
@Composable
fun Sony360IdentityMark(
    modifier: Modifier = Modifier,
    color: Color = Sony360Accent
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.1f)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension * 0.36f
        drawCircle(color = color.copy(alpha = 0.16f), radius = r * 1.15f, center = Offset(cx, cy))
        drawCircle(color = color, radius = r, center = Offset(cx, cy), style = stroke)
        // Longitude / latitude suggestion of a sphere
        drawOval(
            color = color.copy(alpha = 0.85f),
            topLeft = Offset(cx - r * 0.38f, cy - r),
            size = Size(r * 0.76f, r * 2f),
            style = stroke
        )
        val equator = Path().apply {
            moveTo(cx - r, cy)
            quadraticBezierTo(cx, cy + r * 0.22f, cx + r, cy)
        }
        drawPath(equator, color = color.copy(alpha = 0.9f), style = stroke)
        drawCircle(color = color, radius = r * 0.12f, center = Offset(cx, cy))
    }
}
