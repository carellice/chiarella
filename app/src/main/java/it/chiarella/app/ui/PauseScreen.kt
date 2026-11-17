package it.chiarella.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.chiarella.app.audio.Mixer
import it.chiarella.app.ui.components.AuroraBackground
import it.chiarella.app.ui.components.RoundIconButton
import it.chiarella.app.ui.theme.Look

/**
 * "Pausa di un minuto", aperta dalla notifica: un respiro guidato con un suono acceso.
 * Se la musica era spenta parte l'ultimo mix (o il mare) e alla fine si rispegne.
 */
@Composable
fun PauseScreen(mixer: Mixer, onClose: () -> Unit) {
    DisposableEffect(mixer) {
        val startedHere = !mixer.state.value.playing
        if (startedHere) {
            mixer.resumeLastMix()
            if (!mixer.state.value.playing) mixer.playSounds(listOf("mare"))
        }
        onDispose { if (startedHere) mixer.pause() }
    }
    BackHandler(onBack = onClose)

    val aura = if (Look.skeuo) listOf(Color(0xFFFFB648), Color(0xFFE5794F), Color(0xFF6FB3A0))
        else listOf(Color(0xFF5B8DEF), Color(0xFF8B7CF6), Color(0xFF14B8A6))
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(aura, playing = true, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                RoundIconButton(Icons.Rounded.Close, "Chiudi", 40.dp, onClose)
            }
            Box(Modifier.weight(1f)) {
                BreathingExercise(onDone = onClose, title = "Un minuto solo per te")
            }
        }
    }
}
