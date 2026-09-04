# Dashboard & Alert

App Android nativa (Kotlin + Jetpack Compose) che trasforma un qualsiasi
dispositivo Android — telefono o tablet, anche datato — in un **cruscotto
sempre acceso** con monitoraggio di API REST, regole di valutazione e trigger
di allarme. Pensata per riutilizzare un vecchio smartphone come pannello
sotto il monitor principale.

**Nessuna dipendenza da Google Play Services**: funziona identica anche su
dispositivi senza GMS (es. Huawei recenti).

## Funzionalità

- **Monitor multipli simultanei**: polling HTTP/HTTPS verso una o più API,
  ciascuno con URL, metodo (GET/POST/PUT/PATCH/DELETE), header personalizzati
  (anche per Bearer/API key), body, intervallo, timeout e retry con backoff.
- **Rule engine estensibile**: condizioni su status code (=, !=, range),
  campi JSON via JSONPath (==, !=, >, <, >=, <=, contains), regex sul body,
  contains/not contains, errore/timeout/nessuna risposta.
- **Trigger**: chiamata HTTP in uscita con templating (`{{monitor}}`,
  `{{status}}`, `{{error}}`, `{{timestamp}}`, `{{body}}`, `{{json:$.path}}`),
  riproduzione MP3 scelto dal telefono (SAF, permesso URI persistente),
  notifica di sistema, vibrazione con pattern, avviso a schermo pieno con
  fade-out automatico.
- **Edge-trigger + cooldown**: i trigger scattano sulla transizione
  falso→vero, con cooldown anti-rimbalzo; opzione "ripeti finché vera".
- **Modalità browser kiosk**: WebView full-screen con immersive mode, reload
  automatico opzionale e **rotazione tra più dashboard**.
- **Sempre acceso**: foreground service con notifica persistente,
  `FLAG_KEEP_SCREEN_ON`, WakeLock, avvio automatico al boot, riavvio
  automatico (START_STICKY), esenzione ottimizzazione batteria e istruzioni
  guidate per il risparmio energetico degli OEM (EMUI, MIUI, ColorOS,
  One UI, Funtouch + fallback generico).
- **Utility**: import/export JSON della configurazione, storico eventi,
  PIN anti-modifiche accidentali, dimming notturno a fasce orarie,
  rilevamento perdita di connettività.

## Prerequisiti

- JDK 17 (es. Temurin 17)
- Android SDK con platform 35 e build-tools (l'SDK si configura in
  `local.properties` con `sdk.dir=...`)
- Nessun emulatore o account richiesto per compilare.

## Compilazione

```bash
export JAVA_HOME=/percorso/del/jdk17   # su Windows/Git Bash: "$HOME/scoop/apps/temurin17-jdk/current"
./gradlew assembleDebug                # APK di debug
./gradlew testDebugUnitTest            # unit test JVM (rule engine, edge-trigger)
```

Oppure lo script completo (bump versione + build + istruzioni):

```bash
./build.sh [major|minor|patch]
```

L'APK risultante è in `app/build/outputs/apk/debug/app-debug.apk`.

## Installazione sul telefono

### Via USB

1. Abilita **Opzioni sviluppatore** → **Debug USB** sul dispositivo.
2. Collega il cavo e poi:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Via Wi-Fi (ADB wireless)

Comodo per iterare senza cavo. Su Android 11+:

1. Sul telefono: Opzioni sviluppatore → **Debug wireless** → "Associa
   dispositivo con codice".
2. Sul PC: `adb pair <ip>:<porta>` e inserisci il codice.
3. `adb connect <ip>:<porta>` (porta mostrata nella schermata Debug wireless).
4. `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Su versioni più vecchie: collega una volta via USB, poi
`adb tcpip 5555` e `adb connect <ip-del-telefono>:5555`.

## Configurazione di un monitor di esempio

1. Apri l'app → tocca **+**.
2. Nome: `Temperatura ufficio`; URL: `https://api.esempio.it/sensori/1`;
   metodo GET; intervallo 30s.
3. Se serve autenticazione: aggiungi un header `Authorization` =
   `Bearer <token>` e spunta **Segreto** (il valore finisce in
   EncryptedSharedPreferences, non nel database né nei backup).
4. Aggiungi una **regola**: tipo "Campo JSON (JSONPath)", path
   `$.temperature`, operatore `>`, valore atteso `28`.
5. Aggiungi un **trigger**: "Avviso a schermo pieno" (durata 30s) e/o
   "Riproduci suono" scegliendo un MP3 dalla memoria.
6. Salva e abilita il monitor: il servizio di polling parte da solo.

Per il kiosk: icona **globo** nella barra in alto → inserisci l'URL della
dashboard HTML → "Avvia browser".

## Note su stabilità 24/7 e limiti noti

- **Ottimizzazione batteria**: richiedila da Impostazioni (in-app) e segui le
  istruzioni specifiche del produttore (Huawei/Xiaomi/Oppo/Samsung...):
  molti OEM terminano le app in background a prescindere dalle API standard.
  Riferimento: dontkillmyapp.com.
- **Avvio UI al boot**: il servizio riparte sempre (`BOOT_COMPLETED`), ma
  portare l'interfaccia in primo piano dal boot è limitato da Android 10+;
  l'app usa una notifica full-screen come best effort (su Android 14+ può
  servire l'abilitazione manuale in Impostazioni > App > Accesso speciale >
  "notifiche a schermo intero"). Per un kiosk vero, valuta l'uso come
  launcher o una MDM.
- **TLS**: la verifica dei certificati è sempre attiva. Per endpoint con
  certificati self-signed installa il CA sul dispositivo; non disabilitare
  la verifica.
- **WebView**: la versione di Android System WebView varia tra dispositivi;
  dashboard molto moderne potrebbero non renderizzare su WebView datate.

## Struttura del codice

```
app/src/main/java/it/mavida/dashboardalert/
├── DashboardAlertApp.kt      # Application + notification channel
├── AppContainer.kt           # DI manuale
├── MainActivity.kt           # anti-standby, PIN gate, dimming notturno
├── core/json/                # Json condiviso (kotlinx.serialization)
├── domain/                   # logica pura, testabile (no Android)
│   ├── model/                # Monitor, Rule, TriggerConfig (sealed, serializzabili)
│   ├── rule/                 # RuleCondition + implementazioni + factory
│   └── trigger/              # Trigger, TemplateEngine, TriggerStateMachine
├── data/                     # Room (entity/DAO/DB), repository, SecretsStore,
│                             # SettingsRepository, ConfigTransfer (import/export)
├── polling/                  # PollingService (foreground), HttpPoller, ResponseHandler
├── trigger/                  # implementazioni trigger (Android) + dispatcher
├── system/                   # OemBatteryHelper, PinLock, ConnectivityObserver
├── boot/                     # BootReceiver (autostart)
└── ui/                       # schermate Compose (monitors, browser, log,
                              # settings, pin, alert)
```

Test JVM in `app/src/test/`: `RuleEngineTest`, `TriggerStateMachineTest`.
