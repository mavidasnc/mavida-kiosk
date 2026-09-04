package it.mavida.dashboardalert.core.json

import kotlinx.serialization.json.Json

/**
 * Istanza Json condivisa da tutta l'app.
 *
 * - `ignoreUnknownKeys`: fondamentale per la compatibilita' dell'import/export
 *   (un file esportato da una versione futura con campi nuovi deve poter essere
 *   letto da questa versione senza crash).
 * - `classDiscriminator = "type"`: le gerarchie sealed (condizioni e trigger)
 *   vengono serializzate con un campo "type" leggibile nel JSON esportato,
 *   invece del nome completo della classe Kotlin.
 * - `encodeDefaults`: l'export JSON contiene sempre tutti i campi, cosi' il
 *   file di backup e' auto-documentato.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    classDiscriminator = "type"
}
