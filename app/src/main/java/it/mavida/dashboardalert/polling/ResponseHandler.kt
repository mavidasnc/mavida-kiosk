package it.mavida.dashboardalert.polling

import it.mavida.dashboardalert.data.repository.LogRepository
import it.mavida.dashboardalert.domain.model.Monitor
import it.mavida.dashboardalert.domain.model.TriggerDef
import it.mavida.dashboardalert.domain.rule.ConditionFactory
import it.mavida.dashboardalert.domain.rule.PollOutcome
import it.mavida.dashboardalert.domain.trigger.TriggerContext
import it.mavida.dashboardalert.domain.trigger.TriggerStateMachine
import it.mavida.dashboardalert.trigger.TriggerDispatcher

/**
 * Punto di innesto della pipeline dopo ogni polling.
 *
 * Il PollingService si occupa solo di schedulare ed eseguire le richieste;
 * cosa fare della risposta (valutazione regole, trigger, logging) e' delegato
 * a questa interfaccia, cosi' il service non cambia quando la pipeline evolve.
 */
interface ResponseHandler {
    suspend fun onOutcome(monitor: Monitor, outcome: PollOutcome)
}

/**
 * Pipeline completa: log esito -> valutazione regole -> trigger con
 * edge-trigger/cooldown (spec §3.2, §3.3, §3.6).
 *
 * Le macchine a stato dei trigger vivono qui (in memoria, una per trigger id)
 * e vengono ricreate se la politica di ripetizione del trigger cambia.
 */
class RuleEngineResponseHandler(
    private val logRepository: LogRepository,
    private val dispatcher: TriggerDispatcher,
) : ResponseHandler {

    private data class MachineEntry(
        val machine: TriggerStateMachine,
        val cooldownSeconds: Int,
        val repeatWhileTrue: Boolean,
        val repeatIntervalSeconds: Int,
    )

    // I loop dei monitor girano in concorrenza: mappe thread-safe.
    private val machines = java.util.concurrent.ConcurrentHashMap<Long, MachineEntry>()

    /** Ultimo esito per regola: per loggare "regola soddisfatta" solo sulla transizione. */
    private val ruleMatched = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    override suspend fun onOutcome(monitor: Monitor, outcome: PollOutcome) {
        logOutcome(monitor, outcome)

        val now = System.currentTimeMillis()
        for (rule in monitor.rules.filter { it.enabled }) {
            val condition = ConditionFactory.create(rule.condition)
            val matched = runCatching { condition.matches(outcome) }.getOrDefault(false)

            // Log dell'esito regola solo sulla transizione (niente spam nello storico).
            if (matched && ruleMatched[rule.id] != true) {
                logRepository.log(
                    level = LogRepository.LEVEL_ALERT,
                    message = "Regola soddisfatta: ${rule.name.ifBlank { condition.describe() }}",
                    monitorId = monitor.id,
                    monitorName = monitor.name,
                )
            }
            ruleMatched[rule.id] = matched

            for (trigger in rule.triggers.filter { it.enabled }) {
                val machine = machineFor(trigger)
                if (machine.shouldFire(matched, now)) {
                    dispatcher.dispatch(
                        trigger.config,
                        TriggerContext(
                            monitor = monitor,
                            ruleName = rule.name.ifBlank { condition.describe() },
                            outcome = outcome,
                        ),
                    )
                }
            }
        }
    }

    private fun machineFor(trigger: TriggerDef): TriggerStateMachine {
        val existing = machines[trigger.id]
        // Se la politica e' cambiata in editing, la macchina va ricreata:
        // altrimenti cooldown/ripetizione resterebbero quelli vecchi.
        if (existing != null &&
            existing.cooldownSeconds == trigger.cooldownSeconds &&
            existing.repeatWhileTrue == trigger.repeatWhileTrue &&
            existing.repeatIntervalSeconds == trigger.repeatIntervalSeconds
        ) {
            return existing.machine
        }
        val machine = TriggerStateMachine(
            cooldownMs = trigger.cooldownSeconds * 1000L,
            repeatWhileTrue = trigger.repeatWhileTrue,
            repeatIntervalMs = trigger.repeatIntervalSeconds * 1000L,
        )
        machines[trigger.id] = MachineEntry(
            machine,
            trigger.cooldownSeconds,
            trigger.repeatWhileTrue,
            trigger.repeatIntervalSeconds,
        )
        return machine
    }

    private suspend fun logOutcome(monitor: Monitor, outcome: PollOutcome) {
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
