package it.chiarella.app.audio

import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import it.chiarella.app.data.Preset
import it.chiarella.app.data.Store
import it.chiarella.app.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Cuore dell'app: un loop senza giunture (LoopVoice) per ogni suono attivo, con volumi indipendenti,
 * dissolvenze morbide, timer di spegnimento e gestione dell'audio focus.
 * Tutto gira sul main thread (richiesto da ExoPlayer).
 */
class Mixer(private val context: Context, private val store: Store) {

    data class State(
        /** Volume scelto per ogni suono (0..1), ricordato anche quando il suono è spento. */
        val volumes: Map<String, Float> = emptyMap(),
        /** Suoni attivi, nell'ordine in cui sono stati accesi. */
        val active: List<String> = emptyList(),
        val playing: Boolean = false,
        /** In pausa temporanea per una chiamata o un'altra app: riprende da solo. */
        val interrupted: Boolean = false,
        val timerEndsAt: Long = 0L,
        val timerDurationMs: Long = 0L,
        val presetId: String? = null,
        /** Volume generale (0..1): si applica sopra a quello di ogni suono. */
        val master: Float = 1f,
    ) {
        val hasMix get() = active.isNotEmpty()
        val timerActive get() = timerEndsAt > 0L
        fun volume(id: String) = volumes[id] ?: DEFAULT_VOLUME
        fun isActive(id: String) = id in active
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State(volumes = store.volumes, master = store.masterVolume))
    val state: StateFlow<State> = _state.asStateFlow()

    private val voices = HashMap<String, LoopVoice>()
    private val gains = HashMap<String, Float>()
    private var fadeMs = FADE_NORMAL
    private var ticker: Job? = null
    private var timerJob: Job? = null
    private var saveJob: Job? = null

    // ---------------------------------------------------------------- comandi

    fun toggleSound(id: String) {
        val s = _state.value
        if (s.isActive(id)) {
            val remaining = s.active - id
            _state.update {
                it.copy(active = remaining, presetId = null, playing = it.playing && remaining.isNotEmpty())
            }
            if (remaining.isEmpty()) abandonFocus()
        } else {
            _state.update {
                val vol = it.volumes[id]?.takeIf { v -> v > 0.02f } ?: DEFAULT_VOLUME
                it.copy(active = it.active + id, volumes = it.volumes + (id to vol), presetId = null)
            }
            play()
        }
        persist()
        kick()
    }

    fun setVolume(id: String, volume: Float) {
        _state.update { it.copy(volumes = it.volumes + (id to volume.coerceIn(0f, 1f)), presetId = null) }
        persist()
        kick()
    }

    /** Alza o abbassa tutta l'app insieme; i volumi dei singoli suoni (e dei profili) restano invariati. */
    fun setMasterVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _state.update { it.copy(master = v) }
        store.masterVolume = v
        kick()
    }

    fun play() {
        val s = _state.value
        if (!s.hasMix) return
        if (!requestFocus()) return
        fadeMs = FADE_NORMAL
        s.active.forEach { voice(it).play() }
        _state.update { it.copy(playing = true, interrupted = false) }
        startService()
        kick()
    }

    fun pause(fade: Long = FADE_NORMAL, interrupted: Boolean = false) {
        val s = _state.value
        if (!s.playing) {
            // Pausa manuale durante un'interruzione (es. chiamata): non riprendere da solo dopo.
            if (s.interrupted && !interrupted) {
                _state.update { it.copy(interrupted = false) }
                abandonFocus()
            }
            return
        }
        fadeMs = fade
        _state.update { it.copy(playing = false, interrupted = interrupted) }
        if (!interrupted) abandonFocus()
        kick()
    }

    fun togglePlay() {
        val s = _state.value
        when {
            s.playing || s.interrupted -> pause()
            s.hasMix -> play()
            else -> resumeLastMix()
        }
    }

    /** Riprende l'ultimo mix ascoltato (o il primo profilo): usato da widget e notifica. */
    fun resumeLastMix() {
        val last = store.lastMix.filterKeys { Sounds[it] != null }
        if (last.isNotEmpty()) applyMix(last, null)
        else store.presets.value.firstOrNull()?.let(::applyPreset)
    }

    fun applyPreset(preset: Preset) {
        val s = _state.value
        if (s.presetId == preset.id && s.playing) {
            pause()
            return
        }
        applyMix(preset.mix, preset.id)
    }

    private fun applyMix(mix: Map<String, Float>, presetId: String?) {
        val valid = mix.filterKeys { Sounds[it] != null }
        if (valid.isEmpty()) return
        _state.update { it.copy(volumes = it.volumes + valid, active = valid.keys.toList(), presetId = presetId) }
        persist()
        play()
    }

    fun stopAll() {
        fadeMs = FADE_NORMAL
        timerJob?.cancel()
        _state.update {
            it.copy(active = emptyList(), playing = false, interrupted = false, presetId = null,
                timerEndsAt = 0L, timerDurationMs = 0L)
        }
        abandonFocus()
        kick()
    }

    /** Include anche l'ultimo trascinamento dello slider, senza aspettare il salvataggio differito. */
    fun flushPreferences() {
        saveJob?.cancel()
        val s = _state.value
        store.volumes = s.volumes
        if (s.hasMix) store.lastMix = s.active.associateWith { s.volume(it) }
    }

    fun reloadFromStore() {
        saveJob?.cancel()
        stopAll()
        _state.value = State(volumes = store.volumes, master = store.masterVolume)
    }

    // ---------------------------------------------------------------- timer

    fun startTimer(minutes: Int) {
        store.lastTimerMinutes = minutes
        val duration = minutes * 60_000L
        val end = System.currentTimeMillis() + duration
        _state.update { it.copy(timerEndsAt = end, timerDurationMs = duration) }
        scheduleTimer(end)
        if (!_state.value.playing) play()
    }

    fun extendTimer(minutes: Int) {
        val s = _state.value
        if (!s.timerActive) return startTimer(minutes)
        val extra = minutes * 60_000L
        val end = s.timerEndsAt + extra
        _state.update { it.copy(timerEndsAt = end, timerDurationMs = it.timerDurationMs + extra) }
        scheduleTimer(end)
    }

    fun cancelTimer() {
        timerJob?.cancel()
        _state.update { it.copy(timerEndsAt = 0L, timerDurationMs = 0L) }
    }

    private fun scheduleTimer(end: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            // La dissolvenza finale parte prima, così il silenzio arriva esattamente allo scadere.
            delay((end - System.currentTimeMillis() - FADE_TIMER).coerceAtLeast(0L))
            pause(fade = (end - System.currentTimeMillis()).coerceIn(1_500L, FADE_TIMER))
            delay((end - System.currentTimeMillis()).coerceAtLeast(0L))
            _state.update { it.copy(timerEndsAt = 0L, timerDurationMs = 0L) }
        }
    }

    // ---------------------------------------------------------------- motore

    private fun voice(id: String): LoopVoice = voices.getOrPut(id) {
        gains[id] = 0f
        LoopVoice(context, requireNotNull(Sounds[id]).res)
    }

    /**
     * Avvia (o riavvia subito) il ciclo che porta ogni suono verso il proprio volume e gestisce i
     * passaggi di testimone dei loop. Gira veloce durante le dissolvenze, poi si dirada.
     */
    private fun kick() {
        ticker?.cancel()
        ticker = scope.launch {
            var last = SystemClock.uptimeMillis()
            var wait = 16L
            while (wait >= 0) {
                delay(wait)
                val now = SystemClock.uptimeMillis()
                wait = step(now - last)
                last = now
            }
        }
    }

    /** Un passo del motore. Restituisce l'attesa prima del prossimo, o -1 se non c'è più nulla da fare. */
    private fun step(dt: Long): Long {
        val s = _state.value
        val delta = dt.toFloat() / fadeMs
        var moving = false
        var nextWait = Long.MAX_VALUE
        val finished = ArrayList<String>()
        for ((id, v) in voices) {
            val target = if (s.playing && s.isActive(id)) s.volume(id) else 0f
            val cur = gains[id] ?: 0f
            val next = if (cur < target) min(cur + delta, target) else max(cur - delta, target)
            gains[id] = next
            // Curva percettiva (quadratica) sia per il suono sia per il volume generale.
            v.level = next * next * s.master * s.master
            if (next != target) {
                moving = true
            } else if (next == 0f && !s.isActive(id)) {
                finished.add(id)
            } else if (next == 0f && !s.playing && v.isStarted) {
                v.pause()
            }
            val wait = v.update()
            if (v.isStarted) nextWait = min(nextWait, wait)
        }
        // Suoni spenti e ormai silenziosi: libera le risorse.
        for (id in finished) {
            voices.remove(id)?.release()
            gains.remove(id)
        }
        if (!moving) fadeMs = FADE_NORMAL
        return when {
            moving -> 16L
            nextWait != Long.MAX_VALUE -> nextWait
            else -> -1L
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        } catch (e: Exception) {
            // In background alcuni avvii non sono permessi: il servizio è già attivo in quel caso.
            Log.w(TAG, "Impossibile avviare il servizio", e)
        }
    }

    private fun persist() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            val s = _state.value
            store.volumes = s.volumes
            if (s.hasMix) store.lastMix = s.active.associateWith { s.volume(it) }
        }
    }

    // ---------------------------------------------------------------- audio focus

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var hasFocus = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change -> scope.launch { onFocusChange(change) } }
        .build()

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> if (_state.value.interrupted) {
                hasFocus = true
                play()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause(interrupted = true)
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                pause()
            }
        }
    }

    companion object {
        private const val TAG = "Mixer"
        const val DEFAULT_VOLUME = 0.6f
        private const val FADE_NORMAL = 700L
        private const val FADE_TIMER = 12_000L
    }
}
