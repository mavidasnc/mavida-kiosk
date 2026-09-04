package it.mavida.dashboardalert

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import it.mavida.dashboardalert.polling.PollingService
import it.mavida.dashboardalert.ui.AppNavHost
import it.mavida.dashboardalert.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Activity principale (singleTask: una sola istanza, coerente con l'uso kiosk).
 *
 * All'apertura dell'app (foreground) avvia il servizio di polling: da Android 12
 * un foreground service puo' essere avviato solo con l'app in foreground o in
 * casi eccezionali (es. boot), quindi questo e' il punto giusto.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DashboardAlertApp).container

        PollingService.start(this)

        // Anti-standby (spec §3.5): FLAG_KEEP_SCREEN_ON finche' l'Activity e'
        // visibile. Guidato da un'impostazione (disattivabile per risparmiare
        // lo schermo quando il kiosk non serve).
        lifecycleScope.launch {
            container.settingsRepository.settings.collect { settings ->
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            AppTheme {
                // Da Android 13 la notifica persistente del service richiede
                // il permesso POST_NOTIFICATIONS a runtime.
                RequestNotificationPermission()
                AppNavHost(container)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* esito ignorato: il service funziona comunque, solo la notifica non si vede */ }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
