package it.chiarella.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
                Column(Modifier.selectableGroup()) {
                    Text("Stile grafico", color = Palette.Text)
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        Triple(UiStyle.CLASSIC, "Classico", "Cielo notturno, vetro e luci soffuse."),
                        Triple(UiStyle.SKEUO, "Skeuomorfico", "Legno, pelle cucita, ottone e metallo: un vecchio apparecchio hi-fi."),
                        Triple(UiStyle.METRO, "Metro", "Riquadri piatti a tinta unita su nero, come su Windows Phone."),
                        Triple(UiStyle.NOTHING, "Nothing", "Nero OLED, punti, caratteri tecnici e una sola spia rossa."),
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
