package com.audiophile.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VantaBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    /** Cap sheet height as a fraction of the screen. Content still wraps when shorter. */
    maxHeightFraction: Float = 0.72f,
    content: @Composable ColumnScope.() -> Unit
) {
    val dismissKeyboard = rememberKeyboardDismissal()
    val close = { dismissKeyboard(); onDismiss() }
    if (visible) {
        LaunchedEffect(Unit) { dismissKeyboard() }
        BackHandler(onBack = close)

        val sheetProgress by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "sheetProgress"
        )
        val maxHeight = (LocalConfiguration.current.screenHeightDp * maxHeightFraction.coerceIn(0.28f, 0.92f)).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(top = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f * sheetProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close
                    )
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .graphicsLayer {
                        translationY = (1f - sheetProgress) * 200f
                        alpha = sheetProgress
                    }
                    .background(AppBackgroundTop, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(0.5.dp, AppOutline, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(top = 10.dp, bottom = 20.dp),
                content = {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppOutline)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            )
        }
    }
}

@Composable
fun VantaSheetAction(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    tint: Color = AppText
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun VantaSheetDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(AppOutline))
}

@Composable
fun VantaSheetHeader(title: String, subtitle: String? = null, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = AppTextSecondary, fontSize = 13.sp, lineHeight = 19.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
        }
        androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close $title",
                tint = AppTextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}
