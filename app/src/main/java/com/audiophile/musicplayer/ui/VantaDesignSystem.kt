package com.audiophile.musicplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** VANTA's dark liquid-glass foundation. */
object VantaDesignSystem {
    val Background = Color(0xFF0A0B0D)
    val Surface = Color(0xFF151619)
    val AccentIris = Color(0xFFE4CBA4)
    val AccentIce = Color(0xFFC1D1D1)
    val PrimaryText = Color(0xFFF5F1EB)
    val SecondaryText = Color(0xFFBCB9B3)
    val MutedText = Color(0xFF97958F)
    
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
