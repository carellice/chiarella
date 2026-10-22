package it.chiarella.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sounds
import it.chiarella.app.ui.theme.Palette
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val QuickTimers = listOf(15, 30, 45, 60, 90, 120)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSheet(
    state: Mixer.State,
    lastMinutes: Int,
    onStart: (Int) -> Unit,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("Timer di spegnimento", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Allo scadere i suoni sfumano dolcemente fino al silenzio.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
            )
            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = state.timerActive,
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(200)) },
                label = "timerMode",
            ) { active ->
                if (active) {
                    ActiveTimer(state, onExtend, onCancel)
                } else {
                    Column {
                        listOf(QuickTimers.take(3), QuickTimers.drop(3)).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { min ->
                                    TimerChip(formatMinutes(min), Modifier.weight(1f)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onStart(min)
                                        onDismiss()
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        CustomTimer(lastMinutes) { min ->
                            onStart(min)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTimer(state: Mixer.State, onExtend: (Int) -> Unit, onCancel: () -> Unit) {
    val now = rememberNow(true)
    val remaining = (state.timerEndsAt - now).coerceAtLeast(0)
    val progress by animateFloatAsState(
        if (state.timerDurationMs > 0) remaining.toFloat() / state.timerDurationMs else 0f,
        tween(900), label = "ring",
    )
    val sounds = state.active.mapNotNull { Sounds[it] }
    val c1 = sounds.firstOrNull()?.primary ?: Palette.Lavender
    val c2 = sounds.getOrNull(1)?.secondary ?: sounds.firstOrNull()?.secondary ?: Palette.Sky

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(210.dp)
                .drawBehind {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.07f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(c1, c2, c1)), -90f, 360f * progress, false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatCountdown(remaining),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 42.sp),
                    color = Palette.Text,
                )
                Text(
                    "si spegne alle " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.timerEndsAt)),
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimerChip("+15 min", Modifier.weight(1f)) { onExtend(15) }
            TimerChip("+30 min", Modifier.weight(1f)) { onExtend(30) }
            TimerChip("Annulla", Modifier.weight(1f), danger = true) { onCancel() }
        }
    }
}

@Composable
private fun CustomTimer(initial: Int, onStart: (Int) -> Unit) {
    var minutes by remember { mutableFloatStateOf(initial.coerceIn(5, 240).toFloat()) }
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Personalizzato", style = MaterialTheme.typography.titleSmall, color = Palette.TextMuted)
            Spacer(Modifier.weight(1f))
            AnimatedContent(
                targetState = minutes.roundToInt(),
                transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                label = "customMin",
            ) {
                Text(formatMinutes(it), style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            }
        }
        Slider(
            value = minutes,
            onValueChange = {
                if (it.roundToInt() != minutes.roundToInt()) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                minutes = it
            },
            valueRange = 5f..240f,
            steps = 46,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Palette.Lavender,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(6.dp))
        GradientButton("Avvia timer", Modifier.fillMaxWidth()) { onStart(minutes.roundToInt()) }
    }
}

@Composable
private fun TimerChip(label: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (danger) Color(0x26FF6B81) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, if (danger) Color(0x55FF6B81) else Palette.GlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (danger) Color(0xFFFF9AA9) else Palette.Text)
    }
}

@Composable
fun GradientButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, label = "btnAlpha")
    Box(
        modifier
            .height(54.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Palette.Lavender.copy(alpha = alpha), Palette.Sky.copy(alpha = alpha))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.4f + 0.6f * alpha))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePresetSheet(
    state: Mixer.State,
    suggestedName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }
    val sounds = state.active.mapNotNull { Sounds[it] }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("Salva come profilo", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ritrovi questo mix, con i suoi volumi, con un solo tocco.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = { Text("Nome del profilo") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Lavender,
                    unfocusedBorderColor = Palette.GlassBorder,
                    focusedLabelColor = Palette.Lavender,
                    cursorColor = Palette.Lavender,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            sounds.forEach { s ->
                val vol = state.volume(s.id)
                val shown by animateFloatAsState(vol, spring(), label = "mixPreview")
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconStack(listOf(s), size = 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(s.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.width(110.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .drawBehind {
                                drawRoundRect(
                                    Brush.horizontalGradient(listOf(s.primary, s.secondary)),
                                    size = Size(size.width * shown, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                                )
                            }
                    )
                    Text(
                        "${(vol * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted,
                        textAlign = TextAlign.End, modifier = Modifier.width(44.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("Salva profilo", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                onSave(name)
            }
        }
    }
}

/**
 * Volume generale: alza o abbassa tutta l'app insieme. Le barre sotto mostrano come esce davvero
 * ogni suono acceso (suo volume × volume generale), senza toccare i volumi scelti.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterVolumeSheet(
    state: Mixer.State,
    onChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sounds = state.active.mapNotNull { Sounds[it] }
    val c1 = sounds.firstOrNull()?.primary ?: Palette.Lavender
    val c2 = sounds.getOrNull(1)?.secondary ?: sounds.firstOrNull()?.secondary ?: Palette.Sky
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.25f)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Volume generale", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Alza o abbassa tutta l'app insieme. I volumi dei singoli suoni restano come li hai scelti.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
                    )
                }
                Spacer(Modifier.width(16.dp))
                AnimatedContent(
                    targetState = (state.master * 100).roundToInt(),
                    transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                    label = "masterPct",
                ) {
                    Text("$it%", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light), color = Palette.Text)
                }
            }
            Spacer(Modifier.height(20.dp))
            VolumeSlider(state.master, onChange, c1, c2, Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
                    val selected = (state.master * 100).roundToInt() == (level * 100).roundToInt()
                    val bg by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "level")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.07f), c1.copy(alpha = 0.45f), bg))
                            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onChange(level)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${(level * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                    }
                }
            }
            if (sounds.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("COME ESCE OGNI SUONO", style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
                Spacer(Modifier.height(8.dp))
                sounds.forEach { s ->
                    val own = state.volume(s.id)
                    val effective by animateFloatAsState(own * state.master, spring(), label = "effective")
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconStack(listOf(s), size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.width(110.dp), maxLines = 1)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .drawBehind {
                                    // In trasparenza il volume del suono, pieno quello che si sente davvero.
                                    drawRoundRect(
                                        Color.White.copy(alpha = 0.14f), size = Size(size.width * own, size.height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                                    )
                                    drawRoundRect(
                                        Brush.horizontalGradient(listOf(s.primary, s.secondary)),
                                        size = Size(size.width * effective, size.height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                                    )
                                }
                        )
                        Text(
                            "${(own * state.master * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted,
                            textAlign = TextAlign.End, modifier = Modifier.width(44.dp),
                        )
                    }
                }
            }
        }
    }
}
