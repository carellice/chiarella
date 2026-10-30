package it.chiarella.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
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

@Composable
internal fun PreferencesDialog(preferences: AppPreferences, onChange: (AppPreferences) -> Unit, onClose: () -> Unit) {
    AlertDialog(
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
                    Switch(modifier = Modifier.semantics { contentDescription = "Avviso volume del dispositivo" }, checked = preferences.showVolumeNotice,
                        onCheckedChange = { onChange(preferences.copy(showVolumeNotice = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Animazioni ridotte", color = Palette.Text)
                        Spacer(Modifier.height(4.dp))
                        Text("Ferma sfondi, particelle e pulsazioni per rendere l'app più leggera. Audio, timer e respiro continuano a funzionare.", color = Palette.TextMuted)
                    }
                    Switch(modifier = Modifier.semantics { contentDescription = "Animazioni ridotte" }, checked = preferences.reducedAnimations,
                        onCheckedChange = { onChange(preferences.copy(reducedAnimations = it)) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Chiudi") } },
    )
}
