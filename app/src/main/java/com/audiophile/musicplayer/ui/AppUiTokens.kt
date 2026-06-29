package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
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
// Premium dark music app — cozy luxury, butter-smooth chrome
// ============================================================

// Warm velvet dark palette
val AppBackground = VantaDesignSystem.Background
val AppBackgroundTop = Color(0xFF0D0A11)
val AppBackgroundBottom = Color(0xFF040305)
val AppBackgroundGlow = Color(0xFF1F1826)

// Chrome — floating mini player & navigation
val AppChrome = VantaDesignSystem.Background.copy(alpha = 0.98f)
val AppChromeElevated = VantaDesignSystem.Surface
val AppChromeBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)

// Surfaces — warm gray layers
val AppSurface = VantaDesignSystem.Surface
val AppSurfaceRaised = Color(0xFF1D1A22)
val AppSurfaceVariant = Color(0xFF26222B)
val AppCard = AppSurface
val AppOutline = Color(0xFFFFFFFF).copy(alpha = 0.07f)

// Text — warm ivory hierarchy
val AppText = VantaDesignSystem.PrimaryText
val AppTextSecondary = VantaDesignSystem.SecondaryText
val AppTextMuted = VantaDesignSystem.MutedText

// Accent — champagne gold
val AppAccent = VantaDesignSystem.AccentGold
val AppAccentSoft = VantaDesignSystem.AccentGold.copy(alpha = 0.12f)
val AppAccentSecondary = Color(0xFFE8C8A3)
val AppAccentGlow = VantaDesignSystem.AccentGold.copy(alpha = 0.30f)

// Semantic
val AppSuccess = Color(0xFF3DDC97)
val AppWarning = Color(0xFFFFD166)
val AppError = Color(0xFFFF7B7B)

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
    val screenHorizontal = 24.dp
    val sectionVertical = 32.dp
    val itemGap = 12.dp
    val cardGap = 16.dp
    val gridGap = 12.dp
    val chipVertical = 12.dp
    val chipHorizontal = 18.dp
    val hairline = 1.dp
}

object VantaRadius {
    val card = 18.dp
    val largeCard = 26.dp
    val artwork = 14.dp
    val miniPlayer = 20.dp
    val button = 14.dp
    val pill = 50
    val searchField = 30.dp
    val chrome = 30.dp
}

object VantaChrome {
    val miniPlayerHeight = 68.dp
    val bottomNavHeight = 72.dp
    val overlayHorizontal = 16.dp
    val overlayGap = 10.dp
}

// ============================================================
// Content Padding
// ============================================================
@Composable
fun appBottomContentPadding(isMiniPlayerVisible: Boolean): Dp {
    val navBarHeight = VantaChrome.bottomNavHeight
    val miniPlayerHeight = if (isMiniPlayerVisible) VantaChrome.miniPlayerHeight else 0.dp
    val spacing = if (isMiniPlayerVisible) VantaChrome.overlayGap else 0.dp
    val systemBar = with(LocalDensity.current) {
        WindowInsets.systemBars
            .only(WindowInsetsSides.Bottom)
            .getBottom(this)
            .toDp()
    }

    return miniPlayerHeight + spacing + navBarHeight + systemBar
}

@Composable
fun appTopContentPadding(extra: Dp = 16.dp): Dp {
    return WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + extra
}

@Composable
fun appOverlayBottomPadding(
    miniPlayerVisible: Boolean,
    bottomNavVisible: Boolean = false
): androidx.compose.ui.unit.Dp {
    val insets = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
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
    return WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 24.dp
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
    borderAlpha: Float = 0.12f,
    surfaceAlpha: Float = 0.92f
): Modifier = this
    .clip(shape)
    .background(AppChrome.copy(alpha = surfaceAlpha))
    .border(0.5.dp, AppChromeBorder.copy(alpha = borderAlpha), shape)

fun Modifier.glassSurfaceElevated(
    shape: RoundedCornerShape = RoundedCornerShape(VantaRadius.card),
    surfaceAlpha: Float = 0.94f
): Modifier = this
    .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = AppAccentGlow.copy(alpha = 0.08f))
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(
                AppChromeElevated.copy(alpha = surfaceAlpha),
                AppChrome.copy(alpha = surfaceAlpha - 0.04f)
            )
        )
    )
    .border(
        width = 0.5.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                AppAccent.copy(alpha = 0.18f),
                AppChromeBorder.copy(alpha = 0.10f)
            )
        ),
        shape = shape
    )

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
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(
                AppChromeElevated,
                AppChrome
            )
        )
    )
    .border(
        width = 0.5.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                AppAccent.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.04f)
            )
        ),
        shape = shape
    )

@Composable
fun VantaAppBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AppBackgroundTop, AppBackground, AppBackgroundBottom)
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AppBackgroundGlow.copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.72f, size.height * 0.08f),
                        radius = size.width * 0.95f
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width * 0.72f, size.height * 0.08f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AppAccent.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.22f),
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.15f, size.height * 0.22f)
                )
            }
    )
}
