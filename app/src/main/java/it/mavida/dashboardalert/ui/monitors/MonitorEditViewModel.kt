package it.mavida.dashboardalert.ui.monitors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.mavida.dashboardalert.data.repository.MonitorRepository
import it.mavida.dashboardalert.domain.model.HeaderEntry
import it.mavida.dashboardalert.domain.model.Monitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel dell'editor di un monitor.
 *
 * Tiene un draft mutabile in uno StateFlow: la UI modifica il draft e il
 * salvataggio e' atomico (tutto il grafo monitor+regole+trigger in una
 * chiamata al repository). monitorId = 0 significa "nuovo monitor".
 */
class MonitorEditViewModel(
    private val repository: MonitorRepository,
    private val monitorId: Long,
) : ViewModel() {

    private val _draft = MutableStateFlow(
        Monitor(name = "", url = "https://"),
    )
    val draft: StateFlow<Monitor> = _draft.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (monitorId != 0L) {
            viewModelScope.launch {
                repository.getMonitor(monitorId)?.let { _draft.value = it }
            }
        }
    }

    fun update(transform: (Monitor) -> Monitor) {
        _draft.update(transform)
    }

    fun addHeader() {
        _draft.update { it.copy(headers = it.headers + HeaderEntry(name = "", value = "")) }
    }

    fun updateHeader(id: String, transform: (HeaderEntry) -> HeaderEntry) {
        _draft.update { m ->
            m.copy(headers = m.headers.map { if (it.id == id) transform(it) else it })
        }
    }

    fun removeHeader(id: String) {
        _draft.update { m -> m.copy(headers = m.headers.filterNot { it.id == id }) }
    }

    fun save() {
        val monitor = _draft.value
        // Validazione minima lato client prima di toccare il DB.
        when {
            monitor.name.isBlank() -> _error.value = "Il nome e' obbligatorio"
            monitor.url.isBlank() || monitor.url == "https://" ->
                _error.value = "L'URL e' obbligatorio"
            monitor.intervalSeconds < Monitor.MIN_INTERVAL_SECONDS ->
                _error.value = "Intervallo minimo: ${Monitor.MIN_INTERVAL_SECONDS} secondi"
            else -> {
                viewModelScope.launch {
                    runCatching { repository.saveMonitor(monitor) }
                        .onSuccess { _saved.value = true }
                        .onFailure { _error.value = "Errore salvataggio: ${it.message}" }
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
