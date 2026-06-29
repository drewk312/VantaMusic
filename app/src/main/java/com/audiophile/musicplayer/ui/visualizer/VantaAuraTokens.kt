package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.ui.graphics.Color

object VantaAuraTokens {
    val WarmCream = Color(0xFFFFE8C8)
    val Amber = Color(0xFFC4843E)
    val DeepGold = Color(0xFF8B6F47)
    val SoftBlack = Color(0xFF0B0A0D)
    val Shadow = Color(0xFF000000).copy(alpha = 0.4f)

    data class GlowLevels(
        val low: Float = 0.06f,
        val medium: Float = 0.10f,
        val high: Float = 0.18f
    )
}
