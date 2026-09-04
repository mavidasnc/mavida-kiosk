package it.mavida.dashboardalert.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import it.mavida.dashboardalert.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

/**
 * Modalita' browser kiosk (spec §3.4): WebView a schermo intero per un
 * cruscotto HTML online, con immersive mode e reload automatico opzionale.
 *
 * Note di compatibilita': la versione di Android System WebView varia molto
 * tra dispositivi (spec §3.4). Abilitiamo solo API web mature e stabili;
 * la gestione errori mostra un overlay con retry invece di una pagina bianca.
 */

class BrowserViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.Settings())

    fun saveUrl(url: String) {
        // La rotazione multi-URL arriva nella fase dedicata; qui un solo URL.
        settingsRepository.setBrowserUrls(if (url.isBlank()) emptyList() else listOf(url.trim()))
    }

    fun setReloadSeconds(seconds: Int) = settingsRepository.setBrowserReloadSeconds(seconds)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val url = settings.browserUrls.firstOrNull().orEmpty()

    // Immersive mode solo in questa schermata: uscendo si ripristina la UI normale.
    ImmersiveEffect()

    if (url.isBlank()) {
        BrowserSetupScreen(
            initialReload = settings.browserReloadSeconds,
            onSave = { newUrl, reload ->
                viewModel.saveUrl(newUrl)
                viewModel.setReloadSeconds(reload)
            },
            onBack = onBack,
        )
    } else {
        KioskWebView(
            url = url,
            reloadSeconds = settings.browserReloadSeconds,
            onBack = onBack,
        )
    }
}

/** Attiva/disattiva l'immersive mode sulla finestra dell'Activity ospite. */
@Composable
private fun ImmersiveEffect() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Le barre riappaiono temporaneamente con uno swipe dai bordi:
        // serve per poter uscire dal kiosk senza hardware buttons.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Prima configurazione dell'URL del cruscotto. */
@Composable
private fun BrowserSetupScreen(
    initialReload: Int,
    onSave: (String, Int) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf("https://") }
    var reload by remember { mutableStateOf(initialReload.toString()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Configura il cruscotto", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL della dashboard") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = reload,
            onValueChange = { reload = it.filter(Char::isDigit) },
            label = { Text("Ricarica automatica (secondi, 0 = mai)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Annulla") }
            Button(
                onClick = { onSave(url, reload.toIntOrNull() ?: 0) },
                enabled = url.startsWith("http"),
            ) { Text("Avvia browser") }
        }
    }
}

/**
 * WebView kiosk a schermo intero.
 *
 * Passaggio rapido browser <-> monitor (spec §3.4): due piccoli FAB
 * semitrasparenti in sovrapposizione; gli avvisi full-screen dei trigger
 * appaiono comunque sopra perche' sono un'Activity separata.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KioskWebView(
    url: String,
    reloadSeconds: Int,
    onBack: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    // Chiave per forzare il reload manuale ricreando la loadUrl.
    var reloadTick by remember { mutableIntStateOf(0) }

    // Ricarica automatica opzionale a intervalli (spec §3.4).
    LaunchedEffect(reloadSeconds) {
        if (reloadSeconds > 0) {
            while (true) {
                delay(reloadSeconds * 1000L)
                webView?.reload()
            }
        }
    }

    // Carica (o ricarica) quando cambia URL o si preme il pulsante reload.
    LaunchedEffect(url, reloadTick) {
        webView?.loadUrl(url)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Adatta il contenuto allo schermo (dashboard responsive).
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    // Riproduzione media senza gesto utente (dashboard con audio/video).
                    settings.mediaPlaybackRequiresUserGesture = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            loadError = null
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            // Segnaliamo solo gli errori della pagina principale,
                            // non quelli di risorse secondarie (immagini, ecc.).
                            if (request?.isForMainFrame == true) {
                                loadError = error?.description?.toString()
                                    ?: "Errore di caricamento"
                            }
                        }
                    }
                    webView = this
                }
            },
        )

        if (loadError != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Impossibile caricare la dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(loadError.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = { reloadTick++ },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Riprova") }
            }
        }

        // Controlli rapidi: torna ai monitor / ricarica.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallFloatingActionButton(onClick = { reloadTick++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Ricarica")
            }
            SmallFloatingActionButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna ai monitor")
            }
        }
    }
}
