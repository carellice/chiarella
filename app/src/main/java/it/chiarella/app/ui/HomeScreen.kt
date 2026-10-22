package it.chiarella.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sounds
import it.chiarella.app.data.Preset
import it.chiarella.app.data.Store
import it.chiarella.app.ui.components.AuroraBackground
import it.chiarella.app.ui.components.Equalizer
import it.chiarella.app.ui.components.PlayerBar
import it.chiarella.app.ui.components.PresetRow
import it.chiarella.app.ui.components.RoundIconButton
import it.chiarella.app.ui.components.SavePresetSheet
import it.chiarella.app.ui.components.MasterVolumeSheet
import it.chiarella.app.ui.components.SoundCard
import it.chiarella.app.ui.components.SoundDetailDialog
import it.chiarella.app.ui.components.TimerSheet
import it.chiarella.app.ui.components.UpdateBanner
import it.chiarella.app.ui.components.UpdateSheet
import it.chiarella.app.update.UpdateState
import it.chiarella.app.update.Updater
import it.chiarella.app.ui.components.bleed
import it.chiarella.app.ui.theme.Palette
import java.util.Calendar

@Composable
fun HomeScreen(mixer: Mixer, store: Store, updater: Updater, onShowGuide: () -> Unit, onRelax: () -> Unit) {
    val context = LocalContext.current
    val state by mixer.state.collectAsStateWithLifecycle()
    val presets by store.presets.collectAsStateWithLifecycle()

    val update by updater.state.collectAsStateWithLifecycle()
    var showUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { updater.checkIfDue() }
    val showBanner = when (val u = update) {
        is UpdateState.Available -> !updater.isDismissed(u.release)
        is UpdateState.Downloading, is UpdateState.ReadyToInstall -> true
        else -> false
    }
    var showTimer by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Preset?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var showMaster by remember { mutableStateOf(false) }

    // Permesso notifiche (Android 13+): chiesto la prima volta che parte la musica.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(state.playing) {
        if (state.playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Animazione d'ingresso: gli elementi salgono in sequenza (una sola volta).
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1500, easing = LinearEasing)) }

    val activeSounds = state.active.mapNotNull { Sounds[it] }
    val presetName = state.presetId?.let { id -> presets.firstOrNull { it.id == id }?.name }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(
            colors = activeSounds.map { it.primary },
            playing = state.playing,
            modifier = Modifier.fillMaxSize(),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = topInset + 12.dp,
                bottom = bottomInset + 128.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Header(
                    state, onShowGuide,
                    onShowUpdates = {
                        if (update !is UpdateState.Available && update !is UpdateState.Downloading &&
                            update !is UpdateState.ReadyToInstall
                        ) updater.checkNow()
                        showUpdate = true
                    },
                    modifier = Modifier.introStep(intro.value, 0),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "relax") {
                RelaxCard(onRelax, Modifier.introStep(intro.value, 1))
            }
            if (showBanner) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "update") {
                    UpdateBanner(
                        update,
                        onOpen = { showUpdate = true },
                        onDismiss = updater::dismiss,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "presetsTitle") {
                SectionTitle(
                    "Profili",
                    if (presets.isEmpty()) "I tuoi mix preferiti" else "Tieni premuto per eliminare",
                    Modifier.introStep(intro.value, 1),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "presets") {
                PresetRow(
                    presets = presets,
                    activeId = state.presetId,
                    playing = state.playing,
                    onApply = mixer::applyPreset,
                    onDelete = { toDelete = it },
                    onCreate = {
                        if (state.hasMix) showSave = true
                        else Toast.makeText(context, "Accendi almeno un suono per salvare un mix", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.bleed(20.dp).introStep(intro.value, 2),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "soundsTitle") {
                SectionTitle(
                    "Suoni",
                    if (state.hasMix) "Ferma tutti" else "Tocca per accendere",
                    Modifier.introStep(intro.value, 3),
                    onAction = if (state.hasMix) mixer::stopAll else null,
                )
            }
            itemsIndexed(Sounds.all, key = { _, s -> s.id }) { index, sound ->
                SoundCard(
                    sound = sound,
                    active = state.isActive(sound.id),
                    playing = state.playing,
                    volume = state.volume(sound.id),
                    onToggle = { mixer.toggleSound(sound.id) },
                    onVolumeChange = { mixer.setVolume(sound.id, it) },
                    onDetails = { detailId = sound.id },
                    modifier = Modifier.introStep(intro.value, 4 + index / 2),
                )
            }
        }

        AnimatedVisibility(
            visible = state.hasMix,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = 300f)) { it * 2 } + fadeIn(),
            exit = slideOutVertically(tween(320)) { it * 2 } + fadeOut(tween(250)),
        ) {
            PlayerBar(
                state = state,
                presetName = presetName,
                onTogglePlay = mixer::togglePlay,
                onStop = mixer::stopAll,
                onTimer = { showTimer = true },
                onVolume = { showMaster = true },
            )
        }
    }

    detailId?.let { Sounds[it] }?.let { sound ->
        SoundDetailDialog(
            sound = sound,
            active = state.isActive(sound.id),
            playing = state.playing,
            volume = state.volume(sound.id),
            isActive = state::isActive,
            onToggle = mixer::toggleSound,
            onVolumeChange = { mixer.setVolume(sound.id, it) },
            onDismiss = { detailId = null },
        )
    }

    if (showMaster) {
        MasterVolumeSheet(state = state, onChange = mixer::setMasterVolume, onDismiss = { showMaster = false })
    }

    if (showUpdate) {
        UpdateSheet(updater, update, onDismiss = { showUpdate = false })
    }

    if (showTimer) {
        TimerSheet(
            state = state,
            lastMinutes = store.lastTimerMinutes,
            onStart = mixer::startTimer,
            onExtend = mixer::extendTimer,
            onCancel = mixer::cancelTimer,
            onDismiss = { showTimer = false },
        )
    }

    if (showSave) {
        SavePresetSheet(
            state = state,
            suggestedName = "Il mio mix ${presets.size + 1}",
            onSave = { name ->
                val preset = store.addPreset(name, state.active.associateWith { state.volume(it) })
                mixer.applyPreset(preset)
                showSave = false
                Toast.makeText(context, "Profilo \"${preset.name}\" salvato", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSave = false },
        )
    }

    toDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Palette.SurfaceHigh,
            title = { Text("Eliminare \"${preset.name}\"?") },
            text = { Text("Il profilo verrà rimosso. I suoni non verranno toccati.") },
            confirmButton = {
                TextButton(onClick = {
                    store.deletePreset(preset.id)
                    toDelete = null
                }) { Text("Elimina", color = Color(0xFFFF9AA9)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Annulla", color = Palette.TextMuted) }
            },
        )
    }
}

@Composable
private fun Header(
    state: Mixer.State,
    onShowGuide: () -> Unit,
    onShowUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buongiorno"
            in 12..17 -> "Buon pomeriggio"
            in 18..22 -> "Buonasera"
            else -> "Buonanotte"
        }
    }
    Column(modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(greeting.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Rounded.SystemUpdate, "Aggiornamenti", 38.dp, onShowUpdates)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.AutoMirrored.Rounded.HelpOutline, "Come funziona", 38.dp, onShowGuide)
        }
        Spacer(Modifier.height(2.dp))
        Text("Trova la tua quiete", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
        Spacer(Modifier.height(10.dp))
        val status = when {
            !state.hasMix -> 0
            state.playing -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = status to state.active.size,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(200)) + slideOutVertically { -it / 2 })
            },
            label = "status",
        ) { (st, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (st != 0) {
                    Equalizer(st == 1, Palette.Sky, Modifier.size(width = 18.dp, height = 14.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    when (st) {
                        0 -> "Accendi uno o più suoni e mescolali come preferisci."
                        1 -> if (count == 1) "1 suono in riproduzione" else "$count suoni in riproduzione"
                        else -> "In pausa"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = hint,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "hint",
        ) { text ->
            val clickable = onAction != null
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (clickable) Palette.Peach else Palette.TextFaint,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (clickable) Modifier.clickable { onAction?.invoke() } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Invito a "Un momento per te": respiro guidato e poi una proposta tra le passioni dell'utente. */
@Composable
private fun RelaxCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "relaxCard")
    val glow by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "relaxGlow",
    )
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Palette.Lavender.copy(alpha = 0.30f * glow), Palette.Sky.copy(alpha = 0.16f * glow), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, Palette.GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { val s = 0.94f + 0.08f * glow; scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Lavender, Palette.Sky))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SelfImprovement, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Un momento per te", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                "Respira un minuto, poi ti dico cosa fare per rilassarti",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
    }
}

/** Ingresso a cascata: ogni gruppo di elementi appare con un piccolo ritardo. */
@Composable
private fun Modifier.introStep(progress: Float, step: Int): Modifier {
    val distance = with(LocalDensity.current) { 36.dp.toPx() }
    return graphicsLayer {
        val start = step * 0.07f
        val local = ((progress - start) / 0.45f).coerceIn(0f, 1f)
        val eased = FastOutSlowInEasing.transform(local)
        alpha = eased
        translationY = (1f - eased) * distance
        val s = 0.94f + 0.06f * eased
        scaleX = s
        scaleY = s
    }
}
