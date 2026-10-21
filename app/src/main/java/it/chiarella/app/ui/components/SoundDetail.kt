package it.chiarella.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import it.chiarella.app.audio.Sound
import it.chiarella.app.audio.SoundInfos
import it.chiarella.app.audio.Sounds
import it.chiarella.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Dettaglio di un suono, aperto tenendo premuta la sua card: una finestra che "sboccia" dal centro,
 * con la scena animata del suono, la descrizione, il volume e i suoni con cui si abbina bene.
 */
@Composable
fun SoundDetailDialog(
    sound: Sound,
    active: Boolean,
    playing: Boolean,
    volume: Float,
    isActive: (String) -> Boolean,
    onToggle: (String) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val appear = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 220f)) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            appear.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Lo sfondo scuro lo disegniamo noi, così entra ed esce insieme alla finestra.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        BackHandler(onBack = ::close)
        val progress = appear.value
        val distance = with(LocalDensity.current) { 90.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.NightDeep.copy(alpha = 0.72f * progress.coerceIn(0f, 1f)))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = ::close),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(32.dp)
            Column(
                Modifier
                    .padding(20.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        val s = 0.82f + 0.18f * progress
                        scaleX = s
                        scaleY = s
                        translationY = (1f - progress) * distance
                        rotationX = (1f - progress) * 12f
                        cameraDistance = 16 * density
                    }
                    .clip(shape)
                    .background(Palette.Surface)
                    .border(1.dp, Brush.verticalGradient(listOf(sound.secondary.copy(alpha = 0.6f), Palette.GlassBorder)), shape)
                    // I tocchi dentro la finestra non devono chiuderla.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState()),
            ) {
                DetailHeader(sound, active, playing, volume, progress, onClose = ::close)

                Column(Modifier.padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 22.dp)) {
                    val info = SoundInfos[sound.id]
                    VolumeBox(sound, active, volume, onVolumeChange, Modifier.stagger(progress, 1))
                    Spacer(Modifier.height(18.dp))
                    Text(
                        info.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextMuted,
                        modifier = Modifier.stagger(progress, 2),
                    )

                    Spacer(Modifier.height(18.dp))
                    SectionLabel("IDEALE PER", Modifier.stagger(progress, 3))
                    Row(Modifier.stagger(progress, 3), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        info.moods.forEach { mood ->
                            Text(
                                "${mood.emoji}  ${mood.label}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.Text,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(sound.primary.copy(alpha = 0.18f))
                                    .border(1.dp, sound.secondary.copy(alpha = 0.35f), CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    SectionLabel("SI ABBINA BENE CON", Modifier.stagger(progress, 4))
                    Row(Modifier.stagger(progress, 4), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        info.pairs.mapNotNull { Sounds[it] }.forEach { other ->
                            PairChip(other, isActive(other.id), Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                onToggle(other.id)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    ToggleButton(sound, active, Modifier.stagger(progress, 5)) {
                        haptic.performHapticFeedback(if (active) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                        onToggle(sound.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(sound: Sound, active: Boolean, playing: Boolean, volume: Float, progress: Float, onClose: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "detailHeader")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wave")
    val breath by transition.animateFloat(0.96f, 1.06f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breath")
    val live = active && playing
    // Nel dettaglio la scena si vede sempre; se il suono suona davvero è più intensa.
    val intensity by animateFloatAsState(if (live) 0.55f + 0.45f * volume else 0.45f, tween(800), label = "detailScene")
    val glow by animateColorAsState(if (live) sound.secondary else sound.primary, tween(600), label = "glow")

    Box(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .background(
                Brush.linearGradient(listOf(sound.primary.copy(alpha = 0.75f), sound.secondary.copy(alpha = 0.25f), Palette.Surface))
            ),
    ) {
        AmbienceCanvas(sound.ambience, sound.primary, sound.secondary, intensity, Modifier.matchParentSize())
        // Velo in basso per raccordare la scena con il resto della finestra.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Palette.Surface)))

        Box(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = 44.dp)
                .size(96.dp)
                .graphicsLayer {
                    // L'icona arriva un attimo dopo la finestra, con un rimbalzo.
                    val local = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1.2f)
                    val s = local * breath
                    scaleX = s
                    scaleY = s
                    rotationZ = (1f - local.coerceAtMost(1f)) * -25f
                }
                .drawBehind {
                    // Onde che si allargano dall'icona, come il suono che si diffonde.
                    repeat(3) { i ->
                        val t = (wave + i / 3f) % 1f
                        drawCircle(
                            glow.copy(alpha = (1f - t) * 0.5f),
                            radius = size.minDimension / 2 * (1f + t * 0.9f),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(sound.primary, sound.secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(sound.icon, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .stagger(progress, 0),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(sound.name, style = MaterialTheme.typography.headlineSmall, color = Palette.Text, textAlign = TextAlign.Center)
            StatePill(sound, active, live, Modifier.padding(top = 6.dp))
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Chiudi", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** Stato del suono ben visibile: acceso (con equalizzatore) o spento. */
@Composable
private fun StatePill(sound: Sound, active: Boolean, live: Boolean, modifier: Modifier = Modifier) {
    val on by animateFloatAsState(if (active) 1f else 0f, tween(400), label = "statePill")
    Row(
        modifier
            .clip(CircleShape)
            .background(lerp(Color.Black.copy(alpha = 0.18f), sound.primary.copy(alpha = 0.6f), on))
            .border(1.dp, lerp(Palette.GlassBorder, sound.secondary.copy(alpha = 0.85f), on), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Equalizer(live, Color.White, Modifier.size(width = 14.dp, height = 11.dp))
            Spacer(Modifier.width(8.dp))
        }
        AnimatedContent(
            targetState = when {
                live -> "Acceso · in riproduzione"
                active -> "Acceso · in pausa"
                else -> "Spento"
            },
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { it }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it }) },
            label = "stateText",
        ) { text ->
            Text(text, style = MaterialTheme.typography.labelMedium, color = lerp(Palette.TextMuted, Color.White, on))
        }
    }
}

/** Volume sempre a portata: a suono spento si sceglie il volume con cui ripartirà. */
@Composable
private fun VolumeBox(sound: Sound, active: Boolean, volume: Float, onVolumeChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VOLUME", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            Spacer(Modifier.weight(1f))
            Text("${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        }
        VolumeSlider(volume, onVolumeChange, sound.primary, sound.secondary, Modifier.fillMaxWidth().padding(top = 6.dp))
        androidx.compose.animation.AnimatedVisibility(visible = !active) {
            Text(
                "Il suono è spento: partirà con questo volume.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
private fun PairChip(sound: Sound, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val on by animateFloatAsState(if (active) 1f else 0f, spring(stiffness = 400f), label = "pair")
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .graphicsLayer { scaleX = press.value; scaleY = press.value }
            .clip(RoundedCornerShape(20.dp))
            .background(lerp(Color.White.copy(alpha = 0.05f), sound.primary.copy(alpha = 0.35f), on))
            .border(1.dp, lerp(Palette.GlassBorder, sound.secondary.copy(alpha = 0.7f), on), RoundedCornerShape(20.dp))
            .clickable {
                scope.launch {
                    press.snapTo(0.9f)
                    press.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
                }
                onClick()
            }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(sound.primary, sound.secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(sound.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(sound.name, style = MaterialTheme.typography.labelMedium, color = Palette.Text, maxLines = 1, textAlign = TextAlign.Center)
        Text(if (active) "Acceso" else "Aggiungi", style = MaterialTheme.typography.labelSmall, color = if (active) sound.secondary else Palette.TextFaint)
    }
}

@Composable
private fun ToggleButton(sound: Sound, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val on by animateFloatAsState(if (active) 1f else 0f, tween(350), label = "toggleBtn")
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(lerp(sound.primary, Color.White.copy(alpha = 0.08f), on), lerp(sound.secondary, Color.White.copy(alpha = 0.08f), on))
                )
            )
            .border(1.dp, Palette.GlassBorder.copy(alpha = on), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = active,
            transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it }) },
            label = "toggleLabel",
        ) { isOn ->
            Text(if (isOn) "Spegni ${sound.name.lowercase()}" else "Accendi ${sound.name.lowercase()}", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

/** Le sezioni del dettaglio entrano una dopo l'altra mentre la finestra si apre. */
private fun Modifier.stagger(progress: Float, step: Int): Modifier = graphicsLayer {
    val local = ((progress - step * 0.08f) / 0.6f).coerceIn(0f, 1f)
    alpha = local
    translationY = (1f - local) * 24.dp.toPx()
}
