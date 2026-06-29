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
import com.audiophile.musicplayer.ui.AppBackground
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
    secondary = AppAccentSecondary,
    onSecondary = AppBackground,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppTextSecondary,
    outline = AppOutline,
    error = AppError,
    onError = AppText
)

private val VantaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        color = AppText
    ),
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = AppText,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppText,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppText
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = AppTextSecondary,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
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
