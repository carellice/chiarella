package it.chiarella.app.ui.components

import androidx.compose.animation.core.RepeatMode
import it.chiarella.app.ui.motion.animateFloat
import it.chiarella.app.ui.motion.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import it.chiarella.app.ui.motion.rememberInfiniteTransition
import it.chiarella.app.ui.motion.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.chiarella.app.audio.Sound
import it.chiarella.app.ui.theme.Palette
import kotlinx.coroutines.delay

/** Barrette che danzano mentre la musica suona e si adagiano in pausa. */
@Composable
fun Equalizer(playing: Boolean, color: Color, modifier: Modifier = Modifier, bars: Int = 4) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = List(bars) { i ->
        transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420 + i * 130), RepeatMode.Reverse),
            label = "bar$i",
        )
    }
    val level by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "eqLevel")
    Canvas(modifier) {
        val gap = size.width / (bars * 2 - 1)
        heights.forEachIndexed { i, h ->
            val value = 0.18f + (h.value - 0.18f) * level
            val barH = size.height * value
            drawRoundRect(
                color = color,
                topLeft = Offset(i * gap * 2, size.height - barH),
                size = Size(gap, barH),
                cornerRadius = CornerRadius(gap / 2),
            )
        }
    }
}

/** Icone dei suoni sovrapposte, come una piccola pila di gettoni. */
@Composable
fun IconStack(sounds: List<Sound>, modifier: Modifier = Modifier, size: Dp = 36.dp, max: Int = 3) {
    val shown = sounds.take(max)
    val extra = sounds.size - shown.size
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-size * 0.38f)) {
        shown.forEach { s ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(s.primary, s.secondary)))
                    .border(2.dp, Palette.Surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            }
        }
        if (extra > 0) {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Palette.SurfaceHigh)
                    .border(2.dp, Palette.Surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("+$extra", color = Palette.Text, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Fa "sbordare" un elemento oltre il padding del contenitore (per liste orizzontali a tutta larghezza). */
fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Orologio che ticchetta ogni secondo finché [ticking] è vero. */
@Composable
fun rememberNow(ticking: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ticking) {
        now = System.currentTimeMillis()
        while (ticking) {
            delay(1000 - now % 1000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatMinutes(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}
