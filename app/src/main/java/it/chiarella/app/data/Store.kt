package it.chiarella.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Profilo preimpostato: un insieme di suoni, ciascuno con il proprio volume (0..1). */
data class Preset(
    val id: String,
    val name: String,
    val mix: Map<String, Float>,
)

/** Persistenza leggera su SharedPreferences (JSON). */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("chiarella", Context.MODE_PRIVATE)

    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    fun addPreset(name: String, mix: Map<String, Float>): Preset {
        val preset = Preset(UUID.randomUUID().toString(), name.trim(), mix)
        _presets.value = _presets.value + preset
        savePresets()
        return preset
    }

    fun deletePreset(id: String) {
        _presets.value = _presets.value.filterNot { it.id == id }
        savePresets()
    }

    fun preset(id: String): Preset? = _presets.value.firstOrNull { it.id == id }

    private val _passions = MutableStateFlow(loadPassions())
    val passions: StateFlow<List<Passion>> = _passions.asStateFlow()

    fun addPassion(category: PassionCategory, detail: String): Passion {
        val passion = Passion(UUID.randomUUID().toString(), category, detail.trim())
        _passions.value = _passions.value + passion
        savePassions()
        return passion
    }

    fun deletePassion(id: String) {
        _passions.value = _passions.value.filterNot { it.id == id }
        savePassions()
    }

    /** Ultima passione proposta: la volta dopo si evita di ripeterla subito. */
    var lastPassionId: String?
        get() = prefs.getString(KEY_LAST_PASSION, null)
        set(value) = prefs.edit().putString(KEY_LAST_PASSION, value).apply()

    var volumes: Map<String, Float>
        get() = prefs.getString(KEY_VOLUMES, null)?.let(::decodeMix) ?: emptyMap()
        set(value) = prefs.edit().putString(KEY_VOLUMES, encodeMix(value).toString()).apply()

    /** Ultimo mix attivo: usato dal widget per riprendere l'ascolto anche ad app chiusa. */
    var lastMix: Map<String, Float>
        get() = prefs.getString(KEY_LAST_MIX, null)?.let(::decodeMix) ?: emptyMap()
        set(value) = prefs.edit().putString(KEY_LAST_MIX, encodeMix(value).toString()).apply()

    /** Volume generale dell'app (0..1): moltiplica quello dei singoli suoni senza cambiarlo. */
    var masterVolume: Float
        get() = prefs.getFloat(KEY_MASTER, 1f)
        set(value) = prefs.edit().putFloat(KEY_MASTER, value).apply()

    var lastTimerMinutes: Int
        get() = prefs.getInt(KEY_TIMER, 30)
        set(value) = prefs.edit().putInt(KEY_TIMER, value).apply()

    /** Al primo avvio la guida introduttiva è da mostrare. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    fun backupData() = BackupData(presets.value, passions.value, volumes, masterVolume, lastMix,
        lastTimerMinutes, onboardingDone, lastPassionId)

    /** Un'unica scrittura: nessun dato viene toccato prima che il file sia stato validato. */
    fun importBackup(data: BackupData) {
        val previous = backupData()
        fun write(d: BackupData): Boolean = prefs.edit()
            .putString(KEY_PRESETS, encodePresets(d.presets))
            .putString(KEY_PASSIONS, encodePassions(d.passions))
            .putString(KEY_VOLUMES, encodeMix(d.volumes).toString())
            .putString(KEY_LAST_MIX, encodeMix(d.lastMix).toString())
            .putFloat(KEY_MASTER, d.masterVolume).putInt(KEY_TIMER, d.lastTimerMinutes)
            .putBoolean(KEY_ONBOARDING, d.onboardingDone).putString(KEY_LAST_PASSION, d.lastPassionId)
            .putBoolean(KEY_EXAMPLES_REMOVED, true).commit()
        if (!write(data)) {
            write(previous)
            throw java.io.IOException("Impossibile salvare il backup. I dati precedenti sono stati mantenuti.")
        }
        _presets.value = data.presets
        _passions.value = data.passions
    }

    private fun loadPresets(): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        val list = runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Preset(o.getString("id"), o.getString("name"), decodeMix(o.getString("mix")))
            }
        }.getOrDefault(emptyList())
        // Le prime versioni installavano dei profili d'esempio: ora l'app parte senza profili,
        // quindi vengono rimossi una sola volta, lasciando intatti quelli creati dall'utente.
        if (!prefs.getBoolean(KEY_EXAMPLES_REMOVED, false)) {
            val cleaned = list.filterNot { it.id in LEGACY_EXAMPLE_IDS }
            prefs.edit()
                .putString(KEY_PRESETS, encodePresets(cleaned))
                .putBoolean(KEY_EXAMPLES_REMOVED, true)
                .apply()
            return cleaned
        }
        return list
    }

    private fun loadPassions(): List<Passion> {
        val raw = prefs.getString(KEY_PASSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val category = runCatching { PassionCategory.valueOf(o.getString("category")) }
                    .getOrDefault(PassionCategory.ALTRO)
                Passion(o.getString("id"), category, o.optString("detail"))
            }
        }.getOrDefault(emptyList())
    }

    private fun savePassions() {
        prefs.edit().putString(KEY_PASSIONS, encodePassions(_passions.value)).apply()
    }

    private fun encodePassions(list: List<Passion>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("category", it.category.name).put("detail", it.detail))
        }
    }.toString()

    private fun savePresets() {
        prefs.edit().putString(KEY_PRESETS, encodePresets(_presets.value)).apply()
    }

    private fun encodePresets(list: List<Preset>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("name", it.name).put("mix", encodeMix(it.mix).toString()))
        }
    }.toString()

    private fun encodeMix(mix: Map<String, Float>) = JSONObject().apply {
        mix.forEach { (k, v) -> put(k, v.toDouble()) }
    }

    private fun decodeMix(raw: String): Map<String, Float> = runCatching {
        val o = JSONObject(raw)
        buildMap { o.keys().forEach { put(it, o.getDouble(it).toFloat()) } }
    }.getOrDefault(emptyMap())

    companion object {
        private const val KEY_PRESETS = "presets"
        private const val KEY_VOLUMES = "volumes"
        private const val KEY_LAST_MIX = "last_mix"
        private const val KEY_TIMER = "last_timer"
        private const val KEY_MASTER = "master_volume"
        private const val KEY_PASSIONS = "passions"
        private const val KEY_LAST_PASSION = "last_passion"

        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_EXAMPLES_REMOVED = "examples_removed"

        /** Id dei profili d'esempio installati dalle versioni 1.0-1.2. */
        private val LEGACY_EXAMPLE_IDS = setOf(
            "notte_serena", "rifugio", "riva_mare", "bosco", "focus", "falo_estate", "zen", "phon_nanna",
        )
    }
}
