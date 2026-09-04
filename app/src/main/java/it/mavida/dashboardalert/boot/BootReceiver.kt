package it.mavida.dashboardalert.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.mavida.dashboardalert.polling.PollingService

/**
 * Riavvia il servizio di polling dopo il riavvio del dispositivo (spec §3.5).
 *
 * BOOT_COMPLETED e' una delle eccezioni che permettono di avviare un
 * foreground service da background anche su Android 12+.
 *
 * Limite noto: portare in primo piano l'Activity dal boot NON e' affidabile
 * sulle versioni recenti di Android (restrizioni sullo start di Activity da
 * background). Il best effort per l'UI (full-screen intent) e la relativa
 * documentazione arrivano nella fase dedicata al boot/autostart.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PollingService.start(context)
        }
    }
}
