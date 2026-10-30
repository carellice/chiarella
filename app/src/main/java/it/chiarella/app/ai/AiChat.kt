package it.chiarella.app.ai

import android.content.Context
import it.chiarella.app.audio.Mixer
import it.chiarella.app.audio.SoundInfos
import it.chiarella.app.audio.Sounds
import it.chiarella.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Cosa l'assistente propone di fare: diventa un pulsante sotto il suo messaggio. */
sealed interface ChatAction {
    data class PlaySounds(val ids: List<String>) : ChatAction
    data object Breathe : ChatAction
    data object Moment : ChatAction
}

data class ChatMessage(
    val id: String,
    val role: Role,
    /** Testo così come arriva dal modello (con le eventuali azioni tra doppie quadre). */
    val content: String,
    /** Messaggio dell'utente nato da un vocale: [content] è la trascrizione. */
    val voice: Boolean = false,
) {
    /**
     * USER: scritto dall'utente. ASSISTANT: risposta dell'IA. EVENT: qualcosa che l'utente
     * ha fatto nell'app (es. ha finito il respiro), mostrato come nota e inviato come contesto.
     */
    enum class Role { USER, ASSISTANT, EVENT }

    /** Testo da mostrare, senza le azioni (e senza un'azione lasciata a metà durante lo streaming). */
    val text: String get() = ACTION.replace(content, "").replace(PARTIAL_ACTION, "").trim()

    val actions: List<ChatAction>
        get() = ACTION.findAll(content).mapNotNull { m ->
            when (m.groupValues[1].lowercase()) {
                "suoni", "suono" -> m.groupValues[2].split(',', ' ', ';')
                    .map { it.trim().lowercase() }.filter { Sounds[it] != null }.distinct()
                    .takeIf { it.isNotEmpty() }?.let { ChatAction.PlaySounds(it.take(4)) }
                "respiro" -> ChatAction.Breathe
                "momento" -> ChatAction.Moment
                else -> null
            }
        }.distinct().toList()

    companion object {
        private val ACTION = Regex("""\[\[\s*(\w+)\s*(?::\s*([^\]]*))?]]""")
        private val PARTIAL_ACTION = Regex("""\[\[?[^\]]*$""")
    }
}

/** Credenziali di Cloudflare Workers AI inserite dall'utente. */
data class AiConfig(val accountId: String, val token: String, val model: String) {
    val ready get() = accountId.isNotBlank() && token.isNotBlank()
}

/**
 * Chat di supporto con Cloudflare Workers AI: aiuta a sciogliere i pensieri che girano in testa,
 * con domande, piccoli esercizi, suoni dell'app, il respiro guidato e le passioni dell'utente.
 *
 * Credenziali e conversazione stanno in un file di preferenze a parte, escluso dal backup
 * su cloud di Android e dal file di backup di Chiarella.
 */
class AiChat(context: Context, private val store: Store, private val mixer: Mixer) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _config = MutableStateFlow(
        AiConfig(
            prefs.getString(KEY_ACCOUNT, "").orEmpty(),
            prefs.getString(KEY_TOKEN, "").orEmpty(),
            prefs.getString(KEY_MODEL, null) ?: MODELS.first().id,
        )
    )
    val config: StateFlow<AiConfig> = _config.asStateFlow()

    private val _messages = MutableStateFlow(loadMessages())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Un vocale è in trascrizione. */
    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ---------------------------------------------------------------- impostazioni

    /** Prova le credenziali con una richiesta minima e, se funzionano, le salva. */
    suspend fun saveConfig(accountId: String, token: String, model: String): Result<Unit> {
        val candidate = AiConfig(accountId.trim(), token.trim(), model)
        if (!candidate.ready) return Result.failure(IOException("Inserisci Account ID e token."))
        return runCatching {
            withContext(Dispatchers.IO) {
                complete(candidate, listOf(JSONObject().put("role", "user").put("content", "Rispondi solo: ok")), maxTokens = 4) {}
            }
            prefs.edit()
                .putString(KEY_ACCOUNT, candidate.accountId)
                .putString(KEY_TOKEN, candidate.token)
                .putString(KEY_MODEL, candidate.model)
                .apply()
            _config.value = candidate
        }
    }

    fun removeConfig() {
        stop()
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_TOKEN).apply()
        _config.update { it.copy(accountId = "", token = "") }
    }

    // ---------------------------------------------------------------- conversazione

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _busy.value) return
        append(ChatMessage(UUID.randomUUID().toString(), ChatMessage.Role.USER, clean))
        reply()
    }

    /**
     * Messaggio vocale: l'audio (WAV) viene trascritto da Whisper su Cloudflare Workers AI
     * e la trascrizione parte come un normale messaggio, segnato come vocale.
     */
    fun sendVoice(wav: ByteArray) {
        val cfg = _config.value
        if (!cfg.ready || _busy.value || _transcribing.value) return
        _error.value = null
        _transcribing.value = true
        job = scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { transcribe(cfg, wav) } }
            _transcribing.value = false
            result.onSuccess { text ->
                if (text.isBlank()) _error.value = "Non ho sentito parole nel vocale. Riprova parlando un po' più vicino al telefono."
                else {
                    append(ChatMessage(UUID.randomUUID().toString(), ChatMessage.Role.USER, text, voice = true))
                    reply()
                }
            }.onFailure { e ->
                if (e !is kotlinx.coroutines.CancellationException) _error.value = e.message ?: "Non sono riuscita a trascrivere il vocale."
            }
        }
    }

    /** Un gesto fatto nell'app (respiro finito, suoni accesi…): l'assistente ne tiene conto e prosegue. */
    fun sendEvent(text: String, answer: Boolean = true) {
        append(ChatMessage(UUID.randomUUID().toString(), ChatMessage.Role.EVENT, text))
        if (answer && !_busy.value) reply()
    }

    /** Dopo aver configurato l'IA: risponde a quello che era rimasto in attesa. */
    fun replyIfPending() {
        val last = _messages.value.lastOrNull() ?: return
        if (last.role != ChatMessage.Role.ASSISTANT && !_busy.value) reply()
    }

    fun retry() {
        if (_busy.value) return
        // Se l'ultima risposta era rimasta a metà, la si scarta e si riprova.
        _messages.update { list -> if (list.lastOrNull()?.role == ChatMessage.Role.ASSISTANT) list.dropLast(1) else list }
        reply()
    }

    fun reset() {
        stop()
        _messages.value = emptyList()
        _error.value = null
        save()
    }

    fun stop() {
        job?.cancel()
        job = null
        _busy.value = false
        _transcribing.value = false
    }

    /** Esegue un'azione proposta dall'assistente che non richiede di cambiare schermata. */
    fun playSounds(ids: List<String>) {
        mixer.playSounds(ids)
        sendEvent("Ho acceso: " + ids.mapNotNull { Sounds[it]?.name }.joinToString(", ") + ".", answer = false)
    }

    private fun reply() {
        val cfg = _config.value
        if (!cfg.ready) return
        _error.value = null
        _busy.value = true
        val id = UUID.randomUUID().toString()
        val history = buildRequest()
        job = scope.launch {
            var text = ""
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    complete(cfg, history, maxTokens = 700) { piece ->
                        text += piece
                        val snapshot = text
                        scope.launch { upsert(ChatMessage(id, ChatMessage.Role.ASSISTANT, snapshot)) }
                    }
                }
            }
            _busy.value = false
            result.exceptionOrNull()?.let { e ->
                if (e is kotlinx.coroutines.CancellationException) return@let
                if (text.isBlank()) _messages.update { list -> list.filterNot { it.id == id } }
                _error.value = e.message ?: "Qualcosa è andato storto."
            }
            if (result.isSuccess && text.isBlank()) _error.value = "La risposta è arrivata vuota. Riprova."
            save()
        }
    }

    private fun buildRequest(): List<JSONObject> {
        val list = mutableListOf(JSONObject().put("role", "system").put("content", systemPrompt()))
        // Solo gli ultimi messaggi: bastano per il filo del discorso e la richiesta resta leggera.
        _messages.value.takeLast(MAX_HISTORY).forEach { m ->
            val (role, content) = when (m.role) {
                ChatMessage.Role.USER -> "user" to if (m.voice) "(messaggio vocale trascritto) ${m.content}" else m.content
                ChatMessage.Role.ASSISTANT -> "assistant" to m.content
                ChatMessage.Role.EVENT -> "user" to "[Nota dall'app: ${m.content}]"
            }
            // Il modello vuole ruoli alternati: messaggi consecutivi dello stesso ruolo vengono uniti.
            val last = list.last()
            if (list.size > 1 && last.getString("role") == role) last.put("content", last.getString("content") + "\n\n" + content)
            else list += JSONObject().put("role", role).put("content", content)
        }
        return list
    }

    private fun systemPrompt(): String {
        val state = mixer.state.value
        val sounds = Sounds.all.joinToString("\n") { "- ${it.id}: ${it.name} (${it.tagline}). ${SoundInfos[it.id].description}" }
        val playing = if (state.playing && state.hasMix) state.active.mapNotNull { Sounds[it]?.name }.joinToString(", ") else "nessuno"
        val passions = store.passions.value.joinToString("\n") { "- ${it.category.label}" + if (it.detail.isNotBlank()) ": ${it.detail}" else "" }
            .ifBlank { "nessuna (non le ha ancora inserite)" }
        val presets = store.presets.value.joinToString(", ") { it.name }.ifBlank { "nessuno" }
        return """
Sei Chiarella, la voce gentile dell'app di suoni rilassanti "Chiarella". Parli in italiano, dai del tu, con calore e semplicità.
Il tuo compito: aiutare chi ti scrive quando ha la testa piena, è in ansia, confuso o in overthinking, ad arginare il flusso di pensieri e a ritrovare un po' di calma.

Come ti comporti:
- Risposte brevi: 2-5 frasi. Una sola domanda alla volta. Niente elenchi lunghi, niente prediche.
- Prima ascolti e rifletti quello che senti ("sembra che…"), poi fai una domanda che aiuti a mettere ordine.
- Tecniche che puoi proporre, una per volta e adattandole: dare un nome al pensiero, separare ciò che si può controllare da ciò che non si può, scrivere i pensieri su un foglio, il radicamento 5-4-3-2-1 con i sensi, rimandare la preoccupazione a un orario preciso, chiedersi "cosa direi a un amico?".
- Chiedi alla persona quali suoni la rilassano e proponi quelli dell'app; chiedi se le va un breve esercizio di respirazione; quando la mente è un po' più quieta, proponi qualcosa tra le sue passioni.
- Non fai diagnosi, non parli di farmaci, non ti sostituisci a uno psicologo: se il malessere dura da tempo, suggerisci con delicatezza di parlarne con un professionista.
- Se la persona parla di farsi del male, di non voler vivere o di essere in pericolo: rispondi con calore, prendila sul serio e invitala a chiamare subito il 112 o il Telefono Amico (02 2327 2327), o a contattare una persona di fiducia. In quel caso non proporre suoni né giochi.

Azioni: puoi aggiungere in fondo alla risposta, su una riga a parte, dei comandi che l'app trasforma in pulsanti. Usali solo quando la persona è d'accordo o quando è davvero il momento, al massimo uno o due per risposta:
- [[suoni: id1, id2]] per proporre di accendere dei suoni (da 1 a 3, usa solo gli id qui sotto)
- [[respiro]] per proporre l'esercizio di respirazione guidata (circa un minuto)
- [[momento]] per aprire "Un momento per te", che sceglie una delle sue passioni
Non spiegare i comandi e non scriverli in mezzo al testo.

Suoni disponibili (id: nome):
$sounds

Suoni in riproduzione adesso: $playing.
Profili di suoni salvati dalla persona: $presets.
Le sue passioni:
$passions

Le note tra parentesi quadre che iniziano con "Nota dall'app" descrivono cosa la persona ha appena fatto nell'app: tienine conto e prosegui con naturalezza.
""".trim()
    }

    private fun append(message: ChatMessage) {
        _messages.update { it + message }
        save()
    }

    private fun upsert(message: ChatMessage) {
        _messages.update { list ->
            if (list.any { it.id == message.id }) list.map { if (it.id == message.id) message else it } else list + message
        }
    }

    // ---------------------------------------------------------------- rete

    /**
     * Chiama `ai/run` in streaming (server-sent events) e passa a [onPiece] i pezzi di testo
     * man mano che arrivano.
     */
    private fun complete(cfg: AiConfig, messages: List<JSONObject>, maxTokens: Int, onPiece: (String) -> Unit) {
        val url = URL("https://api.cloudflare.com/client/v4/accounts/${cfg.accountId}/ai/run/${cfg.model}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${cfg.token}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            val body = JSONObject()
                .put("messages", JSONArray(messages))
                .put("max_tokens", maxTokens)
                .put("temperature", 0.6)
                .put("stream", true)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                throw IOException("Nessuna connessione. Controlla internet e riprova.", e)
            }
            if (code !in 200..299) throw IOException(describeError(code, conn.errorStream?.bufferedReader()?.readText()))
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val piece = runCatching { JSONObject(data).optString("response") }.getOrNull()
                    if (!piece.isNullOrEmpty()) onPiece(piece)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun transcribe(cfg: AiConfig, wav: ByteArray): String {
        val url = URL("https://api.cloudflare.com/client/v4/accounts/${cfg.accountId}/ai/run/$WHISPER")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${cfg.token}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val body = JSONObject()
                .put("audio", android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP))
                .put("language", "it")
                .put("task", "transcribe")
                .put("vad_filter", true)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                throw IOException("Nessuna connessione. Controlla internet e riprova.", e)
            }
            if (code !in 200..299) throw IOException(describeError(code, conn.errorStream?.bufferedReader()?.readText()))
            val raw = conn.inputStream.bufferedReader().readText()
            return JSONObject(raw).optJSONObject("result")?.optString("text").orEmpty().trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun describeError(code: Int, raw: String?): String {
        val detail = runCatching { JSONObject(raw ?: "").getJSONArray("errors").getJSONObject(0).optString("message") }.getOrNull()
        return when {
            code == 401 || code == 403 -> "Cloudflare ha rifiutato le credenziali: controlla Account ID e token (serve il permesso Workers AI)."
            code == 404 -> "Account ID o modello non trovato su Cloudflare."
            code == 429 -> "Hai raggiunto il limite di richieste di Cloudflare per ora. Riprova più tardi."
            !detail.isNullOrBlank() -> "Cloudflare: $detail"
            else -> "Cloudflare ha risposto con un errore ($code)."
        }
    }

    // ---------------------------------------------------------------- persistenza

    private fun save() {
        val arr = JSONArray()
        _messages.value.takeLast(MAX_SAVED).forEach {
            arr.put(JSONObject().put("id", it.id).put("role", it.role.name).put("content", it.content).put("voice", it.voice))
        }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).apply()
    }

    private fun loadMessages(): List<ChatMessage> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_MESSAGES, null) ?: return emptyList())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChatMessage(o.getString("id"), ChatMessage.Role.valueOf(o.getString("role")), o.getString("content"), o.optBoolean("voice"))
        }
    }.getOrDefault(emptyList())

    data class Model(val id: String, val label: String, val hint: String)

    companion object {
        const val PREFS = "chiarella_ai"
        private const val KEY_ACCOUNT = "account_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_MODEL = "model"
        private const val KEY_MESSAGES = "messages"
        private const val MAX_HISTORY = 24
        private const val MAX_SAVED = 80
        private const val WHISPER = "@cf/openai/whisper-large-v3-turbo"

        val MODELS = listOf(
            Model("@cf/meta/llama-3.3-70b-instruct-fp8-fast", "Llama 3.3 70B", "Più attento e naturale"),
            Model("@cf/meta/llama-3.1-8b-instruct-fast", "Llama 3.1 8B", "Più veloce, consuma meno"),
        )
    }
}
