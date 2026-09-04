package it.mavida.dashboardalert.trigger

import it.mavida.dashboardalert.data.repository.LogRepository
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.trigger.Trigger
import it.mavida.dashboardalert.domain.trigger.TriggerContext

/**
 * Smista l'esecuzione di un trigger all'implementazione giusta.
 *
 * Ogni esecuzione e' isolata: un trigger che fallisce viene loggato nello
 * storico e non blocca gli altri trigger della stessa regola.
 */
class TriggerDispatcher(
    private val httpCallTrigger: HttpCallTrigger,
    private val soundAlertManager: SoundAlertManager,
    private val notificationTrigger: NotificationTrigger,
    private val vibrationTrigger: VibrationTrigger,
    private val fullScreenAlertTrigger: FullScreenAlertTrigger,
    private val logRepository: LogRepository,
) {

    private fun implFor(config: TriggerConfig): Trigger = when (config) {
        is TriggerConfig.HttpCall -> httpCallTrigger
        is TriggerConfig.PlaySound -> soundAlertManager
        is TriggerConfig.Notify -> notificationTrigger
        is TriggerConfig.Vibrate -> vibrationTrigger
        is TriggerConfig.FullScreenAlert -> fullScreenAlertTrigger
    }

    /** Esegue un trigger e registra l'esito nello storico (spec §3.6). */
    suspend fun dispatch(config: TriggerConfig, context: TriggerContext) {
        val trigger = implFor(config)
        val result = runCatching { trigger.execute(config, context) }
            .getOrElse { Result.failure(it) }

        result.fold(
            onSuccess = {
                logRepository.log(
                    level = LogRepository.LEVEL_ALERT,
                    message = "Trigger eseguito: ${trigger.describe(config)} (regola '${context.ruleName}')",
                    monitorId = context.monitor.id,
                    monitorName = context.monitor.name,
                )
            },
            onFailure = { error ->
                // Mai loggare dettagli che potrebbero contenere segreti:
                // solo tipo di trigger e messaggio dell'errore.
                logRepository.log(
                    level = LogRepository.LEVEL_ERROR,
                    message = "Trigger fallito: ${trigger.describe(config)} — ${error.message}",
                    monitorId = context.monitor.id,
                    monitorName = context.monitor.name,
                )
            },
        )
    }
}
