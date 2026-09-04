# Prompt per Claude Code — App Android "Dashboard & Alert" (kiosk su vecchio smartphone)

> Incolla tutto questo blocco in Claude Code. I parametri sono già decisi (vedi §10);
> se emergono ambiguità tecniche durante l'implementazione, fermati e chiedi prima di procedere.

---

## 1. Obiettivo

Voglio un'app **Android nativa in Kotlin**, **generica e portabile**, che trasformi **un qualsiasi dispositivo Android (telefono o tablet)** in un piccolo **cruscotto sempre acceso**, da posizionare sotto il monitor principale. Il caso d'uso tipico è riutilizzare un vecchio dispositivo, ma l'app **non deve essere legata a un modello specifico**: deve funzionare su marche e versioni Android diverse. L'app deve:

- eseguire **polling** periodico verso una o più **API REST** (HTTP/HTTPS);
- **valutare** la risposta secondo regole configurabili;
- attivare uno o più **trigger** in base all'esito (chiamata HTTP verso un'altra API, riproduzione di un MP3, notifica, vibrazione, avviso visivo a schermo pieno);
- offrire una modalità **browser embeddato a schermo intero** per visualizzare un cruscotto HTML online;
- **impedire lo standby** e restare sempre visibile.

Sono uno sviluppatore senior (background PHP/Laravel, sto approfondendo JS/React), quindi:

- usa sempre **best practice** consolidate e un'**architettura pulita**;
- **commenta il codice passo passo**, spiegando le scelte non ovvie;
- gestisci con cura **errori**, **casi limite** e **sicurezza**.

---

## 2. Stack tecnologico

- **Linguaggio:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Build:** Gradle con **Kotlin DSL** (`build.gradle.kts`)
- **Architettura:** MVVM + Repository, `ViewModel`, coroutine + `Flow`
- **Networking:** OkHttp (massima flessibilità su metodi/header/body arbitrari); serializzazione JSON con kotlinx.serialization o Moshi
- **Persistenza config:** Room
- **Segreti (token/API key negli header):** `EncryptedSharedPreferences` (Jetpack Security)
- **Audio:** `MediaPlayer` (o ExoPlayer/Media3 se più robusto per il caso d'uso)
- **Polling:** **foreground service** con loop a coroutine
  - ⚠️ **NON usare WorkManager per il polling**: il suo intervallo minimo è 15 minuti. Per intervalli brevi (secondi/minuti) serve un foreground service con notifica persistente e un ciclo `while (isActive) { poll(); delay(interval) }`.
  - Dichiara il `foregroundServiceType` corretto (es. `dataSync` o `specialUse`) e gestisci i permessi runtime necessari (incluso `POST_NOTIFICATIONS`).
- **minSdk:** **26 (Android 8.0)** — floor scelto per la massima portabilità su dispositivi anche datati, pur avendo pieno supporto a notification channel e foreground service. (Il dispositivo attualmente disponibile è un Huawei P40 Lite / Android 10, ben sopra il floor; il numero è modificabile.)
- **targetSdk/compileSdk:** ultima versione stabile disponibile nell'ambiente (verificala e usala), così l'app resta moderna su tutte le versioni supportate
- **Portabilità (vincolo importante):** **nessuna dipendenza da Google Play Services / GMS**. Usa solo API AOSP standard e **notifiche locali** (niente FCM), così l'app gira identica anche su dispositivi senza servizi Google (es. Huawei recenti).

---

## 3. Funzionalità core

### 3.1 Gestione dei "Monitor" (configurazioni di polling)
CRUD completo di **più configurazioni simultanee**, ognuna con:

- nome descrittivo;
- URL, metodo HTTP (GET/POST/PUT/PATCH/DELETE), **header personalizzati** (usati anche per l'autenticazione: Bearer, API key, ecc.), body opzionale;
- **intervallo di polling** (in secondi, con validazione di un minimo ragionevole);
- timeout richiesta e policy di **retry con backoff**;
- flag **abilitato/disabilitato**.

I monitor abilitati girano **contemporaneamente** e in modo indipendente.

### 3.2 Valutazione della risposta (rule engine)
Ogni monitor ha una o più **regole**. Una regola confronta un aspetto della risposta con un valore atteso. Implementa questi tipi di condizione (con **interfaccia comune ed estensibile** per aggiungerne di nuovi):

- **status code** (uguale / diverso / in un range);
- **campo JSON via JSONPath** con operatori `==`, `!=`, `>`, `<`, `>=`, `<=`, `contains`;
- **regex** sul body;
- **contains / not contains** su testo del body;
- condizione speciale **"errore/timeout/nessuna risposta"** (perdita di connettività).

### 3.3 Trigger
Ogni regola, quando soddisfatta, esegue una o più **azioni (trigger)**. Progetta un'**interfaccia `Trigger` comune** per aggiungerne facilmente di nuovi. Implementa in v1:

1. **Chiamata HTTP in uscita** (GET/POST) verso un'altra API: URL, metodo, header, body configurabili, con **templating** (poter inserire nel body/URL valori estratti dalla risposta del monitor).
2. **Riproduzione MP3** scelto **dalla memoria del telefono**:
   - selezione file tramite **Storage Access Framework** (`ACTION_OPEN_DOCUMENT`) con **permesso URI persistente** (`takePersistableUriPermission`), così da non richiedere permessi invasivi sull'intero storage;
   - controllo volume e stop.
3. **Notifica di sistema** (gestendo il permesso `POST_NOTIFICATIONS`).
4. **Vibrazione** con pattern configurabile.
5. **Avviso visivo a schermo pieno**: overlay colorato ben visibile da lontano, con **fade-out automatico dopo N secondi** (durata configurabile). Deve poter comparire anche sopra la modalità browser.

**Comportamento dei trigger (importante):**
- **Edge-trigger:** l'azione scatta quando la condizione **diventa** vera (transizione), non ad ogni ciclo di polling in cui resta vera.
- **Cooldown/antirimbalzo** configurabile per evitare ripetizioni ravvicinate.
- Prevedi anche l'opzione alternativa "ripeti finché vera ogni N secondi".

### 3.4 Modalità Browser (cruscotto HTML)
- Inserimento di un **URL**; visualizzazione in una **WebView a schermo intero**, dimensione massima possibile, **immersive/kiosk mode** (nasconde status bar e nav bar).
- Obiettivo: mostrare un cruscotto HTML online.
- JavaScript abilitato, gestione zoom/adattamento, **ricarica automatica opzionale** a intervalli, gestione errori di caricamento.
- ℹ️ La versione dell'**Android System WebView** varia molto tra dispositivi e versioni Android; su alcuni (es. device senza Play Store) può essere datata e non aggiornabile. Punta a compatibilità ampia: evita di dipendere da API web molto recenti e verifica il rendering del cruscotto sui dispositivi reali disponibili.
- Passaggio rapido tra "modalità browser" e "modalità monitor"; gli avvisi a schermo pieno (§3.3.5) devono poter comparire anche in questa modalità.

### 3.5 Sempre acceso / anti-standby
- `FLAG_KEEP_SCREEN_ON` sulla finestra + eventuale `WakeLock` dedicato.
- **Immersive mode** persistente.
- **Autostart al boot** (`RECEIVE_BOOT_COMPLETED`): al riavvio del telefono avvia il foreground service e riporta in primo piano l'app.
  - ⚠️ Su Android moderno l'avvio automatico dell'Activity in foreground dal boot ha delle limitazioni: implementa l'autostart del **service** e fai il **best effort** per portare in primo piano l'UI (es. full-screen intent), **documentando** l'approccio scelto e gli eventuali limiti / passi manuali (o l'uso in modalità launcher/kiosk).
- Richiesta di **esenzione dall'ottimizzazione batteria**.
- **Riavvio automatico** del service se viene terminato dal sistema.
- ⚠️ **Gestione OEM del risparmio energetico (multi-marca):** molti produttori (Huawei/EMUI con PowerGenie, Xiaomi/MIUI, Oppo/Realme, Samsung, ecc.) terminano in modo aggressivo i servizi in background, ognuno con impostazioni diverse. Oltre all'esenzione batteria via codice, l'app deve **rilevare il produttore** e **guidare l'utente** con una schermata/istruzioni in-app verso le impostazioni giuste (es. "App protette"/"Avvio app" su EMUI, "Autostart"/blocco in recenti su MIUI, ecc.). Prevedi un fallback generico e documenta i casi principali nel README. Riferimento utile: pattern noti su dontkillmyapp.com.
- Controllo **luminosità** e **tema notturno/dimming a fasce orarie**.

### 3.6 Utility
- **Import/export** della configurazione in **JSON** (backup/ripristino).
- **Schermata di log/storico**: risposte ricevute, esito regole, trigger scattati (con timestamp), utile per debug.
- **Lucchetto/PIN** opzionale per evitare modifiche accidentali toccando lo schermo.
- **Rilevamento perdita di connettività** come condizione a sé (vedi §3.2), utilizzabile come trigger.
- (Se sensato) **rotazione tra più dashboard**/URL in modalità browser.

---

## 4. Requisiti non funzionali

- Codice **commentato passo passo**, leggibile, con nomi chiari.
- **Separazione delle responsabilità** (data / domain / ui) e testabilità.
- Gestione robusta di **errori di rete**, **JSON malformato**, **permessi negati**.
- **Sicurezza:** segreti in storage cifrato; nessun segreto nei log; gestione consapevole di HTTPS (documenta come comportarsi con eventuali certificati self-signed, **senza** disabilitare la verifica TLS di default).
- **Compatibilità dispositivi (telefono e tablet):** **UI responsive/adattiva** che funzioni bene su schermi di dimensioni e densità diverse; supporto sia **verticale (portrait)** che **orizzontale (landscape)**. Nessuna assunzione "solo phone" o "solo portrait".
- **Leggibilità a distanza:** font grandi, alto contrasto, layout pensato per essere guardato da lontano; le dimensioni devono scalare in base allo schermo.
- Almeno alcuni **unit test** sul rule engine e sulla logica edge-trigger/cooldown.

---

## 5. Sistema di build & versionamento

- Usa `versionCode` e `versionName` in `build.gradle.kts` seguendo **Semantic Versioning** (MAJOR.MINOR.PATCH).
- Predisponi uno **script** (es. `./build.sh` o task Gradle) che, dopo ogni modifica:
  1. incrementa la versione (`versionCode` sempre, `versionName` secondo semver);
  2. ricompila la **APK di debug**;
  3. aggiorna il `CHANGELOG.md`.
- Documenta come **installare la APK** via `adb install` e, se possibile, l'**install/debug wireless** via ADB su Wi-Fi (comodo per iterare senza cavo).
- A fine build stampa il **percorso della APK** e le istruzioni di installazione.

---

## 6. Ambiente di sviluppo / verifica

- **Verifica e, se necessario, installa l'Android SDK** e i command-line tools (sdkmanager, platform-tools, build-tools).
- Se non è collegato un dispositivo fisico, **crea e avvia un emulatore** (AVD). Verifica idealmente su **più configurazioni**: una vicina al `minSdk 26` e una recente, in orientamento sia verticale che orizzontale, e su un profilo **tablet** oltre che telefono.
- Dopo l'implementazione, **compila ed esegui** l'app e **cattura screenshot** delle schermate principali (lista monitor, editor regola/trigger, modalità browser, avviso a schermo pieno).

---

## 7. Git & changelog

- Inizializza un **repository Git** con `.gitignore` adeguato per Android (build/, .gradle/, local.properties, keystore, ecc.).
- Adotta i **Conventional Commits** (`feat:`, `fix:`, `chore:`, `docs:`…).
- Mantieni un **`CHANGELOG.md`** nel formato **Keep a Changelog**, aggiornato ad ogni feature/fix.
- Scrivi un **`README.md`** con: descrizione, prerequisiti, come compilare, come installare sul telefono, come configurare un monitor di esempio.
- Fai un **commit per ogni feature/step** completato e con build funzionante.
- (Opzionale) proponi una configurazione **GitHub Actions** minimale per build della APK di debug su push.

---

## 8. Workflow richiesto a Claude Code

Procedi in modo **incrementale** e verificabile:

1. Predisponi ambiente (SDK/emulatore) e **scaffold** del progetto; primo commit.
2. Implementa una feature alla volta, in quest'ordine suggerito:
   1. persistenza + CRUD monitor;
   2. foreground service di polling + chiamata HTTP;
   3. rule engine di valutazione;
   4. sistema di trigger (a partire da HTTP in uscita e MP3);
   5. edge-trigger + cooldown;
   6. modalità browser kiosk + anti-standby;
   7. trigger aggiuntivi (notifica, vibrazione, avviso a schermo pieno con fade-out);
   8. import/export + schermata di log;
   9. autostart al boot, esenzione batteria, PIN, tema notturno/luminosità;
   10. perdita connettività + rotazione dashboard.
3. Dopo **ogni** feature: build, bump versione, aggiorna changelog, commit.
4. Alla fine: build finale, screenshot, istruzioni d'installazione, riepilogo.

Se emergono ambiguità o scelte tecniche rilevanti, **fermati e chiedi** prima di procedere.

---

## 9. Miglioramenti inclusi in v1

- Trigger su **transizione** con **cooldown** (niente MP3 in loop ad ogni polling).
- Trigger **avviso visivo a schermo pieno con fade-out**, **notifica**, **vibrazione**.
- Condizione dedicata alla **perdita di connettività**.
- **Rotazione** tra più dashboard / **tema notturno** e controllo luminosità.
- **Import/export JSON** e **schermata di log**.
- **Autostart al boot**, **riavvio automatico**, **esenzione ottimizzazione batteria** per stabilità 24/7.
- **PIN** anti-modifiche accidentali.

---

## 10. Parametri confermati

- **Stack:** Kotlin nativo (Jetpack Compose + Gradle Kotlin DSL).
- **Compatibilità:** app **generica** per qualsiasi Android (telefono e tablet), **senza dipendenze GMS**. Dispositivo attualmente disponibile per i test: Huawei P40 Lite (Android 10, senza servizi Google), ma è solo uno dei casi.
- **minSdk:** 26 (Android 8.0). **targetSdk/compileSdk:** ultima stabile.
- **Valutazione risposta:** rule engine con status code, JSONPath, regex, contains, perdita connessione.
- **Sorgente MP3:** file dalla memoria del telefono (via Storage Access Framework, URI persistente).
- **Autenticazione API:** gestita tramite header configurabili per monitor (segreti in storage cifrato).
- **Autostart al boot:** sì. **Più monitor simultanei:** sì.
- **Trigger v1:** HTTP in uscita, MP3, notifica, vibrazione, avviso a schermo pieno con fade-out.
