// Catalogo condiviso: suoni e categorie di passioni (gli stessi dell'app Android).

const SOUNDS = [
  { id: "pioggia_dolce", name: "Pioggia dolce", tagline: "Gocce leggere", icon: "💧", primary: "#5B8DEF", secondary: "#8FD8FF", ambience: "rain" },
  { id: "stufa_elettrica", name: "Stufa elettrica", tagline: "Ronzio caldo", icon: "♨️", primary: "#FF8A5B", secondary: "#FFC46B", ambience: "heat" },
  { id: "temporale", name: "Temporale", tagline: "Tuoni lontani", icon: "⛈️", primary: "#7C6CF0", secondary: "#C4BCFF", ambience: "storm" },
  { id: "mare", name: "Mare", tagline: "Onde sulla riva", icon: "🌊", primary: "#0EA5E9", secondary: "#67E8F9", ambience: "waves" },
  { id: "foresta", name: "Foresta", tagline: "Foglie e uccelli", icon: "🌲", primary: "#22C55E", secondary: "#B4F07A", ambience: "leaves" },
  { id: "cascata", name: "Cascata", tagline: "Acqua che scroscia", icon: "💦", primary: "#14B8A6", secondary: "#99F6E4", ambience: "fall" },
  { id: "fiume", name: "Riva del fiume", tagline: "Corrente gentile", icon: "🛶", primary: "#3B82F6", secondary: "#A5D8FF", ambience: "ripple" },
  { id: "falo", name: "Falò", tagline: "Legna che scoppietta", icon: "🔥", primary: "#F97316", secondary: "#FFD166", ambience: "embers" },
  { id: "vento", name: "Vento", tagline: "Brezza tra i rami", icon: "🍃", primary: "#94A3B8", secondary: "#E2E8F0", ambience: "wind" },
  { id: "grilli", name: "Grilli di notte", tagline: "Notte d'estate", icon: "🌙", primary: "#8B7CF6", secondary: "#FDE68A", ambience: "fireflies" },
  { id: "phon", name: "Phon", tagline: "Soffio caldo e costante", icon: "🌀", primary: "#EC4899", secondary: "#FFB4D9", ambience: "airflow" },
];

// `%s` viene sostituito dal dettaglio scritto dall'utente (o da `fallback` se manca).
const CATEGORIES = [
  { id: "FILM", label: "Film", icon: "🎬", primary: "#EF4444", secondary: "#FFB38A", fallback: "un bel film", example: "Il Signore degli Anelli",
    ideas: ["Spegni le luci e guarda %s, senza telefono in mano.", "Prepara qualcosa di caldo e goditi %s dall'inizio alla fine.", "Riguarda %s: le storie che conosci già rilassano di più."] },
  { id: "SERIE_TV", label: "Serie TV", icon: "📺", primary: "#8B7CF6", secondary: "#F0ABFC", fallback: "quella serie che ti piace", example: "Breaking Bad",
    ideas: ["Un episodio di %s, solo uno. Poi vedi come ti senti.", "Mettiti comodo e riprendi %s da dove l'avevi lasciata.", "Coperta, divano e un episodio di %s."] },
  { id: "VIDEOGIOCHI", label: "Videogiochi", icon: "🎮", primary: "#22C55E", secondary: "#5EC8F2", fallback: "il tuo gioco preferito", example: "Zelda",
    ideas: ["Accendi %s e gioca per mezz'ora, senza obiettivi.", "Una partita tranquilla con %s: esplora, non correre.", "Fatti una sessione con %s, con calma e un po' di musica."] },
  { id: "PROGRAMMARE", label: "Programmare", icon: "💻", primary: "#0EA5E9", secondary: "#67E8F9", fallback: "un piccolo progetto", example: "la mia app",
    ideas: ["Lavora a %s, giusto per il gusto di farlo.", "Prova un'idea nuova su %s, senza scadenze.", "Scrivi un po' di codice per %s, una cosa piccola e bella."] },
  { id: "LETTURA", label: "Lettura", icon: "📖", primary: "#F59E0B", secondary: "#FDE68A", fallback: "un libro", example: "un giallo",
    ideas: ["Leggi qualche pagina di %s, con una luce calda.", "Venti minuti con %s: il resto può aspettare.", "Trova un angolo comodo e immergiti in %s."] },
  { id: "MUSICA", label: "Musica", icon: "🎧", primary: "#EC4899", secondary: "#FFB4D9", fallback: "la tua musica", example: "i Pink Floyd",
    ideas: ["Cuffie, occhi chiusi e %s. Ascolta e basta.", "Fai partire %s e lasciati portare.", "Ascolta %s dall'inizio alla fine, senza saltare tracce."] },
  { id: "SPORT", label: "Sport", icon: "⚽", primary: "#14B8A6", secondary: "#B4F07A", fallback: "una corsa", example: "una corsa al parco",
    ideas: ["Muoviti un po': %s, il corpo ringrazia.", "Mezz'ora dedicata a %s, al tuo ritmo.", "Esci per %s, senza guardare il cronometro."] },
  { id: "NATURA", label: "Natura", icon: "🥾", primary: "#16A34A", secondary: "#99F6E4", fallback: "una passeggiata", example: "una passeggiata nel bosco",
    ideas: ["Esci per %s, anche breve: guarda il cielo.", "Concediti %s all'aria aperta, lasciando il telefono in tasca.", "Un po' di verde: %s, con passo lento."] },
  { id: "ARTE", label: "Arte", icon: "🎨", primary: "#F97316", secondary: "#FFD166", fallback: "disegnare", example: "acquerelli",
    ideas: ["Prendi un foglio e dedicati a %s, senza giudicarti.", "Lasciati andare: %s, per il puro piacere di farlo.", "Dieci minuti di %s: crea qualcosa, anche di piccolo."] },
  { id: "FOTOGRAFIA", label: "Fotografia", icon: "📷", primary: "#64748B", secondary: "#E2E8F0", fallback: "belle foto", example: "tramonti",
    ideas: ["Vai in giro a cercare %s: luci, ombre, dettagli.", "Riguarda i tuoi scatti con calma e scegli i migliori.", "Esci con la fotocamera a caccia di %s."] },
  { id: "CUCINA", label: "Cucina", icon: "🍳", primary: "#DC2626", secondary: "#FCA5A5", fallback: "qualcosa di buono", example: "una torta",
    ideas: ["Mettiti ai fornelli e prepara %s.", "Cucina %s con calma, gustandoti ogni passaggio.", "Una tisana e poi %s: cucinare rilassa."] },
  { id: "BENESSERE", label: "Benessere", icon: "🧘", primary: "#5B8DEF", secondary: "#C4BCFF", fallback: "un po' di yoga", example: "yoga",
    ideas: ["Srotola il tappetino e dedicati a %s.", "Dieci minuti di %s, ascoltando il respiro.", "Un momento solo per te: %s."] },
  { id: "ALTRO", label: "Altro", icon: "❤️", primary: "#FF8A5B", secondary: "#FFC46B", fallback: "quello che ti piace", example: "fare un puzzle",
    ideas: ["Fai %s, senza sensi di colpa.", "È il momento giusto per %s.", "Regalati del tempo per %s."] },
];

// Respiro guidato: inspira 4 s, trattieni 2 s, espira 6 s, per 4 cicli.
const BREATH = [
  { label: "Inspira", hint: "lentamente dal naso", seconds: 4, to: 1 },
  { label: "Trattieni", hint: "senza sforzo", seconds: 2, to: null },
  { label: "Espira", hint: "piano, dalla bocca", seconds: 6, to: 0 },
];
const BREATH_CYCLES = 4;
