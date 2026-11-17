package it.chiarella.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import it.chiarella.app.ui.theme.Look
import it.chiarella.app.ui.theme.Nothing
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import it.chiarella.app.data.UiStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import it.chiarella.app.data.AppPreferences
import it.chiarella.app.ui.theme.Palette
import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import it.chiarella.app.pause.PauseReminder

@Composable
internal fun PreferencesDialog(preferences: AppPreferences, onChange: (AppPreferences) -> Unit, onClose: () -> Unit) {
    LeatherAlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.SurfaceHigh,
        title = { Text("Impostazioni") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avviso volume del dispositivo", color = Palette.Text)
                        Spacer(Modifier.height(4.dp))
                        Text("Mostra l'avviso all'apertura se il volume non è al massimo.", color = Palette.TextMuted)
                    }
                    Switch(colors = switchColors(), modifier = Modifier.semantics { contentDescription = "Avviso volume del dispositivo" }, checked = preferences.showVolumeNotice,
                        onCheckedChange = { onChange(preferences.copy(showVolumeNotice = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Animazioni ridotte", color = Palette.Text)
                        Spacer(Modifier.height(4.dp))
                        Text("Ferma sfondi, particelle e pulsazioni per rendere l'app più leggera. Audio, timer e respiro continuano a funzionare.", color = Palette.TextMuted)
                    }
                    Switch(colors = switchColors(), modifier = Modifier.semantics { contentDescription = "Animazioni ridotte" }, checked = preferences.reducedAnimations,
                        onCheckedChange = { onChange(preferences.copy(reducedAnimations = it)) })
                }
                PauseSettings(preferences, onChange)
                Column(Modifier.selectableGroup()) {
                    Text("Stile grafico", color = Palette.Text)
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        Triple(UiStyle.CLASSIC, "Classico", "Cielo notturno, vetro e luci soffuse."),
                        Triple(UiStyle.SKEUO, "Skeuomorfico", "Legno, pelle cucita, ottone e metallo: un vecchio apparecchio hi-fi."),
                        Triple(UiStyle.METRO, "Metro", "Riquadri piatti a tinta unita su nero, come su Windows Phone."),
                        Triple(UiStyle.NOTHING, "Nothing", "Nero OLED, punti, caratteri tecnici e una sola spia rossa."),
                        Triple(UiStyle.DUEFFE, "Dueffe", "Nero caldo, avorio e oro, con nebulose dorate e pulviscolo."),
                    ).forEach { (style, label, hint) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(preferences.style == style, role = Role.RadioButton) { onChange(preferences.copy(style = style)) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = preferences.style == style, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(label, color = Palette.Text)
                                Text(hint, color = Palette.TextMuted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Chiudi") } },
    )
}

/** "Pausa di un minuto": interruttore e orari (al massimo tre). */
@Composable
private fun PauseSettings(preferences: AppPreferences, onChange: (AppPreferences) -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val times = preferences.pauseTimes.sorted()

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60,
            DateFormat.is24HourFormat(context)).show()
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pausa di un minuto", color = Palette.Text)
                Spacer(Modifier.height(4.dp))
                Text("Negli orari scelti arriva una notifica gentile: un tocco e respiri un minuto con un suono acceso.", color = Palette.TextMuted)
            }
            Switch(colors = switchColors(), modifier = Modifier.semantics { contentDescription = "Pausa di un minuto" }, checked = preferences.pauseReminders,
                onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onChange(preferences.copy(pauseReminders = on))
                })
        }
        if (preferences.pauseReminders) {
            times.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickTime(t) { n -> onChange(preferences.copy(pauseTimes = times - t + n)) } }) {
                        Text("%02d:%02d".format(t / 60, t % 60))
                    }
                    Spacer(Modifier.weight(1f))
                    if (times.size > 1) IconButton(onClick = { onChange(preferences.copy(pauseTimes = times - t)) }) {
                        Icon(Icons.Rounded.Close, "Togli la pausa delle %02d:%02d".format(t / 60, t % 60), tint = Palette.TextMuted)
                    }
                }
            }
            if (times.size < PauseReminder.MAX_TIMES) TextButton(onClick = {
                pickTime(times.lastOrNull()?.plus(120)?.rem(24 * 60) ?: (12 * 60)) { n -> onChange(preferences.copy(pauseTimes = times + n)) }
            }) { Text("+ Aggiungi un orario") }
        }
    }
}

/** Nothing: spento, pomello e bordo grigi ben visibili sul nero (il bordo predefinito #333 sparisce). */
@Composable
private fun switchColors() = if (Look.nothing) {
    SwitchDefaults.colors(uncheckedThumbColor = Nothing.TextSecondary, uncheckedBorderColor = Nothing.TextSecondary, uncheckedTrackColor = Nothing.Black)
} else {
    SwitchDefaults.colors()
}
