package it.mavida.dashboardalert.polling

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import it.mavida.dashboardalert.DashboardAlertApp
import it.mavida.dashboardalert.MainActivity
import it.mavida.dashboardalert.data.repository.LogRepository
import it.mavida.dashboardalert.domain.model.Monitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service che esegue il polling di tutti i monitor abilitati.
 *
 * Perche' un foreground service e non WorkManager (spec §2): WorkManager ha
 * un intervallo minimo di 15 minuti, inutilizzabile per polling a secondi/minuti.
 *
 * Struttura:
 * - una coroutine figlia per ogni monitor abilitato, indipendente dalle altre:
 *   un monitor lento o in errore non blocca gli altri;
 * - un osservatore sul Flow dei monitor: quando la configurazione cambia
 *   (CRUD dalla UI), i loop vengono riavviati solo per i monitor modificati;
 * - START_STICKY: se il sistema uccide il service, viene riavviato appena
 *   possibile (anti-standby, spec §3.5);
 * - un partial WakeLock tiene la CPU attiva durante il polling anche a
 *   schermo spento (lo schermo resta comunque acceso in modalita' kiosk).
 */
class PollingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Job del loop di polling, indicizzati per id monitor. */
    private val loopJobs = mutableMapOf<Long, Job>()

    /** Ultima configurazione vista per monitor: serve a riavviare solo i loop cambiati. */
    private val lastConfig = mutableMapOf<Long, Monitor>()

    private var configObserver: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var poller: HttpPoller
    private lateinit var responseHandler: ResponseHandler
    private lateinit var logRepository: LogRepository

    override fun onCreate() {
        super.onCreate()
        val app = application as DashboardAlertApp
        poller = app.container.httpPoller
        responseHandler = app.container.responseHandler
        logRepository = app.container.logRepository

        startForegroundWithNotification(activeMonitors = 0)
        acquireWakeLock()
        observeMonitorChanges()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true) {
            bringAppToFrontBestEffort()
        }
        // START_STICKY: riavvio automatico se il sistema termina il processo.
        return START_STICKY
    }

    /**
     * Best effort per riportare l'UI in primo piano dopo il boot (spec §3.5).
     *
     * Limite documentato: da Android 10+ un'app non puo' avviare un'Activity
     * da background in modo affidabile. L'unico meccanismo concesso e' la
     * notifica con full-screen intent, che il sistema mostra a schermo acceso.
     * Su Android 14+ il permesso "notifiche a schermo intero" puo' richiedere
     * l'abilitazione manuale (Impostazioni > App > Accesso speciale).
     * Per un kiosk vero e proprio la via robusta resta impostare l'app come
     * launcher o usare una MDM/kiosk mode.
     */
    private fun bringAppToFrontBestEffort() {
        val launchIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, DashboardAlertApp.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Dashboard & Alert avviato")
            .setContentText("Monitoraggio riavviato dopo il boot")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()
        runCatching {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(BOOT_NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        configObserver?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------

    /** Osserva il DB e allinea i loop di polling alla configurazione corrente. */
    private fun observeMonitorChanges() {
        configObserver = serviceScope.launch {
            (application as DashboardAlertApp).container.monitorRepository
                .observeMonitors()
                .collect { monitors -> reconcileLoops(monitors) }
        }
    }

    @Synchronized
    private fun reconcileLoops(monitors: List<Monitor>) {
        val enabled = monitors.filter { it.enabled }
        val enabledIds = enabled.map { it.id }.toSet()

        // Ferma i loop dei monitor rimossi o disabilitati.
        val toStop = loopJobs.keys - enabledIds
        toStop.forEach { id ->
            loopJobs.remove(id)?.cancel()
            lastConfig.remove(id)
        }

        // Avvia (o riavvia, se la configurazione e' cambiata) i loop attivi.
        enabled.forEach { monitor ->
            val previous = lastConfig[monitor.id]
            if (previous != monitor) {
                loopJobs[monitor.id]?.cancel()
                loopJobs[monitor.id] = serviceScope.launch { pollLoop(monitor) }
                lastConfig[monitor.id] = monitor
            }
        }

        updateNotification(enabled.size)
    }

    /**
     * Loop di polling di un singolo monitor: gira finche' la coroutine resta
     * attiva. Ogni iterazione e' protetta da try/catch: un errore imprevisto
     * non deve mai uccidere il loop (altrimenti il monitor "muore" in silenzio).
     */
    private suspend fun pollLoop(monitor: Monitor) {
        while (currentCoroutineContextIsActive()) {
            try {
                val outcome = poller.poll(monitor)
                responseHandler.onOutcome(monitor, outcome)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // la cancellazione va sempre propagata
            } catch (e: Exception) {
                logRepository.log(
                    level = LogRepository.LEVEL_ERROR,
                    message = "Eccezione nel loop di polling: ${e.message}",
                    monitorId = monitor.id,
                    monitorName = monitor.name,
                )
            }
            delay(monitor.intervalSeconds * 1000L)
        }
    }

    private suspend fun currentCoroutineContextIsActive(): Boolean =
        kotlinx.coroutines.currentCoroutineContext().isActive

    // --- Notifica persistente (obbligatoria per il foreground service) ---

    private fun buildNotification(activeMonitors: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (activeMonitors == 0) {
            "Nessun monitor attivo"
        } else {
            "Monitoraggio attivo: $activeMonitors monitor"
        }
        return NotificationCompat.Builder(this, DashboardAlertApp.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Dashboard & Alert")
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundWithNotification(activeMonitors: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Da Android 14 il tipo va passato anche a startForeground.
            startForeground(
                NOTIFICATION_ID,
                buildNotification(activeMonitors),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(activeMonitors))
        }
    }

    private fun updateNotification(activeMonitors: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(activeMonitors))
    }

    // --- WakeLock: la CPU deve restare attiva tra un poll e l'altro ---

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            // Timeout di sicurezza: anche in caso di bug il lock non resta per sempre.
            acquire(12 * 60 * 60 * 1000L) // 12 ore
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val BOOT_NOTIFICATION_ID = 2
        private const val WAKELOCK_TAG = "dashboardalert:polling"
        private const val EXTRA_FROM_BOOT = "it.mavida.dashboardalert.extra.FROM_BOOT"

        /** Avvia il service (da UI in foreground o dal BootReceiver). */
        fun start(context: Context, fromBoot: Boolean = false) {
            val intent = Intent(context, PollingService::class.java)
                .putExtra(EXTRA_FROM_BOOT, fromBoot)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PollingService::class.java))
        }
    }
}
