package it.chiarella.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import it.chiarella.app.audio.Ambience
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private class Particle(r: Random) {
    val x = r.nextFloat()
    val y = r.nextFloat()
    val speed = 0.5f + r.nextFloat()
    val size = r.nextFloat()
    val phase = r.nextFloat() * 6.283f
}

/**
 * Piccola scena animata disegnata dentro ogni card: pioggia, onde, braci, lucciole...
 * [intensity] (0..1) ne regola la presenza, ad esempio in base al volume.
 */
@Composable
fun AmbienceCanvas(
    ambience: Ambience,
    primary: Color,
    secondary: Color,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    var time by remember { mutableFloatStateOf(0f) }
    val running = intensity > 0.01f
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it } - (time * 1e9f).toLong()
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    val particles = remember(ambience) {
        val r = Random(ambience.ordinal * 31 + 5)
        List(30) { Particle(r) }
    }
    if (!running) return

    Canvas(modifier) {
        val t = time
        val a = intensity.coerceIn(0f, 1f)
        when (ambience) {
            Ambience.RAIN -> rain(particles, t, secondary, a, fast = false)
            Ambience.STORM -> {
                rain(particles, t, secondary, a, fast = true)
                val cycle = t % 6.5f
                val flash = when {
                    cycle < 0.12f -> 1f - cycle / 0.12f
                    cycle in 0.22f..0.34f -> 0.6f * (1f - (cycle - 0.22f) / 0.12f)
                    else -> 0f
                }
                if (flash > 0f) drawRect(secondary.copy(alpha = 0.28f * flash * a))
            }
            Ambience.HEAT -> heat(t, primary, secondary, a)
            Ambience.WAVES -> waves(t, primary, secondary, a)
            Ambience.LEAVES -> leaves(particles, t, primary, secondary, a)
            Ambience.FALL -> fall(particles, t, secondary, a)
            Ambience.RIPPLE -> ripple(particles, t, secondary, a)
            Ambience.EMBERS -> embers(particles, t, primary, secondary, a)
            Ambience.WIND -> wind(particles, t, secondary, a)
            Ambience.FIREFLIES -> fireflies(particles, t, secondary, a)
            Ambience.AIRFLOW -> airflow(particles, t, primary, secondary, a)
        }
    }
}

private fun DrawScope.rain(ps: List<Particle>, t: Float, color: Color, a: Float, fast: Boolean) {
    val w = size.width
    val h = size.height
    val len = h * if (fast) 0.16f else 0.1f
    val speed = if (fast) 1.1f else 0.55f
    ps.forEach { p ->
        val y = ((p.y + t * speed * p.speed) % 1.2f) - 0.1f
        val x = p.x * w * 1.1f - y * w * 0.08f
        val start = Offset(x, y * h)
        drawLine(
            brush = Brush.linearGradient(listOf(Color.Transparent, color.copy(alpha = 0.55f * a)), start, start + Offset(-len * 0.12f, len)),
            start = start, end = start + Offset(-len * 0.12f, len),
            strokeWidth = (1f + p.size) * density, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.heat(t: Float, primary: Color, secondary: Color, a: Float) {
    val w = size.width
    val h = size.height
    val glowAlpha = (0.28f + 0.08f * sin(t * 1.7f) + 0.04f * sin(t * 4.3f)) * a
    drawCircle(
        Brush.radialGradient(listOf(secondary.copy(alpha = glowAlpha), Color.Transparent), Offset(w / 2, h * 1.05f), w * 0.8f),
        radius = w * 0.8f, center = Offset(w / 2, h * 1.05f),
    )
    repeat(4) { i ->
        val progress = (t * 0.09f + i / 4f) % 1f
        val y = h * (1f - progress)
        val path = Path()
        var x = 0f
        while (x <= w) {
            val yy = y + sin(x / w * 2 * PI.toFloat() * 1.5f + t * 2f + i) * 5f * density
            if (x == 0f) path.moveTo(x, yy) else path.lineTo(x, yy)
            x += 6f
        }
        val alpha = sin(progress * PI.toFloat()) * 0.35f * a
        drawPath(path, lerp(primary, secondary, progress).copy(alpha = alpha), style = Stroke(1.6f * density))
    }
}

private fun DrawScope.waves(t: Float, primary: Color, secondary: Color, a: Float) {
    val w = size.width
    val h = size.height
    repeat(3) { i ->
        val base = h * (0.62f + i * 0.12f)
        val amp = (7f - i * 1.5f) * density
        val path = Path().apply {
            moveTo(0f, h)
            var x = 0f
            while (x <= w + 6f) {
                lineTo(x, base + sin(x / w * 2 * PI.toFloat() + t * (0.9f + i * 0.35f) + i * 1.7f) * amp)
                x += 6f
            }
            lineTo(w, h)
            close()
        }
        drawPath(path, lerp(secondary, primary, i / 2f).copy(alpha = (0.16f + i * 0.06f) * a))
    }
}

private fun DrawScope.leaves(ps: List<Particle>, t: Float, primary: Color, secondary: Color, a: Float) {
    val w = size.width
    val h = size.height
    ps.take(16).forEachIndexed { i, p ->
        val y = ((p.y + t * 0.06f * p.speed) % 1.15f) - 0.08f
        val x = p.x + sin(t * 0.8f * p.speed + p.phase) * 0.06f
        val r = (2f + p.size * 2.5f) * density
        val color = if (i % 2 == 0) primary else secondary
        drawOval(
            color.copy(alpha = 0.55f * a),
            topLeft = Offset(x * w - r, y * h - r * 0.6f),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 1.2f),
        )
    }
    // Raggi di luce tra le fronde.
    val ray = 0.08f + 0.05f * sin(t * 0.5f)
    drawRect(Brush.linearGradient(listOf(secondary.copy(alpha = ray * a), Color.Transparent), Offset(w, 0f), Offset(w * 0.3f, h)))
}

private fun DrawScope.fall(ps: List<Particle>, t: Float, color: Color, a: Float) {
    val w = size.width
    val h = size.height
    ps.forEach { p ->
        val y = ((p.y + t * 1.3f * p.speed) % 1.3f) - 0.3f
        val x = w * (0.2f + p.x * 0.6f)
        val len = h * (0.18f + p.size * 0.2f)
        drawLine(
            Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.5f * a)), y * h, y * h + len),
            Offset(x, y * h), Offset(x, y * h + len),
            strokeWidth = (1f + p.size * 1.5f) * density, cap = StrokeCap.Round,
        )
    }
    val mist = (0.22f + 0.06f * sin(t * 2.2f)) * a
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = mist), Color.Transparent), Offset(w / 2, h), w * 0.6f),
        radius = w * 0.6f, center = Offset(w / 2, h),
    )
}

private fun DrawScope.ripple(ps: List<Particle>, t: Float, color: Color, a: Float) {
    val w = size.width
    val h = size.height
    repeat(5) { i ->
        val base = h * (0.45f + i * 0.11f)
        val path = Path()
        var x = 0f
        while (x <= w) {
            val y = base + sin(x / w * 4f * PI.toFloat() - t * (1.4f + i * 0.2f) + i) * 3f * density
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 6f
        }
        drawPath(path, color.copy(alpha = (0.14f + 0.05f * i) * a), style = Stroke(1.4f * density, cap = StrokeCap.Round))
    }
    ps.take(8).forEach { p ->
        val sparkle = abs(sin(t * 1.5f * p.speed + p.phase))
        drawCircle(color.copy(alpha = sparkle * 0.6f * a), (1f + p.size) * density, Offset(p.x * w, h * (0.45f + p.y * 0.5f)))
    }
}

private fun DrawScope.embers(ps: List<Particle>, t: Float, primary: Color, secondary: Color, a: Float) {
    val w = size.width
    val h = size.height
    val flicker = 0.3f + 0.08f * sin(t * 7f) + 0.06f * sin(t * 13f + 1f)
    drawCircle(
        Brush.radialGradient(listOf(primary.copy(alpha = flicker * a), Color.Transparent), Offset(w / 2, h * 1.1f), w * 0.75f),
        radius = w * 0.75f, center = Offset(w / 2, h * 1.1f),
    )
    ps.take(20).forEach { p ->
        val progress = (p.y + t * 0.22f * p.speed) % 1f
        val y = h * (1.05f - progress)
        val x = w * (0.3f + p.x * 0.4f) + sin(t * 2f * p.speed + p.phase) * 10f * density * progress
        val r = (2.4f * (1f - progress) + 0.6f) * density
        drawCircle(lerp(secondary, primary, progress).copy(alpha = (1f - progress) * 0.85f * a), r, Offset(x, y))
    }
}

private fun DrawScope.wind(ps: List<Particle>, t: Float, color: Color, a: Float) {
    val w = size.width
    val h = size.height
    ps.take(8).forEach { p ->
        val x = (((p.x + t * 0.25f * p.speed) % 1.6f) - 0.5f) * w
        val y = h * (0.15f + p.y * 0.75f)
        val len = w * (0.35f + p.size * 0.3f)
        val path = Path().apply {
            moveTo(x, y)
            quadraticTo(x + len / 2, y - (6f + 8f * sin(t + p.phase)) * density, x + len, y)
        }
        drawPath(
            path,
            Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = 0.45f * a), Color.Transparent), x, x + len),
            style = Stroke((1f + p.size) * density, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.fireflies(ps: List<Particle>, t: Float, color: Color, a: Float) {
    val w = size.width
    val h = size.height
    ps.take(12).forEach { p ->
        val x = w * (p.x + 0.05f * sin(t * 0.4f * p.speed + p.phase))
        val y = h * (p.y + 0.06f * sin(t * 0.3f * p.speed + p.phase * 2))
        val glow = ((sin(t * 1.4f * p.speed + p.phase) + 1f) / 2f)
        val alpha = glow * glow * a
        val r = 10f * density
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.45f * alpha), Color.Transparent), Offset(x, y), r),
            r, Offset(x, y),
        )
        drawCircle(color.copy(alpha = alpha), 1.6f * density, Offset(x, y))
    }
}

/** Getto d'aria calda: un cono che si allarga da sinistra, con filamenti veloci e un bagliore tiepido. */
private fun DrawScope.airflow(ps: List<Particle>, t: Float, primary: Color, secondary: Color, a: Float) {
    val w = size.width
    val h = size.height
    val origin = Offset(-w * 0.05f, h * 0.42f)
    val warm = (0.24f + 0.05f * sin(t * 9f) + 0.03f * sin(t * 23f)) * a
    drawCircle(
        Brush.radialGradient(listOf(primary.copy(alpha = warm), Color.Transparent), origin, w * 0.9f),
        radius = w * 0.9f, center = origin,
    )
    ps.take(22).forEach { p ->
        val progress = (p.x + t * 1.6f * p.speed) % 1f
        val spread = (p.y - 0.5f) * 0.9f * progress
        val x = origin.x + progress * w * 1.15f
        val y = origin.y + spread * h + sin(t * 14f + p.phase) * 1.5f * density
        val len = w * (0.08f + 0.1f * p.size) * (0.5f + progress)
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, lerp(secondary, primary, progress).copy(alpha = (1f - progress) * 0.7f * a)), x - len, x),
            Offset(x - len, y - spread * len * 0.3f), Offset(x, y),
            strokeWidth = (1f + p.size) * density, cap = StrokeCap.Round,
        )
    }
}
