package com.audiophile.musicplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Shared motion language — soft, premium, never snappy or jarring. */
object VantaMotion {
    val easeOutLuxury = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val easeInOutCozy = FastOutSlowInEasing

    const val screenFadeMs = 280
    const val screenSlideMs = 320
    const val chromeFadeMs = 240
    const val microMs = 180

    fun screenTween() = tween<Float>(screenFadeMs, easing = easeOutLuxury)
    fun screenSlideTween() = tween<Int>(screenSlideMs, easing = easeOutLuxury)
    fun chromeTween() = tween<Float>(chromeFadeMs, easing = easeInOutCozy)
    fun microTween() = tween<Float>(microMs, easing = easeInOutCozy)

    fun gentleSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun progressSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
