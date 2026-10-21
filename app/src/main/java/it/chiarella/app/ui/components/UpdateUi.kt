package it.chiarella.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.chiarella.app.update.Release
import it.chiarella.app.update.UpdateState
import it.chiarella.app.update.Updater
import it.chiarella.app.ui.theme.Palette
import kotlin.math.roundToInt

/** Banner in cima alla home quando c'è una versione nuova (o un download in corso). */
@Composable
fun UpdateBanner(state: UpdateState, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val (title, subtitle) = when (state) {
        is UpdateState.Available -> "Nuova versione ${state.release.version}" to "Tocca per vedere le novità e aggiornare"
        is UpdateState.Downloading -> "Scarico la versione ${state.release.version}" to "${(state.progress * 100).roundToInt()}%"
        is UpdateState.ReadyToInstall -> "Versione ${state.release.version} pronta" to "Tocca per installarla"
        else -> return
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Palette.Lavender.copy(alpha = 0.35f), Palette.Sky.copy(alpha = 0.18f))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Lavender, Palette.Sky))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        if (state is UpdateState.Available) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, "Più tardi", tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(updater: Updater, state: UpdateState, onDismiss: () -> Unit) {
    val context = LocalContext.current
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
            Text("Aggiornamenti", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
            Spacer(Modifier.height(4.dp))
            Text(
                "Versione installata: ${updater.currentVersion}",
                style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted,
            )
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "update",
            ) { s ->
                when (s) {
                    UpdateState.Idle, UpdateState.Checking -> Centered {
                        CircularProgressIndicator(color = Palette.Lavender, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Controllo se ci sono novità…", color = Palette.TextMuted)
                    }
                    is UpdateState.UpToDate -> Centered {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF7FD6A4), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Hai già l'ultima versione", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Spacer(Modifier.height(16.dp))
                        GradientButton("Controlla di nuovo", Modifier.fillMaxWidth()) { updater.checkNow() }
                    }
                    is UpdateState.Available -> ReleaseDetails(s.release) {
                        val size = formatSize(s.release.apkSize)
                        GradientButton(if (size.isEmpty()) "Scarica e installa" else "Scarica e installa · $size", Modifier.fillMaxWidth()) {
                            updater.download(s.release)
                        }
                    }
                    is UpdateState.Downloading -> ReleaseDetails(s.release) {
                        DownloadBar(s.progress)
                    }
                    is UpdateState.ReadyToInstall -> ReleaseDetails(s.release) {
                        GradientButton("Installa la versione ${s.release.version}", Modifier.fillMaxWidth()) {
                            updater.install(context, s.file)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Android ti chiederà conferma. La prima volta ti chiederà anche di consentire a Chiarella " +
                                "di installare app: attiva l'opzione, torna qui e tocca di nuovo «Installa».",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                        )
                    }
                    is UpdateState.Failed -> Centered {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF9AA9), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(s.message, color = Palette.Text, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        GradientButton("Riprova", Modifier.fillMaxWidth()) {
                            s.release?.let(updater::download) ?: updater.checkNow()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}

@Composable
private fun ReleaseDetails(release: Release, actions: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Novità della versione ${release.version}", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                release.notes.ifBlank { "Miglioramenti e correzioni." },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
            )
        }
        Spacer(Modifier.height(18.dp))
        actions()
    }
}

@Composable
private fun DownloadBar(progress: Float) {
    val shown by animateFloatAsState(progress, tween(250), label = "dl")
    Column {
        Row {
            Text("Scaricamento in corso…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            Spacer(Modifier.weight(1f))
            Text("${(progress * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .drawBehind {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Palette.Lavender, Palette.Sky)),
                        size = Size(size.width * shown, size.height),
                        cornerRadius = CornerRadius(size.height / 2),
                    )
                }
        )
        Spacer(Modifier.width(1.dp))
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0) "" else "%.1f MB".format(bytes / 1_000_000.0).replace('.', ',')
