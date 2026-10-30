package it.chiarella.app.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat as coreAnimateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Solo le animazioni dell'interfaccia: audio e attese funzionali usano i loro tempi normali. */
object Motion {
    var reduced by mutableStateOf(false)
}

fun <T> tween(durationMillis: Int = 300, delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing): TweenSpec<T> = androidx.compose.animation.core.tween(
    if (Motion.reduced) 1 else durationMillis, if (Motion.reduced) 0 else delayMillis, easing)

fun <T> spring(dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium, visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
    if (Motion.reduced) androidx.compose.animation.core.snap()
    else androidx.compose.animation.core.spring(dampingRatio, stiffness, visibilityThreshold)

class DecorativeTransition internal constructor(internal val delegate: InfiniteTransition?)

@Composable
fun rememberInfiniteTransition(label: String = "decorative"): DecorativeTransition {
    // Non si crea alcun clock infinito in modalità leggera.
    val delegate = if (Motion.reduced) null else androidx.compose.animation.core.rememberInfiniteTransition(label)
    return remember(delegate) { DecorativeTransition(delegate) }
}

@Composable
fun DecorativeTransition.animateFloat(initialValue: Float, targetValue: Float,
    animationSpec: InfiniteRepeatableSpec<Float>, label: String = "decorative"): State<Float> =
    delegate?.coreAnimateFloat(initialValue, targetValue, animationSpec, label)
        ?: rememberUpdatedState(initialValue)

@Composable
fun animateFloatAsState(targetValue: Float, animationSpec: AnimationSpec<Float> = spring(),
    visibilityThreshold: Float = 0.01f, label: String = "value", finishedListener: ((Float) -> Unit)? = null): State<Float> =
    if (Motion.reduced) rememberUpdatedState(targetValue)
    else androidx.compose.animation.core.animateFloatAsState(targetValue, animationSpec, visibilityThreshold, label, finishedListener)

@Composable
fun animateColorAsState(targetValue: Color, animationSpec: AnimationSpec<Color> = spring(),
    label: String = "color", finishedListener: ((Color) -> Unit)? = null): State<Color> =
    if (Motion.reduced) rememberUpdatedState(targetValue)
    else androidx.compose.animation.animateColorAsState(targetValue, animationSpec, label, finishedListener)
