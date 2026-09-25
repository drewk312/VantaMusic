package com.audiophile.musicplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.audiophile.musicplayer.data.display.AudioQualityInfo
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import java.util.Locale

enum class FormatBadgeSize {
    Compact,
    Standard,
    Hero
}

// -------------------------------------------------------------------------
// Color Palettes
// -------------------------------------------------------------------------

private val DolbyAtmosBadgeAccent = Color(0xFFB8A7FF)
private val DolbyAtmosBadgeStart = Color(0xFF140D2B)
private val DolbyAtmosBadgeEnd = Color(0xFF221544)
private val DolbyAtmosBadgeBorder = Color(0xFF8A76DB)

private val Sony360BadgeAccent = Color(0xFF5EEAD4)
private val Sony360BadgeStart = Color(0xFF06151B)
private val Sony360BadgeEnd = Color(0xFF0B2530)
private val Sony360BadgeBorder = Color(0xFF2DD4BF)

private val TrueHdBadgeAccent = Color(0xFF93C5FD)
private val TrueHdBadgeStart = Color(0xFF0C1929)
private val TrueHdBadgeEnd = Color(0xFF152A42)
private val TrueHdBadgeBorder = Color(0xFF60A5FA)

private val HiResBadgeAccent = Color(0xFFFFDF73)
private val HiResBadgeStart = Color(0xFF1F1709)
private val HiResBadgeEnd = Color(0xFF33240D)
private val HiResBadgeBorder = Color(0xFFFFD700)

private val LosslessFlacBadgeAccent = Color(0xFF6EE7B7)
private val LosslessFlacBadgeStart = Color(0xFF061B14)
private val LosslessFlacBadgeEnd = Color(0xFF0F3025)
private val LosslessFlacBadgeBorder = Color(0xFF34D399)

private val AcousticBadgeAccent = Color(0xFFFBBF24)
private val AcousticBadgeStart = Color(0xFF261406)
private val AcousticBadgeEnd = Color(0xFF3D1F08)
private val AcousticBadgeBorder = Color(0xFFF59E0B)

private val StandardLossyBadgeAccent = Color(0xFFCBD5E1)
private val StandardLossyBadgeStart = Color(0xFF14171F)
private val StandardLossyBadgeEnd = Color(0xFF1E222D)
private val StandardLossyBadgeBorder = Color(0xFF64748B)

// -------------------------------------------------------------------------
// Individual Visual Format Badges
// -------------------------------------------------------------------------

@Composable
fun DolbyAtmosFormatBadge(
    modifier: Modifier = Modifier,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    verified: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    BaseFormatBadge(
        modifier = modifier,
        size = size,
        title = if (size == FormatBadgeSize.Compact) "ATMOS" else "DOLBY ATMOS",
        subtitle = if (size == FormatBadgeSize.Hero) "3D OBJECT BITSTREAM" else if (size == FormatBadgeSize.Standard) (if (verified) "E-AC-3 JOC" else "CATALOG") else null,
        contentDesc = "Dolby Atmos audio",
        accent = DolbyAtmosBadgeAccent,
        bgStart = DolbyAtmosBadgeStart,
        bgEnd = DolbyAtmosBadgeEnd,
        border = DolbyAtmosBadgeBorder,
        onClick = onClick
    ) { markModifier ->
        AtmosIdentityMark(modifier = markModifier, color = DolbyAtmosBadgeAccent)
    }
}

@Composable
fun Sony360FormatBadge(
    modifier: Modifier = Modifier,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    verified: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    BaseFormatBadge(
        modifier = modifier,
        size = size,
        title = if (size == FormatBadgeSize.Compact) "360 RA" else "SONY 360 RA",
        subtitle = if (size == FormatBadgeSize.Hero) "360° SPATIAL MIX" else if (size == FormatBadgeSize.Standard) (if (verified) "MPEG-H" else "CATALOG") else null,
        contentDesc = "Sony 360 Reality Audio",
        accent = Sony360BadgeAccent,
        bgStart = Sony360BadgeStart,
        bgEnd = Sony360BadgeEnd,
        border = Sony360BadgeBorder,
        onClick = onClick
    ) { markModifier ->
        Sony360IdentityMark(modifier = markModifier, color = Sony360BadgeAccent)
    }
}

@Composable
fun TrueHdSurroundBadge(
    modifier: Modifier = Modifier,
    channels: Int? = 6,
    isTrueHd: Boolean = true,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    onClick: (() -> Unit)? = null
) {
    val chLabel = when (channels) {
        6 -> "5.1"
        8 -> "7.1"
        null -> "SURROUND"
        else -> "$channels.0"
    }
    val title = when (size) {
        FormatBadgeSize.Compact -> if (isTrueHd) "TRUEHD $chLabel" else "SURROUND $chLabel"
        else -> if (isTrueHd) "DOLBY TRUEHD $chLabel" else "SURROUND $chLabel"
    }
    val subtitle = when (size) {
        FormatBadgeSize.Hero -> "DISCRETE MULTI-CHANNEL LOSSLESS"
        FormatBadgeSize.Standard -> "DISCRETE BITSTREAM"
        FormatBadgeSize.Compact -> null
    }

    BaseFormatBadge(
        modifier = modifier,
        size = size,
        title = title,
        subtitle = subtitle,
        contentDesc = "TrueHD Multi-Channel Audio $chLabel",
        accent = TrueHdBadgeAccent,
        bgStart = TrueHdBadgeStart,
        bgEnd = TrueHdBadgeEnd,
        border = TrueHdBadgeBorder,
        onClick = onClick
    ) { markModifier ->
        TrueHdSurroundMark(modifier = markModifier, channels = channels ?: 6, color = TrueHdBadgeAccent)
    }
}

@Composable
fun HiResAudioBadge(
    modifier: Modifier = Modifier,
    bitDepth: Int? = 24,
    sampleRateHz: Int? = 96000,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    onClick: (() -> Unit)? = null
) {
    val rateText = formatSampleRateBadge(sampleRateHz) ?: "96 kHz"
    val bits = "${bitDepth ?: 24}-BIT"
    val specLine = "$bits / $rateText"

    BaseFormatBadge(
        modifier = modifier,
        size = size,
        title = if (size == FormatBadgeSize.Compact) "HI-RES" else "HI-RES AUDIO",
        subtitle = if (size == FormatBadgeSize.Compact) null else specLine,
        contentDesc = "Hi-Res Audio $specLine",
        accent = HiResBadgeAccent,
        bgStart = HiResBadgeStart,
        bgEnd = HiResBadgeEnd,
        border = HiResBadgeBorder,
        onClick = onClick
    ) { markModifier ->
        HiResEmblemMark(modifier = markModifier, color = HiResBadgeAccent)
    }
}

@Composable
fun LosslessFlacBadge(
    modifier: Modifier = Modifier,
    bitDepth: Int? = 16,
    sampleRateHz: Int? = 44100,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    onClick: (() -> Unit)? = null
) {
    val rateText = formatSampleRateBadge(sampleRateHz) ?: "44.1 kHz"
    val bits = "${bitDepth ?: 16}-BIT"
    val specLine = "$bits / $rateText"

    BaseFormatBadge(
        modifier = modifier,
        size = size,
        title = if (size == FormatBadgeSize.Compact) "FLAC" else "LOSSLESS FLAC",
        subtitle = if (size == FormatBadgeSize.Compact) null else specLine,
        contentDesc = "Lossless FLAC $specLine",
        accent = LosslessFlacBadgeAccent,
        bgStart = LosslessFlacBadgeStart,
        bgEnd = LosslessFlacBadgeEnd,
        border = LosslessFlacBadgeBorder,
        onClick = onClick
    ) { markModifier ->
        LosslessPulseMark(modifier = markModifier, color = LosslessFlacBadgeAccent)
    }
}

@Composable
fun AcousticMasterBadge(
    modifier: Modifier = Modifier,
    acousticness: Double,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    onClick: (() -> Unit)? = null
) {
    val pct = (acousticness * 100).toInt().coerceIn(0, 100)
    BaseFormatBadge(
        modifier = modifier,
        size = size,
        title = if (size == FormatBadgeSize.Compact) "ACOUSTIC" else "PURE ACOUSTIC",
        subtitle = if (size == FormatBadgeSize.Compact) null else "NATURAL TIMBRE · $pct%",
        contentDesc = "Pure Acoustic recording natural timbre $pct percent",
        accent = AcousticBadgeAccent,
        bgStart = AcousticBadgeStart,
        bgEnd = AcousticBadgeEnd,
        border = AcousticBadgeBorder,
        onClick = onClick
    ) { markModifier ->
        AcousticResonanceMark(modifier = markModifier, color = AcousticBadgeAccent)
    }
}

@Composable
fun StandardFormatBadge(
    modifier: Modifier = Modifier,
    format: String?,
    bitrateKbps: Int?,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    onClick: (() -> Unit)? = null
) {
    val fmt = format?.uppercase(Locale.US) ?: "STEREO"
    val bitText = bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }

    BaseFormatBadge(
        modifier = modifier,
        size = size,
        title = fmt,
        subtitle = if (size == FormatBadgeSize.Compact) null else bitText,
        contentDesc = "$fmt $bitText",
        accent = StandardLossyBadgeAccent,
        bgStart = StandardLossyBadgeStart,
        bgEnd = StandardLossyBadgeEnd,
        border = StandardLossyBadgeBorder,
        onClick = onClick
    ) { markModifier ->
        StandardFormatMark(modifier = markModifier, color = StandardLossyBadgeAccent)
    }
}

// -------------------------------------------------------------------------
// Unified Format Badge Row
// -------------------------------------------------------------------------

/**
 * Clusters rich audiophile badges mapped from [qualityInfo] and [acousticness]:
 * 1. Spatial badge: Dolby Atmos, Sony 360, or TrueHD Surround
 * 2. Fidelity badge: Hi-Res Audio Gold, Lossless FLAC, or Standard Lossy
 * 3. Sonic DNA badge: Pure Acoustic Timbre when acousticness >= 0.65
 */
@Composable
fun VantaFormatBadgeRow(
    qualityInfo: VantaQualityInfo?,
    modifier: Modifier = Modifier,
    acousticness: Double? = null,
    size: FormatBadgeSize = FormatBadgeSize.Standard,
    onClick: (() -> Unit)? = null
) {
    if (qualityInfo == null && acousticness == null) return

    val isAtmos = qualityInfo?.isDolbyAtmos == true ||
        isDolbyAtmosLabel(qualityInfo?.bestQualityLabel()) ||
        AudioQualityInfo.hasAtmosCodecEvidence(qualityInfo?.label, qualityInfo?.mimeType, qualityInfo?.format)

    val isSony360 = qualityInfo?.isSony360RealityAudio == true ||
        isSony360Label(qualityInfo?.bestQualityLabel()) ||
        isSony360Label(qualityInfo?.format)

    val isSurroundOrTrueHd = !isAtmos && !isSony360 && (
        qualityInfo?.isSurround == true ||
        (qualityInfo?.channels != null && qualityInfo.channels > 2) ||
        qualityInfo?.format?.contains("truehd", ignoreCase = true) == true ||
        qualityInfo?.pcmEncoding?.contains("truehd", ignoreCase = true) == true
    )

    val isHiRes = qualityInfo?.isHiRes == true ||
        (qualityInfo?.bitDepth != null && qualityInfo.bitDepth > 16) ||
        (qualityInfo?.sampleRateHz != null && qualityInfo.sampleRateHz > 44100) ||
        qualityInfo?.bestQualityLabel()?.contains("24-bit", ignoreCase = true) == true ||
        qualityInfo?.bestQualityLabel()?.contains("hi-res", ignoreCase = true) == true

    val isLossless = qualityInfo?.isLossless == true ||
        qualityInfo?.format?.equals("flac", ignoreCase = true) == true ||
        qualityInfo?.format?.equals("alac", ignoreCase = true) == true ||
        qualityInfo?.format?.equals("wav", ignoreCase = true) == true

    val isAcousticMaster = acousticness != null && acousticness >= 0.65

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (size == FormatBadgeSize.Compact) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Spatial Dimension
        when {
            isAtmos -> DolbyAtmosFormatBadge(size = size, onClick = onClick)
            isSony360 -> Sony360FormatBadge(size = size, onClick = onClick)
            isSurroundOrTrueHd -> TrueHdSurroundBadge(
                channels = qualityInfo.channels ?: 6,
                isTrueHd = qualityInfo.format?.contains("truehd", ignoreCase = true) == true ||
                    qualityInfo.pcmEncoding?.contains("truehd", ignoreCase = true) == true,
                size = size,
                onClick = onClick
            )
        }

        // 2. Fidelity / Compression Dimension
        if (qualityInfo != null) {
            when {
                isHiRes -> HiResAudioBadge(
                    bitDepth = qualityInfo.bitDepth ?: 24,
                    sampleRateHz = qualityInfo.sampleRateHz ?: 96000,
                    size = size,
                    onClick = onClick
                )
                isLossless -> LosslessFlacBadge(
                    bitDepth = qualityInfo.bitDepth ?: 16,
                    sampleRateHz = qualityInfo.sampleRateHz ?: 44100,
                    size = size,
                    onClick = onClick
                )
                !isAtmos && !isSony360 && !isSurroundOrTrueHd -> StandardFormatBadge(
                    format = qualityInfo.format,
                    bitrateKbps = qualityInfo.bitrateKbps,
                    size = size,
                    onClick = onClick
                )
            }
        }

        // 3. Sonic Timbre / Acousticness Dimension
        if (acousticness != null && acousticness >= 0.65) {
            AcousticMasterBadge(
                acousticness = acousticness,
                size = size,
                onClick = onClick
            )
        }
    }
}

// -------------------------------------------------------------------------
// Base Badge Container
// -------------------------------------------------------------------------

@Composable
private fun BaseFormatBadge(
    modifier: Modifier,
    size: FormatBadgeSize,
    title: String,
    subtitle: String?,
    contentDesc: String,
    accent: Color,
    bgStart: Color,
    bgEnd: Color,
    border: Color,
    onClick: (() -> Unit)?,
    mark: @Composable (Modifier) -> Unit
) {
    val shape = RoundedCornerShape(if (size == FormatBadgeSize.Hero) 10.dp else 6.dp)
    val horizontalPadding = when (size) {
        FormatBadgeSize.Compact -> 6.dp
        FormatBadgeSize.Standard -> 8.dp
        FormatBadgeSize.Hero -> 12.dp
    }
    val verticalPadding = when (size) {
        FormatBadgeSize.Compact -> 2.dp
        FormatBadgeSize.Standard -> 4.dp
        FormatBadgeSize.Hero -> 7.dp
    }
    val markSize = when (size) {
        FormatBadgeSize.Compact -> 9.dp
        FormatBadgeSize.Standard -> 12.dp
        FormatBadgeSize.Hero -> 18.dp
    }
    val titleFontSize = when (size) {
        FormatBadgeSize.Compact -> 9.sp
        FormatBadgeSize.Standard -> 10.sp
        FormatBadgeSize.Hero -> 13.sp
    }
    val subtitleFontSize = when (size) {
        FormatBadgeSize.Compact -> 8.sp
        FormatBadgeSize.Standard -> 8.5.sp
        FormatBadgeSize.Hero -> 10.sp
    }

    Row(
        modifier = modifier
            .semantics { this.contentDescription = contentDesc }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (size == FormatBadgeSize.Compact) 20.dp else if (size == FormatBadgeSize.Standard) 26.dp else 34.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(bgStart, bgEnd)))
            .border(0.75.dp, border.copy(alpha = 0.85f), shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        mark(Modifier.size(markSize))
        if (subtitle == null) {
            Text(
                text = title,
                color = accent,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                maxLines = 1
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy((-1.5).dp)) {
                Text(
                    text = title,
                    color = accent,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = subtitleFontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Custom Canvas Marks
// -------------------------------------------------------------------------

/**
 * Multi-channel surround satellite speaker mark:
 * Central listening position with discrete satellite dots and wavefront arc.
 */
@Composable
fun TrueHdSurroundMark(
    modifier: Modifier = Modifier,
    channels: Int = 6,
    color: Color = TrueHdBadgeAccent
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.42f

        // Center sweet spot
        drawCircle(color = color, radius = size.minDimension * 0.12f, center = Offset(cx, cy))

        // Discrete satellite speakers arranged on surround circle
        val numSpeakers = if (channels >= 8) 7 else 5
        val angleStep = 360f / numSpeakers
        for (i in 0 until numSpeakers) {
            val angleRad = Math.toRadians((i * angleStep - 90).toDouble())
            val sx = (cx + r * Math.cos(angleRad)).toFloat()
            val sy = (cy + r * Math.sin(angleRad)).toFloat()
            drawCircle(color = color.copy(alpha = 0.85f), radius = size.minDimension * 0.08f, center = Offset(sx, sy))
        }

        // Ambient surround soundwave ring
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = size.minDimension * 0.08f)
        )
    }
}

/**
 * Golden Audiophile "HR" mark:
 * Stylized high-res sound frequency pillars.
 */
@Composable
fun HiResEmblemMark(
    modifier: Modifier = Modifier,
    color: Color = HiResBadgeAccent
) {
    Canvas(modifier = modifier) {
        val pad = size.minDimension * 0.1f
        val w = size.width - pad * 2f
        val h = size.height - pad * 2f

        // Diamond/hexagonal audiophile framing
        val stroke = Stroke(width = size.minDimension * 0.10f)
        drawRoundRect(
            color = color.copy(alpha = 0.3f),
            topLeft = Offset(pad, pad),
            size = Size(w, h),
            cornerRadius = CornerRadius(size.minDimension * 0.2f)
        )

        // Stepped frequency bars representing high-bandwidth frequency extension
        val barWidth = w * 0.15f
        val gap = w * 0.08f
        val x0 = pad + w * 0.12f
        val heights = floatArrayOf(0.45f, 0.75f, 1.0f, 0.60f)
        for (i in heights.indices) {
            val barH = h * heights[i]
            val bx = x0 + i * (barWidth + gap)
            val by = pad + (h - barH)
            drawRoundRect(
                color = color,
                topLeft = Offset(bx, by),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth * 0.4f)
            )
        }
    }
}

/**
 * Lossless FLAC pulse mark:
 * Precise sine/digital bitstream wave.
 */
@Composable
fun LosslessPulseMark(
    modifier: Modifier = Modifier,
    color: Color = LosslessFlacBadgeAccent
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.12f)
        val cy = size.height / 2f
        val w = size.width
        val path = Path().apply {
            moveTo(0f, cy)
            lineTo(w * 0.22f, cy)
            lineTo(w * 0.36f, cy - size.height * 0.35f)
            lineTo(w * 0.52f, cy + size.height * 0.35f)
            lineTo(w * 0.68f, cy - size.height * 0.20f)
            lineTo(w * 0.82f, cy)
            lineTo(w, cy)
        }
        drawPath(path, color = color, style = stroke)
        // Precision direct bitstream lock dot
        drawCircle(color = color, radius = size.minDimension * 0.12f, center = Offset(w * 0.36f, cy - size.height * 0.35f))
    }
}

/**
 * Acoustic resonance mark:
 * Organic soundboard vibration arcs / tonewood harmonic waves.
 */
@Composable
fun AcousticResonanceMark(
    modifier: Modifier = Modifier,
    color: Color = AcousticBadgeAccent
) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.3f
        val cy = size.height / 2f
        val stroke = Stroke(width = size.minDimension * 0.11f)

        // Resonant sound source dot (like acoustic string pluck)
        drawCircle(color = color, radius = size.minDimension * 0.12f, center = Offset(cx, cy))

        // Three harmonic radiating acoustic wavefronts
        val radii = floatArrayOf(size.minDimension * 0.25f, size.minDimension * 0.45f, size.minDimension * 0.65f)
        val alphas = floatArrayOf(0.95f, 0.70f, 0.40f)
        for (i in radii.indices) {
            val r = radii[i]
            drawArc(
                color = color.copy(alpha = alphas[i]),
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = stroke
            )
        }
    }
}

/**
 * Standard format mark:
 * Minimal digital level pill.
 */
@Composable
fun StandardFormatMark(
    modifier: Modifier = Modifier,
    color: Color = StandardLossyBadgeAccent
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.10f)
        drawCircle(color = color.copy(alpha = 0.5f), radius = size.minDimension * 0.4f, center = Offset(size.width / 2f, size.height / 2f), style = stroke)
        drawCircle(color = color, radius = size.minDimension * 0.18f, center = Offset(size.width / 2f, size.height / 2f))
    }
}

private fun formatSampleRateBadge(sampleRateHz: Int?): String? {
    sampleRateHz ?: return null
    if (sampleRateHz <= 0) return null
    return if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz"
    else AudioQualityInfo.formatKhz(sampleRateHz)
}
