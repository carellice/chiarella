package it.chiarella.app.playback

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Avvia o mette in pausa l'ultimo mix e si chiude subito, senza mostrare nulla.
 * Serve ai launcher (Kanso): un servizio in primo piano si può avviare solo da un'app visibile,
 * e questa activity trasparente lo è per un istante.
 */
class ToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_TOGGLE))
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
