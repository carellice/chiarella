# Chiarella — suoni rilassanti per Android

App nativa Android (Kotlin + Jetpack Compose) per mescolare suoni rilassanti.

## Funzioni

- **11 suoni** in loop continuo, da accendere insieme e mescolare liberamente
- **Volume per ogni suono**, con slider dedicato, dissolvenze morbide e feedback aptico
- **Play/Pausa** e **Ferma tutti**
- **Timer di spegnimento** (rapidi da 15 min a 2 h oppure personalizzato da 5 min a 4 h), con dissolvenza finale di 12 secondi
- **Profili**: all'installazione sono vuoti; salvi tu il mix corrente con «Salva mix» (tieni premuto per eliminarli). Se tocchi il profilo che sta già suonando, va in pausa
- **Guida introduttiva** animata al primo avvio (8 schermate), rivedibile dal pulsante «?» in alto
- **Notifica "Live Update"**: su Android 16 appare come chip nella barra di stato, con il conto alla rovescia del timer e una barra di avanzamento. Dalla notifica puoi mettere in pausa o riprendere, allungare il timer (+15 min) e fermare tutto
- **Widget** per la home: play/pausa e, se lo allarghi, i primi 3 profili
- Pausa automatica durante le chiamate (poi riprende da sola) e quando scolleghi le cuffie
- Animazioni: sfondo "aurora" che prende i colori dei suoni attivi, scene animate dentro ogni card (pioggia, onde, braci, lucciole…), ingresso a cascata, splash con il logo bianco

## I suoni e i loop

Le registrazioni originali sono in `loop-chiarella/`. Vengono trasformate in loop infiniti senza
giunture dallo script `scripts/loops/process.py`, che scrive i file finali in `app/src/main/res/raw/`:

- **File lunghi**: cerca i punti di loop in cui inizio e fine si somigliano di più (volume e timbro),
  preferendo momenti quieti (es. fra un'onda e l'altra), allinea la fase dei suoni tonali e applica una
  dissolvenza incrociata di 3-6 s. Il loop è continuo per costruzione.
- **File corti** (falò 3 s, fiume 7 s): sintesi granulare, cioè un flusso di 75-90 s ricomposto in ordine
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

## Struttura

```
app/src/main/java/it/chiarella/app/
├── ChiarellaApp.kt              Application: crea mixer e archivio, aggiorna il widget
├── audio/Sounds.kt           Catalogo dei suoni
├── audio/Mixer.kt            Motore: un player ExoPlayer in loop per suono, fade, timer, audio focus
├── data/Store.kt             Profili, volumi, ultimo mix (SharedPreferences)
├── playback/PlaybackService  Servizio in primo piano + notifica Live Update
├── widget/ChiarellaWidget.kt    Widget (Jetpack Glance)
└── ui/                       Schermata principale e componenti animati (Compose)
```
