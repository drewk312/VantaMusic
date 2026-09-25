package com.audiophile.musicplayer.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dense 10-foot metrics — fill the first viewport with presence, not empty charcoal.
 */
data class TvMetrics(
    val pagePadding: Dp,
    val rowGap: Dp,
    val cardWidth: Dp,
    val artSize: Dp,
    val heroHeight: Dp,
    val heroArt: Dp,
    val miniArt: Dp,
    val brand: TextUnit,
    val pageTitle: TextUnit,
    val heroTitle: TextUnit,
    val rowTitle: TextUnit,
    val cardTitle: TextUnit,
    val cardSubtitle: TextUnit,
    val body: TextUnit,
    val caption: TextUnit,
    val focusScale: Float,
    /** Jukebox machine sizing — magazine / platter / now-playing / meter. */
    val jukeboxMagazineWidth: Dp,
    val jukeboxNowPlayingWidth: Dp,
    val jukeboxPlatterSize: Dp,
    val jukeboxMeterWidth: Dp
)

@Composable
fun rememberTvMetrics(): TvMetrics {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val heightDp = LocalConfiguration.current.screenHeightDp
    return remember(widthDp, heightDp) {
        val compact = heightDp in 1..720 || widthDp < 1280
        when {
            widthDp >= 2400 -> TvMetrics(
                pagePadding = 64.dp,
                rowGap = 36.dp,
                cardWidth = 228.dp,
                artSize = 212.dp,
                heroHeight = 420.dp,
                heroArt = 360.dp,
                miniArt = 72.dp,
                brand = 28.sp,
                pageTitle = 40.sp,
                heroTitle = 52.sp,
                rowTitle = 22.sp,
                cardTitle = 16.sp,
                cardSubtitle = 13.sp,
                body = 17.sp,
                caption = 12.sp,
                focusScale = 1.06f,
                jukeboxMagazineWidth = 360.dp,
                jukeboxNowPlayingWidth = 400.dp,
                jukeboxPlatterSize = 540.dp,
                jukeboxMeterWidth = 150.dp
            )
            widthDp >= 1600 -> TvMetrics(
                pagePadding = 48.dp,
                rowGap = 30.dp,
                cardWidth = 196.dp,
                artSize = 180.dp,
                heroHeight = 360.dp,
                heroArt = 300.dp,
                miniArt = 60.dp,
                brand = 24.sp,
                pageTitle = 34.sp,
                heroTitle = 44.sp,
                rowTitle = 20.sp,
                cardTitle = 15.sp,
                cardSubtitle = 12.sp,
                body = 16.sp,
                caption = 11.sp,
                focusScale = 1.05f,
                jukeboxMagazineWidth = 290.dp,
                jukeboxNowPlayingWidth = 320.dp,
                jukeboxPlatterSize = 430.dp,
                jukeboxMeterWidth = 124.dp
            )
            compact -> TvMetrics(
                pagePadding = 28.dp,
                rowGap = 22.dp,
                cardWidth = 148.dp,
                artSize = 132.dp,
                heroHeight = 260.dp,
                heroArt = 200.dp,
                miniArt = 48.dp,
                brand = 20.sp,
                pageTitle = 26.sp,
                heroTitle = 32.sp,
                rowTitle = 16.sp,
                cardTitle = 13.sp,
                cardSubtitle = 11.sp,
                body = 14.sp,
                caption = 10.sp,
                focusScale = 1.05f,
                jukeboxMagazineWidth = 200.dp,
                jukeboxNowPlayingWidth = 220.dp,
                jukeboxPlatterSize = 300.dp,
                jukeboxMeterWidth = 86.dp
            )
            else -> TvMetrics(
                pagePadding = 40.dp,
                rowGap = 26.dp,
                cardWidth = 172.dp,
                artSize = 156.dp,
                heroHeight = 320.dp,
                heroArt = 260.dp,
                miniArt = 56.dp,
                brand = 22.sp,
                pageTitle = 30.sp,
                heroTitle = 38.sp,
                rowTitle = 18.sp,
                cardTitle = 14.sp,
                cardSubtitle = 12.sp,
                body = 15.sp,
                caption = 11.sp,
                focusScale = 1.05f,
                jukeboxMagazineWidth = 240.dp,
                jukeboxNowPlayingWidth = 260.dp,
                jukeboxPlatterSize = 360.dp,
                jukeboxMeterWidth = 100.dp
            )
        }
    }
}
