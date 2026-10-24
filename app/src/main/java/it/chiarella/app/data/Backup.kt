package it.chiarella.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Backup JSON v1: lo stesso formato della versione web e Windows. */
data class BackupData(
    val presets: List<Preset>,
    val passions: List<Passion>,
    val volumes: Map<String, Float>,
    val masterVolume: Float,
    val lastMix: Map<String, Float>,
    val lastTimerMinutes: Int,
    val onboardingDone: Boolean,
    val lastPassionId: String?,
)

object Backup {
    const val MAX_BYTES = 2 * 1024 * 1024
    private val idPattern = Regex("[A-Za-z0-9_.-]{1,128}")
    private val soundPattern = Regex("[a-z][a-z0-9_]{0,63}")
    private fun invalid(): Nothing = throw IllegalArgumentException("Il file non è un backup Chiarella valido.")
    private fun obj(value: Any?): JSONObject = value as? JSONObject ?: invalid()
    private fun text(value: Any?, max: Int, empty: Boolean = false): String {
        val s = value as? String ?: invalid()
        if (s.length > max || (!empty && s.isBlank())) invalid()
        return s
    }
    private fun id(value: Any?): String = text(value, 128).also { if (!idPattern.matches(it)) invalid() }
    private fun volume(value: Any?): Float {
        val n = (value as? Number)?.toDouble() ?: invalid()
        if (!n.isFinite() || n !in 0.0..1.0) invalid()
        return n.toFloat()
    }
    private fun mix(value: Any?): Map<String, Float> {
        val o = obj(value)
        if (o.length() > 100) invalid()
        return buildMap {
            o.keys().forEach { key ->
                if (!soundPattern.matches(key)) invalid()
                put(key, volume(o.get(key)))
            }
        }
    }
    private fun <T> list(value: Any?, read: (JSONObject) -> T): List<T> {
        val arr = value as? JSONArray ?: invalid()
        if (arr.length() > 1000) invalid()
        return List(arr.length()) { read(obj(arr.get(it))) }
    }

    fun parse(raw: String): BackupData {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) invalid()
        try {
            val root = JSONObject(raw.removePrefix("\uFEFF"))
            if (root.get("format") != "chiarella-backup") invalid()
            val version = root.get("version")
            if (version !is Number || version.toDouble() != 1.0) {
                throw IllegalArgumentException("Versione del backup non supportata. Aggiorna Chiarella e riprova.")
            }
            val d = obj(root.get("data"))
            val timer = d.get("lastTimerMinutes")
            if (timer !is Number || timer.toDouble() % 1.0 != 0.0 || timer.toDouble() !in 5.0..240.0) invalid()
            val presets = list(d.get("presets")) { p -> Preset(id(p.get("id")), text(p.get("name"), 200), mix(p.get("mix"))) }
            val passions = list(d.get("passions")) { p ->
                val category = PassionCategory.entries.firstOrNull { it.name == p.get("category") } ?: invalid()
                Passion(id(p.get("id")), category, text(p.get("detail"), 1000, true))
            }
            if (presets.map { it.id }.toSet().size != presets.size || passions.map { it.id }.toSet().size != passions.size) invalid()
            return BackupData(presets, passions, mix(d.get("volumes")), volume(d.get("masterVolume")),
                mix(d.get("lastMix")), timer.toInt(), d.get("onboardingDone") as? Boolean ?: invalid(),
                d.get("lastPassionId").let { if (it == JSONObject.NULL) null else id(it) })
        } catch (e: org.json.JSONException) {
            invalid()
        }
    }

    fun stringify(data: BackupData): String {
        fun encodeMix(m: Map<String, Float>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v.toDouble()) } }
        val d = JSONObject()
            .put("presets", JSONArray().apply { data.presets.forEach { p ->
                put(JSONObject().put("id", p.id).put("name", p.name).put("mix", encodeMix(p.mix)))
            } })
            .put("passions", JSONArray().apply { data.passions.forEach { p ->
                put(JSONObject().put("id", p.id).put("category", p.category.name).put("detail", p.detail))
            } })
            .put("volumes", encodeMix(data.volumes)).put("masterVolume", data.masterVolume.toDouble())
            .put("lastMix", encodeMix(data.lastMix)).put("lastTimerMinutes", data.lastTimerMinutes)
            .put("onboardingDone", data.onboardingDone).put("lastPassionId", data.lastPassionId ?: JSONObject.NULL)
        return JSONObject().put("format", "chiarella-backup").put("version", 1)
            .put("exportedAt", Instant.now().toString()).put("data", d).toString(2).also { parse(it) }
    }
}
