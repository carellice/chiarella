package it.chiarella.app.ui.components

import androidx.compose.animation.core.Spring
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
import it.chiarella.app.ui.motion.animateFloatAsState
import it.chiarella.app.ui.motion.spring
import it.chiarella.app.ui.theme.Look
@Composable
fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    if (Look.metro) MetroVolumeSlider(value = value, onValueChange = onValueChange, modifier = modifier)
    else if (Look.skeuo) SkeuoVolumeSlider(value = value, onValueChange = onValueChange, primary = primary, secondary = secondary, modifier = modifier) else ClassicVolumeSlider(value = value, onValueChange = onValueChange, primary = primary, secondary = secondary, modifier = modifier)
}

/** Slider a pillola con riempimento sfumato, pomello luminoso e piccoli "tick" aptici. */
@Composable
private fun ClassicVolumeSlider(
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

// ------------------------------------------------------------------ stile skeuomorfico

/** Fader da mixer: scanalatura, scala graduata, cursore cromato e piccoli "tick" aptici. */
@Composable
private fun SkeuoVolumeSlider(
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
                // Fader da mixer: scanalatura incisa, tubo luminoso e cursore cromato.
                val h = size.height
                val capW = 16.dp.toPx() * thumb
                val capH = h * 0.92f
                val travel = size.width - 16.dp.toPx()
                val x = 8.dp.toPx() + travel * shown
                val grooveH = 7.dp.toPx()
                val gy = h / 2 - grooveH / 2
                val radius = CornerRadius(grooveH / 2)
                // Tacche della scala.
                for (i in 0..10) {
                    val tx = 8.dp.toPx() + travel * i / 10f
                    val long = i % 5 == 0
                    drawLine(Color(0x66F2E3C4), Offset(tx, gy - (if (long) 5.dp else 3.dp).toPx()), Offset(tx, gy - 1.dp.toPx()), 1f)
                }
                drawRoundRect(Color(0xFF0A0705), Offset(0f, gy), Size(size.width, grooveH), radius)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent, Color(0x33FFFFFF)), startY = gy, endY = gy + grooveH),
                    Offset(0f, gy), Size(size.width, grooveH), radius,
                )
                if (x > 2f) {
                    drawRoundRect(Brush.horizontalGradient(listOf(primary, secondary), 0f, x), Offset(1f, gy + 1.5f), Size(x, grooveH - 3f), radius)
                    drawRoundRect(secondary.copy(alpha = 0.35f), Offset(1f, gy - 2f), Size(x, grooveH + 4f), CornerRadius(grooveH))
                    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(2f, gy + 2f), Size(x - 2f, 1.5f))
                }
                // Cursore.
                val left = x - capW / 2
                val top = (h - capH) / 2
                drawRoundRect(Color(0x99000000), Offset(left + 1f, top + 3f), Size(capW, capH), CornerRadius(4.dp.toPx()))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFF7F4EF), Color(0xFFBDB7AF), Color(0xFF8A847D), Color(0xFFD9D4CD)), startY = top, endY = top + capH),
                    Offset(left, top), Size(capW, capH), CornerRadius(4.dp.toPx()),
                )
                for (i in -2..2) {
                    val ly = h / 2 + i * 3.dp.toPx()
                    drawLine(Color(0x88000000), Offset(left + 3.dp.toPx(), ly), Offset(left + capW - 3.dp.toPx(), ly), 1.2f)
                    drawLine(Color(0x99FFFFFF), Offset(left + 3.dp.toPx(), ly + 1.2f), Offset(left + capW - 3.dp.toPx(), ly + 1.2f), 1f)
                }
                drawLine(secondary, Offset(x, top + 2.dp.toPx()), Offset(x, top + 5.dp.toPx()), 2f)
            },
    )
}

// ------------------------------------------------------------------ stile Metro

/** Slider Metro: barra piatta e cursore rettangolare bianco, senza animazioni elastiche. */
@Composable
private fun MetroVolumeSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val onChange by rememberUpdatedState(onValueChange)
    var width by remember { mutableIntStateOf(1) }
    var lastStep by remember { mutableIntStateOf((value * 10).toInt()) }

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
            .height(26.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { emit(it.x) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    emit(change.position.x)
                }
            }
            .drawBehind {
                val track = 8.dp.toPx()
                val thumbW = 10.dp.toPx()
                val top = (size.height - track) / 2
                val x = thumbW / 2 + (size.width - thumbW) * value
                drawRect(Color.White.copy(alpha = 0.28f), Offset(0f, top), Size(size.width, track))
                drawRect(Color.White, Offset(0f, top), Size(x, track))
                drawRect(Color.Black, Offset(x - thumbW / 2, 0f), Size(thumbW, size.height))
                drawRect(Color.White, Offset(x - thumbW / 2 + 2.dp.toPx(), 2.dp.toPx()), Size(thumbW - 4.dp.toPx(), size.height - 4.dp.toPx()))
            },
    )
}
