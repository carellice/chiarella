using Windows.UI;

namespace Chiarella.Data;

/// <summary>Stile dell'animazione che vive dentro la card del suono quando è attivo.</summary>
public enum Ambience { Rain, Storm, Heat, Waves, Leaves, Fall, Ripple, Embers, Wind, Fireflies, Airflow }

public sealed record Sound(string Id, string Name, string Tagline, string Icon, Color Primary, Color Secondary, Ambience Ambience);

/// <summary>Dettaglio di un suono: descrizione, momenti ideali (emoji + etichetta) e suoni con cui si abbina.</summary>
public sealed record SoundInfo(string Description, string[] Moods, string[] Pairs);

public sealed record PassionCategory(
    string Id, string Label, string Icon, Color Primary, Color Secondary,
    string Fallback, string Example, string[] Ideas)
{
    /// <summary>Proposta n. <paramref name="index"/>: "%s" diventa il dettaglio scritto dall'utente.</summary>
    public string Idea(Passion passion, int index) =>
        Ideas[((index % Ideas.Length) + Ideas.Length) % Ideas.Length]
            .Replace("%s", string.IsNullOrWhiteSpace(passion.Detail) ? Fallback : passion.Detail);
}

/// <summary>Catalogo dei suoni e delle categorie di passioni: gli stessi dell'app Android.</summary>
public static class Catalog
{
    public static Color Hex(string hex)
    {
        var v = Convert.ToUInt32(hex.TrimStart('#'), 16);
        return Color.FromArgb(255, (byte)(v >> 16), (byte)(v >> 8), (byte)v);
    }

    public static readonly IReadOnlyList<Sound> Sounds =
    [
        new("pioggia_dolce", "Pioggia dolce", "Gocce leggere", "💧", Hex("#5B8DEF"), Hex("#8FD8FF"), Ambience.Rain),
        new("stufa_elettrica", "Stufa elettrica", "Ronzio caldo", "♨️", Hex("#FF8A5B"), Hex("#FFC46B"), Ambience.Heat),
        new("temporale", "Temporale", "Tuoni lontani", "⛈️", Hex("#7C6CF0"), Hex("#C4BCFF"), Ambience.Storm),
        new("mare", "Mare", "Onde sulla riva", "🌊", Hex("#0EA5E9"), Hex("#67E8F9"), Ambience.Waves),
        new("foresta", "Foresta", "Foglie e uccelli", "🌲", Hex("#22C55E"), Hex("#B4F07A"), Ambience.Leaves),
        new("cascata", "Cascata", "Acqua che scroscia", "💦", Hex("#14B8A6"), Hex("#99F6E4"), Ambience.Fall),
        new("fiume", "Riva del fiume", "Corrente gentile", "🛶", Hex("#3B82F6"), Hex("#A5D8FF"), Ambience.Ripple),
        new("falo", "Falò", "Legna che scoppietta", "🔥", Hex("#F97316"), Hex("#FFD166"), Ambience.Embers),
        new("vento", "Vento", "Brezza tra i rami", "🍃", Hex("#94A3B8"), Hex("#E2E8F0"), Ambience.Wind),
        new("grilli", "Grilli di notte", "Notte d'estate", "🌙", Hex("#8B7CF6"), Hex("#FDE68A"), Ambience.Fireflies),
        new("phon", "Phon", "Soffio caldo e costante", "🌀", Hex("#EC4899"), Hex("#FFB4D9"), Ambience.Airflow),
    ];

    private static readonly Dictionary<string, Sound> SoundById = Sounds.ToDictionary(s => s.Id);

    public static Sound? Sound(string id) => SoundById.GetValueOrDefault(id);

    private const string Sleep = "🌙  Dormire", Focus = "🎯  Concentrarsi", Relax = "🍃  Rilassarsi";

    private static readonly Dictionary<string, SoundInfo> Infos = new()
    {
        ["pioggia_dolce"] = new("Una pioggia leggera e regolare su foglie e tetti: copre i rumori di fondo senza mai diventare invadente.", [Sleep, Focus], ["stufa_elettrica", "vento", "falo"]),
        ["stufa_elettrica"] = new("Il ronzio caldo e costante di una stufa: un rumore bianco morbido, che isola e accompagna.", [Sleep, Focus], ["pioggia_dolce", "vento", "temporale"]),
        ["temporale"] = new("Tuoni lontani che rotolano sopra la pioggia battente: perfetto per sentirsi al riparo.", [Sleep, Relax], ["pioggia_dolce", "falo", "stufa_elettrica"]),
        ["mare"] = new("Onde che arrivano e si ritirano sulla riva, con un ritmo lento che invita a respirare.", [Relax, Sleep], ["vento", "grilli", "falo"]),
        ["foresta"] = new("Foglie mosse dal vento e il canto degli uccelli: una passeggiata nel bosco senza muoversi.", [Focus, Relax], ["fiume", "vento", "cascata"]),
        ["cascata"] = new("Acqua che precipita e scroscia senza sosta: un suono pieno che cancella ogni distrazione.", [Focus, Sleep], ["foresta", "vento", "grilli"]),
        ["fiume"] = new("Una corrente gentile che scorre tra i sassi: leggera, viva, mai monotona.", [Relax, Focus], ["foresta", "grilli", "vento"]),
        ["falo"] = new("Legna che scoppietta e brace che crepita: il calore di un fuoco acceso, anche in città.", [Relax, Sleep], ["grilli", "temporale", "pioggia_dolce"]),
        ["vento"] = new("Brezza tra i rami, a raffiche morbide: dà respiro a qualsiasi mix.", [Relax, Focus], ["mare", "foresta", "falo"]),
        ["grilli"] = new("Una notte d'estate in campagna, con il canto dei grilli e l'aria tiepida.", [Sleep, Relax], ["falo", "fiume", "mare"]),
        ["phon"] = new("Il soffio caldo e costante di un phon: un rumore bianco amato da molti per addormentarsi.", [Sleep, Focus], ["pioggia_dolce", "stufa_elettrica", "vento"]),
    };

    public static SoundInfo Info(string id) => Infos[id];

    public static readonly IReadOnlyList<PassionCategory> Categories =
    [
        new("FILM", "Film", "🎬", Hex("#EF4444"), Hex("#FFB38A"), "un bel film", "Il Signore degli Anelli",
            ["Spegni le luci e guarda %s, senza telefono in mano.", "Prepara qualcosa di caldo e goditi %s dall'inizio alla fine.", "Riguarda %s: le storie che conosci già rilassano di più."]),
        new("SERIE_TV", "Serie TV", "📺", Hex("#8B7CF6"), Hex("#F0ABFC"), "quella serie che ti piace", "Breaking Bad",
            ["Un episodio di %s, solo uno. Poi vedi come ti senti.", "Mettiti comodo e riprendi %s da dove l'avevi lasciata.", "Coperta, divano e un episodio di %s."]),
        new("VIDEOGIOCHI", "Videogiochi", "🎮", Hex("#22C55E"), Hex("#5EC8F2"), "il tuo gioco preferito", "Zelda",
            ["Accendi %s e gioca per mezz'ora, senza obiettivi.", "Una partita tranquilla con %s: esplora, non correre.", "Fatti una sessione con %s, con calma e un po' di musica."]),
        new("PROGRAMMARE", "Programmare", "💻", Hex("#0EA5E9"), Hex("#67E8F9"), "un piccolo progetto", "la mia app",
            ["Lavora a %s, giusto per il gusto di farlo.", "Prova un'idea nuova su %s, senza scadenze.", "Scrivi un po' di codice per %s, una cosa piccola e bella."]),
        new("LETTURA", "Lettura", "📖", Hex("#F59E0B"), Hex("#FDE68A"), "un libro", "un giallo",
            ["Leggi qualche pagina di %s, con una luce calda.", "Venti minuti con %s: il resto può aspettare.", "Trova un angolo comodo e immergiti in %s."]),
        new("MUSICA", "Musica", "🎧", Hex("#EC4899"), Hex("#FFB4D9"), "la tua musica", "i Pink Floyd",
            ["Cuffie, occhi chiusi e %s. Ascolta e basta.", "Fai partire %s e lasciati portare.", "Ascolta %s dall'inizio alla fine, senza saltare tracce."]),
        new("SPORT", "Sport", "⚽", Hex("#14B8A6"), Hex("#B4F07A"), "una corsa", "una corsa al parco",
            ["Muoviti un po': %s, il corpo ringrazia.", "Mezz'ora dedicata a %s, al tuo ritmo.", "Esci per %s, senza guardare il cronometro."]),
        new("NATURA", "Natura", "🥾", Hex("#16A34A"), Hex("#99F6E4"), "una passeggiata", "una passeggiata nel bosco",
            ["Esci per %s, anche breve: guarda il cielo.", "Concediti %s all'aria aperta, lasciando il telefono in tasca.", "Un po' di verde: %s, con passo lento."]),
        new("ARTE", "Arte", "🎨", Hex("#F97316"), Hex("#FFD166"), "disegnare", "acquerelli",
            ["Prendi un foglio e dedicati a %s, senza giudicarti.", "Lasciati andare: %s, per il puro piacere di farlo.", "Dieci minuti di %s: crea qualcosa, anche di piccolo."]),
        new("FOTOGRAFIA", "Fotografia", "📷", Hex("#64748B"), Hex("#E2E8F0"), "belle foto", "tramonti",
            ["Vai in giro a cercare %s: luci, ombre, dettagli.", "Riguarda i tuoi scatti con calma e scegli i migliori.", "Esci con la fotocamera a caccia di %s."]),
        new("CUCINA", "Cucina", "🍳", Hex("#DC2626"), Hex("#FCA5A5"), "qualcosa di buono", "una torta",
            ["Mettiti ai fornelli e prepara %s.", "Cucina %s con calma, gustandoti ogni passaggio.", "Una tisana e poi %s: cucinare rilassa."]),
        new("BENESSERE", "Benessere", "🧘", Hex("#5B8DEF"), Hex("#C4BCFF"), "un po' di yoga", "yoga",
            ["Srotola il tappetino e dedicati a %s.", "Dieci minuti di %s, ascoltando il respiro.", "Un momento solo per te: %s."]),
        new("ALTRO", "Altro", "❤️", Hex("#FF8A5B"), Hex("#FFC46B"), "quello che ti piace", "fare un puzzle",
            ["Fai %s, senza sensi di colpa.", "È il momento giusto per %s.", "Regalati del tempo per %s."]),
    ];

    public static PassionCategory Category(string id) =>
        Categories.FirstOrDefault(c => c.Id == id) ?? Categories[^1];
}
