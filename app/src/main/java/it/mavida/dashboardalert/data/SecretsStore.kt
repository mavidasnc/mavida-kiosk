package it.mavida.dashboardalert.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Storage cifrato per i segreti (token, API key negli header HTTP).
 *
 * I valori marcati come "segreti" nel modello (HeaderEntry.secret = true)
 * non finiscono mai nel database Room ne' nei log: vengono salvati qui,
 * indicizzati dall'UUID dell'header. Nel DB resta solo un riferimento opaco.
 *
 * Regola di sicurezza: nessun metodo di questa classe deve essere chiamato
 * da percorsi di logging.
 */
class SecretsStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val FILE_NAME = "dashboard_alert_secrets"

        /** Chiave sotto cui viene salvato il valore segreto di un header. */
        fun headerKey(headerId: String): String = "header_$headerId"
    }
}
