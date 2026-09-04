package it.mavida.dashboardalert.trigger

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.mavida.dashboardalert.DashboardAlertApp
import it.mavida.dashboardalert.MainActivity
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.trigger.TemplateEngine
import it.mavida.dashboardalert.domain.trigger.Trigger
import it.mavida.dashboardalert.domain.trigger.TriggerContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Trigger "notifica di sistema" (spec §3.3.3) sul channel ad alta importanza.
 *
 * Se il permesso POST_NOTIFICATIONS e' negato (Android 13+) la notifica non
 * appare: lo segnaliamo nel Result invece di lanciare un'eccezione.
 */
class NotificationTrigger(private val appContext: Context) : Trigger {

    override suspend fun execute(
        config: TriggerConfig,
        context: TriggerContext,
    ): Result<Unit> {
        val cfg = config as? TriggerConfig.Notify
            ?: return Result.failure(IllegalArgumentException("config non Notify"))

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(SecurityException("Permesso POST_NOTIFICATIONS negato"))
        }

        return runCatching {
            val now = System.currentTimeMillis()
            val title = TemplateEngine.render(cfg.title, context.monitor.name, context.outcome, now)
            val message = TemplateEngine.render(cfg.message, context.monitor.name, context.outcome, now)

            val openApp = PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(appContext, DashboardAlertApp.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title.ifBlank { "Avviso: ${context.monitor.name}" })
                .setContentText(message)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(appContext).notify(nextId(), notification)
        }
    }

    override fun describe(config: TriggerConfig): String = "Notifica di sistema"

    companion object {
        // Id progressivi per non sovrascrivere le notifiche precedenti.
        private val counter = AtomicInteger(1000)
        private fun nextId() = counter.incrementAndGet()
    }
}
