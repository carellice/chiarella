package it.chiarella.app.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Registra un messaggio vocale in PCM 16 kHz mono (il formato che Whisper preferisce)
 * e lo restituisce come file WAV. [level] segue il volume della voce per l'animazione.
 */
class VoiceRecorder {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Millisecondi registrati finora. */
    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    val isRecording get() = running

    /** Richiede il permesso RECORD_AUDIO già concesso. Ritorna false se il microfono non è disponibile. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        pcm.reset()
        _elapsed.value = 0L
        _level.value = 0f
        record = rec
        running = true
        rec.startRecording()
        thread = Thread({
            val buf = ShortArray(RATE / 20)
            val bytes = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            var samples = 0L
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                bytes.clear()
                var sum = 0.0
                for (i in 0 until n) {
                    bytes.putShort(buf[i])
                    sum += buf[i].toDouble() * buf[i]
                }
                synchronized(pcm) { pcm.write(bytes.array(), 0, n * 2) }
                samples += n
                _elapsed.value = samples * 1000 / RATE
                // RMS normalizzato con una curva morbida: la voce normale arriva a circa metà.
                _level.value = (sqrt(sum / n) / 6000.0).coerceIn(0.0, 1.0).toFloat()
                if (samples >= RATE * MAX_SECONDS) running = false
            }
        }, "chiarella-voice").apply { start() }
        return true
    }

    /** Ferma la registrazione e restituisce il WAV, o null se troppo corto. */
    fun stop(): ByteArray? {
        running = false
        thread?.join(500)
        thread = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        _level.value = 0f
        val data = synchronized(pcm) { pcm.toByteArray() }
        pcm.reset()
        if (data.size < RATE * 2 * MIN_MS / 1000) return null
        return wav(data)
    }

    fun cancel() {
        stop()
        _elapsed.value = 0L
    }

    private fun wav(data: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }
        return header.array() + data
    }

    companion object {
        const val RATE = 16_000
        const val MAX_SECONDS = 120
        private const val MIN_MS = 600
    }
}
