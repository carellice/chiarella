package it.chiarella.app.audio

import androidx.annotation.RawRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Dry
import androidx.compose.material.icons.rounded.Fireplace
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Kayaking
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import it.chiarella.app.R

/** Stile dell'animazione che vive dentro la card del suono quando è attivo. */
enum class Ambience { RAIN, STORM, HEAT, WAVES, LEAVES, FALL, RIPPLE, EMBERS, WIND, FIREFLIES, AIRFLOW }

data class Sound(
    val id: String,
    val name: String,
    val tagline: String,
    @RawRes val res: Int,
    val icon: ImageVector,
    val primary: Color,
    val secondary: Color,
    val ambience: Ambience,
)

/**
 * Catalogo dei suoni. Per sostituire un suono basta rimpiazzare il file omonimo in
 * `app/src/main/res/raw` (es. `pioggia_dolce.mp3` al posto di `pioggia_dolce.ogg`).
 */
object Sounds {
    val all = listOf(
        Sound("pioggia_dolce", "Pioggia dolce", "Gocce leggere", R.raw.pioggia_dolce,
            Icons.Rounded.WaterDrop, Color(0xFF5B8DEF), Color(0xFF8FD8FF), Ambience.RAIN),
        Sound("stufa_elettrica", "Stufa elettrica", "Ronzio caldo", R.raw.stufa_elettrica,
            Icons.Rounded.Fireplace, Color(0xFFFF8A5B), Color(0xFFFFC46B), Ambience.HEAT),
        Sound("temporale", "Temporale", "Tuoni lontani", R.raw.temporale,
            Icons.Rounded.Thunderstorm, Color(0xFF7C6CF0), Color(0xFFC4BCFF), Ambience.STORM),
        Sound("mare", "Mare", "Onde sulla riva", R.raw.mare,
            Icons.Rounded.Waves, Color(0xFF0EA5E9), Color(0xFF67E8F9), Ambience.WAVES),
        Sound("foresta", "Foresta", "Foglie e uccelli", R.raw.foresta,
            Icons.Rounded.Forest, Color(0xFF22C55E), Color(0xFFB4F07A), Ambience.LEAVES),
        Sound("cascata", "Cascata", "Acqua che scroscia", R.raw.cascata,
            Icons.Rounded.Water, Color(0xFF14B8A6), Color(0xFF99F6E4), Ambience.FALL),
        Sound("fiume", "Riva del fiume", "Corrente gentile", R.raw.fiume,
            Icons.Rounded.Kayaking, Color(0xFF3B82F6), Color(0xFFA5D8FF), Ambience.RIPPLE),
        Sound("falo", "Falò", "Legna che scoppietta", R.raw.falo,
            Icons.Rounded.LocalFireDepartment, Color(0xFFF97316), Color(0xFFFFD166), Ambience.EMBERS),
        Sound("vento", "Vento", "Brezza tra i rami", R.raw.vento,
            Icons.Rounded.Air, Color(0xFF94A3B8), Color(0xFFE2E8F0), Ambience.WIND),
        Sound("grilli", "Grilli di notte", "Notte d'estate", R.raw.grilli,
            Icons.Rounded.NightsStay, Color(0xFF8B7CF6), Color(0xFFFDE68A), Ambience.FIREFLIES),
        Sound("phon", "Phon", "Soffio caldo e costante", R.raw.phon,
            Icons.Rounded.Dry, Color(0xFFEC4899), Color(0xFFFFB4D9), Ambience.AIRFLOW),
    )

    private val byId = all.associateBy { it.id }

    operator fun get(id: String): Sound? = byId[id]
}

/** A cosa si presta un suono: mostrato come etichetta nel dettaglio. */
enum class Mood(val label: String, val emoji: String) {
    SLEEP("Dormire", "🌙"),
    FOCUS("Concentrarsi", "🎯"),
    RELAX("Rilassarsi", "🍃"),
}

/** Dettaglio di un suono: descrizione, momenti ideali e suoni con cui si abbina bene. */
data class SoundInfo(val description: String, val moods: List<Mood>, val pairs: List<String>)

object SoundInfos {
    private val byId = mapOf(
        "pioggia_dolce" to SoundInfo(
            "Una pioggia leggera e regolare su foglie e tetti: copre i rumori di fondo senza mai diventare invadente.",
            listOf(Mood.SLEEP, Mood.FOCUS), listOf("stufa_elettrica", "vento", "falo"),
        ),
        "stufa_elettrica" to SoundInfo(
            "Il ronzio caldo e costante di una stufa: un rumore bianco morbido, che isola e accompagna.",
            listOf(Mood.SLEEP, Mood.FOCUS), listOf("pioggia_dolce", "vento", "temporale"),
        ),
        "temporale" to SoundInfo(
            "Tuoni lontani che rotolano sopra la pioggia battente: perfetto per sentirsi al riparo.",
            listOf(Mood.SLEEP, Mood.RELAX), listOf("pioggia_dolce", "falo", "stufa_elettrica"),
        ),
        "mare" to SoundInfo(
            "Onde che arrivano e si ritirano sulla riva, con un ritmo lento che invita a respirare.",
            listOf(Mood.RELAX, Mood.SLEEP), listOf("vento", "grilli", "falo"),
        ),
        "foresta" to SoundInfo(
            "Foglie mosse dal vento e il canto degli uccelli: una passeggiata nel bosco senza muoversi.",
            listOf(Mood.FOCUS, Mood.RELAX), listOf("fiume", "vento", "cascata"),
        ),
        "cascata" to SoundInfo(
            "Acqua che precipita e scroscia senza sosta: un suono pieno che cancella ogni distrazione.",
            listOf(Mood.FOCUS, Mood.SLEEP), listOf("foresta", "vento", "grilli"),
        ),
        "fiume" to SoundInfo(
            "Una corrente gentile che scorre tra i sassi: leggera, viva, mai monotona.",
            listOf(Mood.RELAX, Mood.FOCUS), listOf("foresta", "grilli", "vento"),
        ),
        "falo" to SoundInfo(
            "Legna che scoppietta e brace che crepita: il calore di un fuoco acceso, anche in città.",
            listOf(Mood.RELAX, Mood.SLEEP), listOf("grilli", "temporale", "pioggia_dolce"),
        ),
        "vento" to SoundInfo(
            "Brezza tra i rami, a raffiche morbide: dà respiro a qualsiasi mix.",
            listOf(Mood.RELAX, Mood.FOCUS), listOf("mare", "foresta", "falo"),
        ),
        "grilli" to SoundInfo(
            "Una notte d'estate in campagna, con il canto dei grilli e l'aria tiepida.",
            listOf(Mood.SLEEP, Mood.RELAX), listOf("falo", "fiume", "mare"),
        ),
        "phon" to SoundInfo(
            "Il soffio caldo e costante di un phon: un rumore bianco amato da molti per addormentarsi.",
            listOf(Mood.SLEEP, Mood.FOCUS), listOf("pioggia_dolce", "stufa_elettrica", "vento"),
        ),
    )

    operator fun get(id: String): SoundInfo = byId.getValue(id)
}
