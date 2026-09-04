package it.mavida.dashboardalert.ui.monitors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.mavida.dashboardalert.domain.model.Monitor
import it.mavida.dashboardalert.system.PinLock

/**
 * Schermata principale: lista dei monitor configurati.
 * Pensata per schermi piccoli e grandi: LazyColumn a larghezza piena con
 * righe alte e font grandi (leggibilita' a distanza, spec §4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorListScreen(
    viewModel: MonitorListViewModel,
    onEditMonitor: (Long) -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val monitors by viewModel.monitors.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard & Alert") },
                actions = {
                    // "Blocca ora": visibile solo se il PIN e' impostato (spec §3.6).
                    if (PinLock.isPinSet) {
                        IconButton(onClick = { PinLock.lock() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Blocca con PIN")
                        }
                    }
                    IconButton(onClick = onOpenBrowser) {
                        Icon(Icons.Default.Public, contentDescription = "Modalita' browser")
                    }
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.History, contentDescription = "Storico eventi")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditMonitor(0L) }) {
                Icon(Icons.Default.Add, contentDescription = "Nuovo monitor")
            }
        },
    ) { padding ->
        if (monitors.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nessun monitor configurato.\nTocca + per crearne uno.",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(monitors, key = { it.id }) { monitor ->
                    MonitorRow(
                        monitor = monitor,
                        onEdit = { onEditMonitor(monitor.id) },
                        onDelete = { viewModel.delete(monitor) },
                        onToggle = { enabled -> viewModel.setEnabled(monitor, enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorRow(
    monitor: Monitor,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(monitor.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${monitor.method} ${monitor.url}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    "Ogni ${monitor.intervalSeconds}s · ${monitor.rules.size} regole",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = monitor.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifica")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Elimina",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
