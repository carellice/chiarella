# Backup Chiarella

Dal pulsante **Backup e ripristino** in alto si esporta un file `.json` e lo si importa
su web, Windows o Android. La funzione è disponibile dalla versione 1.9; le versioni
precedenti devono essere aggiornate per importare i file.

Il backup include profili (con identificatori, nomi, suoni e volumi), passioni e dettagli,
volumi dei singoli suoni, volume generale, ultimo mix, durata preferita del timer,
ultima passione proposta e stato della guida introduttiva.
Non include i file audio, i permessi del dispositivo o lo stato temporaneo della riproduzione.

L'importazione mostra il numero di profili e passioni, richiede una conferma e **sostituisce**
i dati locali. Ferma riproduzione e timer; non avvia automaticamente i suoni del backup.
Si può esportare un backup dei dati correnti prima di confermare. Annullare o selezionare
un file non valido lascia i dati presenti invariati. I file non vengono inviati a un server.

## Formato v1

```json
{
  "format": "chiarella-backup",
  "version": 1,
  "exportedAt": "2026-10-24T10:00:00Z",
  "data": {
    "presets": [{"id": "mix-1", "name": "Il mio mix", "mix": {"mare": 0.7}}],
    "passions": [{"id": "passion-1", "category": "LETTURA", "detail": "Un libro"}],
    "volumes": {"mare": 0.7},
    "masterVolume": 1,
    "lastMix": {"mare": 0.7},
    "lastTimerMinutes": 30,
    "onboardingDone": true,
    "lastPassionId": "passion-1"
  }
}
```

`format`, `version` e tutti i campi di `data` sono obbligatori. `exportedAt` è informativo.
I campi sconosciuti vengono ignorati. Gli identificatori dei suoni non ancora disponibili
su una piattaforma vengono conservati nei dati, ma non riprodotti.

- File UTF-8, con o senza BOM, fino a 2 MiB.
- Al massimo 1000 profili e 1000 passioni, con identificatori distinti per ciascun elenco.
- Identificatori di profili e passioni: `[A-Za-z0-9_.-]{1,128}`.
- Nomi dei profili non vuoti, massimo 200 unità UTF-16; dettagli delle passioni massimo 1000.
- Categorie: `FILM`, `SERIE_TV`, `VIDEOGIOCHI`, `PROGRAMMARE`, `LETTURA`, `MUSICA`, `SPORT`,
  `NATURA`, `ARTE`, `FOTOGRAFIA`, `CUCINA`, `BENESSERE`, `ALTRO`.
- Ogni mappa dei volumi contiene al massimo 100 voci, con chiavi `[a-z][a-z0-9_]{0,63}`
  e valori numerici finiti nell'intervallo 0–1, senza conversioni da stringhe.
- Timer: numero intero tra 5 e 240 minuti. Guida: booleano. Ultima passione: identificatore o `null`.
- Una versione di formato non supportata richiede di aggiornare Chiarella; non viene importata parzialmente.

## Verifiche

I test usano lo stesso file `tests/fixtures/backup-v1.json` con Unicode e casi limite.
Ogni codec scrive un file in `dist/backup-tests/`, letto dai test delle altre piattaforme.
Eseguire nell'ordine seguente dalla radice del repository:

```powershell
node --test tests/backup.test.cjs
dotnet run --project tests/windows/BackupTests.csproj -- .
.\gradlew.bat testDebugUnitTest
node --test tests/backup.test.cjs
dotnet run --project tests/windows/BackupTests.csproj -- .
```

Il test browser `tests/browser-backup.cjs` usa Playwright. Si può indicare il modulo
con `PLAYWRIGHT_MODULE` e l'eseguibile del browser con `CHIARELLA_BROWSER`; altrimenti
usa il pacchetto e Chromium installati nell'ambiente. Verifica esportazione dal pulsante,
importazione dei file nativi, conferma, annullamento, rifiuto dei file non validi, ripristino
su errore di scrittura, persistenza dopo riapertura e funzionamento offline.
