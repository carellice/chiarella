<p align="center">
  <img src="branding/chiarella_icon_512.png" alt="Icona di Chiarella" width="160" style="border-radius: 36px;" />
</p>

<h1 align="center">Chiarella</h1>

<p align="center">
  Suoni della natura da mescolare per rilassarti, concentrarti e dormire.<br/>
  App nativa Android e Windows, più una versione web. Open source.
</p>

<p align="center">
  <a href="https://github.com/carellice/chiarella/releases/latest"><img alt="Ultima versione" src="https://img.shields.io/github/v/release/carellice/chiarella?label=scarica&color=134447" /></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-749878" />
  <img alt="Kotlin e Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-8B7CF6" />
  <a href="LICENSE"><img alt="Licenza MIT" src="https://img.shields.io/badge/licenza-MIT-lightgrey" /></a>
</p>

## Scaricare l'app

Dall'[ultima release](https://github.com/carellice/chiarella/releases/latest):

- **Android**: scarica `Chiarella-X.apk` e installalo (Android 8.0 o successivo). Gli aggiornamenti successivi arrivano direttamente dall'app.
- **Windows**: scarica `Chiarella-Windows-X.zip`, estrailo dove preferisci e avvia `Chiarella.exe` (Windows 10 1809 o successivo, 64 bit). Non serve installare nulla.
- **Web**: apri **[carellice.github.io/chiarella](https://carellice.github.io/chiarella/)**. Si installa anche come app dal browser e funziona offline. In alternativa `Chiarella-Web-X.zip` si può pubblicare su un qualsiasi hosting statico.

## Funzioni

- **12 suoni** in loop continuo, inclusa una candela che scoppietta, da accendere insieme e mescolare liberamente
- **Volume generale**: dal pulsante con l'altoparlante nella barra in basso alzi o abbassi tutta l'app insieme, senza toccare i volumi dei singoli suoni (né dei profili); il pannello mostra anche come esce davvero ogni suono acceso
- **Volume del dispositivo**: all'apertura Android e Windows avvisano se il volume di sistema è sotto il massimo o disattivato e propongono «Porta al 100%», da confermare con un tocco. Se il sistema lo impedisce rimane l'indicazione per regolarlo manualmente. Sul web appare un promemoria: il browser non può leggere né modificare il volume del dispositivo. I volumi interni di Chiarella restano come li hai scelti.
- **Impostazioni**: dal pulsante con l'ingranaggio puoi disabilitare l'avviso del volume (anche con «Non mostrare più» nell'avviso) e attivare **Animazioni ridotte**. La modalità leggera ferma sfondi, particelle e pulsazioni e riduce le transizioni; audio, timer e istruzioni del respiro restano operativi. Le due preferenze vengono salvate sul singolo dispositivo e non sono sovrascritte dai backup.
- **Volume per ogni suono**, con slider dedicato, dissolvenze morbide e feedback aptico
- **Dettaglio del suono**: tieni premuta una card (clic destro su Windows e sul web) e si apre una finestra animata con lo stato del suono (acceso/spento), il volume sempre regolabile, una descrizione, i momenti ideali (dormire, concentrarsi, rilassarsi) e i suoni con cui si abbina bene, da aggiungere con un tocco
- **Play/Pausa** e **Ferma tutti**
- **Timer di spegnimento** (rapidi da 15 min a 2 h oppure personalizzato da 5 min a 4 h), con dissolvenza finale di 12 secondi
- **Profili**: all'installazione sono vuoti; salvi tu il mix corrente con «Salva mix» (tieni premuto per eliminarli). Se tocchi il profilo che sta già suonando, va in pausa
- **Un momento per te**: indichi le tue passioni per categoria (film, serie TV, videogiochi, programmare, lettura, musica…) con un dettaglio facoltativo («Zelda», «Breaking Bad»). Quando vuoi rilassarti fai prima un breve respiro guidato (inspira 4 s, trattieni 2 s, espira 6 s, per 4 cicli, con un fiore animato che si apre e si chiude) e poi Chiarella ti propone cosa fare tra le tue passioni, con un'idea concreta
- **Parla con Chiarella** (solo Android): una chat con l'intelligenza artificiale di [Cloudflare Workers AI](https://developers.cloudflare.com/workers-ai/) per quando hai la testa piena, sei in ansia o in overthinking. Ti ascolta, ti fa una domanda alla volta, propone piccole tecniche (dare un nome al pensiero, separare ciò che puoi controllare, il 5-4-3-2-1…) e, quando è il momento, dei pulsanti per accendere i suoni giusti, fare il respiro guidato direttamente nella chat o aprire «Un momento per te»; conosce le tue passioni e i tuoi profili, e quello che fai nell'app (respiro finito, suoni accesi, proposta accettata) entra nella conversazione. Da «Un momento per te» puoi anche «parlarne con Chiarella». Puoi anche mandare **messaggi vocali**: tocca il microfono, parla (fino a 2 minuti, con onde che seguono la voce e i suoni in pausa mentre registri) e invia; il vocale viene trascritto da Whisper su Workers AI con lo stesso account e la trascrizione compare nella chat come messaggio vocale. Si collega al tuo account Cloudflare: inserisci **Account ID** e un **API token** con il permesso Workers AI (modello «Workers AI» in *Profilo → API Tokens*); l'app li verifica, e puoi scegliere tra Llama 3.3 70B e Llama 3.1 8B. Credenziali e conversazione restano solo sul telefono: sono escluse dal backup su cloud di Android e dal file di backup di Chiarella. Non sostituisce un professionista: in caso di pericolo ricorda il 112 e il Telefono Amico
- **Guida introduttiva** animata al primo avvio (8 schermate), rivedibile dal pulsante «?» in alto
- **Notifica "Live Update"**: su Android 16 appare come chip nella barra di stato, con il conto alla rovescia del timer e una barra di avanzamento. Dalla notifica puoi mettere in pausa o riprendere, allungare il timer (+15 min) e fermare tutto
- **Widget** per la home: play/pausa e, se lo allarghi, i primi 3 profili
- **Aggiornamenti dall'app**: Chiarella controlla le release di GitHub, mostra le novità, scarica il nuovo APK e avvia l'installazione
- Pausa automatica durante le chiamate (poi riprende da sola) e quando scolleghi le cuffie
- Animazioni: sfondo "aurora" che prende i colori dei suoni attivi, scene animate dentro ogni card (pioggia, onde, braci, lucciole…), ingresso a cascata, splash con il logo bianco

## Versione Windows

App nativa **WinUI 3** (Windows App SDK, .NET 8) in `windows/Chiarella.Windows`, con le stesse funzioni dell'app
Android: suoni con volume e scene animate, sfondo "aurora", profili, timer con dissolvenza, «Un momento per te».
L'audio passa da un unico flusso WASAPI (NAudio) e i file Vorbis vengono riletti a livello di campione (NVorbis),
quindi i loop restano senza giunture. I suoni sono gli stessi dell'app Android (`app/src/main/res/raw`), non duplicati.
Dati e profili sono in `%LOCALAPPDATA%\Chiarella`.

```bash
cd windows/Chiarella.Windows && dotnet run -c Release -p:Platform=x64
```

## Versione web

Sito statico in `web/` (HTML, CSS e JavaScript, nessuna compilazione), installabile come app (PWA) e utilizzabile offline.
L'audio usa la Web Audio API con buffer in loop, quindi senza giunture. Per provarla in locale avvia un server
dalla cartella del progetto e apri `/web/` (i suoni vengono presi da `app/src/main/res/raw`):

```bash
python -m http.server 8765
```

All'apertura una schermata mostra l'avanzamento del caricamento dei 12 suoni e rende disponibile l'interfaccia
quando tutti i file sono pronti. I suoni vengono conservati nella cache del browser per gli accessi successivi e l'uso
offline; se il caricamento fallisce, «Riprova» riprende senza riscaricare i file già pronti.

Nel pacchetto della release i suoni sono già dentro `sounds/`. Il sito su GitHub Pages si aggiorna da solo a ogni push
su `main` che tocca `web/` o i suoni (workflow `.github/workflows/pages.yml`).

## Backup tra web, Windows e Android

Dal pulsante **Backup e ripristino** in alto puoi **esportare** profili, passioni, volumi e preferenze
in un file JSON e **importarlo** su qualsiasi altra versione di Chiarella (dalla 1.9).
Prima di sostituire i dati presenti viene mostrato un riepilogo e chiesta conferma.
La riproduzione e il timer si fermano; i dati del backup non avviano automaticamente l'audio.
Il file resta sul tuo dispositivo. [Formato e verifiche](docs/backup-format.md).

## I suoni e i loop

Le registrazioni originali sono in `loop-chiarella/`. Vengono trasformate in loop infiniti senza
giunture dallo script `scripts/loops/process.py`, che scrive i file finali in `app/src/main/res/raw/`:

- **File lunghi**: cerca i punti di loop in cui inizio e fine si somigliano di più (volume e timbro),
  preferendo momenti quieti (es. fra un'onda e l'altra), allinea la fase dei suoni tonali e applica una
  dissolvenza incrociata di 3-6 s. Il loop è continuo per costruzione.
- **File corti** (fiume 7 s): sintesi granulare, cioè un flusso di 75-90 s ricomposto in ordine
  casuale da frammenti della registrazione, anche leggermente ri-intonati, così lo schema non si riconosce.
- **Volume uniforme**: tutti a -24 LUFS (la pioggia a -28 per non schiacciare le gocce), con limiter
  applicato "ad anello" per non creare gradini nel punto di loop.
- `scripts/loops/verify.py` controlla che la giuntura non sia distinguibile da un istante qualsiasi del file;
  in `anteprime-giunture/` ci sono 12 s attorno a ogni giuntura da ascoltare.

Nell'app ogni suono usa due player che si passano il testimone con 2,5 s di dissolvenza (`LoopVoice.kt`),
così nessuna imperfezione di decodifica può creare un "tic" alla ripetizione.

Per cambiare o aggiungere un suono: metti il WAV in `loop-chiarella/` (nome `Nome_loop_….wav`), aggiungi la
corrispondenza in `NAMES` dentro `process.py` e lancia (serve Python con numpy e ffmpeg):

```bash
cd scripts/loops && python process.py ../../loop-chiarella ../../app/src/main/res/raw ../../anteprime-giunture
```

Il falò usa la nuova registrazione `loop-chiarella/falo nuovo.wav` (circa 89 s), con precedenza
sul vecchio WAV di 3 s. Per rigenerare soltanto questo suono aggiungi `--only falo` al comando;
il loop mantiene la registrazione originale con una dissolvenza alla giuntura.

La candela usa `loop-chiarella/suono candela.wav` e si rigenera con `--only candela`.
È già preparata come loop: viene soltanto convertita in Vorbis, conservando durata, volume e giuntura originali.
I segnali in `suoni-respirazione/` sono esportati in `res/raw/respiro_inspira.ogg` e
`res/raw/respiro_espira.ogg`: vengono riprodotti una volta all'inizio di ogni inspirazione
ed espirazione del respiro guidato, anche nella chat Android. La fase di trattenimento resta silenziosa.

Nomi, descrizioni, icone e colori dei suoni sono in
`app/src/main/java/it/chiarella/app/audio/Sounds.kt`; profili e preferenze in `data/Store.kt`. Icona, splash e logo bianco si rigenerano con `scripts/gen_icon.py` a partire da `branding/chiarella_logo.png`.

## Compilare e installare

Serve JDK 17 e l'Android SDK (piattaforma 36). Dalla cartella del progetto:

```bash
./gradlew assembleRelease
```

L'APK viene creato in `app/build/outputs/apk/release/app-release.apk`.
Con il telefono collegato via USB (debug USB attivo):

```bash
./gradlew installRelease
```

Oppure apri la cartella con Android Studio e premi Run.

> La build release è firmata con la chiave di debug, così si installa subito.
> Per pubblicarla sul Play Store va configurata una chiave di firma tua.

## Pubblicare una nuova versione

Fai doppio clic su **`release.bat`** (servono la [GitHub CLI](https://cli.github.com/) collegata con `gh auth login` e il .NET 8 SDK). Lo script:

1. alza la versione (es. 1.5 → 1.6) in `app/build.gradle.kts` e nel progetto Windows;
2. compila l'APK firmato, l'app Windows (zip autonomo) e il pacchetto web (zip);
3. fa commit, tag `v1.6` e push;
4. crea la release su GitHub con APK e zip allegati e le note di rilascio
   (te le chiede; premendo Invio usa l'elenco dei commit dall'ultima release).

Opzioni: `release.bat -Version 2.0` per scegliere la versione, `release.bat -Notes "Novità…"` per le note.

> **Importante:** Android installa un aggiornamento solo se è firmato con la stessa chiave della versione installata.
> Le release sono firmate con la chiave di questo PC (`%USERPROFILE%\.android\debug.keystore`):
> **conservane una copia di backup**, altrimenti le versioni future non potranno aggiornare l'app già installata.

## Struttura

```
app/src/main/java/it/chiarella/app/
├── ChiarellaApp.kt              Application: crea mixer e archivio, aggiorna il widget
├── ai/AiChat.kt              Chat con Cloudflare Workers AI (streaming, azioni, credenziali)
├── audio/Sounds.kt           Catalogo dei suoni
├── audio/Mixer.kt            Motore: un player ExoPlayer in loop per suono, fade, timer, audio focus
├── data/Store.kt             Profili, volumi, ultimo mix (SharedPreferences)
├── playback/PlaybackService  Servizio in primo piano + notifica Live Update
├── update/Updater.kt         Aggiornamenti dalle release di GitHub
├── widget/ChiarellaWidget.kt    Widget (Jetpack Glance)
└── ui/                       Schermata principale, «Un momento per te», chat e componenti animati (Compose)

windows/Chiarella.Windows/       App Windows (WinUI 3): Audio/Mixer.cs, Data/, Ui/
web/                             Versione web (index.html, app.js, data.js, style.css)
```
