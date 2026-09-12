# Changelog

Tutte le modifiche rilevanti a questo progetto sono documentate in questo file.

Il formato si basa su [Keep a Changelog](https://keepachangelog.com/it/1.1.0/),
e il progetto aderisce al [Semantic Versioning](https://semver.org/lang/it/).

## [Unreleased]

## [0.3.0] - 2026-09-12

### Added
- Auto-aggiornamento via GitHub Releases: sezione "Aggiornamenti" nelle Impostazioni con versione installata, pulsante "Verifica aggiornamenti" (confronto semver con l'ultima release del repo pubblico, nessun token richiesto) e pulsante "Aggiorna a ..." che scarica l'APK e lancia l'installazione di sistema (con richiesta guidata del permesso "fonti sconosciute" al primo uso).
- `AppUpdater` (check `releases/latest`, download con timeout estesi, installazione via FileProvider) e permesso `REQUEST_INSTALL_PACKAGES` nel manifest.
- 6 unit test JVM sul confronto semver (`AppUpdaterTest`).
- README: sezione "Aggiornamento" e installazione diretta da GitHub Releases.

## [0.2.3] - 2026-09-12

### Added
- Modalità browser kiosk: i controlli (ricarica, configura, torna ai monitor) si nascondono automaticamente dopo 5 secondi di inattività per lasciare la pagina pulita; riappaiono con un tap sullo schermo o al cambio di orientamento, e si nascondono di nuovo se non usati.

### Fixed
- Schermata di configurazione delle dashboard (opzioni del kiosk) ora con sfondo scuro e testi chiari, coerente con il tema dell'app (prima appariva con sfondo bianco).

## [0.2.2] - 2026-09-04

### Fixed
- Setup dashboard (modalità browser): box URL più compatto (96dp con scroll interno) e schermata scorrevole, così è utilizzabile anche in orientamento orizzontale (prima i pulsanti uscivano dallo schermo).

## [0.2.1] - 2026-09-04

### Added
- Modalità browser kiosk: pulsante "Configura dashboard" (ingranaggio) nei FAB del kiosk per modificare URL/reload/rotazione senza cancellare i dati dell'app; la schermata di setup precarica gli URL già salvati.

## [0.2.0] - 2026-09-04

### Added
- Persistenza Room: entity `Monitor`, `Rule`, `Trigger`, `LogEntry` (con FK a cascata), DAO, database e repository (MVVM + Repository).
- Modelli di dominio serializzabili (kotlinx.serialization): condizioni delle regole e configurazioni dei trigger come gerarchie sealed con discriminatore "type".
- Storage cifrato dei segreti (header sensibili) con EncryptedSharedPreferences: i valori segreti non finiscono nel DB né nei log.
- CRUD completo dei monitor con UI Compose: lista con switch abilita/disabilita, editor con URL/metodo/header/body/intervallo/timeout/retry.
- Tema Material 3 scuro di default con tipografia ingrandita (leggibilità a distanza).
- `PollingService` foreground (tipo dataSync, notifica persistente, START_STICKY, partial WakeLock): un loop a coroutine indipendente per ogni monitor abilitato, riavvio selettivo dei loop quando la configurazione cambia.
- `HttpPoller` su OkHttp: metodi/header/body arbitrari, timeout per monitor, retry con backoff esponenziale; segreti risolti solo in memoria e mai loggati.
- `BootReceiver`: avvia il service al boot (eccezione alle restrizioni di background start).
- Richiesta runtime del permesso `POST_NOTIFICATIONS` (Android 13+).
- Rule engine puro (nessuna dipendenza Android): interfaccia estensibile `RuleCondition` con condizioni status code (=, !=, range), JSONPath (==, !=, >, <, >=, <=, contains via Jayway), regex sul body, contains/not contains, errore/timeout/nessuna risposta. Eccezioni mai propagate: input malformato = condizione falsa.
- 12 unit test JVM sul rule engine (`RuleEngineTest`).
- Sistema di trigger con interfaccia comune `Trigger`: chiamata HTTP in uscita (con templating `{{monitor}}/{{status}}/{{error}}/{{timestamp}}/{{body}}/{{json:$.path}}`), riproduzione MP3 da URI SAF (MediaPlayer, canale alarm, stop), notifica di sistema, vibrazione con pattern, avviso full-screen con fade-out (`AlertActivity` sopra lockscreen, lanciata via full-screen intent).
- `TriggerDispatcher`: esecuzione isolata per trigger, esito registrato nello storico senza mai loggare segreti.
- Edge-trigger + cooldown: `TriggerStateMachine` pura (transizione falso→vero, cooldown anti-rimbalzo, opzione "ripeti finché vera ogni N secondi"); pipeline completa polling → regole → trigger in `RuleEngineResponseHandler` con log delle transizioni.
- 8 unit test JVM su edge-trigger/cooldown (`TriggerStateMachineTest`).
- Modalità browser kiosk: WebView full-screen (JavaScript, DOM storage, adattamento viewport, zoom), gestione errori con retry, reload automatico opzionale a intervalli, immersive mode con ripristino all'uscita, FAB rapidi monitor↔browser.
- Anti-standby: `FLAG_KEEP_SCREEN_ON` guidato da impostazione; `SettingsRepository` reattivo (SharedPreferences + StateFlow) per URL dashboard e reload.
- UI di configurazione regole e trigger nell'editor monitor: editor per ogni tipo di condizione e di trigger, policy edge/cooldown/ripeti, selettore MP3 via SAF con permesso URI persistente.
- Import/export della configurazione in JSON via SAF (`ConfigTransfer`, formato versionato, sostituzione completa al ripristino; header segreti esclusi dall'export).
- Schermata storico eventi (risposte, esiti regole, trigger scattati con timestamp, livello, dettagli; svuotamento e rotazione automatica a 2000 voci).
- Boot autostart: `BootReceiver` avvia il service e fa best effort per l'UI via full-screen intent (limiti Android 10+/14+ documentati nei commenti).
- Esenzione ottimizzazione batteria: richiesta in-app con stato; rilevamento OEM (EMUI, MIUI, ColorOS/OxygenOS, One UI, Funtouch + fallback) con istruzioni guidate in-app.
- PIN opzionale anti-modifiche accidentali: hash salted SHA-256, tastierino full-screen, blocco manuale dalla lista monitor.
- Dimming notturno a fasce orarie con controllo luminosità della finestra (anche a cavallo di mezzanotte).
- Rilevamento perdita di connettività del dispositivo (`ConnectivityObserver` su ConnectivityManager, transizioni loggate nello storico), complementare alla condizione "errore/timeout/nessuna risposta" del rule engine.
- Rotazione automatica tra più dashboard in modalità browser (lista URL, intervallo configurabile, indicatore posizione).
- Scaffold iniziale del progetto: Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL, Room, OkHttp, kotlinx.serialization.
