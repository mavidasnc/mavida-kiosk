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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.lifecycle.viewModelScope
import it.mavida.dashboardalert.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Modalita' browser kiosk (spec §3.4): WebView a schermo intero per uno o
 * piu' cruscotti HTML online, con immersive mode, reload automatico opzionale
 * e rotazione tra piu' dashboard (spec §3.6).
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

    fun saveUrls(urls: List<String>) {
        settingsRepository.setBrowserUrls(urls.map { it.trim() }.filter { it.startsWith("http") })
    }

    fun setReloadSeconds(seconds: Int) = settingsRepository.setBrowserReloadSeconds(seconds)
    fun setRotationSeconds(seconds: Int) = settingsRepository.setBrowserRotationSeconds(seconds)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()

    // true quando l'utente chiede esplicitamente di riconfigurare gli URL
    // dal kiosk (altrimenti, una volta salvati, il setup non riapparirebbe piu').
    var showSetup by remember { mutableStateOf(false) }

    // Immersive mode solo in questa schermata: uscendo si ripristina la UI normale.
    ImmersiveEffect()

    if (settings.browserUrls.isEmpty() || showSetup) {
        BrowserSetupScreen(
            initialUrls = settings.browserUrls,
            initialReload = settings.browserReloadSeconds,
            initialRotation = settings.browserRotationSeconds,
            onSave = { urls, reload, rotation ->
                viewModel.saveUrls(urls)
                viewModel.setReloadSeconds(reload)
                viewModel.setRotationSeconds(rotation)
                showSetup = false
            },
            // Se esistono gia' URL, "Annulla" torna al kiosk, non alla home.
            onBack = if (settings.browserUrls.isEmpty()) onBack else ({ showSetup = false }),
        )
    } else {
        KioskWebView(
            urls = settings.browserUrls,
            reloadSeconds = settings.browserReloadSeconds,
            rotationSeconds = settings.browserRotationSeconds,
            onBack = onBack,
            onConfigure = { showSetup = true },
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

/** Configurazione delle dashboard: un URL per riga (rotazione, spec §3.6). */
@Composable
private fun BrowserSetupScreen(
    initialUrls: List<String>,
    initialReload: Int,
    initialRotation: Int,
    onSave: (List<String>, Int, Int) -> Unit,
    onBack: () -> Unit,
) {
    // Precarica gli URL gia' salvati (uno per riga), cosi' la riconfigurazione
    // parte dallo stato attuale invece che da un campo vuoto.
    var urlsText by remember {
        mutableStateOf(if (initialUrls.isEmpty()) "https://" else initialUrls.joinToString("\n"))
    }
    var reload by remember { mutableStateOf(initialReload.toString()) }
    var rotation by remember { mutableStateOf(initialRotation.toString()) }

    // Scroll verticale: senza, in orientamento orizzontale i campi in basso
    // (e i pulsanti) uscirebbero dallo schermo rendendoli irraggiungibili.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Configura le dashboard", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = urlsText,
            onValueChange = { urlsText = it },
            label = { Text("URL delle dashboard (uno per riga)") },
            // Altezza contenuta: il campo ha scroll interno se gli URL
            // superano le righe visibili (comodo soprattutto in landscape).
            modifier = Modifier.fillMaxWidth().height(96.dp),
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
        OutlinedTextField(
            value = rotation,
            onValueChange = { rotation = it.filter(Char::isDigit) },
            label = { Text("Rotazione tra dashboard (secondi)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Annulla") }
            Button(
                onClick = {
                    onSave(
                        urlsText.lines().filter { it.isNotBlank() },
                        reload.toIntOrNull() ?: 0,
                        rotation.toIntOrNull() ?: 30,
                    )
                },
                enabled = urlsText.lines().any { it.trim().startsWith("http") },
            ) { Text("Avvia browser") }
        }
    }
}

/**
 * WebView kiosk a schermo intero con rotazione tra piu' URL.
 *
 * Passaggio rapido browser <-> monitor (spec §3.4): piccoli FAB
 * semitrasparenti in sovrapposizione; gli avvisi full-screen dei trigger
 * appaiono comunque sopra perche' sono un'Activity separata.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KioskWebView(
    urls: List<String>,
    reloadSeconds: Int,
    rotationSeconds: Int,
    onBack: () -> Unit,
    onConfigure: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
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

    // Rotazione automatica tra le dashboard configurate (spec §3.6).
    LaunchedEffect(urls, rotationSeconds) {
        if (urls.size > 1) {
            while (true) {
                delay(rotationSeconds * 1000L)
                currentIndex = (currentIndex + 1) % urls.size
            }
        }
    }

    // Carica la dashboard corrente (cambio URL, rotazione o reload manuale).
    LaunchedEffect(currentIndex, reloadTick) {
        urls.getOrNull(currentIndex)?.let { webView?.loadUrl(it) }
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

        // Controlli rapidi: torna ai monitor / ricarica / indicatore rotazione.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (urls.size > 1) {
                Text(
                    "${currentIndex + 1}/${urls.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            SmallFloatingActionButton(onClick = { reloadTick++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Ricarica")
            }
            // Riconfigura gli URL/reload/rotazione senza uscire dal kiosk.
            SmallFloatingActionButton(onClick = onConfigure) {
                Icon(Icons.Default.Settings, contentDescription = "Configura dashboard")
            }
            SmallFloatingActionButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna ai monitor")
            }
        }
    }
}
