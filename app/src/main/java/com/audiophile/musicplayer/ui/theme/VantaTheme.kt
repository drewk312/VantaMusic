package com.audiophile.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppAccentSecondary
import com.audiophile.musicplayer.ui.AppAuroraRose
import com.audiophile.musicplayer.ui.AppAuroraViolet
import com.audiophile.musicplayer.ui.AppBackground
import com.audiophile.musicplayer.ui.AppBackgroundBottom
import com.audiophile.musicplayer.ui.AppError
import com.audiophile.musicplayer.ui.AppOutline
import com.audiophile.musicplayer.ui.AppSuccess
import com.audiophile.musicplayer.ui.AppSurface
import com.audiophile.musicplayer.ui.AppSurfaceRaised
import com.audiophile.musicplayer.ui.AppSurfaceVariant
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.ui.AppWarning
import com.audiophile.musicplayer.ui.VantaRadius

private val VantaColorScheme = darkColorScheme(
    primary = AppAccent,
    onPrimary = AppBackground,
    primaryContainer = AppAccent.copy(alpha = 0.18f),
    onPrimaryContainer = AppAccent,
    inversePrimary = AppAccent,
    secondary = AppAccentSecondary,
    onSecondary = AppBackground,
    secondaryContainer = AppAccentSecondary.copy(alpha = 0.16f),
    onSecondaryContainer = AppAccentSecondary,
    tertiary = AppAuroraViolet,
    onTertiary = AppBackground,
    tertiaryContainer = AppAuroraViolet.copy(alpha = 0.18f),
    onTertiaryContainer = AppAuroraRose,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceTint = AppAccent,
    surfaceDim = AppBackground,
    surfaceBright = AppSurfaceRaised,
    surfaceContainerLowest = AppBackgroundBottom,
    surfaceContainerLow = AppSurface,
    surfaceContainer = AppSurface,
    surfaceContainerHigh = AppSurfaceRaised,
    surfaceContainerHighest = AppSurfaceVariant,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppTextSecondary,
    inverseSurface = AppSurfaceRaised,
    inverseOnSurface = AppText,
    outline = AppOutline,
    outlineVariant = AppAccent.copy(alpha = 0.22f),
    scrim = AppBackground,
    error = AppError,
    onError = AppBackground,
    errorContainer = AppError.copy(alpha = 0.16f),
    onErrorContainer = AppError
)

private val VantaTypography = Typography(
    bodyLarge = TextStyle(fontFamily = VantaSans, fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = VantaSans, fontSize = 12.sp, lineHeight = 18.sp),
    titleLarge = TextStyle(fontFamily = VantaSans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = VantaSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontFamily = VantaSans, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = VantaSans, fontSize = 11.sp, lineHeight = 16.sp),
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        color = AppText
    ),
    headlineLarge = TextStyle(
        fontFamily = VantaSans,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = AppText,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = VantaSans,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppText,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = VantaSans,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppText
    ),
    bodyMedium = TextStyle(
        fontFamily = VantaSans,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = AppTextSecondary,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = VantaSans,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = AppTextMuted,
        letterSpacing = 0.4.sp
    )
)

private val VantaShapes = Shapes(
    extraSmall = RoundedCornerShape(VantaRadius.button),
    small = RoundedCornerShape(VantaRadius.artwork),
    medium = RoundedCornerShape(VantaRadius.card),
    large = RoundedCornerShape(VantaRadius.largeCard),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VantaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VantaColorScheme,
        typography = VantaTypography,
        shapes = VantaShapes,
        content = content
    )
}
