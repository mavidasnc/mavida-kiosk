package it.mavida.dashboardalert.domain.rule

/**
 * Esito di un singolo tentativo di polling (dopo eventuali retry).
 *
 * Tipo puro, senza dipendenze Android: il rule engine lo consuma direttamente
 * ed e' quindi testabile con unit test JVM.
 */
sealed interface PollOutcome {

    /** Risposta HTTP ricevuta (qualsiasi status code: la valutazione spetta alle regole). */
    data class Success(
        val statusCode: Int,
        val body: String,
        val responseHeaders: Map<String, String> = emptyMap(),
    ) : PollOutcome

    /**
     * Nessuna risposta: errore di rete, DNS, timeout, connessione assente.
     * E' il caso coperto dalla condizione speciale "errore/timeout/nessuna risposta"
     * (spec §3.2), usata per rilevare la perdita di connettivita'.
     */
    data class Failure(
        val error: String,
        val isTimeout: Boolean = false,
    ) : PollOutcome
}
