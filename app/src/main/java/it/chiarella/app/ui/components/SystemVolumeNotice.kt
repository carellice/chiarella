package it.chiarella.app.ui.components

import it.chiarella.app.ui.theme.look
import it.chiarella.app.ui.theme.Look
import it.chiarella.app.ui.theme.metroSwivel
import it.chiarella.app.ui.theme.Skeuo
import it.chiarella.app.ui.theme.keyCap
import it.chiarella.app.ui.theme.metalCap
import it.chiarella.app.ui.theme.leather
import androidx.compose.ui.graphics.Color
import it.chiarella.app.ui.theme.recessed
import it.chiarella.app.ui.theme.BrassBrush
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = { notice = null }) {
        Column(
            Modifier.fillMaxWidth()
                .look(
                    skeuo = { leather(shape, elevation = 24.dp) },
                    classic = {
                        clip(shape).background(Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Surface)))
                            .border(1.dp, Palette.GlassBorder, shape)
                    },
                    metro = { metroSwivel().background(Palette.SurfaceHigh) },
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).look(
                    skeuo = { metalCap() },
                    classic = {
                        clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Palette.Lavender.copy(alpha = 0.35f), Palette.Sky.copy(alpha = 0.25f))))
                            .border(1.dp, Palette.GlassBorder, CircleShape)
                    },
                    metro = { border(2.dp, Palette.Text, CircleShape) },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (volume.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeDown,
                    contentDescription = null, tint = Palette.Text, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Volume del dispositivo", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(8.dp).look(skeuo = { recessed(CircleShape, Color(0x66000000)) }, classic = { clip(CircleShape).background(Palette.Glass) }, metro = { background(Palette.Glass) })) {
                    Box(Modifier.fillMaxWidth(if (volume.muted) 0f else volume.percent / 100f).fillMaxHeight()
                        .clip(CircleShape).background(BrassBrush))
                }
                Spacer(Modifier.width(12.dp))
                Text(if (volume.muted) "Muto" else "${volume.percent}%", color = Palette.Text,
                    style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Per ascoltare Chiarella come previsto, porta il volume multimediale al 100% e regola l'ascolto con i volumi dell'app.",
                color = Palette.TextMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (canRaise) "La modifica riguarda anche le altre app multimediali."
                else "Android non consente di alzarlo dall'app: usa i tasti del volume.",
                color = Palette.TextFaint, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp)
                    .look(skeuo = { keyCap(RoundedCornerShape(14.dp), brass = true) }, classic = { clip(CircleShape).background(BrassBrush) }, metro = { background(Palette.Lavender) })
                    .clickable {
                        if (canRaise) {
                            notice = check.raiseToMaximum()?.takeIf { it.needsIncrease }
                            manualOnly = true
                        } else notice = null
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (canRaise) "Porta al 100%" else "Ho capito", color = if (Look.skeuo) Skeuo.Ink else Palette.NightDeep,
                    style = if (Look.skeuo) MaterialTheme.typography.titleMedium.copy(shadow = null) else MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (canRaise) TextButton(onClick = { notice = null }) { Text("Non ora", color = Palette.TextMuted) }
                TextButton(onClick = { notice = null; onDisable() }) { Text("Non mostrare più", color = Palette.TextMuted) }
            }
        }
    }
}
