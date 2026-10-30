package it.chiarella.app.ui.components

import android.media.AudioManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import it.chiarella.app.audio.AndroidSystemVolume
import it.chiarella.app.audio.SystemVolumeCheck
import it.chiarella.app.audio.SystemVolumeState
import it.chiarella.app.ui.theme.Palette
import kotlinx.coroutines.delay

/** Controllo a ogni apertura in primo piano, con un solo invito per apertura. */
@Composable
internal fun SystemVolumeNotice(enabled: Boolean, onDisable: () -> Unit) {
    val context = LocalContext.current
    val check = remember(context) {
        SystemVolumeCheck(AndroidSystemVolume(context.getSystemService(AudioManager::class.java)))
    }
    var notice by remember { mutableStateOf<SystemVolumeState?>(null) }
    var manualOnly by remember { mutableStateOf(false) }
    val enabledNow by rememberUpdatedState(enabled)

    LaunchedEffect(enabled) { if (!enabled) notice = null }

    LifecycleStartEffect(check) {
        manualOnly = false
        notice = if (enabledNow) check.inspect()?.takeIf { it.needsIncrease } else null
        onStopOrDispose { notice = null }
    }

    // Aggiorna o chiude l'avviso anche quando si usano i tasti fisici del volume.
    LaunchedEffect(notice != null) {
        while (notice != null) {
            delay(300)
            notice = check.inspect()?.takeIf { it.needsIncrease }
        }
    }

    val volume = notice ?: return
    val canRaise = volume.canAdjust && !manualOnly
    AlertDialog(
        onDismissRequest = { notice = null },
        containerColor = Palette.SurfaceHigh,
        title = { Text("Volume del dispositivo") },
        text = {
            val current = if (volume.muted) "Il volume multimediale del dispositivo è disattivato."
                else "Il volume multimediale del dispositivo è al ${volume.percent}%."
            Text("$current Per ascoltare Chiarella come previsto, portalo al 100% e regola l'ascolto con i volumi dell'app.\n\n" +
                if (canRaise) "Possiamo alzarlo per te: la modifica riguarda anche le altre app multimediali."
                else "Android non consente di portarlo al massimo dall'app. Puoi regolarlo dai comandi del dispositivo.")
        },
        confirmButton = {
            TextButton(onClick = {
                if (canRaise) {
                    notice = check.raiseToMaximum()?.takeIf { it.needsIncrease }
                    manualOnly = true
                } else notice = null
            }) { Text(if (canRaise) "Porta al 100%" else "Ho capito", color = Palette.Peach) }
        },
        dismissButton = {
            Column {
                if (canRaise) TextButton(onClick = { notice = null }) { Text("Non ora", color = Palette.TextMuted) }
                TextButton(onClick = { notice = null; onDisable() }) { Text("Non mostrare più", color = Palette.TextMuted) }
            }
        },
    )
}
