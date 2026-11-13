package it.chiarella.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import it.chiarella.app.ui.motion.animateColorAsState
import it.chiarella.app.ui.motion.animateFloat
import it.chiarella.app.ui.motion.animateFloatAsState
import it.chiarella.app.ui.motion.rememberInfiniteTransition
import it.chiarella.app.ui.motion.tween
import it.chiarella.app.ui.theme.Look
import it.chiarella.app.ui.theme.Palette
import it.chiarella.app.ui.theme.walnut
import it.chiarella.app.ui.theme.dotGrid
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
@Composable
fun AuroraBackground(colors: List<Color>, playing: Boolean, modifier: Modifier = Modifier) {
    when {
        Look.skeuo -> SkeuoAuroraBackground(colors, playing, modifier)
        // Metro: nero assoluto, come lo sfondo scuro di Windows Phone.
        // Nothing: nero OLED con la griglia di punti appena percettibile.
        Look.nothing -> androidx.compose.foundation.layout.Box(modifier.background(Color.Black).dotGrid(alpha = 0.35f))
        Look.metro -> androidx.compose.foundation.layout.Box(modifier.background(Color.Black))
        else -> ClassicAuroraBackground(colors, playing, modifier)
    }
}

private val IdleColors = listOf(Color(0xFF3B2F8F), Color(0xFF1F4E8C), Color(0xFF5B2C83))

// Dueffe: nebulose sempre oro e ambra, un solo accento qualunque suono sia acceso.
private val GoldColors = listOf(Color(0xFFFFD84D), Color(0xFFFFB800), Color(0xFFB86A00))

private val NightMid get() = if (Look.dueffe) Color(0xFF0B0A08) else Color(0xFF0D1030)

/**
 * Sfondo vivo: tre grandi bagliori che fluttuano lentamente e prendono il colore
 * dei suoni attivi, più un cielo di stelle che brillano.
 */
@Composable
private fun ClassicAuroraBackground(colors: List<Color>, playing: Boolean, modifier: Modifier = Modifier) {
    if (it.chiarella.app.ui.motion.Motion.reduced) {
        androidx.compose.foundation.layout.Box(modifier.then(Modifier.background(
            Brush.verticalGradient(listOf(Palette.Night, NightMid, Palette.NightDeep)))))
        return
    }
    val palette = if (Look.dueffe) GoldColors else if (colors.isEmpty()) IdleColors else List(3) { colors[it % colors.size] }
    val c1 by animateColorAsState(palette[0], tween(1600), label = "a1")
    val c2 by animateColorAsState(palette[1], tween(1800), label = "a2")
    val c3 by animateColorAsState(palette[2], tween(2000), label = "a3")
    // Dueffe: alfa delle nebulose tra 12 e 22%, come da design system.
    val intensity by animateFloatAsState((if (playing) 1f else 0.55f) * (if (Look.dueffe) 0.5f else 1f), tween(1400), label = "intensity")

    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)),
        label = "t",
    )
    val twinkle by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "twinkle",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(5_000), RepeatMode.Reverse),
        label = "breathe",
    )

    val stars = remember {
        val r = Random(7)
        List(55) { Star(r.nextFloat(), r.nextFloat() * 0.75f, 0.5f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f) }
    }

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Palette.Night, NightMid, Palette.NightDeep)))

        val w = size.width
        val h = size.height
        val pulse = if (playing) breathe else 1f
        glow(c1, Offset(w * (0.2f + 0.15f * cos(t)), h * (0.18f + 0.06f * sin(t * 2))), w * 0.85f * pulse, 0.42f * intensity)
        glow(c2, Offset(w * (0.85f + 0.1f * sin(t + 1f)), h * (0.45f + 0.08f * cos(t))), w * 0.75f, 0.32f * intensity)
        glow(c3, Offset(w * (0.35f + 0.2f * sin(t + 2.5f)), h * (0.9f + 0.05f * cos(t * 2))), w * 0.9f * pulse, 0.30f * intensity)

        stars.forEach { s ->
            val a = 0.25f + 0.55f * ((sin(twinkle * s.speed + s.phase) + 1f) / 2f)
            drawCircle((if (Look.dueffe) Color(0xFFFFF0AD) else Color.White).copy(alpha = a * 0.8f), radius = s.size * density, center = Offset(s.x * w, s.y * h))
        }

        // Velo scuro in basso per dare profondità alla barra del player.
        drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Palette.NightDeep.copy(alpha = 0.85f)))
    }
}

private class Star(val x: Float, val y: Float, val size: Float, val phase: Float) {
    val speed = 0.6f + (phase % 1f)
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.45f to color.copy(alpha = alpha * 0.45f),
            1f to Color.Transparent,
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
}

// ------------------------------------------------------------------ stile skeuomorfico

/**
 * Sfondo "mobile in noce": legno lucidato con vignettatura, più tre aloni caldi
 * come la luce di una lampada, che prendono il colore dei suoni attivi e
 * respirano lentamente mentre la musica suona.
 */
@Composable
private fun SkeuoAuroraBackground(colors: List<Color>, playing: Boolean, modifier: Modifier = Modifier) {
    if (it.chiarella.app.ui.motion.Motion.reduced) {
        androidx.compose.foundation.layout.Box(modifier.walnut())
        return
    }
    val palette = if (colors.isEmpty()) LampColors else List(3) { colors[it % colors.size] }
    val c1 by animateColorAsState(palette[0], tween(1600), label = "a1")
    val c2 by animateColorAsState(palette[1], tween(1800), label = "a2")
    val c3 by animateColorAsState(palette[2], tween(2000), label = "a3")
    val intensity by animateFloatAsState(if (playing) 1f else 0.5f, tween(1400), label = "intensity")

    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)),
        label = "t",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(5_000), RepeatMode.Reverse),
        label = "breathe",
    )

    Canvas(modifier.walnut()) {
        val w = size.width
        val h = size.height
        val pulse = if (playing) breathe else 1f
        glow(c1, Offset(w * (0.2f + 0.12f * cos(t)), h * (0.16f + 0.05f * sin(t * 2))), w * 0.8f * pulse, 0.20f * intensity)
        glow(c2, Offset(w * (0.85f + 0.08f * sin(t + 1f)), h * (0.48f + 0.06f * cos(t))), w * 0.7f, 0.14f * intensity)
        glow(c3, Offset(w * (0.35f + 0.15f * sin(t + 2.5f)), h * (0.9f + 0.04f * cos(t * 2))), w * 0.85f * pulse, 0.14f * intensity)
        // Ombra in basso per dare profondità alla console del player.
        drawRect(Brush.verticalGradient(0.65f to Color.Transparent, 1f to Palette.NightDeep.copy(alpha = 0.8f)))
    }
}

private val LampColors = listOf(Color(0xFFFFB04A), Color(0xFFE0782E), Color(0xFFFFD58A))
