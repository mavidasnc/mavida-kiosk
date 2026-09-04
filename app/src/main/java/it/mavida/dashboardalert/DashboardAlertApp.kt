package it.mavida.dashboardalert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * Entry point dell'applicazione.
 *
 * Qui inizializziamo cio' che deve esistere prima di qualsiasi Activity/Service:
 * i notification channel (obbligatori da Android 8 per mostrare notifiche,
 * inclusa quella persistente del foreground service).
 */
class DashboardAlertApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Channel del foreground service: importanza bassa, niente suono,
        // perche' e' una notifica "di servizio" sempre visibile.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Servizio di polling",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Notifica persistente del servizio di monitoraggio" },
        )

        // Channel degli alert dei monitor: importanza alta perche' deve
        // attirare l'attenzione anche da lontano.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Avvisi monitor",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Notifiche generate dai trigger dei monitor" },
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "polling_service"
        const val CHANNEL_ALERTS = "monitor_alerts"
    }
}
