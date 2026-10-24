package it.chiarella.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupTest {
    private val root = File(requireNotNull(System.getProperty("chiarella.root")))
    private val fixture get() = File(root, "tests/fixtures/backup-v1.json").readText()

    private fun verify(raw: String) {
        val data = Backup.parse(raw)
        assertEquals(2, data.presets.size)
        assertEquals(2, data.passions.size)
        assertEquals("Quiete 🌊 e pioggia", data.presets[0].name)
        assertEquals("L'amica geniale — un po’ di quiete", data.passions[0].detail)
        assertEquals(0.65f, data.masterVolume, 0.000001f)
        assertEquals(0.5f, data.volumes.getValue("future_sound"), 0.000001f)
        assertEquals(0.35f, data.lastMix.getValue("mare"), 0.000001f)
        assertEquals(45, data.lastTimerMinutes)
        assertEquals("passion-2", data.lastPassionId)
        assertTrue(data.onboardingDone)
    }

    @Test fun roundTripAndOtherPlatforms() {
        verify(fixture)
        verify("\uFEFF$fixture")
        verify(Backup.stringify(Backup.parse(fixture)))
        val out = File(root, "dist/backup-tests").apply { mkdirs() }
        listOf("web.json", "windows.json", "browser.json").map { File(out, it) }.filter { it.exists() }.forEach { verify(it.readText()) }
        File(out, "android.json").writeText(Backup.stringify(Backup.parse(fixture)))
    }

    @Test fun invalidFilesDoNotProduceImportableData() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("version", 2) }, { it.put("version", "1") }, { it.put("format", "other-app") },
            { it.getJSONObject("data").remove("volumes") },
            { it.getJSONObject("data").put("masterVolume", -0.1) },
            { it.getJSONObject("data").put("masterVolume", 1.01) },
            { it.getJSONObject("data").getJSONObject("volumes").put("mare", "0.5") },
            { it.getJSONObject("data").put("lastTimerMinutes", 4) },
            { it.getJSONObject("data").put("lastTimerMinutes", 30.5) },
            { val a = it.getJSONObject("data").getJSONArray("presets"); a.put(a.getJSONObject(0)) },
            { it.getJSONObject("data").put("passions", JSONObject.NULL) },
            { it.getJSONObject("data").getJSONArray("presets").getJSONObject(0).put("id", "\" onclick=alert(1)") },
            { it.getJSONObject("data").getJSONArray("passions").getJSONObject(0).put("category", "UNKNOWN") },
            { it.getJSONObject("data").put("onboardingDone", "true") },
        )
        val invalid = changes.map { change -> JSONObject(fixture).also(change).toString() } +
            listOf("null", "[]", "{broken", " ".repeat(Backup.MAX_BYTES + 1))
        invalid.forEach { raw ->
            try { Backup.parse(raw); fail("Backup non valido accettato") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun emptyBackup() {
        val raw = JSONObject(fixture)
        raw.getJSONObject("data").put("presets", JSONArray()).put("passions", JSONArray())
            .put("lastPassionId", JSONObject.NULL).put("onboardingDone", false)
        val data = Backup.parse(raw.toString())
        assertTrue(data.presets.isEmpty())
        assertNull(data.lastPassionId)
        assertFalse(data.onboardingDone)
    }
}
