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
        // START_STICKY: riavvio automatico se il sistema termina il processo.
        return START_STICKY
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
        private const val WAKELOCK_TAG = "dashboardalert:polling"

        /** Avvia il service (da UI in foreground o dal BootReceiver). */
        fun start(context: Context) {
            val intent = Intent(context, PollingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PollingService::class.java))
        }
    }
}
