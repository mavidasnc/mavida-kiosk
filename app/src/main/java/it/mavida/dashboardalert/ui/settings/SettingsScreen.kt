package it.mavida.dashboardalert.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.mavida.dashboardalert.data.ConfigTransfer
import it.mavida.dashboardalert.data.SettingsRepository
import it.mavida.dashboardalert.system.PinLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Schermata impostazioni. Per ora: backup/ripristino JSON e anti-standby.
 * Le sezioni batteria/OEM, PIN e tema notturno arrivano nella fase dedicata.
 */

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val configTransfer: ConfigTransfer,
) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.Settings())

    private val _exportedJson = MutableStateFlow<String?>(null)
    val exportedJson: StateFlow<String?> = _exportedJson.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setKeepScreenOn(enabled: Boolean) = settingsRepository.setKeepScreenOn(enabled)

    fun setPin(pin: String) {
        settingsRepository.setPinHash(PinLock.hashFor(pin))
        _message.value = "PIN impostato. Alla prossima apertura l'app sara' bloccata."
    }

    fun clearPin(currentPin: String) {
        if (PinLock.verify(currentPin)) {
            settingsRepository.setPinHash(null)
            _message.value = "PIN rimosso."
        } else {
            _message.value = "PIN errato: non rimosso."
        }
    }

    fun setNightMode(enabled: Boolean, start: Int, end: Int, brightness: Int) =
        settingsRepository.setNightMode(enabled, start, end, brightness)

    /** Prepara il JSON di export: la UI lo scrive poi sul file scelto via SAF. */
    fun prepareExport() {
        viewModelScope.launch {
            runCatching { configTransfer.exportJson() }
                .onSuccess { _exportedJson.value = it }
                .onFailure { _message.value = "Export fallito: ${it.message}" }
        }
    }

    fun import(json: String) {
        viewModelScope.launch {
            configTransfer.importJson(json)
                .onSuccess { _message.value = "Importati $it monitor (i monitor precedenti sono stati sostituiti)" }
                .onFailure { _message.value = "Import fallito: ${it.message}" }
        }
    }

    fun consumeExportedJson() {
        _exportedJson.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, updateViewModel: UpdateViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val exportedJson by viewModel.exportedJson.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }

    // Export: il JSON viene scritto sul documento scelto dall'utente (SAF).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExport
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray())
                }
            }
        }
        pendingExport = null
    }

    // Import: lettura del file JSON scelto (SAF, nessun permesso storage).
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }.onSuccess { it?.let(viewModel::import) }
        }
    }

    // Quando il ViewModel ha preparato il JSON, apri il selettore file.
    androidx.compose.runtime.LaunchedEffect(exportedJson) {
        exportedJson?.let {
            pendingExport = it
            viewModel.consumeExportedJson()
            exportLauncher.launch("dashboard-alert-backup.json")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (message != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        message.orEmpty(),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // --- Anti-standby ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mantieni schermo acceso", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Consigliato per l'uso kiosk (spec anti-standby)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = settings.keepScreenOn, onCheckedChange = viewModel::setKeepScreenOn)
                }
            }

            // --- Esenzione ottimizzazione batteria (spec §3.5) ---
            BatteryCard()

            // --- Istruzioni specifiche per il produttore (OEM) ---
            OemCard()

            // --- PIN anti-modifiche accidentali ---
            PinCard(viewModel, pinSet = settings.pinHash != null)

            // --- Tema notturno / dimming a fasce orarie ---
            NightModeCard(viewModel, settings)

            // --- Aggiornamenti dell'app via GitHub Releases ---
            UpdateCard(updateViewModel)

            // --- Backup / ripristino ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup e ripristino", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Esporta la configurazione dei monitor in JSON. " +
                            "Gli header segreti NON vengono esportati: dopo il ripristino vanno reinseriti.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = viewModel::prepareExport) { Text("Esporta JSON") }
                        Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                            Text("Importa JSON")
                        }
                    }
                }
            }
        }
    }
}
