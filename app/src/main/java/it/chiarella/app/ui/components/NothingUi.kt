package it.chiarella.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sound
import it.chiarella.app.audio.Sounds
import it.chiarella.app.ui.motion.animateColorAsState
import it.chiarella.app.ui.motion.animateFloat
import it.chiarella.app.ui.motion.animateFloatAsState
import it.chiarella.app.ui.motion.rememberInfiniteTransition
import it.chiarella.app.ui.motion.tween
import it.chiarella.app.ui.theme.Nothing
import kotlin.math.roundToInt
import kotlin.math.sin

/** Spia rossa: l'unico accento di colore, acceso solo su ciò che sta suonando. */
@Composable
fun NothingDot(lit: Boolean, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    val a by animateFloatAsState(if (lit) 1f else 0f, tween(200, easing = Nothing.Ease), label = "dot")
    Box(modifier.size(size).clip(CircleShape).background(Nothing.Accent.copy(alpha = a)))
}

/** Misuratore a matrice di punti: colonne che salgono e scendono mentre il suono è vivo. */
@Composable
fun NothingDotMeter(live: Boolean, level: Float, modifier: Modifier = Modifier, columns: Int = 6, rows: Int = 4, seed: Int = 0) {
    val t by rememberInfiniteTransition(label = "meter").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val on by animateFloatAsState(if (live) 1f else 0f, tween(300, easing = Nothing.Ease), label = "meterOn")
    Box(modifier.drawBehind {
        val step = size.height / rows
        val r = step * 0.22f
        for (c in 0 until columns) {
            val wave = 0.5f + 0.5f * sin(t * (1 + c % 3) + c * 1.7f + seed)
            val lit = ((0.25f + 0.75f * level) * wave * on * rows).roundToInt()
            for (row in 0 until rows) {
                val color = if (rows - row <= lit) Nothing.TextDisplay else Nothing.BorderVisible
                drawCircle(color, r, Offset(step * (c + 0.5f), step * (row + 0.5f)))
            }
        }
    })
}

/** Barra a segmenti discreti, firma visiva di Nothing: tocco o trascinamento impostano il valore. */
@Composable
fun NothingSegmentedSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, segments: Int = 20) {
    val haptic = LocalHapticFeedback.current
    val onChange by rememberUpdatedState(onValueChange)
    var width by remember { mutableIntStateOf(1) }
    var lastStep by remember { mutableIntStateOf((value * segments).roundToInt()) }

    fun emit(x: Float) {
        val v = (x / width).coerceIn(0f, 1f)
        val step = (v * segments).roundToInt()
        if (step != lastStep) {
            lastStep = step
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        onChange(v)
    }

    Box(
        modifier
            .height(28.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics {
                contentDescription = "Volume"
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, segments - 1)
                setProgress { onChange(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) { detectTapGestures { emit(it.x) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    emit(change.position.x)
                }
            }
            .drawBehind {
                val gap = 2.dp.toPx()
                val h = 10.dp.toPx()
                val top = (size.height - h) / 2
                val w = (size.width - gap * (segments - 1)) / segments
                val filled = (value * segments).roundToInt()
                for (i in 0 until segments) {
                    drawRect(
                        if (i < filled) Nothing.TextDisplay else Nothing.Border,
                        Offset(i * (w + gap), top), Size(w, h),
                    )
                }
            },
    )
}

/** Card del suono: superficie piatta, bordo che si accende di bianco, dati in Space Mono. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NothingSoundCard(
    sound: Sound,
    active: Boolean,
    playing: Boolean,
    volume: Float,
    onToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val live = active && playing
    val shape = RoundedCornerShape(16.dp)
    val border by animateColorAsState(
        when {
            active -> Nothing.TextDisplay
            pressed -> Nothing.TextDisabled
            else -> Nothing.Border
        },
        tween(220, easing = Nothing.Ease), label = "border",
    )
    val ink by animateColorAsState(if (active) Nothing.TextDisplay else Nothing.TextSecondary, tween(220, easing = Nothing.Ease), label = "ink")

    Box(
        modifier
            .height(178.dp)
            .clip(shape)
            .background(Nothing.Surface)
            .border(1.dp, border, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onLongClickLabel = "Dettagli",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDetails()
                },
            ) {
                haptic.performHapticFeedback(if (active) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onToggle()
            },
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(sound.icon, contentDescription = sound.name, tint = ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NothingDot(live)
                    Spacer(Modifier.width(6.dp))
                    Text(if (active) "ON" else "OFF", style = Nothing.Label, color = if (active) Nothing.TextDisplay else Nothing.TextDisabled)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                sound.name,
                style = MaterialTheme.typography.titleMedium,
                color = lerp(Nothing.TextPrimary, Nothing.TextDisplay, if (active) 1f else 0f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                targetState = active,
                transitionSpec = { fadeIn(tween(220, 80, Nothing.Ease)) togetherWith fadeOut(tween(150, easing = Nothing.Ease)) },
                label = "cardBottom",
            ) { isActive ->
                if (isActive) {
                    Column {
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("VOL", style = Nothing.Label, color = Nothing.TextSecondary)
                            Spacer(Modifier.weight(1f))
                            Text("%03d".format((volume * 100).roundToInt()), style = Nothing.Data, color = Nothing.TextDisplay)
                        }
                        NothingSegmentedSlider(volume, onVolumeChange, Modifier.fillMaxWidth(), segments = 12)
                    }
                } else {
                    Text(
                        sound.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = Nothing.TextDisabled,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (live) NothingDotMeter(true, volume, Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(width = 36.dp, height = 24.dp), seed = sound.id.hashCode())
    }
}

/** Barra del player: pannello piatto con stato in maiuscoletto mono, play bianco a pillola. */
@Composable
internal fun NothingPlayerBar(
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
    val title = presetName ?: if (sounds.size == 1) sounds[0].name else "Il tuo mix"
    val status = when {
        state.interrupted -> "IN ATTESA"
        state.playing -> if (sounds.size == 1) "IN RIPRODUZIONE" else "${sounds.size} SUONI"
        else -> "IN PAUSA"
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clip(shape)
            .background(Nothing.Surface)
            .border(1.dp, Nothing.BorderVisible, shape)
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NothingDot(state.playing && !state.interrupted)
            Spacer(Modifier.width(8.dp))
            Text(status, style = Nothing.Label, color = Nothing.TextSecondary)
            Spacer(Modifier.weight(1f))
            if (state.timerActive) {
                Text("SPEGNIMENTO ", style = Nothing.Label, color = Nothing.TextDisabled)
                Text(formatCountdown(remaining), style = Nothing.Data, color = Nothing.TextDisplay)
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, style = MaterialTheme.typography.titleLarge, color = Nothing.TextDisplay,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NothingRoundIconButton(
                    when {
                        state.master < 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                        state.master < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    "Volume generale ${(state.master * 100).roundToInt()}%", 44.dp, onVolume, Nothing.TextPrimary,
                )
                NothingRoundIconButton(Icons.Rounded.Bedtime, "Timer", 44.dp, onTimer, if (state.timerActive) Nothing.TextDisplay else Nothing.TextPrimary, lit = state.timerActive)
                NothingRoundIconButton(Icons.Rounded.Stop, "Ferma", 44.dp, onStop, Nothing.TextPrimary)
                // Play: il cerchio bianco pieno, l'elemento primario della barra.
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Nothing.TextDisplay)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onTogglePlay()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(state.playing, transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) }, label = "play") { p ->
                        Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p) "Pausa" else "Riproduci", tint = Nothing.Black, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

/** Tasto tondo a contorno sottile; [lit] lo inverte (bianco pieno) come un interruttore acceso. */
@Composable
internal fun NothingRoundIconButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit, tint: Color, lit: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (lit) Nothing.TextDisplay else Nothing.Black.copy(alpha = 0f), tween(200, easing = Nothing.Ease), label = "bg")
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (lit) Nothing.TextDisplay else Nothing.BorderVisible, CircleShape)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (lit) Nothing.Black else tint, modifier = Modifier.size(size * 0.45f))
    }
}
