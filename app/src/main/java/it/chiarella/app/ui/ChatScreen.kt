package it.chiarella.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import it.chiarella.app.ai.VoiceRecorder
import it.chiarella.app.ui.components.formatCountdown
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.chiarella.app.ai.AiChat
import it.chiarella.app.ai.ChatAction
import it.chiarella.app.ai.ChatMessage
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.Sounds
import it.chiarella.app.ui.components.AuroraBackground
import it.chiarella.app.ui.components.GradientButton
import it.chiarella.app.ui.components.IconStack
import it.chiarella.app.ui.components.RoundIconButton
import it.chiarella.app.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private enum class ChatMode { SETUP, CHAT, BREATHE }

/** Frasi per iniziare quando non si sa da dove partire. */
private val Starters = listOf(
    "Non riesco a smettere di pensare",
    "Mi sento agitato e non so perché",
    "Ho troppe cose in testa",
    "Non riesco a dormire",
)

/**
 * "Parla con Chiarella": una chat con l'IA di Cloudflare Workers AI per arginare l'overthinking.
 * L'assistente può proporre suoni, il respiro guidato e "Un momento per te" come pulsanti.
 */
@Composable
fun ChatScreen(ai: AiChat, mixer: Mixer, onClose: () -> Unit, onMoment: () -> Unit) {
    val config by ai.config.collectAsStateWithLifecycle()
    val messages by ai.messages.collectAsStateWithLifecycle()
    val busy by ai.busy.collectAsStateWithLifecycle()
    val error by ai.error.collectAsStateWithLifecycle()
    val transcribing by ai.transcribing.collectAsStateWithLifecycle()
    val mix by mixer.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(if (config.ready) ChatMode.CHAT else ChatMode.SETUP) }
    var confirmReset by remember { mutableStateOf(false) }
    var resumeAfterVoice by remember { mutableStateOf(false) }

    BackHandler {
        when {
            mode == ChatMode.BREATHE -> mode = ChatMode.CHAT
            mode == ChatMode.SETUP && config.ready -> mode = ChatMode.CHAT
            else -> onClose()
        }
    }

    val aura = when (mode) {
        ChatMode.BREATHE -> listOf(Color(0xFF5B8DEF), Color(0xFF8B7CF6), Color(0xFF14B8A6))
        else -> mix.active.mapNotNull { Sounds[it]?.primary }.ifEmpty { listOf(Palette.Lavender, Color(0xFFEC4899), Palette.Sky) }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(aura, playing = mode == ChatMode.BREATHE || (mix.playing && mix.hasMix), modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(Icons.Rounded.Close, "Chiudi", 40.dp, onClick = {
                    if (mode == ChatMode.BREATHE) mode = ChatMode.CHAT else onClose()
                })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Parla con Chiarella", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    AnimatedContent(busy, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { b ->
                        Text(
                            if (b) "Sta scrivendo…" else "Ti ascolto, con calma",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                        )
                    }
                }
                AnimatedVisibility(mode == ChatMode.CHAT && messages.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Row {
                        RoundIconButton(Icons.Rounded.Refresh, "Nuova conversazione", 40.dp, onClick = { confirmReset = true })
                        Spacer(Modifier.width(8.dp))
                    }
                }
                AnimatedVisibility(mode == ChatMode.CHAT, enter = fadeIn(), exit = fadeOut()) {
                    RoundIconButton(Icons.Rounded.Key, "Impostazioni IA", 40.dp, onClick = { mode = ChatMode.SETUP })
                }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(450, delayMillis = 100)) + scaleIn(tween(450, delayMillis = 100), initialScale = 0.95f)) togetherWith
                        (fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.03f))
                },
                modifier = Modifier.weight(1f),
                label = "chatMode",
            ) { current ->
                when (current) {
                    ChatMode.SETUP -> AiSetup(
                        ai = ai,
                        onSaved = {
                            mode = ChatMode.CHAT
                            ai.replyIfPending()
                        },
                        onCancel = if (config.ready) ({ mode = ChatMode.CHAT }) else null,
                    )
                    ChatMode.BREATHE -> BreathingExercise(
                        onDone = {
                            mode = ChatMode.CHAT
                            ai.sendEvent("Ho finito l'esercizio di respirazione guidata.")
                        },
                        onSkip = {
                            mode = ChatMode.CHAT
                            ai.sendEvent("Ho interrotto l'esercizio di respirazione prima della fine.", answer = false)
                        },
                    )
                    ChatMode.CHAT -> Conversation(
                        messages = messages,
                        busy = busy,
                        transcribing = transcribing,
                        error = error,
                        mix = mix,
                        onSend = ai::send,
                        onVoice = ai::sendVoice,
                        // Mentre parli i suoni si fermano, così il microfono sente solo la tua voce.
                        onRecording = { recording ->
                            if (recording && mix.playing) {
                                resumeAfterVoice = true
                                mixer.pause()
                            } else if (!recording && resumeAfterVoice) {
                                resumeAfterVoice = false
                                mixer.play()
                            }
                        },
                        onStop = ai::stop,
                        onRetry = ai::retry,
                        onAction = { action ->
                            when (action) {
                                is ChatAction.PlaySounds -> ai.playSounds(action.ids)
                                ChatAction.Breathe -> mode = ChatMode.BREATHE
                                ChatAction.Moment -> onMoment()
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Palette.SurfaceHigh,
            title = { Text("Ricominciare da capo?") },
            text = { Text("La conversazione verrà cancellata da questo telefono.") },
            confirmButton = {
                TextButton(onClick = {
                    ai.reset()
                    confirmReset = false
                }) { Text("Ricomincia", color = Palette.Peach) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Annulla", color = Palette.TextMuted) }
            },
        )
    }
}

// ------------------------------------------------------------------ conversazione

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Conversation(
    messages: List<ChatMessage>,
    busy: Boolean,
    transcribing: Boolean,
    error: String?,
    mix: Mixer.State,
    onSend: (String) -> Unit,
    onVoice: (ByteArray) -> Unit,
    onRecording: (Boolean) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onAction: (ChatAction) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val last = messages.lastOrNull()
    val waiting = busy && (last == null || last.role != ChatMessage.Role.ASSISTANT || last.text.isEmpty())
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder() }
    var recording by remember { mutableStateOf(false) }
    var micMessage by remember { mutableStateOf<String?>(null) }

    fun startRecording() {
        micMessage = null
        onRecording(true)
        if (recorder.start()) {
            recording = true
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        } else {
            onRecording(false)
            micMessage = "Il microfono non è disponibile in questo momento."
        }
    }

    fun finishRecording(send: Boolean) {
        if (!recording) return
        recording = false
        val wav = recorder.stop()
        onRecording(false)
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        when {
            !send -> Unit
            wav == null -> micMessage = "Vocale troppo corto: tocca il microfono, parla e poi tocca invia."
            else -> onVoice(wav)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else micMessage = "Per i vocali serve il permesso del microfono. Puoi darlo dalle impostazioni di Android."
    }

    fun onMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Arrivata al limite, la registrazione parte da sola. Uscendo dalla chat, si scarta.
    val elapsed by recorder.elapsed.collectAsStateWithLifecycle()
    LaunchedEffect(recording, elapsed) {
        if (recording && elapsed >= VoiceRecorder.MAX_SECONDS * 1000L) finishRecording(send = true)
    }
    DisposableEffect(Unit) {
        onDispose {
            if (recorder.isRecording) {
                recorder.cancel()
                onRecording(false)
            }
        }
    }

    // Segue la risposta mentre arriva, finché l'utente non torna indietro a rileggere.
    LaunchedEffect(messages.size, last?.content?.length, waiting, error, transcribing) {
        val count = list.layoutInfo.totalItemsCount
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    fun send() {
        if (draft.isBlank() || busy) return
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        onSend(draft)
        draft = ""
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Intro(compact = messages.isNotEmpty(), onStarter = { if (!busy) onSend(it) })
            }
            items(messages, key = { it.id }) { m ->
                Box(Modifier.animateItem()) {
                    when (m.role) {
                        ChatMessage.Role.USER -> UserBubble(m.text, m.voice)
                        ChatMessage.Role.EVENT -> EventNote(m.content)
                        ChatMessage.Role.ASSISTANT -> AssistantBubble(
                            message = m,
                            // Le azioni compaiono a risposta completata, per non far "saltare" i pulsanti.
                            showActions = !(busy && m === last),
                            mix = mix,
                            onAction = onAction,
                        )
                    }
                }
            }
            if (transcribing) item(key = "transcribing") { TranscribingBubble(Modifier.animateItem()) }
            if (waiting) item(key = "typing") { TypingBubble(Modifier.animateItem()) }
            if (error != null && !busy) item(key = "error") { ErrorCard(error, onRetry, Modifier.animateItem()) }
        }

        AnimatedVisibility(micMessage != null) {
            Text(
                micMessage.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9AA9),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { micMessage = null }
                    .padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AnimatedContent(
                targetState = recording,
                transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f),
                label = "inputMode",
            ) { rec ->
                if (rec) {
                    RecordingBar(recorder, onCancel = { finishRecording(send = false) })
                } else {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2000) },
                        placeholder = { Text("Scrivi o manda un vocale…", color = Palette.TextFaint) },
                        maxLines = 5,
                        shape = RoundedCornerShape(26.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.Lavender,
                            unfocusedBorderColor = Palette.GlassBorder,
                            focusedContainerColor = Palette.Surface.copy(alpha = 0.85f),
                            unfocusedContainerColor = Palette.Surface.copy(alpha = 0.7f),
                            cursorColor = Palette.Lavender,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // Un solo pulsante: invia il testo, ferma la risposta, avvia o invia il vocale.
            val mode = when {
                busy || transcribing -> SendMode.STOP
                recording || draft.isNotBlank() -> SendMode.SEND
                else -> SendMode.MIC
            }
            val level by recorder.level.collectAsStateWithLifecycle()
            val pulse by animateFloatAsState(
                if (recording) 1f + level * 0.35f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "micPulse",
            )
            val bg by animateColorAsState(
                if (mode == SendMode.MIC) Color.White.copy(alpha = 0.1f) else Palette.Lavender, tween(250), label = "sendBg",
            )
            Box(
                Modifier
                    .padding(bottom = 4.dp)
                    .size(52.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .clip(CircleShape)
                    .background(bg)
                    .clickable {
                        when {
                            mode == SendMode.STOP -> onStop()
                            recording -> finishRecording(send = true)
                            mode == SendMode.SEND -> send()
                            else -> onMic()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(mode, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "sendIcon") { m ->
                    Icon(
                        when (m) {
                            SendMode.STOP -> Icons.Rounded.Stop
                            SendMode.SEND -> Icons.AutoMirrored.Rounded.Send
                            SendMode.MIC -> Icons.Rounded.Mic
                        },
                        when (m) {
                            SendMode.STOP -> "Ferma la risposta"
                            SendMode.SEND -> if (recording) "Invia il vocale" else "Invia"
                            SendMode.MIC -> "Registra un vocale"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private enum class SendMode { MIC, SEND, STOP }

private const val BARS = 28

/** Barra di registrazione: puntino rosso, durata, onde che seguono la voce e cestino per annullare. */
@Composable
private fun RecordingBar(recorder: VoiceRecorder, onCancel: () -> Unit) {
    val level by recorder.level.collectAsStateWithLifecycle()
    val elapsed by recorder.elapsed.collectAsStateWithLifecycle()
    // Storico dei livelli: le onde scorrono da destra a sinistra.
    val history = remember { mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } } }
    LaunchedEffect(Unit) {
        while (true) {
            history.removeAt(0)
            history.add(recorder.level.value)
            delay(70)
        }
    }
    val transition = rememberInfiniteTransition(label = "rec")
    val blink by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recBlink")
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.Surface.copy(alpha = 0.85f))
            .border(1.dp, Palette.Lavender.copy(alpha = 0.4f + 0.4f * level), RoundedCornerShape(28.dp))
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Delete, "Annulla il vocale", tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
        }
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { alpha = blink }
                .clip(CircleShape)
                .background(Color(0xFFFF5C7A))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatCountdown(elapsed),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = Palette.Text,
        )
        Spacer(Modifier.width(12.dp))
        Canvas(Modifier.weight(1f).height(32.dp)) {
            val gap = size.width / (BARS * 2f)
            val brush = Brush.verticalGradient(listOf(Palette.Sky, Palette.Lavender))
            history.forEachIndexed { i, v ->
                val h = (size.height * (0.12f + 0.88f * v)).coerceAtMost(size.height)
                drawRoundRect(
                    brush = brush,
                    topLeft = androidx.compose.ui.geometry.Offset(i * gap * 2 + gap / 2, (size.height - h) / 2),
                    size = androidx.compose.ui.geometry.Size(gap, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap / 2),
                    alpha = 0.35f + 0.65f * (i + 1f) / BARS,
                )
            }
        }
    }
}

/** In attesa della trascrizione del vocale. */
@Composable
private fun TranscribingBubble(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "transcribing")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "transcribingT")
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .background(Brush.linearGradient(listOf(Palette.Lavender.copy(alpha = 0.55f), Palette.Sky.copy(alpha = 0.4f))))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.GraphicEq, null, tint = Color.White,
                modifier = Modifier.size(18.dp).graphicsLayer { alpha = 0.5f + 0.5f * sin(t * 2 * PI).toFloat() },
            )
            Spacer(Modifier.width(8.dp))
            Text("Trascrivo il vocale…", style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(compact: Boolean, onStarter: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 0.dp else 24.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Orb(if (compact) 56.dp else 120.dp)
        Spacer(Modifier.height(if (compact) 10.dp else 22.dp))
        if (!compact) {
            Text(
                "Cosa ti passa per la testa?",
                style = MaterialTheme.typography.headlineMedium, color = Palette.Text, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Scrivimi quello che senti, anche in disordine. Mettiamo in fila i pensieri insieme, " +
                    "con qualche domanda, un suono giusto o un respiro.",
                style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Starters.forEach { s ->
                    Text(
                        s,
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Text,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Palette.GlassBorder, CircleShape)
                            .clickable { onStarter(s) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        Text(
            "Chiarella non sostituisce un professionista. Se sei in pericolo chiama il 112 o il Telefono Amico 02 2327 2327.",
            style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** Sfera luminosa che respira piano: il "volto" dell'assistente. */
@Composable
private fun Orb(size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "orbT")
    val spin by transition.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14000)), label = "orbSpin")
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(
            Brush.radialGradient(
                0f to Palette.Lavender.copy(alpha = 0.55f), 1f to Color.Transparent,
                center = center, radius = r * (0.85f + 0.15f * t),
            ),
            radius = r,
        )
        val colors = listOf(Palette.Lavender, Palette.Sky, Color(0xFFEC4899))
        colors.forEachIndexed { i, c ->
            val a = spin + i * 2.1f
            val off = r * 0.12f
            val pc = center + androidx.compose.ui.geometry.Offset(kotlin.math.cos(a) * off, sin(a) * off)
            drawCircle(
                Brush.radialGradient(0f to c.copy(alpha = 0.8f), 1f to c.copy(alpha = 0f), center = pc, radius = r * 0.55f),
                radius = r * 0.55f, center = pc,
            )
        }
        drawCircle(Color.White.copy(alpha = 0.55f + 0.25f * t), radius = r * 0.16f, center = center)
    }
}

@Composable
private fun UserBubble(text: String, voice: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .padding(start = 48.dp)
                .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .background(Brush.linearGradient(listOf(Palette.Lavender.copy(alpha = 0.9f), Palette.Sky.copy(alpha = 0.75f))))
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            if (voice) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Mic, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vocale", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantBubble(message: ChatMessage, showActions: Boolean, mix: Mixer.State, onAction: (ChatAction) -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 320f)) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(end = 36.dp)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 24f
            }
    ) {
        Text(
            message.text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = Palette.Text,
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
                .background(Palette.Surface.copy(alpha = 0.78f))
                .border(1.dp, Palette.GlassBorder, RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        val actions = message.actions
        AnimatedVisibility(
            visible = showActions && actions.isNotEmpty(),
            enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(tween(400)),
        ) {
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { ActionChip(it, mix, onAction) }
            }
        }
    }
}

@Composable
private fun ActionChip(action: ChatAction, mix: Mixer.State, onAction: (ChatAction) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val (label, done) = when (action) {
        is ChatAction.PlaySounds -> {
            val names = action.ids.mapNotNull { Sounds[it]?.name }
            val on = mix.playing && action.ids.all(mix::isActive)
            (if (on) "In ascolto: " else "Accendi ") + names.joinToString(" + ") to on
        }
        ChatAction.Breathe -> "Respira con me" to false
        ChatAction.Moment -> "Un momento per te" to false
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .clip(shape)
            .background(
                if (done) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f)))
                else Brush.linearGradient(listOf(Palette.Lavender.copy(alpha = 0.35f), Palette.Sky.copy(alpha = 0.22f)))
            )
            .border(1.dp, if (done) Palette.GlassBorder else Palette.Lavender.copy(alpha = 0.6f), shape)
            .clickable(enabled = !done) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onAction(action)
            }
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (action) {
            is ChatAction.PlaySounds -> IconStack(action.ids.mapNotNull { Sounds[it] }, size = 26.dp)
            ChatAction.Breathe -> ActionIcon(Icons.Rounded.Air)
            ChatAction.Moment -> ActionIcon(Icons.Rounded.SelfImprovement)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (done) Palette.TextMuted else Palette.Text)
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Palette.Lavender, Palette.Sky))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun EventNote(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Palette.TextFaint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TypingBubble(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100)), label = "typingT")
    Row(
        modifier
            .clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
            .background(Palette.Surface.copy(alpha = 0.78f))
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            val phase = ((t - i * 0.18f) * 2 * PI).toFloat()
            val lift = (sin(phase).coerceAtLeast(0f))
            Box(
                Modifier
                    .graphicsLayer { translationY = -lift * 10f }
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Palette.Lavender.copy(alpha = 0.45f + 0.55f * lift))
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x33FF6B81))
            .border(1.dp, Color(0x55FF9AA9), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Riprova",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.Peach,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRetry)
                .padding(vertical = 6.dp),
        )
    }
}

// ------------------------------------------------------------------ configurazione

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSetup(ai: AiChat, onSaved: () -> Unit, onCancel: (() -> Unit)?) {
    val config by ai.config.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var accountId by rememberSaveable { mutableStateOf(config.accountId) }
    var token by rememberSaveable { mutableStateOf(config.token) }
    var model by rememberSaveable { mutableStateOf(config.model) }
    var showToken by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (checking) return
        checking = true
        message = null
        scope.launch {
            val result = ai.saveConfig(accountId, token, model)
            checking = false
            result.onSuccess {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onSaved()
            }.onFailure { message = it.message ?: "Non sono riuscita a collegarmi a Cloudflare." }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Text("INTELLIGENZA ARTIFICIALE", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        Spacer(Modifier.height(4.dp))
        Text("Collega Cloudflare", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "La chat usa Cloudflare Workers AI con il tuo account: il piano gratuito offre ogni giorno " +
                "abbastanza richieste per chiacchierare. Account ID e token restano solo su questo telefono " +
                "e non finiscono nei backup.",
            style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted,
        )
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Palette.GlassBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Text("Come ottenerli", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Accedi (o registrati gratis) su dash.cloudflare.com.",
                "Account ID: lo trovi nella pagina Workers AI, oppure nella home del tuo account, in basso a destra.",
                "Token: Profilo → API Tokens → Create Token → modello \"Workers AI\". Copialo subito: Cloudflare lo mostra una volta sola.",
            ).forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = Palette.Lavender, modifier = Modifier.width(20.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable { uri.openUri("https://dash.cloudflare.com/profile/api-tokens") }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Apri Cloudflare", style = MaterialTheme.typography.labelLarge, color = Palette.Peach)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Palette.Peach, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        SetupField(
            value = accountId,
            onChange = { accountId = it.trim().take(64) },
            label = "Account ID",
            placeholder = "32 caratteri, es. 1a2b3c…",
        )
        Spacer(Modifier.height(12.dp))
        SetupField(
            value = token,
            onChange = { token = it.trim().take(200) },
            label = "API token",
            placeholder = "Il token con il permesso Workers AI",
            secret = !showToken,
            trailing = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { showToken = !showToken },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (showToken) "Nascondi token" else "Mostra token",
                        tint = Palette.TextMuted,
                    )
                }
            },
        )

        Spacer(Modifier.height(18.dp))
        Text("Modello", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AiChat.MODELS.forEach { m ->
                val selected = model == m.id
                val border by animateColorAsState(if (selected) Palette.Lavender else Palette.GlassBorder, label = "modelBorder")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Palette.Lavender.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, border, RoundedCornerShape(18.dp))
                        .clickable { model = m.id }
                        .padding(12.dp)
                ) {
                    Text(m.label, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                    Text(m.hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
        }

        AnimatedVisibility(message != null) {
            Text(
                message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF9AA9),
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Spacer(Modifier.height(22.dp))
        GradientButton(
            if (checking) "Mi collego a Cloudflare…" else "Verifica e salva",
            Modifier.fillMaxWidth(),
            enabled = !checking && accountId.isNotBlank() && token.isNotBlank(),
            onClick = ::save,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("Annulla", color = Palette.TextMuted) }
            }
            if (config.ready) {
                TextButton(onClick = {
                    ai.removeConfig()
                    accountId = ""
                    token = ""
                }) { Text("Scollega", color = Color(0xFFFF9AA9)) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SetupField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    secret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Palette.TextFaint) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii,
            autoCorrectEnabled = false,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        trailingIcon = trailing,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Lavender,
            unfocusedBorderColor = Palette.GlassBorder,
            focusedLabelColor = Palette.Lavender,
            cursorColor = Palette.Lavender,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Invito alla chat nella home. */
@Composable
internal fun ChatCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chatCard")
    val glow by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(4600), RepeatMode.Reverse), label = "chatGlow")
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFEC4899).copy(alpha = 0.22f * glow), Palette.Lavender.copy(alpha = 0.18f * glow), Color.White.copy(alpha = 0.04f))
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
                .background(Brush.linearGradient(listOf(Color(0xFFEC4899), Palette.Lavender))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.EditNote, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Parla con Chiarella", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(
                "Troppi pensieri? Scrivimi e li mettiamo in ordine",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
    }
}
