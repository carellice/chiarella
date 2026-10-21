package it.chiarella.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.chiarella.app.audio.Sound
import it.chiarella.app.ui.theme.Palette
import kotlin.math.roundToInt

@Composable
fun SoundCard(
    sound: Sound,
    active: Boolean,
    playing: Boolean,
    volume: Float,
    onToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    val on by animateFloatAsState(if (active) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "on")
    val live = active && playing
    val ambience by animateFloatAsState(if (live) 0.35f + 0.65f * volume else 0f, tween(900), label = "ambience")
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier
            .height(178.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Palette.Glass)
            .drawBehind {
                if (on > 0f) {
                    drawRect(
                        Brush.linearGradient(
                            listOf(sound.primary.copy(alpha = 0.55f * on), sound.secondary.copy(alpha = 0.14f * on)),
                            start = Offset.Zero, end = Offset(size.width, size.height),
                        )
                    )
                }
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        lerp(Palette.GlassBorder, sound.secondary.copy(alpha = 0.85f), on),
                        lerp(Palette.GlassBorder, sound.primary.copy(alpha = 0.25f), on),
                    )
                ),
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Switch) {
                haptic.performHapticFeedback(if (active) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onToggle()
            },
    ) {
        AmbienceCanvas(sound.ambience, sound.primary, sound.secondary, ambience, Modifier.matchParentSize())

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(sound, active, on, live, volume)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = active,
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f),
                ) {
                    Text(
                        "${(volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                sound.name,
                style = MaterialTheme.typography.titleMedium,
                color = lerp(Palette.Text.copy(alpha = 0.9f), Color.White, on),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                targetState = active,
                transitionSpec = {
                    (fadeIn(tween(260, 90)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically { -it / 2 }) using SizeTransform(clip = false)
                },
                label = "cardBottom",
            ) { isActive ->
                if (isActive) {
                    VolumeSlider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        primary = sound.primary,
                        secondary = sound.secondary,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                } else {
                    Text(
                        sound.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconBubble(sound: Sound, active: Boolean, on: Float, live: Boolean, volume: Float) {
    // Piccolo "pop" elastico ogni volta che il suono viene acceso o spento.
    val pop = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(active) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        pop.snapTo(0.72f)
        pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 380f))
    }

    val transition = rememberInfiniteTransition(label = "bubble")
    val breath by transition.animateFloat(
        1f, 1.07f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breath",
    )
    val ring by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "ring",
    )
    val liveLevel by animateFloatAsState(if (live) 1f else 0f, tween(600), label = "live")

    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer {
                val s = pop.value * (1f + (breath - 1f) * liveLevel * (0.5f + volume))
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                if (liveLevel > 0f) {
                    // Onda che si propaga dall'icona, come un suono che si diffonde.
                    val r = size.minDimension / 2 * (1f + ring * 0.7f)
                    drawCircle(
                        sound.secondary.copy(alpha = (1f - ring) * 0.55f * liveLevel),
                        radius = r, style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(Color.White.copy(alpha = 0.08f), sound.primary, on),
                        lerp(Color.White.copy(alpha = 0.04f), sound.secondary, on),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            sound.icon,
            contentDescription = sound.name,
            tint = lerp(sound.secondary, Color.White, on),
            modifier = Modifier.size(24.dp),
        )
    }
}
