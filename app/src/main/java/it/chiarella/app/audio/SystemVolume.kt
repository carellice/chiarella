package it.chiarella.app.audio

import android.media.AudioManager

internal data class SystemVolumeState(
    val current: Int,
    val maximum: Int,
    val muted: Boolean = false,
    val canAdjust: Boolean = true,
) {
    val needsIncrease get() = maximum > 0 && (muted || current < maximum)
    val percent get() = if (muted || maximum <= 0) 0 else current * 100 / maximum
}

internal interface SystemVolumeDevice {
    fun read(): SystemVolumeState
    fun setToMaximum()
}

/** Leggere il volume non lo modifica mai: la scrittura è un'azione esplicita dell'utente. */
internal class SystemVolumeCheck(private val device: SystemVolumeDevice) {
    fun inspect(): SystemVolumeState? = runCatching { device.read() }.getOrNull()
        ?.takeIf { it.maximum > 0 }

    fun raiseToMaximum(): SystemVolumeState? {
        val before = inspect() ?: return null
        if (!before.needsIncrease || !before.canAdjust) return before
        runCatching { device.setToMaximum() }
        // Il sistema può limitare il volume: si mostra sempre il risultato effettivo.
        return inspect()
    }
}

internal class AndroidSystemVolume(private val audio: AudioManager) : SystemVolumeDevice {
    override fun read() = SystemVolumeState(
        current = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
        canAdjust = !audio.isVolumeFixed,
    )

    override fun setToMaximum() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
        }
    }
}
