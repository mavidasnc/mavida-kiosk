package it.mavida.dashboardalert.domain.trigger

/**
 * Macchina a stati per la politica di scatto dei trigger (spec §3.3):
 *
 * - **Edge-trigger**: l'azione scatta solo sulla transizione falso -> vero
 *   della condizione, non ad ogni ciclo in cui resta vera (altrimenti un MP3
 *   partirebbe a ogni polling);
 * - **Cooldown**: dopo uno scatto, nuove transizioni entro `cooldownMs`
 *   vengono ignorate (anti-rimbalzo);
 * - **Ripeti finche' vera**: modalita' alternativa — mentre la condizione
 *   resta vera, il trigger riesegue ogni `repeatIntervalMs`.
 *
 * Kotlin puro, niente Android: la logica temporale e' interamente testabile
 * passando `nowMs` come parametro (vedi TriggerStateMachineTest).
 *
 * Una istanza per trigger: tiene lo stato (ultima valutazione e ultimo scatto)
 * solo in memoria. Dopo un riavvio del service il primo "vero" scatta di
 * nuovo: scelta deliberata e documentata — meglio un alert in piu' che uno
 * in meno su un dispositivo kiosk.
 */
class TriggerStateMachine(
    private val cooldownMs: Long,
    private val repeatWhileTrue: Boolean,
    private val repeatIntervalMs: Long,
) {

    private var conditionWasTrue = false
    private var lastFiredAtMs: Long? = null

    /**
     * Va chiamata ad OGNI valutazione (vera o falsa che sia), perche' il
     * rilevamento della transizione dipende dalla storia completa.
     *
     * @return true se in questo ciclo il trigger deve essere eseguito.
     */
    fun shouldFire(conditionTrue: Boolean, nowMs: Long): Boolean {
        val isEdge = conditionTrue && !conditionWasTrue
        conditionWasTrue = conditionTrue

        if (!conditionTrue) return false

        val lastFired = lastFiredAtMs
        val fire = when {
            // Primo "vero" in assoluto: scatta sempre.
            lastFired == null -> true
            // Nuovo edge: solo se e' passato il cooldown dall'ultimo scatto.
            isEdge -> nowMs - lastFired >= cooldownMs
            // Condizione persistente: solo in modalita' "ripeti finche' vera".
            repeatWhileTrue -> nowMs - lastFired >= repeatIntervalMs
            else -> false
        }

        if (fire) lastFiredAtMs = nowMs
        return fire
    }

    /** Azzera lo stato (es. quando la configurazione del trigger cambia). */
    fun reset() {
        conditionWasTrue = false
        lastFiredAtMs = null
    }
}
