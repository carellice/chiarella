package it.chiarella.app.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Riproduce un file in loop infinito senza giunture.
 *
 * Invece di affidarsi alla ripetizione del player (che con alcuni formati compressi può lasciare
 * qualche millisecondo di silenzio fra la fine e il nuovo inizio), usa due player che si passano
 * il testimone: poco prima della fine parte il secondo dall'inizio e i due si incrociano con una
 * dissolvenza a potenza costante. Qualsiasi imperfezione di decodifica in coda cade a volume zero.
 */
internal class LoopVoice(context: Context, @RawRes res: Int, private val seamless: Boolean = false) {

    private val players = Array(if (seamless) 1 else 2) { createPlayer(context, res) }
    private var current = 0
    private var crossfading = false

    /** Volume lineare già "percettivo" (0..1) impostato dal mixer. */
    var level = 0f

    val isStarted: Boolean get() = players[current].playWhenReady

    fun play() {
        players[current].play()
        if (crossfading) players[1 - current].play()
    }

    fun pause() = players.forEach { it.pause() }

    fun release() = players.forEach { it.release() }

    /** Applica volumi e passaggi di testimone. Restituisce fra quanti ms serve la prossima chiamata. */
    fun update(): Long {
        // Un loop già pronto si ripete integralmente, senza sovrapporre la coda all'inizio.
        if (seamless) {
            players[0].volume = level
            return IDLE_POLL_MS
        }
        val a = players[current]
        val b = players[1 - current]
        val duration = a.duration
        var progress = 0f
        var wait = IDLE_POLL_MS

        if (a.playWhenReady && duration != C.TIME_UNSET && duration > OVERLAP_MS * 2) {
            val start = duration - OVERLAP_MS
            val position = a.currentPosition
            val ended = a.playbackState == Player.STATE_ENDED
            if (position >= start || ended) {
                if (!crossfading) {
                    crossfading = true
                    b.seekTo(0)
                    b.play()
                }
                progress = if (ended) 1f else ((position - start).toFloat() / OVERLAP_MS).coerceIn(0f, 1f)
                wait = FADE_POLL_MS
                if (progress >= 1f) {
                    // Il secondo player diventa quello principale; il primo si riavvolge e attende.
                    a.pause()
                    a.volume = 0f
                    a.seekTo(0)
                    current = 1 - current
                    crossfading = false
                    progress = 0f
                }
            } else {
                wait = (start - position).coerceIn(FADE_POLL_MS, IDLE_POLL_MS)
            }
        }

        val angle = progress * PI.toFloat() / 2
        players[current].volume = level * cos(angle)
        players[1 - current].volume = if (crossfading) level * sin(angle) else 0f
        return wait
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(context: Context, res: Int): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$res")))
                repeatMode = if (seamless) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = 0f
                prepare()
            }

    companion object {
        /** Durata della dissolvenza fra le due copie del file. */
        const val OVERLAP_MS = 2_500L
        private const val FADE_POLL_MS = 30L
        private const val IDLE_POLL_MS = 1_000L
    }
}
