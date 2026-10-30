package it.chiarella.app.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import it.chiarella.app.R

/** Segnali singoli, precaricati per accompagnare l'inizio di ogni fase del respiro. */
internal class BreathAudio(context: Context) {
    private var released = false
    private val players = listOf(R.raw.respiro_inspira, R.raw.respiro_espira).map { res ->
        ExoPlayer.Builder(context)
            // Condivide l'uscita con il mix, senza sottrargli l'audio focus.
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$res")))
                prepare()
            }
    }

    fun play(inhaling: Boolean, volume: Float) {
        if (released) return
        stop()
        players[if (inhaling) 0 else 1].apply {
            this.volume = volume.coerceIn(0f, 1f)
            play()
        }
    }

    fun stop() {
        if (!released) players.forEach { it.pause(); it.seekTo(0) }
    }

    fun release() {
        released = true
        players.forEach { it.release() }
    }
}
