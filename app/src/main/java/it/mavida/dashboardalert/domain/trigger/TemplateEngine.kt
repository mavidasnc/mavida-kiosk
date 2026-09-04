package it.mavida.dashboardalert.domain.trigger

import com.jayway.jsonpath.JsonPath
import it.mavida.dashboardalert.domain.rule.PollOutcome

/**
 * Motore di templating per i trigger (spec §3.3.1): permette di inserire
 * valori della risposta del monitor dentro URL, body e testi dei trigger.
 *
 * Placeholder supportati (con o senza spazi: `{{status}}` o `{{ status }}`):
 * - `{{monitor}}`   nome del monitor
 * - `{{status}}`    status code HTTP (vuoto se la richiesta e' fallita)
 * - `{{error}}`     messaggio di errore (vuoto se la richiesta e' riuscita)
 * - `{{timestamp}}` epoch millis del momento del rendering
 * - `{{body}}`      body completo della risposta
 * - `{{json:$.path}}` valore estratto via JSONPath (es. `{{json:$.temperature}}`)
 *
 * Puro e testabile: `nowMillis` e' un parametro, non una lettura di orologio.
 * Placeholder sconosciuti o non risolvibili diventano stringa vuota:
 * un template errato non deve mai bloccare l'esecuzione di un trigger.
 */
object TemplateEngine {

    private val PLACEHOLDER = Regex("\\{\\{\\s*([a-zA-Z]+)(?::([^}]*?))?\\s*\\}\\}")

    fun render(
        template: String,
        monitorName: String,
        outcome: PollOutcome,
        nowMillis: Long,
    ): String = PLACEHOLDER.replace(template) { match ->
        val key = match.groupValues[1].lowercase()
        val arg = match.groupValues.getOrNull(2)
        resolve(key, arg, monitorName, outcome, nowMillis)
    }

    private fun resolve(
        key: String,
        arg: String?,
        monitorName: String,
        outcome: PollOutcome,
        nowMillis: Long,
    ): String = when (key) {
        "monitor" -> monitorName
        "timestamp" -> nowMillis.toString()
        "status" -> (outcome as? PollOutcome.Success)?.statusCode?.toString().orEmpty()
        "error" -> (outcome as? PollOutcome.Failure)?.error.orEmpty()
        "body" -> (outcome as? PollOutcome.Success)?.body.orEmpty()
        "json" -> extractJson(outcome, arg)
        else -> "" // placeholder sconosciuto: stringa vuota, mai un errore
    }

    private fun extractJson(outcome: PollOutcome, path: String?): String {
        if (path.isNullOrBlank()) return ""
        val body = (outcome as? PollOutcome.Success)?.body ?: return ""
        return try {
            JsonPath.read<Any>(body, path)?.toString().orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
}
