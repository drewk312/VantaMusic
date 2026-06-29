package com.audiophile.musicplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * VANTA Master Luxury Design System.
 * Define once, use everywhere for consistent "20 dollar a month" editorial vibe.
 */
object VantaDesignSystem {
    // Master Palette
    val Background = Color(0xFF0A0A0C)
    val Surface = Color(0xFF141416)
    val AccentGold = Color(0xFFE0A050)
    val PrimaryText = Color(0xFFF5F0EB)
    val SecondaryText = Color(0xFFB5AFB8)
    val MutedText = Color(0xFF7E777F)
    
    // Typography
    val TopBarTitle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.5.sp,
        color = PrimaryText
    )
    
    val EditorialHero = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 32.sp,
        color = PrimaryText
    )
    
    val CardTitle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = PrimaryText
    )
}
