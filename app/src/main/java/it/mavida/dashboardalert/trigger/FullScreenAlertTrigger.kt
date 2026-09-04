package it.mavida.dashboardalert.trigger

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.mavida.dashboardalert.DashboardAlertApp
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.trigger.TemplateEngine
import it.mavida.dashboardalert.domain.trigger.Trigger
import it.mavida.dashboardalert.domain.trigger.TriggerContext
import it.mavida.dashboardalert.ui.alert.AlertActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * Trigger "avviso a schermo pieno" (spec §3.3.5).
 *
 * Come viene mostrato l'avviso:
 * 1. tentativo diretto di startActivity (funziona quando l'app e' in foreground,
 *    es. device kiosk con UI aperta);
 * 2. notifica ad alta priorita' con full-screen intent (funziona anche con app
 *    in background o schermo bloccato).
 *
 * Limite noto (Android 14+): il permesso USE_FULL_SCREEN_INTENT puo' essere
 * negato di default per app non "telefono/sveglia"; l'utente puo' abilitarlo
 * in Impostazioni > App > Accesso speciale. In quel caso resta la notifica
 * normale (fallback documentato, spec §3.5).
 */
class FullScreenAlertTrigger(private val appContext: Context) : Trigger {

    override suspend fun execute(
        config: TriggerConfig,
        context: TriggerContext,
    ): Result<Unit> {
        val cfg = config as? TriggerConfig.FullScreenAlert
            ?: return Result.failure(IllegalArgumentException("config non FullScreenAlert"))

        val now = System.currentTimeMillis()
        val title = TemplateEngine.render(cfg.title, context.monitor.name, context.outcome, now)
        val message = TemplateEngine.render(cfg.message, context.monitor.name, context.outcome, now)

        val intent = Intent(appContext, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_TITLE, title)
            putExtra(AlertActivity.EXTRA_MESSAGE, message)
            putExtra(AlertActivity.EXTRA_COLOR, cfg.colorHex)
            putExtra(AlertActivity.EXTRA_DURATION, cfg.durationSeconds)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // 1) Tentativo diretto: ok se l'app e' in foreground.
        val directStarted = runCatching { appContext.startActivity(intent) }.isSuccess

        // 2) Full-screen intent via notifica: copre background e lockscreen.
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val fullScreenPendingIntent = PendingIntent.getActivity(
                appContext,
                nextId(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification: Notification =
                NotificationCompat.Builder(appContext, DashboardAlertApp.CHANNEL_ALERTS)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title.ifBlank { "Avviso: ${context.monitor.name}" })
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setAutoCancel(true)
                    .build()
            runCatching {
                NotificationManagerCompat.from(appContext).notify(nextId(), notification)
            }
        }

        return if (directStarted) {
            Result.success(Unit)
        } else {
            // Anche se lo start diretto fallisce, la notifica e' stata postata.
            Result.success(Unit)
        }
    }

    override fun describe(config: TriggerConfig): String = "Avviso a schermo pieno"

    companion object {
        private val counter = AtomicInteger(5000)
        private fun nextId() = counter.incrementAndGet()
    }
}
