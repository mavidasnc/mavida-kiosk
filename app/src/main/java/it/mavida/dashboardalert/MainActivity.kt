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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import it.mavida.dashboardalert.data.SettingsRepository
import it.mavida.dashboardalert.polling.PollingService
import it.mavida.dashboardalert.system.PinLock
import it.mavida.dashboardalert.ui.AppNavHost
import it.mavida.dashboardalert.ui.pin.PinLockScreen
import it.mavida.dashboardalert.ui.theme.AppTheme
import java.util.Calendar
import kotlinx.coroutines.delay
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

        lifecycleScope.launch {
            // Anti-standby (spec §3.5): FLAG_KEEP_SCREEN_ON finche' l'Activity e'
            // visibile. Guidato da un'impostazione (disattivabile per risparmiare
            // lo schermo quando il kiosk non serve).
            launch {
                container.settingsRepository.settings.collect { settings ->
                    PinLock.onSettingsChanged(settings)
                    if (settings.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
            // Tema notturno/dimming a fasce orarie (spec §3.5): ogni minuto
            // controlla la fascia e regola la luminosita' della finestra.
            // Non servono permessi: agisce solo sulla finestra di questa app.
            launch {
                while (true) {
                    applyNightBrightness(container.settingsRepository.settings.value)
                    delay(60_000)
                }
            }
        }

        PinLock.init(container.settingsRepository.settings.value)

        setContent {
            AppTheme {
                // Da Android 13 la notifica persistente del service richiede
                // il permesso POST_NOTIFICATIONS a runtime.
                RequestNotificationPermission()

                // PIN opzionale (spec §3.6): se bloccato, solo il tastierino.
                val locked by PinLock.locked.collectAsState()
                if (locked) {
                    PinLockScreen()
                } else {
                    AppNavHost(container)
                }
            }
        }
    }

    /** Se siamo nella fascia notturna, abbassa la luminosita'; altrimenti ripristina. */
    private fun applyNightBrightness(settings: SettingsRepository.Settings) {
        val attrs = window.attributes
        if (settings.nightModeEnabled && isNightHour(settings.nightStartHour, settings.nightEndHour)) {
            attrs.screenBrightness = settings.nightBrightnessPercent / 100f
        } else {
            // Valore di default: segue la luminosita' di sistema.
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attrs
    }

    companion object {
        /** Fascia oraria che puo' anche attraversare la mezzanotte (es. 22 -> 7). */
        fun isNightHour(startHour: Int, endHour: Int, hour: Int = Calendar.getInstance()
            .get(Calendar.HOUR_OF_DAY)): Boolean =
            if (startHour <= endHour) {
                hour in startHour until endHour
            } else {
                hour >= startHour || hour < endHour
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
