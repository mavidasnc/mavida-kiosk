package it.mavida.dashboardalert.polling

import it.mavida.dashboardalert.data.repository.LogRepository
import it.mavida.dashboardalert.domain.model.Monitor
import it.mavida.dashboardalert.domain.rule.PollOutcome

/**
 * Punto di innesto della pipeline dopo ogni polling.
 *
 * Il PollingService si occupa solo di schedulare ed eseguire le richieste;
 * cosa fare della risposta (valutazione regole, trigger, logging) e' delegato
 * a questa interfaccia, cosi' il service non cambia quando aggiungiamo
 * rule engine e trigger (fasi 3-5).
 */
interface ResponseHandler {
    suspend fun onOutcome(monitor: Monitor, outcome: PollOutcome)
}

/**
 * Implementazione base: registra l'esito nello storico.
 * Verra' estesa con la valutazione delle regole e l'esecuzione dei trigger.
 */
class LoggingResponseHandler(
    private val logRepository: LogRepository,
) : ResponseHandler {

    override suspend fun onOutcome(monitor: Monitor, outcome: PollOutcome) {
        when (outcome) {
            is PollOutcome.Success -> logRepository.log(
                level = LogRepository.LEVEL_INFO,
                message = "Risposta ${outcome.statusCode} (${outcome.body.length} byte)",
                monitorId = monitor.id,
                monitorName = monitor.name,
                detail = outcome.body.take(500),
            )
            is PollOutcome.Failure -> logRepository.log(
                level = LogRepository.LEVEL_ERROR,
                message = "Errore polling: ${outcome.error}",
                monitorId = monitor.id,
                monitorName = monitor.name,
            )
        }
    }
}
