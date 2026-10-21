package it.chiarella.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Una passione dell'utente: una categoria e, facoltativo, un dettaglio ("Zelda", "Breaking Bad"…). */
data class Passion(
    val id: String,
    val category: PassionCategory,
    val detail: String,
) {
    /** Nome da mostrare: il dettaglio se c'è, altrimenti la categoria. */
    val title: String get() = detail.ifBlank { category.label }
}

/**
 * Categorie di passioni. Ogni categoria ha i suoi colori e una manciata di proposte:
 * `%s` viene sostituito dal dettaglio scritto dall'utente (o da [fallback] se manca).
 */
enum class PassionCategory(
    val label: String,
    val icon: ImageVector,
    val primary: Color,
    val secondary: Color,
    val fallback: String,
    val ideas: List<String>,
) {
    FILM(
        "Film", Icons.Rounded.Movie, Color(0xFFEF4444), Color(0xFFFFB38A), "un bel film",
        listOf(
            "Spegni le luci e guarda %s, senza telefono in mano.",
            "Prepara qualcosa di caldo e goditi %s dall'inizio alla fine.",
            "Riguarda %s: le storie che conosci già rilassano di più.",
        ),
    ),
    SERIE_TV(
        "Serie TV", Icons.Rounded.LiveTv, Color(0xFF8B7CF6), Color(0xFFF0ABFC), "quella serie che ti piace",
        listOf(
            "Un episodio di %s, solo uno. Poi vedi come ti senti.",
            "Mettiti comodo e riprendi %s da dove l'avevi lasciata.",
            "Coperta, divano e un episodio di %s.",
        ),
    ),
    VIDEOGIOCHI(
        "Videogiochi", Icons.Rounded.SportsEsports, Color(0xFF22C55E), Color(0xFF5EC8F2), "il tuo gioco preferito",
        listOf(
            "Accendi %s e gioca per mezz'ora, senza obiettivi.",
            "Una partita tranquilla con %s: esplora, non correre.",
            "Fatti una sessione con %s, con calma e un po' di musica.",
        ),
    ),
    PROGRAMMARE(
        "Programmare", Icons.Rounded.Code, Color(0xFF0EA5E9), Color(0xFF67E8F9), "un piccolo progetto",
        listOf(
            "Lavora a %s, giusto per il gusto di farlo.",
            "Prova un'idea nuova su %s, senza scadenze.",
            "Scrivi un po' di codice per %s, una cosa piccola e bella.",
        ),
    ),
    LETTURA(
        "Lettura", Icons.Rounded.AutoStories, Color(0xFFF59E0B), Color(0xFFFDE68A), "un libro",
        listOf(
            "Leggi qualche pagina di %s, con una luce calda.",
            "Venti minuti con %s: il resto può aspettare.",
            "Trova un angolo comodo e immergiti in %s.",
        ),
    ),
    MUSICA(
        "Musica", Icons.Rounded.Headphones, Color(0xFFEC4899), Color(0xFFFFB4D9), "la tua musica",
        listOf(
            "Cuffie, occhi chiusi e %s. Ascolta e basta.",
            "Fai partire %s e lasciati portare.",
            "Ascolta %s dall'inizio alla fine, senza saltare tracce.",
        ),
    ),
    SPORT(
        "Sport", Icons.Rounded.SportsSoccer, Color(0xFF14B8A6), Color(0xFFB4F07A), "una corsa",
        listOf(
            "Muoviti un po': %s, il corpo ringrazia.",
            "Mezz'ora dedicata a %s, al tuo ritmo.",
            "Esci per %s, senza guardare il cronometro.",
        ),
    ),
    NATURA(
        "Natura", Icons.Rounded.Hiking, Color(0xFF16A34A), Color(0xFF99F6E4), "una passeggiata",
        listOf(
            "Esci per %s, anche breve: guarda il cielo.",
            "Concediti %s all'aria aperta, lasciando il telefono in tasca.",
            "Un po' di verde: %s, con passo lento.",
        ),
    ),
    ARTE(
        "Arte", Icons.Rounded.Brush, Color(0xFFF97316), Color(0xFFFFD166), "disegnare",
        listOf(
            "Prendi un foglio e dedicati a %s, senza giudicarti.",
            "Lasciati andare: %s, per il puro piacere di farlo.",
            "Dieci minuti di %s: crea qualcosa, anche di piccolo.",
        ),
    ),
    FOTOGRAFIA(
        "Fotografia", Icons.Rounded.PhotoCamera, Color(0xFF64748B), Color(0xFFE2E8F0), "belle foto",
        listOf(
            "Vai in giro a cercare %s: luci, ombre, dettagli.",
            "Riguarda i tuoi scatti con calma e scegli i migliori.",
            "Esci con la fotocamera a caccia di %s.",
        ),
    ),
    CUCINA(
        "Cucina", Icons.Rounded.Restaurant, Color(0xFFDC2626), Color(0xFFFCA5A5), "qualcosa di buono",
        listOf(
            "Mettiti ai fornelli e prepara %s.",
            "Cucina %s con calma, gustandoti ogni passaggio.",
            "Una tisana e poi %s: cucinare rilassa.",
        ),
    ),
    BENESSERE(
        "Benessere", Icons.Rounded.SelfImprovement, Color(0xFF5B8DEF), Color(0xFFC4BCFF), "un po' di yoga",
        listOf(
            "Srotola il tappetino e dedicati a %s.",
            "Dieci minuti di %s, ascoltando il respiro.",
            "Un momento solo per te: %s.",
        ),
    ),
    ALTRO(
        "Altro", Icons.Rounded.Favorite, Color(0xFFFF8A5B), Color(0xFFFFC46B), "quello che ti piace",
        listOf(
            "Fai %s, senza sensi di colpa.",
            "È il momento giusto per %s.",
            "Regalati del tempo per %s.",
        ),
    );

    fun idea(passion: Passion, index: Int): String =
        ideas[Math.floorMod(index, ideas.size)].format(passion.detail.ifBlank { fallback })
}
