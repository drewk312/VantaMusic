package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// VANTA design tokens
// Dark liquid glass: near-black ink, spectral light, artwork-first surfaces.
// ============================================================

// Obsidian canvas
val AppBackground = VantaDesignSystem.Background
val AppBackgroundTop = Color(0xFF191817)
val AppBackgroundBottom = Color(0xFF08090B)
val AppBackgroundGlow = Color(0xFF554633)

// Chrome — floating mini player & navigation
val AppChrome = Color(0xF0101113)
val AppChromeElevated = Color(0xF01D1E21)
val AppChromeBorder = Color.White.copy(alpha = 0.15f)

// Surfaces — cool neutral layers that let artwork carry the color
val AppSurface = VantaDesignSystem.Surface
val AppSurfaceRaised = Color(0xFF202124)
val AppSurfaceVariant = Color(0xFF292A2D)
val AppCard = AppSurface
val AppOutline = Color.White.copy(alpha = 0.09f)

// Text
val AppText = VantaDesignSystem.PrimaryText
val AppTextSecondary = VantaDesignSystem.SecondaryText
val AppTextMuted = VantaDesignSystem.MutedText

// Spectral accents used sparingly in chrome and state
val AppAccent = VantaDesignSystem.AccentIris
val AppAccentSoft = AppAccent.copy(alpha = 0.14f)
val AppAccentSecondary = VantaDesignSystem.AccentIce
val AppAccentGlow = AppAccent.copy(alpha = 0.34f)
val AppAuroraViolet = Color(0xFFB99D78)
val AppAuroraCyan = Color(0xFF90AAAA)
val AppAuroraRose = Color(0xFFC79F9A)
val AppGlassHighlight = Color.White.copy(alpha = 0.20f)
val AppGlassLowlight = Color(0xFF090B12).copy(alpha = 0.74f)

// Semantic
val AppSuccess = Color(0xFF3DDC97)
val AppWarning = Color(0xFFFFD166)
val AppError = Color(0xFFFF7B7B)

// NowPlaying fallbacks — used when a track has no artwork to react to.
// Warm ember/umber tones that keep the artwork-first language intact.
val VantaFallbackAccent = Color(0xFFB8A77F)
val VantaFallbackUmber = Color(0xFF1A1218)
val VantaFallbackUmberDark = Color(0xFF0D0A0C)
val VantaLyricsBackdrop = Color(0xFF0C0B0D)

val AppDestructive = AppError
val AppCardRaised = AppSurfaceRaised
val AppSurfaceSoft = AppSurfaceRaised

// ============================================================
// Typography — Serif for editorial, Sans for UI
// ============================================================
object VantaType {
    // Editorial — serif italic for moments of beauty
    val editorialHero = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        color = AppText
    )
    val editorialLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        color = AppText
    )
    val editorialBody = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = AppText.copy(alpha = 0.8f)
    )
    val editorialSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = AppText.copy(alpha = 0.6f)
    )

    // Sans — geometric sans for UI
    val pageTitle = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = AppText,
        letterSpacing = (-0.4).sp
    )
    val sectionTitle = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppText,
        letterSpacing = 0.2.sp
    )
    val cardTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppText)
    val songTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppText)
    val subtitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppTextSecondary)
    val caption = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppTextMuted, letterSpacing = 0.5.sp)

    // Tabular numbers for time displays
    val tabularNumbers = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = AppText.copy(alpha = 0.5f)
    )
}

// ============================================================
// Spacing
// ============================================================
object VantaSpacing {
    val screenHorizontal = 20.dp
    val sectionVertical = 28.dp
    val itemGap = 12.dp
    val cardGap = 16.dp
    val gridGap = 12.dp
    val chipVertical = 12.dp
    val chipHorizontal = 18.dp
    val hairline = 1.dp
}

object VantaRadius {
    val card = 22.dp
    val largeCard = 30.dp
    val artwork = 16.dp
    val miniPlayer = 24.dp
    val button = 18.dp
    val pill = 50
    val searchField = 30.dp
    val chrome = 34.dp
}

object VantaChrome {
    val miniPlayerHeight = 72.dp
    val bottomNavHeight = 70.dp
    val overlayHorizontal = 12.dp
    val overlayGap = 8.dp
}

// ============================================================
// Content Padding
// ============================================================
@Composable
fun appBottomContentPadding(
    isMiniPlayerVisible: Boolean,
    isBottomNavVisible: Boolean = true
): Dp {
    val navBarHeight = if (isBottomNavVisible) VantaChrome.bottomNavHeight else 0.dp
    val miniPlayerHeight = if (isMiniPlayerVisible) VantaChrome.miniPlayerHeight else 0.dp
    val spacing = if (isMiniPlayerVisible) VantaChrome.overlayGap else 0.dp
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    return miniPlayerHeight + spacing + navBarHeight + systemBar
}

@Composable
fun appTopContentPadding(extra: Dp = 16.dp): Dp {
    return WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + extra
}

@Composable
fun appOverlayBottomPadding(
    miniPlayerVisible: Boolean,
    bottomNavVisible: Boolean = false
): androidx.compose.ui.unit.Dp {
    val insets = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val overlayBottomMargin = 18.dp
    val miniPlayerGap = VantaChrome.overlayGap
    val miniPlayerHeight = VantaChrome.miniPlayerHeight
    val bottomNavHeight = VantaChrome.bottomNavHeight
    var overlay = overlayBottomMargin
    if (miniPlayerVisible) overlay += miniPlayerGap + miniPlayerHeight
    if (bottomNavVisible) overlay += bottomNavHeight
    return insets + overlay
}

@Composable
fun appBottomWindowInsets(): androidx.compose.ui.unit.Dp {
    return WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
}

fun Modifier.vantaSurface(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.card)
): Modifier = this
    .clip(shape)
    .background(AppSurface)
    .border(0.5.dp, AppOutline, shape)

fun Modifier.vantaRaised(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.card)
): Modifier = this
    .clip(shape)
    .background(AppSurfaceRaised)
    .border(0.5.dp, AppOutline, shape)

fun Modifier.vantaCard(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.card)
): Modifier = this
    .clip(shape)
    .background(AppCard)
    .border(0.5.dp, AppAccent.copy(alpha = 0.08f), shape)

fun Modifier.glassSurface(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.card),
    borderAlpha: Float = 0.16f,
    surfaceAlpha: Float = 0.78f
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f * surfaceAlpha),
                AppChromeElevated.copy(alpha = surfaceAlpha),
                AppChrome.copy(alpha = surfaceAlpha)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    )
    .border(
        0.75.dp,
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = borderAlpha + 0.08f),
                AppAuroraCyan.copy(alpha = borderAlpha * 0.55f),
                AppAuroraViolet.copy(alpha = borderAlpha * 0.75f),
                Color.White.copy(alpha = borderAlpha * 0.35f)
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape
    )

fun Modifier.glassSurfaceElevated(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.card),
    surfaceAlpha: Float = 0.94f
): Modifier = this
    .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
    .clip(shape)
    .background(Brush.verticalGradient(listOf(AppSurfaceRaised.copy(alpha = surfaceAlpha.coerceAtLeast(0.92f)), AppChrome)))
    .border(0.5.dp, Color.White.copy(alpha = 0.13f), shape)

fun Modifier.luxuryCard(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.card)
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(
                AppSurfaceRaised,
                AppSurface.copy(alpha = 0.96f)
            )
        )
    )
    .border(
        width = 0.5.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                AppAccent.copy(alpha = 0.22f),
                AppOutline
            )
        ),
        shape = shape
    )

fun Modifier.velvetChrome(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.chrome)
): Modifier = this
    .glassSurfaceElevated(shape = shape, surfaceAlpha = 0.84f)

@Composable
fun VantaAppBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AppBackgroundTop, AppBackground, AppBackgroundBottom),
                    endY = 1600f
                )
            )
            .drawBehind {
                // Large, low-alpha light fields create depth beneath translucent chrome.
                val violetCenter = Offset(size.width * 0.12f, size.height * 0.10f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AppAuroraViolet.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = violetCenter,
                        radius = size.width * 0.78f
                    ),
                    radius = size.width * 0.78f,
                    center = violetCenter
                )
                val cyanCenter = Offset(size.width * 0.92f, size.height * 0.30f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AppAuroraCyan.copy(alpha = 0.10f), Color.Transparent),
                        center = cyanCenter,
                        radius = size.width * 0.66f
                    ),
                    radius = size.width * 0.66f,
                    center = cyanCenter
                )
                val roseCenter = Offset(size.width * 0.18f, size.height * 0.92f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AppAuroraRose.copy(alpha = 0.07f), Color.Transparent),
                        center = roseCenter,
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = roseCenter
                )
            }
    )
}
