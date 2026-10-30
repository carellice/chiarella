package it.chiarella.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import it.chiarella.app.ui.motion.animateFloat
import it.chiarella.app.ui.motion.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import it.chiarella.app.ui.motion.rememberInfiniteTransition
import it.chiarella.app.ui.motion.spring
import it.chiarella.app.ui.motion.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import it.chiarella.app.R
import it.chiarella.app.audio.Sound
import it.chiarella.app.audio.Sounds
import it.chiarella.app.ui.components.AmbienceCanvas
import it.chiarella.app.ui.components.AuroraBackground
import it.chiarella.app.ui.components.Equalizer
import it.chiarella.app.ui.components.GradientButton
import it.chiarella.app.ui.components.IconStack
import it.chiarella.app.ui.components.VolumeSlider
import it.chiarella.app.ui.components.formatCountdown
import it.chiarella.app.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class GuidePage(
    val title: String,
    val body: String,
    val colors: List<Color>,
    val art: @Composable () -> Unit,
)

private fun sound(id: String): Sound = requireNotNull(Sounds[id])

/** Guida introduttiva mostrata al primo avvio (e rivedibile dal pulsante "?" della home). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinish() }

    val pages = remember {
        listOf(
            GuidePage(
                "Benvenuto in Chiarella",
                "Suoni della natura registrati dal vero, da mescolare come preferisci per rilassarti, " +
                    "concentrarti o addormentarti. Ecco come funziona: ci vuole un minuto.",
                emptyList(),
            ) { WelcomeArt() },
            GuidePage(
                "Accendi e mescola",
                "Tocca una card per accendere un suono, toccala di nuovo per spegnerlo. " +
                    "Puoi accenderne quanti vuoi insieme: pioggia e falò, mare e vento… il mix lo decidi tu.",
                listOf(sound("pioggia_dolce").primary, sound("falo").primary, sound("grilli").primary),
            ) { MixArt() },
            GuidePage(
                "Ogni suono, il suo volume",
                "Quando un suono è acceso compare il suo cursore: trascinalo o toccalo per regolare il volume " +
                    "di quel suono. Ogni cambiamento è graduale, senza scatti.",
                listOf(sound("mare").primary, sound("vento").primary, sound("mare").secondary),
            ) { VolumeArt() },
            GuidePage(
                "Tutto sotto controllo",
                "Appena accendi un suono, in basso compare la barra di riproduzione: " +
                    "da lì metti in pausa o riprendi tutto il mix, imposti il timer o spegni tutto.",
                listOf(sound("foresta").primary, sound("fiume").primary, Palette.Lavender),
            ) { PlayerBarArt() },
            GuidePage(
                "Salva i tuoi profili",
                "Hai trovato il mix perfetto? Tocca «Salva mix» e dagli un nome: suoni e volumi restano salvati. " +
                    "Un tocco sul profilo lo fa partire, un altro lo mette in pausa, tenendolo premuto lo elimini.",
                listOf(sound("grilli").primary, sound("pioggia_dolce").primary, sound("grilli").secondary),
            ) { PresetsArt() },
            GuidePage(
                "Addormentati sereno",
                "Con il pulsante a forma di luna imposti il timer di spegnimento, da 5 minuti a 4 ore. " +
                    "Allo scadere i suoni sfumano dolcemente fino al silenzio. Puoi allungarlo o annullarlo quando vuoi.",
                listOf(Palette.Lavender, Color(0xFF3B2F8F), sound("grilli").secondary),
            ) { TimerArt() },
            GuidePage(
                "Anche ad app chiusa",
                "I suoni continuano a schermo spento. Dalla notifica controlli tutto e vedi il timer " +
                    "(su Android 16 anche nella barra di stato); con il widget parti dalla home. " +
                    "Durante una chiamata o se scolleghi le cuffie, Chiarella si mette in pausa da sola.",
                listOf(sound("temporale").primary, sound("cascata").primary, Palette.Lavender),
            ) { ControlsArt() },
            GuidePage(
                "Tutto pronto",
                "Consenti le notifiche per avere i controlli e il conto alla rovescia del timer sempre a portata " +
                    "di mano. Puoi rivedere questa guida quando vuoi dal pulsante «?» in alto.",
                listOf(Palette.Lavender, Palette.Sky, Color(0xFF5B2C83)),
            ) { ReadyArt() },
        )
    }
    val pagerState = rememberPagerState { pages.size }
    val last = pagerState.currentPage == pages.lastIndex

    fun finish() {
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinish()
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(
            colors = pages[pagerState.currentPage].colors,
            playing = true,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pagerState.currentPage + 1} di ${pages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextFaint,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(!last, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        "Salta",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.TextMuted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onFinish() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                PageContent(pages[page], pagerState, page)
            }

            PageDots(pages.size, pagerState.currentPage, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = last,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "cta",
                modifier = Modifier.padding(horizontal = 24.dp),
            ) { isLast ->
                GradientButton(
                    label = when {
                        !isLast -> "Avanti"
                        needsPermission -> "Consenti notifiche e inizia"
                        else -> "Inizia ad ascoltare"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (isLast) finish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            }
            AnimatedVisibility(last && needsPermission) {
                Text(
                    "Più tardi",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { onFinish() }
                        .padding(vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(if (last && needsPermission) 6.dp else 24.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageContent(page: GuidePage, pagerState: PagerState, index: Int) {
    val distance = with(LocalDensity.current) { 120.dp.toPx() }
    // Effetto profondità: il disegno scorre più lentamente del testo e sfuma.
    fun offset() = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    val o = offset()
                    translationX = o * distance
                    alpha = 1f - (abs(o) * 1.2f).coerceAtMost(1f)
                    val s = 1f - abs(o).coerceAtMost(1f) * 0.12f
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) { FitBox { page.art() } }
        Column(
            Modifier.graphicsLayer {
                val o = offset()
                alpha = 1f - (abs(o) * 1.6f).coerceAtMost(1f)
            }
        ) {
            Text(page.title, style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
            Spacer(Modifier.height(10.dp))
            Text(page.body, style = MaterialTheme.typography.bodyLarge, color = Palette.TextMuted)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Mostra l'illustrazione alla sua misura naturale, rimpicciolendola solo se non c'è spazio. */
@Composable
private fun FitBox(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val p = measurables.first().measure(Constraints())
        val scale = minOf(
            1f,
            constraints.maxWidth.toFloat() / p.width.coerceAtLeast(1),
            constraints.maxHeight.toFloat() / p.height.coerceAtLeast(1),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            p.placeWithLayer((constraints.maxWidth - p.width) / 2, (constraints.maxHeight - p.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 7.dp, spring(dampingRatio = 0.6f), label = "dot")
            val alpha by animateFloatAsState(if (selected) 1f else 0.3f, label = "dotA")
            Box(
                Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}

// ------------------------------------------------------------------ illustrazioni animate

@Composable
private fun WelcomeArt() {
    val t = rememberInfiniteTransition(label = "welcome")
    val breath by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "b")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "w")
    Box(
        Modifier
            .size(280.dp)
            .drawBehind {
                // Onde sonore che si allargano dal logo.
                repeat(3) { i ->
                    val p = (wave + i / 3f) % 1f
                    drawCircle(
                        Color.White.copy(alpha = (1f - p) * 0.22f),
                        radius = size.minDimension * (0.3f + 0.2f * p),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
                drawCircle(
                    Brush.radialGradient(listOf(Palette.Lavender.copy(alpha = 0.35f), Color.Transparent)),
                    radius = size.minDimension * 0.45f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_white),
            contentDescription = "Logo di Chiarella",
            modifier = Modifier
                .width(190.dp)
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                },
        )
    }
}

/** Card in miniatura, come quelle della home. */
@Composable
private fun MiniTile(s: Sound, active: Boolean, modifier: Modifier = Modifier, tapKey: Int = -1) {
    val on by animateFloatAsState(if (active) 1f else 0f, spring(stiffness = Spring.StiffnessLow), label = "on")
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .clip(shape)
            .background(Palette.Glass)
            .drawBehind {
                if (on > 0f) {
                    drawRect(
                        Brush.linearGradient(
                            listOf(s.primary.copy(alpha = 0.55f * on), s.secondary.copy(alpha = 0.14f * on)),
                            Offset.Zero, Offset(size.width, size.height),
                        )
                    )
                }
            }
            .border(1.dp, lerp(Palette.GlassBorder, s.secondary.copy(alpha = 0.8f), on), shape),
    ) {
        AmbienceCanvas(s.ambience, s.primary, s.secondary, if (active) 0.85f else 0f, Modifier.matchParentSize())
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(lerp(Color.White.copy(alpha = 0.08f), s.primary, on), lerp(Color.White.copy(alpha = 0.04f), s.secondary, on))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(s.icon, null, tint = lerp(s.secondary, Color.White, on), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(s.name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (tapKey >= 0) TapRipple(tapKey, Modifier.matchParentSize())
    }
}

/** Cerchio che simula il tocco di un dito. */
@Composable
private fun TapRipple(key: Int, modifier: Modifier = Modifier) {
    val anim = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(key) { anim.animateTo(1f, tween(650)) }
    Box(
        modifier.drawBehind {
            val p = anim.value
            if (p in 0.001f..0.999f) {
                drawCircle(Color.White.copy(alpha = 0.35f * (1f - p)), radius = 18.dp.toPx() + 40.dp.toPx() * p)
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - p)), radius = 14.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
        }
    )
}

@Composable
private fun MixArt() {
    val demo = remember { listOf(sound("pioggia_dolce"), sound("falo"), sound("mare"), sound("grilli")) }
    // Sequenza dimostrativa: (suono toccato, suoni accesi dopo il tocco).
    val steps = remember { listOf(0 to setOf(0), 1 to setOf(0, 1), 3 to setOf(0, 1, 3), 0 to setOf(1, 3), 2 to setOf(1, 2, 3), 1 to setOf(2, 3), 3 to setOf(2), 2 to emptySet()) }
    var step by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Unit) {
        delay(600)
        if (it.chiarella.app.ui.motion.Motion.reduced) return@LaunchedEffect
        while (true) {
            step = (step + 1) % steps.size
            delay(1300)
        }
    }
    val active = steps.getOrNull(step)?.second ?: emptySet()
    val tapped = steps.getOrNull(step)?.first ?: -1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    MiniTile(
                        demo[i], i in active,
                        Modifier
                            .width(148.dp)
                            .height(118.dp),
                        tapKey = if (i == tapped) step else -1,
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeArt() {
    val t = rememberInfiniteTransition(label = "vol")
    val a by t.animateFloat(0.25f, 0.85f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.7f, 0.3f, infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "b")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(300.dp)) {
        VolumeRow(sound("mare"), a)
        VolumeRow(sound("vento"), b)
    }
}

@Composable
private fun VolumeRow(s: Sound, value: Float) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(s.primary.copy(alpha = 0.5f), s.secondary.copy(alpha = 0.12f))))
            .border(1.dp, s.secondary.copy(alpha = 0.6f), shape),
    ) {
        AmbienceCanvas(s.ambience, s.primary, s.secondary, 0.35f + 0.65f * value, Modifier.matchParentSize())
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.weight(1f))
            VolumeSlider(value, {}, s.primary, s.secondary, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlayerBarArt() {
    var playing by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        if (it.chiarella.app.ui.motion.Motion.reduced) return@LaunchedEffect
        while (true) {
            delay(1800)
            playing = 1 - playing
        }
    }
    val sounds = listOf(sound("foresta"), sound("fiume"))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(310.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF21E2444), Color(0xF2141933))))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(30.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconStack(sounds, size = 32.dp, max = 2)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Il tuo mix", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                Text(if (playing == 1) "2 suoni in riproduzione" else "In pausa", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
            }
            CircleIcon(Icons.Rounded.Bedtime, 38.dp, Color.White.copy(alpha = 0.07f), Palette.TextMuted)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(sounds[0].primary, sounds[1].secondary))),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = playing == 1,
                    transitionSpec = { (scaleIn(initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut()) },
                    label = "pp",
                ) { p ->
                    Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            CircleIcon(Icons.Rounded.Stop, 36.dp, Color.White.copy(alpha = 0.07f), Palette.TextMuted)
        }
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Legend(Icons.Rounded.PlayArrow, "Pausa e ripresa di tutto il mix")
            Legend(Icons.Rounded.Bedtime, "Timer di spegnimento")
            Legend(Icons.Rounded.Stop, "Spegne tutti i suoni")
        }
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, size: Dp, bg: Color, tint: Color) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun Legend(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleIcon(icon, 30.dp, Color.White.copy(alpha = 0.1f), Color.White)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
    }
}

@Composable
private fun PresetsArt() {
    // 0: solo «Salva mix» · 1: tocco su «Salva mix», nasce il profilo · 2: profilo in riproduzione · 3: in pausa
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (it.chiarella.app.ui.motion.Motion.reduced) return@LaunchedEffect
        while (true) {
            delay(1400)
            phase = (phase + 1) % 4
        }
    }
    val sounds = listOf(sound("pioggia_dolce"), sound("grilli"), sound("vento"))
    val selected = phase >= 2
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(450), label = "glow")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(92.dp)
                .height(112.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                Text("Salva mix", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
            }
            if (phase == 1) TapRipple(phase, Modifier.matchParentSize())
        }
        AnimatedVisibility(
            visible = phase >= 1,
            enter = scaleIn(spring(dampingRatio = 0.55f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier
                    .width(170.dp)
                    .height(112.dp)
                    .graphicsLayer {
                        val s = 1f + 0.04f * glow
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(sounds[0].primary.copy(alpha = 0.4f + 0.2f * glow), sounds[1].primary.copy(alpha = 0.15f + 0.15f * glow))))
                    .border((1f + glow).dp, Color.White.copy(alpha = 0.12f + 0.6f * glow), RoundedCornerShape(24.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconStack(sounds, size = 26.dp)
                        Spacer(Modifier.weight(1f))
                        if (selected) Equalizer(phase == 2, Color.White, Modifier.size(width = 16.dp, height = 14.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Notte serena", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        when (phase) {
                            2 -> "In riproduzione"
                            3 -> "In pausa"
                            else -> "3 suoni"
                        },
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (phase >= 2) TapRipple(phase, Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun TimerArt() {
    val t = rememberInfiniteTransition(label = "timer")
    val progress by t.animateFloat(1f, 0f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "p")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(210.dp)
                .drawBehind {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(Palette.Lavender, Palette.Sky, Palette.Lavender)), -90f, 360f * progress, false,
                        Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Bedtime, null, tint = Palette.Sky, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    formatCountdown((progress * 30 * 60_000).toLong()),
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Text,
                )
                Text("poi sfuma nel silenzio", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("15 min", "30 min", "1 h", "2 h").forEach { label ->
                val on = label == "30 min"
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else Palette.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) Palette.Lavender.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ControlsArt() {
    val t = rememberInfiniteTransition(label = "ctrl")
    val progress by t.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "p")
    val remaining = ((1f - progress) * 30 * 60_000).toLong()
    BoxWithConstraints {
        val w = minOf(maxWidth, 320.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(w)) {
            // Chip "Live Update" della barra di stato.
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bedtime, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(formatCountdown(remaining), style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            Spacer(Modifier.height(14.dp))
            // Notifica.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF232A4A))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF134447)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.logo_white), null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Chiarella · ${formatCountdown(remaining)}", style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                }
                Spacer(Modifier.height(8.dp))
                Text("Notte serena", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                Text("In riproduzione · timer attivo", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(CircleShape).background(Palette.Sky))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("Pausa", "+15 min", "Stop").forEach {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = Palette.Sky)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge(Icons.Rounded.Widgets, "Widget")
                Badge(Icons.Rounded.Phone, "Pausa in chiamata")
                Badge(Icons.Rounded.Headphones, "Cuffie")
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Text, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Palette.Text, maxLines = 1)
    }
}

@Composable
private fun ReadyArt() {
    val t = rememberInfiniteTransition(label = "ready")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "r")
    val swing by t.animateFloat(-12f, 12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.logo_white),
            contentDescription = null,
            modifier = Modifier.width(150.dp),
        )
        Spacer(Modifier.height(30.dp))
        Box(
            Modifier
                .size(84.dp)
                .drawBehind {
                    drawCircle(Palette.Sky.copy(alpha = (1f - ring) * 0.5f), radius = size.minDimension / 2 * (1f + ring * 0.6f), style = Stroke(2.dp.toPx()))
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Lavender, Palette.Sky))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.NotificationsActive, null, tint = Color.White,
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer { rotationZ = swing },
            )
        }
    }
}
