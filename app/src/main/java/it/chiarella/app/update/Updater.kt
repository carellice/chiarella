package it.chiarella.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una release pubblicata su GitHub con il suo APK. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Aggiornamenti direttamente dalle release di GitHub: controlla l'ultima release,
 * scarica l'APK allegato e avvia l'installazione di sistema.
 */
class Updater(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Controllo automatico all'avvio, al massimo ogni [AUTO_CHECK_INTERVAL_MS]. */
    fun checkIfDue() {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) {
            // Se l'ultimo controllo aveva trovato una versione nuova, la ripropone senza rete.
            cachedRelease()?.takeIf { isNewer(it.version, currentVersion) }?.let {
                if (_state.value is UpdateState.Idle) _state.value = UpdateState.Available(it)
            }
            return
        }
        checkNow(silent = true)
    }

    fun checkNow(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!silent) _state.value = UpdateState.Checking
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val release = result.getOrNull()
            _state.value = when {
                release != null && isNewer(release.version, currentVersion) -> {
                    cache(release)
                    UpdateState.Available(release)
                }
                release != null -> UpdateState.UpToDate(currentVersion)
                silent -> UpdateState.Idle
                else -> UpdateState.Failed("Non riesco a contattare GitHub. Controlla la connessione e riprova.")
            }
        }
    }

    fun download(release: Release) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    downloadApk(release) { p -> _state.value = UpdateState.Downloading(release, p) }
                }
            }
            _state.value = result.fold(
                onSuccess = { UpdateState.ReadyToInstall(release, it) },
                onFailure = { UpdateState.Failed("Download non riuscito. Riprova tra poco.", release) },
            )
        }
    }

    /**
     * Avvia l'installer di Android. Se Chiarella non ha ancora il permesso di installare app,
     * apre prima la schermata delle impostazioni dove concederlo (poi si tocca di nuovo "Installa").
     */
    fun install(activityContext: Context, file: File): Boolean {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            activityContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        activityContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    fun dismiss() {
        val s = _state.value
        if (s is UpdateState.Available) prefs.edit().putString(KEY_DISMISSED, s.release.version).apply()
        if (s !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    /** true se l'utente ha già chiuso il banner per questa versione (resta il controllo manuale). */
    fun isDismissed(release: Release) = prefs.getString(KEY_DISMISSED, null) == release.version

    // ------------------------------------------------------------------ rete

    private fun fetchLatest(): Release {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Chiarella/$currentVersion")
        }
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .first { it.getString("name").endsWith(".apk", ignoreCase = true) }
            return Release(
                version = json.getString("tag_name").removePrefix("v"),
                notes = cleanNotes(json.optString("body")),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size"),
                pageUrl = json.optString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadApk(release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // niente APK vecchi a occupare spazio
        val target = File(dir, "Chiarella-${release.version}.apk")
        val partial = File(dir, "${target.name}.part")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // GitHub reindirizza al CDN degli asset
            setRequestProperty("User-Agent", "Chiarella/$currentVersion")
        }
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            var read = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        val pct = if (total > 0) (read * 100 / total).toInt() else 0
                        if (pct != lastReported) {
                            lastReported = pct
                            onProgress(pct / 100f)
                        }
                    }
                }
            }
            check(total <= 0 || read == total) { "Download incompleto" }
            check(partial.renameTo(target)) { "Impossibile salvare l'aggiornamento" }
            return target
        } finally {
            conn.disconnect()
        }
    }

    private fun cache(release: Release) {
        prefs.edit()
            .putString(KEY_CACHED, JSONObject()
                .put("version", release.version).put("notes", release.notes).put("apkUrl", release.apkUrl)
                .put("apkSize", release.apkSize).put("pageUrl", release.pageUrl).toString())
            .apply()
    }

    private fun cachedRelease(): Release? = prefs.getString(KEY_CACHED, null)?.let {
        runCatching {
            val o = JSONObject(it)
            Release(o.getString("version"), o.getString("notes"), o.getString("apkUrl"), o.getLong("apkSize"), o.getString("pageUrl"))
        }.getOrNull()
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/carellice/chiarella/releases/latest"
        private const val AUTO_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_CACHED = "cached_release"
        private const val KEY_DISMISSED = "dismissed_version"

        /** Confronta versioni tipo "1.10" e "1.9" numero per numero. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** Note di rilascio in testo semplice (le release usano Markdown). */
        private fun cleanNotes(body: String): String = body
            .lines()
            .map { it.trim().removePrefix("#").removePrefix("#").removePrefix("#").trim() }
            .map { if (it.startsWith("- ") || it.startsWith("* ")) "• " + it.drop(2) else it }
            .map { it.replace("**", "").replace("`", "") }
            .joinToString("\n")
            .trim()
    }
}
