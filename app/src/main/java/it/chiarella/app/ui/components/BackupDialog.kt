package it.chiarella.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.chiarella.app.audio.Mixer
import it.chiarella.app.data.Backup
import it.chiarella.app.data.BackupData
import it.chiarella.app.data.Store
import it.chiarella.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate

@Composable
fun BackupDialog(store: Store, mixer: Mixer, onDismiss: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Lo stesso file funziona su web, Windows e Android. Include profili, passioni, volumi e preferenze.") }
    var pending by remember { mutableStateOf<BackupData?>(null) }
    var busy by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                mixer.flushPreferences()
                val exported = Backup.stringify(store.backupData())
                withContext(Dispatchers.IO) {
                    (resolver.openOutputStream(uri, "wt") ?: throw IOException("Impossibile aprire il file.")).use {
                        it.write(exported.toByteArray(Charsets.UTF_8))
                    }
                }
                message = "Backup esportato. Conservalo per ripristinare i dati su qualsiasi dispositivo."
            } catch (e: Exception) { message = "Esportazione non riuscita: ${e.message}" }
            finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                pending = withContext(Dispatchers.IO) {
                    val bytes = (resolver.openInputStream(uri) ?: throw IOException("Impossibile aprire il file.")).use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > Backup.MAX_BYTES) throw IOException("Il file è troppo grande (massimo 2 MB).")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    Backup.parse(bytes.toString(Charsets.UTF_8))
                }
            } catch (e: Exception) { message = "Importazione non riuscita: ${e.message}" }
            finally { busy = false }
        }
    }

    val data = pending
    if (data != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null }, containerColor = Palette.SurfaceHigh,
            title = { Text("Importare il backup?") },
            text = { Text("${data.presets.size} profili e ${data.passions.size} passioni, con volumi e preferenze.\n\nSostituirà tutti i dati presenti su questo dispositivo. La riproduzione e il timer verranno fermati.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            mixer.flushPreferences()
                            withContext(NonCancellable) {
                                withContext(Dispatchers.IO) { store.importBackup(data) }
                                mixer.reloadFromStore()
                            }
                            pending = null
                            message = "Backup importato. Profili, passioni e preferenze sono pronti."
                        } catch (e: Exception) { pending = null; message = "Importazione non riuscita: ${e.message}" }
                        finally { busy = false }
                    }
                }) { Text(if (busy) "Importazione…" else "Sostituisci e importa") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Annulla") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = { if (!busy) onDismiss() }, containerColor = Palette.SurfaceHigh,
            title = { Text("Backup e ripristino") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (busy) "Operazione in corso…" else message)
                    TextButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
                        try {
                            mixer.flushPreferences()
                            Backup.stringify(store.backupData())
                            export.launch("Chiarella-backup-${LocalDate.now()}.json")
                        } catch (e: Exception) { message = "Esportazione non riuscita: ${e.message}" }
                    }) { Text("Esporta backup") }
                    TextButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Importa backup") }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Chiudi") } },
        )
    }
}
