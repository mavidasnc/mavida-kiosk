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
- Scaffold iniziale del progetto: Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL, Room, OkHttp, kotlinx.serialization.
