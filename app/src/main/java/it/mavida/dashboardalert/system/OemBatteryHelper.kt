package it.mavida.dashboardalert.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Gestione del risparmio energetico aggressivo degli OEM (spec §3.5).
 *
 * Molti produttori (Huawei/EMUI con PowerGenie, Xiaomi/MIUI, Oppo, Samsung...)
 * terminano i servizi in background anche se il service e' in foreground e
 * l'app e' esentata dall'ottimizzazione batteria standard. Non esiste API:
 * l'unica via e' guidare l'utente alle impostazioni giuste per il suo device
 * (pattern documentati su dontkillmyapp.com).
 *
 * Qui: rilevamento produttore + istruzioni in-app per i casi principali,
 * con fallback generico.
 */
object OemBatteryHelper {

    data class OemProfile(
        val manufacturerLabel: String,
        val steps: List<String>,
    )

    fun detect(): OemProfile {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            "huawei" in manufacturer || "honor" in manufacturer -> OemProfile(
                manufacturerLabel = "Huawei / Honor (EMUI)",
                steps = listOf(
                    "Impostazioni > Batteria > Avvio app (o 'Lancio app')",
                    "Trova 'Dashboard & Alert' e disattiva la gestione automatica",
                    "Attiva tutte e tre le opzioni: Avvio automatico, Avvio secondario, Esecuzione in background",
                    "Inoltre: Impostazioni > Batteria > attiva 'Mantieni attiva' se presente (PowerGenie)",
                ),
            )
            "xiaomi" in manufacturer || "redmi" in manufacturer || "poco" in manufacturer -> OemProfile(
                manufacturerLabel = "Xiaomi / Redmi / POCO (MIUI)",
                steps = listOf(
                    "Impostazioni > App > Gestisci app > 'Dashboard & Alert'",
                    "Attiva 'Avvio automatico' (Autostart)",
                    "In 'Risparmio batteria' scegli 'Nessuna restrizione'",
                    "Blocca l'app nella vista Recenti (trascina giu' e tocca il lucchetto)",
                ),
            )
            "oppo" in manufacturer || "realme" in manufacturer || "oneplus" in manufacturer -> OemProfile(
                manufacturerLabel = "Oppo / Realme / OnePlus (ColorOS/OxygenOS)",
                steps = listOf(
                    "Impostazioni > Batteria > Gestione energetica app > 'Dashboard & Alert'",
                    "Seleziona 'Non ottimizzare' / 'Consenti esecuzione in background'",
                    "Blocca l'app nella vista Recenti (icona lucchetto)",
                ),
            )
            "samsung" in manufacturer -> OemProfile(
                manufacturerLabel = "Samsung (One UI)",
                steps = listOf(
                    "Impostazioni > Manutenzione dispositivo > Batteria > Utilizzo batteria in background",
                    "Rimuovi 'Dashboard & Alert' da 'App in sospensione' e 'App in sospensione profonda'",
                    "Impostazioni > App > 'Dashboard & Alert' > Batteria > 'Senza restrizioni'",
                ),
            )
            "vivo" in manufacturer -> OemProfile(
                manufacturerLabel = "Vivo (Funtouch OS)",
                steps = listOf(
                    "Impostazioni > Batteria > Gestione energetica in background",
                    "Consenti a 'Dashboard & Alert' l'esecuzione in background",
                    "Abilita l'avvio automatico nelle impostazioni dell'app",
                ),
            )
            else -> OemProfile(
                manufacturerLabel = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                steps = listOf(
                    "Cerca nelle Impostazioni la voce 'Ottimizzazione batteria' o 'Risparmio energetico'",
                    "Imposta 'Dashboard & Alert' su 'Non ottimizzare' / 'Nessuna restrizione'",
                    "Se presente, abilita l''avvio automatico' per l'app",
                    "Blocca l'app nella vista Recenti se il sistema lo consente",
                ),
            )
        }
    }

    /** True se l'app e' gia' esentata dall'ottimizzazione batteria standard. */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Intent di sistema per chiedere l'esenzione (permesso gia' nel manifest). */
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
}
