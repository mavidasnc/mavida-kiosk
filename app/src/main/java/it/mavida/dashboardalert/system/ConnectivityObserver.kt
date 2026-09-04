package it.mavida.dashboardalert.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import it.mavida.dashboardalert.data.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Osservatore della connettivita' di rete del dispositivo (spec §3.6).
 *
 * E' complementare alla condizione "errore/timeout/nessuna risposta" del rule
 * engine: quella scatta quando IL MONITOR non risponde, questa rileva quando
 * e' IL DISPOSITIVO a essere offline (Wi-Fi crollata, ecc.). Le transizioni
 * vengono registrate nello storico, cosi' nei log si distingue subito
 * "server down" da "rete del kiosk assente".
 */
class ConnectivityObserver(
    context: Context,
    private val logRepository: LogRepository,
    private val scope: CoroutineScope,
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update(true)
        override fun onLost(network: Network) = update(currentlyOnline())
    }

    fun start() {
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
        // Stato iniziale nello storico, per debug.
        scope.launch {
            logRepository.log(
                level = LogRepository.LEVEL_INFO,
                message = if (_isOnline.value) "Rete disponibile" else "Rete assente all'avvio",
            )
        }
    }

    fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun update(online: Boolean) {
        if (_isOnline.value != online) {
            _isOnline.value = online
            scope.launch {
                logRepository.log(
                    level = if (online) LogRepository.LEVEL_INFO else LogRepository.LEVEL_ERROR,
                    message = if (online) "Connettivita' ripristinata" else "Connettivita' persa",
                )
            }
        }
    }

    private fun currentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
