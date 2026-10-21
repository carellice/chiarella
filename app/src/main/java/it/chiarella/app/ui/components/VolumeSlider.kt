package it.chiarella.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** Slider a pillola con riempimento sfumato, pomello luminoso e piccoli "tick" aptici. */
@Composable
fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val onChange by rememberUpdatedState(onValueChange)
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var lastStep by remember { mutableIntStateOf((value * 10).toInt()) }
    val shown by animateFloatAsState(
        targetValue = value,
        animationSpec = if (dragging) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "volume",
    )
    val thumb by animateFloatAsState(if (dragging) 1.25f else 1f, spring(dampingRatio = 0.45f), label = "thumb")

    fun emit(x: Float) {
        val v = (x / width).coerceIn(0f, 1f)
        val step = (v * 10).toInt()
        if (step != lastStep) {
            lastStep = step
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        onChange(v)
    }

    Box(
        modifier
            .height(30.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { emit(it.x) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; emit(it.x) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    emit(change.position.x)
                }
            }
            .drawBehind {
                val h = size.height
                val radius = CornerRadius(h / 2)
                drawRoundRect(Color.Black.copy(alpha = 0.28f), cornerRadius = radius)
                val fillW = h + (size.width - h) * shown
                drawRoundRect(
                    Brush.horizontalGradient(listOf(primary, secondary), 0f, fillW),
                    size = Size(fillW, h), cornerRadius = radius,
                )
                val center = Offset(fillW - h / 2, h / 2)
                drawCircle(Color.White.copy(alpha = 0.25f), radius = h / 2 * thumb, center = center)
                drawCircle(Color.White, radius = (h / 2 - 5.dp.toPx()) * thumb, center = center)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = when {
                value < 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                value < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            },
            contentDescription = null,
            tint = Color.White.copy(alpha = if (shown > 0.12f) 0.95f else 0f),
            modifier = Modifier.padding(start = 8.dp).size(15.dp),
        )
    }
}
