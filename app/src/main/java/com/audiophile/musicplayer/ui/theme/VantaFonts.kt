package com.audiophile.musicplayer.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.audiophile.musicplayer.R

/** Bundled SIL Open Font License typeface; typography stays consistent across devices. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val VantaSans = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(R.font.manrope, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }
)
