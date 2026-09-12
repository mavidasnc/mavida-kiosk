package it.mavida.dashboardalert.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.mavida.dashboardalert.BuildConfig
import it.mavida.dashboardalert.updater.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sezione "Aggiornamenti" delle impostazioni: mostra la versione installata,
 * verifica la presenza di una release piu' recente su GitHub e guida
 * l'installazione dell'APK (vedi [AppUpdater] per i dettagli).
 */

class UpdateViewModel(
    private val appContext: Context,
    private val updater: AppUpdater,
) : ViewModel() {

    val currentVersion: String = BuildConfig.VERSION_NAME

    sealed interface State {
        data object Idle : State
        data object Checking : State
        /** Nessuna release piu' recente su GitHub. */
        data object UpToDate : State
        /** Trovata una release piu' recente: pronta da scaricare/installare. */
        data class Available(val info: AppUpdater.UpdateInfo) : State
        data class Downloading(val version: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun check() {
        if (_state.value is State.Checking) return
        _state.value = State.Checking
        viewModelScope.launch {
            runCatching { updater.checkForUpdate(currentVersion) }
                .onSuccess { info ->
                    _state.value = if (info != null) State.Available(info) else State.UpToDate
                }
                .onFailure {
                    _state.value = State.Error("Verifica fallita: ${it.message}")
                }
        }
    }

    /**
     * Scarica l'aggiornamento trovato e lancia l'installazione.
     * Se manca il permesso "fonti sconosciute", apre la pagina di sistema:
     * l'utente concede il permesso e ripreme il pulsante.
     */
    fun downloadAndInstall() {
        val info = (_state.value as? State.Available)?.info ?: return
        if (!updater.canInstallPackages(appContext)) {
            appContext.startActivity(updater.unknownSourcesIntent(appContext))
            _state.value = State.Error(
                "Concedi il permesso di installare app da questa fonte, poi riprova.",
            )
            return
        }
        _state.value = State.Downloading(info.version)
        viewModelScope.launch {
            runCatching {
                val apk = updater.downloadApk(appContext, info.apkUrl)
                updater.installApk(appContext, apk)
            }
                .onSuccess {
                    // L'installer di sistema prende il sopravvento: se l'utente
                    // annulla, lo stato torna "disponibile" per riprovare.
                    _state.value = State.Available(info)
                }
                .onFailure {
                    _state.value = State.Error("Aggiornamento fallito: ${it.message}")
                }
        }
    }
}

@Composable
fun UpdateCard(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aggiornamenti", style = MaterialTheme.typography.titleMedium)
            Text(
                "Versione installata: ${viewModel.currentVersion}",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (val s = state) {
                is UpdateViewModel.State.UpToDate -> Text(
                    "L'app e' aggiornata all'ultima versione.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is UpdateViewModel.State.Available -> Text(
                    "Nuova versione disponibile: ${s.info.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                is UpdateViewModel.State.Downloading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Scaricamento della versione ${s.version}...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is UpdateViewModel.State.Error -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::check,
                    enabled = state !is UpdateViewModel.State.Checking &&
                        state !is UpdateViewModel.State.Downloading,
                ) {
                    Text(if (state is UpdateViewModel.State.Checking) "Verifica..." else "Verifica aggiornamenti")
                }
                if (state is UpdateViewModel.State.Available) {
                    Button(onClick = viewModel::downloadAndInstall) {
                        val version = (state as UpdateViewModel.State.Available).info.version
                        Text("Aggiorna a $version")
                    }
                }
            }
        }
    }
}
