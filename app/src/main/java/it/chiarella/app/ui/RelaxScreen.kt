package it.chiarella.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import it.chiarella.app.ChiarellaApp
import it.chiarella.app.audio.BreathAudio
import it.chiarella.app.data.Passion
import it.chiarella.app.data.PassionCategory
import it.chiarella.app.data.Store
import it.chiarella.app.ui.components.AuroraBackground
import it.chiarella.app.ui.components.GradientButton
import it.chiarella.app.ui.components.RoundIconButton
import it.chiarella.app.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class RelaxStep { PASSIONS, BREATHE, REVEAL }

/** Fasi del respiro guidato: inspira 4 s, trattieni 2 s, espira 6 s (l'espirazione lunga calma). */
private enum class BreathPhase(val label: String, val hint: String, val seconds: Int) {
    IN("Inspira", "lentamente dal naso", 4),
    HOLD("Trattieni", "senza sforzo", 2),
    OUT("Espira", "piano, dalla bocca", 6),
}

private const val BREATH_CYCLES = 4

/**
 * "Un momento per te": l'utente sceglie le sue passioni, fa un breve esercizio di respirazione
 * e poi Chiarella gli propone cosa fare per rilassarsi.
 *
 * [onChoice] riceve la proposta accettata (per raccontarla alla chat); con [onTalk] compare
 * il pulsante per parlarne con la chat.
 */
@Composable
fun RelaxScreen(
    store: Store,
    onClose: () -> Unit,
    onChoice: (String) -> Unit = {},
    onTalk: ((String) -> Unit)? = null,
) {
    val passions by store.passions.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(if (passions.isEmpty()) RelaxStep.PASSIONS else RelaxStep.BREATHE) }
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }
    var round by rememberSaveable { mutableIntStateOf(0) }

    fun pick() {
        val last = store.lastPassionId
        val pool = passions.filter { it.id != last }.ifEmpty { passions }
        val choice = pool.randomOrNull() ?: return
        pickedId = choice.id
        store.lastPassionId = choice.id
        round++
        step = RelaxStep.REVEAL
    }

    BackHandler {
        if (step == RelaxStep.PASSIONS && passions.isNotEmpty() && pickedId != null) step = RelaxStep.REVEAL
        else onClose()
    }

    val picked = passions.firstOrNull { it.id == pickedId }
    val auraColors = when (step) {
        RelaxStep.PASSIONS -> emptyList()
        RelaxStep.BREATHE -> listOf(Color(0xFF5B8DEF), Color(0xFF8B7CF6), Color(0xFF14B8A6))
        RelaxStep.REVEAL -> picked?.let { listOf(it.category.primary, it.category.secondary, it.category.primary) } ?: emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(auraColors, playing = step != RelaxStep.PASSIONS, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(Icons.Rounded.Close, "Chiudi", 40.dp, onClose)
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(step != RelaxStep.PASSIONS, enter = fadeIn(), exit = fadeOut()) {
                    RoundIconButton(Icons.Rounded.Edit, "Le mie passioni", 40.dp, onClick = { step = RelaxStep.PASSIONS })
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(500, delayMillis = 120)) + scaleIn(tween(500, delayMillis = 120), initialScale = 0.94f)) togetherWith
                        (fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.04f))
                },
                modifier = Modifier.weight(1f),
                label = "relaxStep",
            ) { current ->
                when (current) {
                    RelaxStep.PASSIONS -> PassionsEditor(
                        passions = passions,
                        onAdd = store::addPassion,
                        onDelete = store::deletePassion,
                        onContinue = { step = RelaxStep.BREATHE },
                    )
                    RelaxStep.BREATHE -> BreathingExercise(onDone = ::pick)
                    RelaxStep.REVEAL -> picked?.let {
                        Reveal(
                            passion = it,
                            all = passions,
                            round = round,
                            onAnother = ::pick,
                            onBreatheAgain = { step = RelaxStep.BREATHE },
                            onDone = { proposal ->
                                onChoice("In \"Un momento per te\" mi è stato proposto: $proposal Ho deciso di farlo.")
                                onClose()
                            },
                            onTalk = onTalk?.let { talk ->
                                { proposal -> talk("In \"Un momento per te\" mi è stato proposto: $proposal Vorrei parlarne con te.") }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ passioni

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassionsEditor(
    passions: List<Passion>,
    onAdd: (PassionCategory, String) -> Unit,
    onDelete: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<PassionCategory?>(null) }
    var detail by remember { mutableStateOf("") }

    fun add() {
        val category = selected ?: return
        onAdd(category, detail)
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        selected = null
        detail = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text("UN MOMENTO PER TE", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text("Le tue passioni", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Dimmi cosa ti piace fare. Quando vorrai rilassarti, dopo un breve respiro guidato, " +
                    "ti proporrò io qualcosa tra queste.",
                style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted,
            )
            Spacer(Modifier.height(22.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PassionCategory.entries.forEach { c ->
                    CategoryChip(c, selected == c) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        selected = if (selected == c) null else c
                    }
                }
            }

            AnimatedVisibility(
                visible = selected != null,
                enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val c = selected ?: PassionCategory.ALTRO
                Column(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Palette.GlassBorder, RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Text(c.label, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Facoltativo: scrivi cosa in particolare, così le proposte saranno più tue.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it.take(40) },
                        placeholder = { Text("Es. ${detailExample(c)}", color = Palette.TextFaint) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.primary,
                            unfocusedBorderColor = Palette.GlassBorder,
                            cursorColor = c.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Aggiungi", Modifier.fillMaxWidth()) { add() }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                if (passions.isEmpty()) "Nessuna passione, per ora" else "Le tue passioni (${passions.size})",
                style = MaterialTheme.typography.titleMedium, color = Palette.Text,
            )
            Spacer(Modifier.height(10.dp))
            passions.forEach { p ->
                key(p.id) {
                    PassionRow(p, onDelete = { onDelete(p.id) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        GradientButton(
            if (passions.isEmpty()) "Aggiungi almeno una passione" else "Respira e scopri cosa fare",
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            enabled = passions.isNotEmpty(),
            onClick = onContinue,
        )
    }
}

private fun detailExample(c: PassionCategory) = when (c) {
    PassionCategory.FILM -> "Il Signore degli Anelli"
    PassionCategory.SERIE_TV -> "Breaking Bad"
    PassionCategory.VIDEOGIOCHI -> "Zelda"
    PassionCategory.PROGRAMMARE -> "la mia app"
    PassionCategory.LETTURA -> "un giallo"
    PassionCategory.MUSICA -> "i Pink Floyd"
    PassionCategory.SPORT -> "una corsa al parco"
    PassionCategory.NATURA -> "una passeggiata nel bosco"
    PassionCategory.ARTE -> "acquerelli"
    PassionCategory.FOTOGRAFIA -> "tramonti"
    PassionCategory.CUCINA -> "una torta"
    PassionCategory.BENESSERE -> "yoga"
    PassionCategory.ALTRO -> "fare un puzzle"
}

@Composable
private fun CategoryChip(category: PassionCategory, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) category.primary.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.06f), tween(250), label = "chipBg",
    )
    val border by animateColorAsState(
        if (selected) category.secondary.copy(alpha = 0.8f) else Palette.GlassBorder, tween(250), label = "chipBorder",
    )
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "chipScale")
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, null, tint = if (selected) Color.White else category.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(category.label, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

@Composable
private fun PassionRow(passion: Passion, onDelete: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
    Row(
        Modifier
            .padding(vertical = 5.dp)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 40f
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PassionBadge(passion.category, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(passion.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1)
            if (passion.detail.isNotBlank()) {
                Text(passion.category.label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, "Rimuovi", tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PassionBadge(category: PassionCategory, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(category.primary, category.secondary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

// ------------------------------------------------------------------ respiro

@Composable
internal fun BreathingExercise(onDone: () -> Unit, onSkip: () -> Unit = onDone) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cues = remember(context) { BreathAudio(context) }
    val mixerState by (context as ChiarellaApp).mixer.state.collectAsStateWithLifecycle()
    val latestMaster by rememberUpdatedState(mixerState.master)
    val latestOnDone by rememberUpdatedState(onDone)
    DisposableEffect(cues) { onDispose { cues.release() } }
    val breath = remember { Animatable(0f) }
    var phase by remember { mutableStateOf<BreathPhase?>(null) }
    var secondsLeft by remember { mutableIntStateOf(3) }
    var cycle by remember { mutableIntStateOf(0) }

    LaunchedEffect(cues, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                phase = null
                cycle = 0
                breath.snapTo(0f)
                // Tre secondi per mettersi comodi.
                for (s in 3 downTo 1) {
                    secondsLeft = s
                    delay(1000)
                }
                repeat(BREATH_CYCLES) { c ->
                    cycle = c + 1
                    for (p in BreathPhase.entries) {
                        phase = p
                        when (p) {
                            BreathPhase.IN -> cues.play(true, latestMaster * latestMaster)
                            BreathPhase.OUT -> cues.play(false, latestMaster * latestMaster)
                            BreathPhase.HOLD -> cues.stop()
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        val anim = launch {
                            when (p) {
                                BreathPhase.IN -> breath.animateTo(1f, tween(p.seconds * 1000, easing = FastOutSlowInEasing))
                                BreathPhase.OUT -> breath.animateTo(0f, tween(p.seconds * 1000, easing = FastOutSlowInEasing))
                                BreathPhase.HOLD -> Unit
                            }
                        }
                        for (s in p.seconds downTo 1) {
                            secondsLeft = s
                            delay(1000)
                        }
                        anim.join()
                    }
                }
                phase = null
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                delay(400)
                latestOnDone()
            } finally {
                cues.stop()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text("RESPIRA CON ME", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(
            "Prima di tutto, un minuto per te",
            style = MaterialTheme.typography.headlineMedium, color = Palette.Text, textAlign = TextAlign.Center,
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            BreathFlower(breath.value, Modifier.size(300.dp))
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(tween(500)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(300)) + slideOutVertically { -it / 3 })
                },
                label = "phase",
            ) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        p?.label ?: "Mettiti comodo",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                        color = Color.White,
                    )
                    Text(
                        p?.hint ?: "e chiudi gli occhi, se vuoi",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }

        AnimatedContent(
            targetState = secondsLeft,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "seconds",
        ) { s ->
            Text("$s", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 40.sp), color = Palette.Text)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(BREATH_CYCLES) { i ->
                val done by animateFloatAsState(if (i < cycle) 1f else 0f, tween(500), label = "dot$i")
                Box(
                    Modifier
                        .height(8.dp)
                        .width(8.dp + 18.dp * done)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f + 0.6f * done))
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Salta",
            style = MaterialTheme.typography.labelLarge,
            color = Palette.TextFaint,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onSkip)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

/** Fiore di sei petali luminosi che si apre con l'inspirazione e si chiude con l'espirazione. */
@Composable
private fun BreathFlower(breath: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flower")
    val spin by transition.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "spin",
    )
    val shimmer by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3_000), RepeatMode.Reverse), label = "shimmer")
    val petals = listOf(Color(0xFF5B8DEF), Color(0xFF8B7CF6), Color(0xFF14B8A6), Color(0xFF5EC8F2), Color(0xFFC4BCFF), Color(0xFF99F6E4))
    val sparks = remember {
        val r = Random(11)
        List(18) { Triple(r.nextFloat() * 6.28f, 0.75f + r.nextFloat() * 0.35f, 1f + r.nextFloat() * 2f) }
    }

    Canvas(modifier) {
        val c = center
        val maxR = size.minDimension / 2
        val petalR = maxR * (0.30f + 0.16f * breath)
        val dist = maxR * (0.05f + 0.33f * breath)

        // Alone che respira.
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFF8B7CF6).copy(alpha = 0.35f + 0.2f * breath),
                1f to Color.Transparent,
                center = c, radius = maxR * (0.7f + 0.3f * breath),
            ),
            radius = maxR * (0.7f + 0.3f * breath), center = c,
        )
        petals.forEachIndexed { i, color ->
            val a = spin * 0.6f + i * (PI.toFloat() / 3f) + breath * 0.5f
            val pc = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist)
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = 0.55f),
                    1f to color.copy(alpha = 0.12f),
                    center = pc, radius = petalR,
                ),
                radius = petalR, center = pc,
            )
        }
        drawCircle(Color.White.copy(alpha = 0.25f + 0.15f * shimmer), radius = maxR * (0.80f + 0.1f * breath), center = c, style = Stroke(1.dp.toPx()))
        sparks.forEach { (angle, dr, sz) ->
            val a = angle + spin * (0.8f + dr * 0.2f)
            val r = maxR * dr * (0.7f + 0.3f * breath)
            drawCircle(
                Color.White.copy(alpha = 0.35f + 0.4f * shimmer * breath),
                radius = sz * density, center = Offset(c.x + cos(a) * r, c.y + sin(a) * r),
            )
        }
    }
}

// ------------------------------------------------------------------ proposta

@Composable
private fun Reveal(
    passion: Passion,
    all: List<Passion>,
    round: Int,
    onAnother: () -> Unit,
    onBreatheAgain: () -> Unit,
    onDone: (String) -> Unit,
    onTalk: ((String) -> Unit)?,
) {
    val haptic = LocalHapticFeedback.current
    // Breve "rullo": le passioni scorrono rallentando, poi si ferma su quella scelta.
    var shown by remember(round) { mutableStateOf(if (all.size > 1) all.first() else passion) }
    var landed by remember(round) { mutableStateOf(all.size <= 1) }
    LaunchedEffect(round) {
        if (all.size > 1) {
            var wait = 60L
            var i = all.indexOf(passion) + 1
            repeat(12) {
                shown = all[i % all.size]
                i++
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                delay(wait)
                wait = (wait * 1.22f).toLong()
            }
            shown = passion
        }
        landed = true
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val pop by animateFloatAsState(if (landed) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 260f), label = "pop")
    val ideaIndex = remember(round) { Random.nextInt(passion.category.ideas.size) }
    val proposal = "${passion.title}. ${passion.category.idea(passion, ideaIndex)}"

    val transition = rememberInfiniteTransition(label = "reveal")
    val halo by transition.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "halo")

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text("BEN FATTO, ORA VA MEGLIO", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
        Spacer(Modifier.height(4.dp))
        Text("Ecco cosa ti propongo", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    val cat = shown.category
                    Canvas(Modifier.size(220.dp)) {
                        val r = size.minDimension / 2 * (if (landed) halo else 0.8f)
                        drawCircle(
                            Brush.radialGradient(
                                0f to cat.primary.copy(alpha = 0.55f * (0.4f + 0.6f * pop)),
                                1f to Color.Transparent,
                                center = center, radius = r,
                            ),
                            radius = r,
                        )
                    }
                    AnimatedContent(
                        targetState = shown,
                        transitionSpec = {
                            (slideInVertically(tween(90)) { it / 2 } + fadeIn(tween(90))) togetherWith
                                (slideOutVertically(tween(90)) { -it / 2 } + fadeOut(tween(90)))
                        },
                        label = "reel",
                    ) { p ->
                        Box(Modifier.graphicsLayer { val s = 0.85f + 0.25f * pop; scaleX = s; scaleY = s }) {
                            PassionBadge(p.category, 112.dp)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    shown.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Palette.Text,
                    textAlign = TextAlign.Center,
                )
                AnimatedVisibility(
                    visible = landed,
                    enter = fadeIn(tween(500, delayMillis = 150)) + slideInVertically(tween(500, delayMillis = 150)) { it / 3 },
                ) {
                    Text(
                        passion.category.idea(passion, ideaIndex),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                        color = Palette.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = landed,
            enter = fadeIn(tween(400, delayMillis = 350)) + slideInVertically(tween(400, delayMillis = 350)) { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GradientButton("Perfetto, lo faccio", Modifier.fillMaxWidth(), onClick = { onDone(proposal) })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (all.size > 1) {
                        SecondaryAction("Un'altra idea", Icons.Rounded.Refresh, Modifier.weight(1f), onAnother)
                    }
                    SecondaryAction("Respira ancora", null, Modifier.weight(1f), onBreatheAgain)
                }
                if (onTalk != null) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryAction("Parlane con Chiarella", Icons.Rounded.Forum, Modifier.fillMaxWidth()) { onTalk(proposal) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Palette.GlassBorder, CircleShape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Palette.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
    }
}
