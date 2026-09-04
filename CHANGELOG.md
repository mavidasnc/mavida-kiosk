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
- Scaffold iniziale del progetto: Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL, Room, OkHttp, kotlinx.serialization.
