package it.chiarella.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sounds
import it.chiarella.app.ui.theme.Palette

/** Barra flottante in basso: cosa sta suonando, timer, play/pausa e stop. */
@Composable
fun PlayerBar(
    state: Mixer.State,
    presetName: String?,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onTimer: () -> Unit,
    onVolume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sounds = state.active.mapNotNull { Sounds[it] }
    val now = rememberNow(state.timerActive)
    val remaining = (state.timerEndsAt - now).coerceAtLeast(0)
    val title = presetName ?: when (sounds.size) {
        0 -> "Il tuo mix"
        1 -> sounds[0].name
        else -> "Il tuo mix"
    }
    val subtitle = when {
        state.timerActive -> "Si spegne tra ${formatCountdown(remaining)}"
        state.interrupted -> "In attesa…"
        state.playing -> if (sounds.size == 1) "In riproduzione" else "${sounds.size} suoni in riproduzione"
        else -> "In pausa"
    }
    val accent = sounds.map { it.primary }.ifEmpty { listOf(Palette.Lavender) }
    val accent2 = sounds.getOrNull(1)?.secondary ?: sounds.firstOrNull()?.secondary ?: Palette.Sky
    val shape = RoundedCornerShape(34.dp)

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF21E2444), Color(0xF2141933))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF))), shape)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconStack(sounds, size = 38.dp, max = 2)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            AnimatedContent(
                targetState = title,
                transitionSpec = { (fadeIn() + slideInVertically { it }) togetherWith (fadeOut() + slideOutVertically { -it }) },
                label = "title",
            ) {
                Text(it, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MasterVolumeButton(state.master, accent.first(), onVolume)
        Spacer(Modifier.width(8.dp))
        TimerButton(
            active = state.timerActive,
            progress = if (state.timerDurationMs > 0) remaining.toFloat() / state.timerDurationMs else 0f,
            color = accent2,
            onClick = onTimer,
        )
        Spacer(Modifier.width(8.dp))
        PlayButton(state.playing, accent.first(), accent2, onTogglePlay)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Rounded.Stop, "Ferma tutto", 40.dp, onStop)
    }
}

@Composable
private fun PlayButton(playing: Boolean, c1: Color, c2: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "pp")
    val halo by rememberInfiniteTransition(label = "halo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "haloT",
    )
    val haloLevel by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "haloL")

    Box(
        Modifier
            .size(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (haloLevel > 0f) {
                    drawCircle(
                        c2.copy(alpha = (1f - halo) * 0.5f * haloLevel),
                        radius = size.minDimension / 2 * (1f + halo * 0.45f),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .clickable(interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut())
            },
            label = "playIcon",
        ) { isPlaying ->
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Volume generale: l'icona cambia con il livello e l'anello mostra quanto è alto. */
@Composable
private fun MasterVolumeButton(master: Float, color: Color, onClick: () -> Unit) {
    val shown by animateFloatAsState(master, tween(400), label = "masterRing")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2 + 1.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                // Anello aperto in basso, come una manopola del volume.
                drawArc(
                    color = Color.White.copy(alpha = 0.08f), startAngle = 135f, sweepAngle = 270f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (shown > 0.005f) {
                    drawArc(
                        color = color, startAngle = 135f, sweepAngle = 270f * shown, useCenter = false,
                        topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when {
                master < 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                master < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            },
            contentDescription = "Volume generale ${(master * 100).toInt()}%",
            tint = if (master < 0.01f) Palette.TextMuted else Palette.Text,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TimerButton(active: Boolean, progress: Float, color: Color, onClick: () -> Unit) {
    val shown by animateFloatAsState(if (active) progress else 0f, tween(700), label = "timerRing")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2 + 1.dp.toPx()
                drawArc(
                    color = Color.White.copy(alpha = 0.08f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(stroke),
                )
                if (shown > 0f) {
                    drawArc(
                        color = color, startAngle = -90f, sweepAngle = 360f * shown, useCenter = false,
                        topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Bedtime,
            contentDescription = "Timer",
            tint = if (active) color else Palette.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
    tint: Color = Palette.TextMuted,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
