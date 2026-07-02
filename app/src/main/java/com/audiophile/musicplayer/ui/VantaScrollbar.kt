package com.audiophile.musicplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VantaScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = Color.White.copy(alpha = 0.35f),
    trackColor: Color = Color.White.copy(alpha = 0.08f),
    trackWidth: Dp = 4.dp,
    touchWidth: Dp = 20.dp
) {
    // Geometry is derived so scrolling only invalidates the draw pass, never
    // the whole composition (avoids per-frame recomposition of the parent).
    val thumbGeometry by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
            val visibleItems = layoutInfo.visibleItemsInfo
            if (totalItems <= 0 || visibleItems.isEmpty() || viewportHeight <= 0) return@derivedStateOf null
            val first = visibleItems.first()
            val last = visibleItems.last()
            val spanIndices = (last.index - first.index + 1).coerceAtLeast(1)
            val spanPixels = (last.offset + last.size - first.offset).coerceAtLeast(1)
            val avgItemHeight = spanPixels.toFloat() / spanIndices
            val totalContentHeight = avgItemHeight * totalItems
            val scrollRange = (totalContentHeight - viewportHeight).coerceAtLeast(1f)
            val scrollPos = (first.index * avgItemHeight) + first.offset
            val fraction = (scrollPos / scrollRange).coerceIn(0f, 1f)
            val thumbHeight = ((viewportHeight / totalContentHeight) * viewportHeight)
                .coerceIn(40f, viewportHeight.toFloat())
            val thumbOffset = fraction * (viewportHeight - thumbHeight)
            thumbOffset to thumbHeight
        }
    }

    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .width(touchWidth)
            .pointerInput(listState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastTarget = -1
                    val total = listState.layoutInfo.totalItemsCount
                    if (total <= 0) return@awaitEachGesture
                    fun scrollTo(y: Float) {
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val frac = (y / h).coerceIn(0f, 1f)
                        val idx = (frac * total).toInt().coerceIn(0, total - 1)
                        if (idx != lastTarget) {
                            lastTarget = idx
                            scope.launch {
                                listState.scrollToItem(idx)
                            }
                        }
                    }
                    scrollTo(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            scrollTo(change.position.y)
                            change.consume()
                        }
                    } while (change.pressed)
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(trackWidth)
                .align(Alignment.Center)
        ) {
            val geometry = thumbGeometry ?: return@Canvas
            val (thumbOffset, thumbHeight) = geometry
            val w = size.width
            val r = CornerRadius(w / 2)
            drawRoundRect(color = trackColor, topLeft = Offset.Zero, size = Size(w, size.height), cornerRadius = r)
            drawRoundRect(color = thumbColor, topLeft = Offset(0f, thumbOffset), size = Size(w, thumbHeight), cornerRadius = r)
        }
    }
}
