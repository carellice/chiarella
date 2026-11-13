package it.chiarella.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sounds
import it.chiarella.app.ui.motion.animateFloat
import it.chiarella.app.ui.motion.animateFloatAsState
import it.chiarella.app.ui.motion.rememberInfiniteTransition
import it.chiarella.app.ui.motion.spring
import it.chiarella.app.ui.motion.tween
import it.chiarella.app.ui.theme.Look
import it.chiarella.app.ui.theme.Palette
import it.chiarella.app.ui.theme.Skeuo
import it.chiarella.app.ui.theme.brushedMetal
import it.chiarella.app.ui.theme.glossyCap
import it.chiarella.app.ui.theme.lcd
import it.chiarella.app.ui.theme.metalCap
import it.chiarella.app.ui.theme.screw
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
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
    if (Look.nothing) NothingPlayerBar(state = state, presetName = presetName, onTogglePlay = onTogglePlay, onStop = onStop, onTimer = onTimer, onVolume = onVolume, modifier = modifier)
    else if (Look.metro) MetroPlayerBar(state = state, presetName = presetName, onTogglePlay = onTogglePlay, onStop = onStop, onTimer = onTimer, onVolume = onVolume, modifier = modifier)
    else if (Look.skeuo) SkeuoPlayerBar(state = state, presetName = presetName, onTogglePlay = onTogglePlay, onStop = onStop, onTimer = onTimer, onVolume = onVolume, modifier = modifier) else ClassicPlayerBar(state = state, presetName = presetName, onTogglePlay = onTogglePlay, onStop = onStop, onTimer = onTimer, onVolume = onVolume, modifier = modifier)
}

@Composable
fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
) {
    // Senza colore esplicito ogni stile usa il suo: grigio-lavanda o avorio sul metallo.
    if (Look.nothing) NothingRoundIconButton(icon, description, size, onClick, tint.takeOrElse { it.chiarella.app.ui.theme.Nothing.TextPrimary })
    else if (Look.metro) MetroRoundIconButton(icon, description, size, onClick, tint.takeOrElse { Palette.Text })
    else if (Look.skeuo) SkeuoRoundIconButton(icon, description, size, onClick, tint.takeOrElse { Color(0xFFEDE3D2) })
    else ClassicRoundIconButton(icon, description, size, onClick, tint.takeOrElse { Palette.TextMuted })
}

/** Barra flottante in basso: cosa sta suonando, timer, play/pausa e stop. */
@Composable
private fun ClassicPlayerBar(
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
    // Dueffe: comandi sempre in oro, un solo accento per schermata.
    val accent = if (Look.dueffe) listOf(Palette.Lavender) else sounds.map { it.primary }.ifEmpty { listOf(Palette.Lavender) }
    val accent2 = if (Look.dueffe) Palette.Sky else sounds.getOrNull(1)?.secondary ?: sounds.firstOrNull()?.secondary ?: Palette.Sky
    val shape = RoundedCornerShape(if (Look.dueffe) 28.dp else 34.dp)

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (Look.dueffe) Modifier.background(Color(0xF0080705)).border(1.dp, Palette.GlassBorder, shape)
                else Modifier.background(Brush.verticalGradient(listOf(Color(0xF21E2444), Color(0xF2141933))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF))), shape)
            )
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
        ClassicMasterVolumeButton(state.master, accent.first(), onVolume)
        Spacer(Modifier.width(8.dp))
        ClassicTimerButton(
            active = state.timerActive,
            progress = if (state.timerDurationMs > 0) remaining.toFloat() / state.timerDurationMs else 0f,
            color = accent2,
            onClick = onTimer,
        )
        Spacer(Modifier.width(8.dp))
        ClassicPlayButton(state.playing, accent.first(), accent2, onTogglePlay)
        Spacer(Modifier.width(8.dp))
        ClassicRoundIconButton(Icons.Rounded.Stop, "Ferma tutto", 40.dp, onStop)
    }
}

@Composable
private fun ClassicPlayButton(playing: Boolean, c1: Color, c2: Color, onClick: () -> Unit) {
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
                tint = Palette.OnAccent,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Volume generale: l'icona cambia con il livello e l'anello mostra quanto è alto. */
@Composable
private fun ClassicMasterVolumeButton(master: Float, color: Color, onClick: () -> Unit) {
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
private fun ClassicTimerButton(active: Boolean, progress: Float, color: Color, onClick: () -> Unit) {
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
private fun ClassicRoundIconButton(
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

// ------------------------------------------------------------------ stile skeuomorfico

/** Barra flottante in basso: cosa sta suonando, timer, play/pausa e stop. */
@Composable
private fun SkeuoPlayerBar(
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
    val shape = RoundedCornerShape(26.dp)

    // Console in alluminio spazzolato con display ambra e tasti fisici.
    Row(
        modifier
            .fillMaxWidth()
            .brushedMetal(shape, elevation = 18.dp)
            .drawBehind {
                val r = 3.dp.toPx()
                screw(Offset(7.dp.toPx(), size.height / 2), r, 20f)
                screw(Offset(size.width - 7.dp.toPx(), size.height / 2), r, 70f)
            }
            .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .padding(horizontal = 8.dp)
                .weight(1f)
                .lcd(RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            AnimatedContent(
                targetState = title,
                transitionSpec = { (fadeIn() + slideInVertically { it }) togetherWith (fadeOut() + slideOutVertically { -it }) },
                label = "title",
            ) {
                Text(it, style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, shadow = AmberGlow), color = Skeuo.Amber, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = (-0.3).sp, shadow = AmberGlow), color = Skeuo.Amber.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SkeuoMasterVolumeButton(state.master, accent.first(), onVolume)
        Spacer(Modifier.width(8.dp))
        SkeuoTimerButton(
            active = state.timerActive,
            progress = if (state.timerDurationMs > 0) remaining.toFloat() / state.timerDurationMs else 0f,
            color = accent2,
            onClick = onTimer,
        )
        Spacer(Modifier.width(8.dp))
        SkeuoPlayButton(state.playing, accent.first(), accent2, onTogglePlay)
        Spacer(Modifier.width(8.dp))
        SkeuoRoundIconButton(Icons.Rounded.Stop, "Ferma tutto", 40.dp, onStop)
    }
}

@Composable
private fun SkeuoPlayButton(playing: Boolean, c1: Color, c2: Color, onClick: () -> Unit) {
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
            .glossyCap(c1)
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
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Volume generale: l'icona cambia con il livello e l'anello mostra quanto è alto. */
@Composable
private fun SkeuoMasterVolumeButton(master: Float, color: Color, onClick: () -> Unit) {
    val shown by animateFloatAsState(master, tween(400), label = "masterRing")
    Box(
        Modifier
            .size(44.dp)
            .metalCap()
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2 + 1.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                // Anello aperto in basso, come una manopola del volume.
                drawArc(
                    color = Color.Black.copy(alpha = 0.35f), startAngle = 135f, sweepAngle = 270f, useCenter = false,
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
            tint = if (master < 0.01f) Palette.TextMuted else Color(0xFFEDE3D2),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SkeuoTimerButton(active: Boolean, progress: Float, color: Color, onClick: () -> Unit) {
    val shown by animateFloatAsState(if (active) progress else 0f, tween(700), label = "timerRing")
    Box(
        Modifier
            .size(44.dp)
            .metalCap()
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2 + 1.dp.toPx()
                drawArc(
                    color = Color.Black.copy(alpha = 0.35f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
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
            tint = if (active) color else Color(0xFFEDE3D2),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SkeuoRoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
    tint: Color = Color(0xFFEDE3D2),
) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .metalCap()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

private val AmberGlow = Shadow(Color(0xCCFF9A1F), androidx.compose.ui.geometry.Offset.Zero, 12f)

// ------------------------------------------------------------------ stile Metro

/** Application bar di Windows Phone: fascia piatta a tutta larghezza con pulsanti tondi a contorno ed etichette. */
@Composable
private fun MetroPlayerBar(
    state: Mixer.State,
    presetName: String?,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onTimer: () -> Unit,
    onVolume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val sounds = state.active.mapNotNull { Sounds[it] }
    val now = rememberNow(state.timerActive)
    val remaining = (state.timerEndsAt - now).coerceAtLeast(0)
    val title = presetName ?: if (sounds.size == 1) sounds[0].name else "il tuo mix"
    val subtitle = when {
        state.interrupted -> "in attesa…"
        state.playing -> if (sounds.size == 1) "in riproduzione" else "${sounds.size} suoni in riproduzione"
        else -> "in pausa"
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .navigationBarsPadding()
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Text(
            title.lowercase(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.75f),
            color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MetroBarButton(
                when {
                    state.master < 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                    state.master < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                "volume ${(state.master * 100).toInt()}", onClick = onVolume,
            )
            MetroBarButton(Icons.Rounded.Bedtime, if (state.timerActive) formatCountdown(remaining) else "timer", lit = state.timerActive, onClick = onTimer)
            MetroBarButton(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.playing) "pausa" else "riproduci", lit = true,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onTogglePlay()
            }
            MetroBarButton(Icons.Rounded.Stop, "ferma", onClick = onStop)
        }
    }
}

/** Pulsante della application bar: cerchio a contorno, pieno quando [lit]. */
@Composable
private fun MetroBarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, lit: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (lit) Palette.Text else Color.Transparent)
                .border(2.dp, Palette.Text, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (lit) Color.Black else Palette.Text, modifier = Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = Palette.TextMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MetroRoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
    tint: Color,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, tint, CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
