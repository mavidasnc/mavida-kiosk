# Changelog

Tutte le modifiche rilevanti a questo progetto sono documentate in questo file.

Il formato si basa su [Keep a Changelog](https://keepachangelog.com/it/1.1.0/),
e il progetto aderisce al [Semantic Versioning](https://semver.org/lang/it/).

## [Unreleased]

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
- Scaffold iniziale del progetto: Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL, Room, OkHttp, kotlinx.serialization.
