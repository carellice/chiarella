package it.chiarella.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import it.chiarella.app.ui.motion.animateFloatAsState
import it.chiarella.app.ui.motion.spring
import it.chiarella.app.ui.motion.tween

/** Stile Metro di Windows Phone: riquadri piatti a tinta unita, testo sottile e minuscolo, nero assoluto. */
object Metro {
    val Tile = Color(0xFF1F1F1F)
    val Magenta = Color(0xFFD80073)
    val Accent = Color(0xFF1BA1E2)

    /** Curva "esponenziale" delle animazioni di Windows Phone: parte veloce e si posa lentamente. */
    val Ease = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
}

/** "Tilt" dei riquadri Metro: alla pressione il riquadro si abbassa e si inclina verso il basso. */
fun Modifier.metroTilt(press: Float): Modifier = graphicsLayer {
    val s = 1f - 0.04f * press
    scaleX = s
    scaleY = s
    rotationX = 8f * press
    cameraDistance = 14f * density
    transformOrigin = TransformOrigin.Center
}

/** Tilt legato alla pressione di [interaction]; negli altri stili non fa nulla. */
@Composable
fun Modifier.metroTilt(interaction: InteractionSource): Modifier {
    if (!Look.metro) return this
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "tilt")
    return metroTilt(press)
}

/** "Swivel" di Windows Phone: il pannello entra ruotando dall'alto come una tenda che si abbassa. */
@Composable
fun Modifier.metroSwivel(): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(420, easing = Metro.Ease)) }
    return graphicsLayer { swivel(p.value) }
}

/** Applica lo swivel con avanzamento [progress] (0 nascosto, 1 a riposo). */
fun androidx.compose.ui.graphics.GraphicsLayerScope.swivel(progress: Float) {
    alpha = progress.coerceIn(0f, 1f)
    rotationX = (1f - progress) * -75f
    transformOrigin = TransformOrigin(0.5f, 0f)
    cameraDistance = 16f * density
}
