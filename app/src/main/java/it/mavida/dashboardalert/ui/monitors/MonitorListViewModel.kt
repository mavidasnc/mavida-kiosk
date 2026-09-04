package it.mavida.dashboardalert.ui.monitors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.mavida.dashboardalert.data.repository.MonitorRepository
import it.mavida.dashboardalert.domain.model.Monitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorListViewModel(private val repository: MonitorRepository) : ViewModel() {

    val monitors: StateFlow<List<Monitor>> = repository.observeMonitors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(monitor: Monitor, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(monitor.id, enabled) }
    }

    fun delete(monitor: Monitor) {
        viewModelScope.launch { repository.deleteMonitor(monitor.id) }
    }
}
