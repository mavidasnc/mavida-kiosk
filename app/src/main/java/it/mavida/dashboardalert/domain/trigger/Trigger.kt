package it.mavida.dashboardalert.domain.trigger

import it.mavida.dashboardalert.domain.model.Monitor
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.rule.PollOutcome

/**
 * Contesto passato a ogni trigger: chi ha generato l'evento e con quale esito.
 */
data class TriggerContext(
    val monitor: Monitor,
    val ruleName: String,
    val outcome: PollOutcome,
)

/**
 * Interfaccia comune dei trigger (spec §3.3): per aggiungere un nuovo tipo di
 * azione basta una nuova implementazione registrata nel TriggerDispatcher.
 *
 * Il risultato e' un Result: un trigger che fallisce (es. rete giu' per una
 * chiamata HTTP in uscita) viene loggato ma non interrompe gli altri trigger
 * della stessa regola.
 */
interface Trigger {

    /** Esegue l'azione. `config` e' gia' del sottotipo corretto. */
    suspend fun execute(config: TriggerConfig, context: TriggerContext): Result<Unit>

    /** Descrizione breve per i log dello storico. */
    fun describe(config: TriggerConfig): String
}
