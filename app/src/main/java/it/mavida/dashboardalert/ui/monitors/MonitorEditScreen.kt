package it.mavida.dashboardalert.ui.monitors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.mavida.dashboardalert.domain.model.HttpMethod

/**
 * Editor di un monitor: dati generali, header personalizzati, retry/timeout.
 * L'editing di regole e trigger arriva con il rule engine (fasi successive).
 *
 * Layout a colonna scrollabile: funziona sia in portrait che in landscape
 * e su tablet (dove semplicemente i campi sono piu' larghi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorEditScreen(
    viewModel: MonitorEditViewModel,
    onBack: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(saved) { if (saved) onBack() }

    if (error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            },
            title = { Text("Attenzione") },
            text = { Text(error.orEmpty()) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.id == 0L) "Nuovo monitor" else "Modifica monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Nome descrittivo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.url,
                onValueChange = { v -> viewModel.update { it.copy(url = v) } },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodDropdown(
                    selected = draft.method,
                    onSelected = { m -> viewModel.update { it.copy(method = m) } },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = draft.intervalSeconds.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { n -> viewModel.update { it.copy(intervalSeconds = n) } }
                    },
                    label = { Text("Intervallo (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.timeoutSeconds.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { n -> viewModel.update { it.copy(timeoutSeconds = n) } }
                    },
                    label = { Text("Timeout (s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = draft.maxRetries.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { n -> viewModel.update { it.copy(maxRetries = n) } }
                    },
                    label = { Text("Retry max") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            // Body opzionale: utile per POST/PUT/PATCH.
            OutlinedTextField(
                value = draft.body.orEmpty(),
                onValueChange = { v ->
                    viewModel.update { it.copy(body = v.ifBlank { null }) }
                },
                label = { Text("Body (opzionale)") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            OutlinedTextField(
                value = draft.bodyContentType.orEmpty(),
                onValueChange = { v ->
                    viewModel.update { it.copy(bodyContentType = v.ifBlank { null }) }
                },
                label = { Text("Content-Type body (opzionale)") },
                placeholder = { Text("application/json") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // --- Header personalizzati (anche per l'autenticazione) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Header personalizzati",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::addHeader) {
                            Icon(Icons.Default.Add, contentDescription = "Aggiungi header")
                        }
                    }
                    draft.headers.forEach { header ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = header.name,
                                onValueChange = { v ->
                                    viewModel.updateHeader(header.id) { it.copy(name = v) }
                                },
                                label = { Text("Nome") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = header.value,
                                onValueChange = { v ->
                                    viewModel.updateHeader(header.id) { it.copy(value = v) }
                                },
                                label = {
                                    // Se segreto e gia' salvato, il valore e' vuoto:
                                    // inserirne uno nuovo lo sovrascrive nello storage cifrato.
                                    Text(if (header.secret) "Valore (cifrato)" else "Valore")
                                },
                                modifier = Modifier.weight(1.2f),
                                singleLine = true,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Checkbox(
                                    checked = header.secret,
                                    onCheckedChange = { v ->
                                        viewModel.updateHeader(header.id) { it.copy(secret = v) }
                                    },
                                )
                                Text("Segreto", style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = { viewModel.removeHeader(header.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Rimuovi header")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salva monitor")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodDropdown(
    selected: HttpMethod,
    onSelected: (HttpMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Metodo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HttpMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = { Text(method.name) },
                    onClick = {
                        onSelected(method)
                        expanded = false
                    },
                )
            }
        }
    }
}
