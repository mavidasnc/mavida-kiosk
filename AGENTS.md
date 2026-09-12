# AGENTS.md

## Progetto

App Android nativa (Kotlin + Jetpack Compose, MVVM, DI manuale in
`AppContainer`) che trasforma un dispositivo Android in un cruscotto kiosk
sempre acceso con monitoraggio di API REST. Dettagli in `README.md`,
cronologia versioni in `CHANGELOG.md` (Keep a Changelog + SemVer).

## Build e test

- `./gradlew assembleDebug` — compila l'APK di debug
- `./gradlew testDebugUnitTest` — unit test JVM (obbligatori verdi prima di rilasciare)
- `./build.sh [major|minor|patch]` — bump versione (versionCode sempre +1) + build APK

## Flusso obbligatorio a ogni modifica

Richiesto esplicitamente dal proprietario del progetto: **a ogni modifica
rilasciata** seguire SEMPRE questo flusso completo, nell'ordine:

1. **Bump versione**: `./build.sh major|minor|patch` (SemVer: fix = patch,
   feature = minor, breaking = major). Lo script compila anche l'APK.
2. **Documentazione**: aggiornare `CHANGELOG.md` (nuova sezione con versione
   e data) e, se la modifica tocca funzionalità o comportamenti documentati,
   anche `README.md` e questo file.
3. **Commit e push**: commit su `main` con messaggio convenzionale
   (`feat:`, `fix:`, `docs:`, ...) e `git push`.
4. **Release GitHub**: creare la release sul repo
   `mavidasnc/mavida-kiosk` con tag `vX.Y.Z` e allegare l'APK rinominato
   `mavida-kiosk-X.Y.Z.apk`, ad esempio:
   `gh release create vX.Y.Z /tmp/mavida-kiosk-X.Y.Z.apk --repo mavidasnc/mavida-kiosk --title "vX.Y.Z" --notes "..."`

Regole correlate:

- Il tag release DEVE seguire il formato `vX.Y.Z` e ogni release DEVE avere
  un asset `.apk` allegato: l'auto-aggiornamento in-app
  (`updater/AppUpdater`) dipende da entrambe le convenzioni.
- L'APK pubblicato è quello debug firmato con il debug keystore della
  macchina di build: gli aggiornamenti in-app funzionano solo con APK firmati
  dallo stesso certificato.
- Non committare mai segreti; i segreti runtime stanno in
  EncryptedSharedPreferences, mai nel DB né nei log.
