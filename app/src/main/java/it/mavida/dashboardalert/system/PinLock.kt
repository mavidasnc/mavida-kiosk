package it.mavida.dashboardalert.system

import it.mavida.dashboardalert.data.SettingsRepository
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PIN opzionale anti-modifiche accidentali (spec §3.6): utile in modalita'
 * kiosk, dove il dispositivo e' esposto e basta un tocco per cambiare config.
 *
 * Modello semplice: se il PIN e' impostato, l'app parte "bloccata" sulla
 * lista monitor in sola lettura; per modificare serve sbloccare. Non e' un
 * meccanismo di sicurezza anti-intrusione (i dati restano accessibili da
 * adb/root), ma un guard rail contro tocchi accidentali.
 *
 * Il PIN non e' mai salvato in chiaro: solo salted SHA-256.
 */
object PinLock {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var currentHash: String? = null

    /** Chiamato all'avvio: carica lo stato dal repository e blocca se serve. */
    fun init(settings: SettingsRepository.Settings) {
        currentHash = settings.pinHash
        _locked.value = settings.pinHash != null
    }

    fun onSettingsChanged(settings: SettingsRepository.Settings) {
        val hadPin = currentHash != null
        currentHash = settings.pinHash
        if (settings.pinHash == null) {
            _locked.value = false // PIN rimosso: sblocca
        } else if (!hadPin) {
            _locked.value = false // PIN appena impostato: la sessione corrente resta sbloccata
        }
    }

    val isPinSet: Boolean get() = currentHash != null

    fun tryUnlock(pin: String): Boolean {
        val ok = verify(pin)
        if (ok) _locked.value = false
        return ok
    }

    /** Verifica senza effetti collaterali (es. per rimuovere il PIN). */
    fun verify(pin: String): Boolean = hash(pin) == currentHash

    fun lock() {
        if (currentHash != null) _locked.value = true
    }

    /** Imposta un nuovo PIN: ritorna l'hash da persistere. */
    fun hashFor(pin: String): String = hash(pin)

    private fun hash(pin: String): String {
        // Salt statico per-installazione sarebbe meglio, ma per l'obiettivo
        // (anti-tocco accidentale) un salt di dominio e' sufficiente e
        // semplifica il ripristino via export/import.
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("dashboard-alert-pin:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
