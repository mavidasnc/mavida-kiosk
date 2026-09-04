package it.mavida.dashboardalert.domain.model

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modelli di dominio dell'app, tutti serializzabili con kotlinx.serialization.
 *
 * Vivono nel layer `domain`: nessuna dipendenza da Android, quindi la logica
 * che li usa (rule engine, edge-trigger, templating) e' testabile con semplici
 * unit test JVM. La persistenza Room ha le sue entity in `data.db` e la
 * conversione avviene nei mapper (`data.Mappers`).
 */

/** Metodi HTTP supportati dai monitor e dalle chiamate HTTP dei trigger. */
enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

/**
 * Singolo header HTTP.
 *
 * Se `secret = true` il valore NON viene salvato nel database Room (campo
 * `value` vuoto) ma in EncryptedSharedPreferences, indicizzato da `id`.
 * In questo modo un export del database o un backup JSON non contiene segreti.
 */
@Serializable
data class HeaderEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: String = "",
    val secret: Boolean = false,
)

// ---------------------------------------------------------------------------
// Condizioni delle regole (rule engine, spec §3.2)
// ---------------------------------------------------------------------------

/**
 * Parametri di una condizione. Gerarchia sealed: il campo "type" nel JSON
 * indica il tipo di condizione, cosi' l'import/export resta leggibile.
 *
 * La valutazione vera e propria e' in `domain.rule.RuleCondition`:
 * qui ci sono solo i dati.
 */
@Serializable
sealed class ConditionParams {

    /** Confronto sullo status code HTTP della risposta. */
    @Serializable
    @SerialName("status_code")
    data class StatusCode(
        val operator: StatusOperator = StatusOperator.EQUALS,
        val value: Int = 200,
        // Estremi inclusivi, usati solo con operator = RANGE.
        val rangeMin: Int = 200,
        val rangeMax: Int = 299,
    ) : ConditionParams()

    /**
     * Estrazione di un valore via JSONPath e confronto con `expected`.
     * `expected` e' una stringa: per gli operatori numerici viene convertita
     * in Double (se il valore estratto non e' numerico, il confronto numerico
     * fallisce e la condizione e' falsa).
     */
    @Serializable
    @SerialName("json_path")
    data class JsonPathCondition(
        val path: String = "$",
        val operator: CompareOperator = CompareOperator.EQUALS,
        val expected: String = "",
    ) : ConditionParams()

    /** Regex (Java) applicata al body testuale della risposta. */
    @Serializable
    @SerialName("regex")
    data class RegexMatch(val pattern: String) : ConditionParams()

    /** Il body contiene (o NON contiene) una sottostringa. */
    @Serializable
    @SerialName("body_contains")
    data class BodyContains(
        val text: String,
        val negate: Boolean = false,
    ) : ConditionParams()

    /**
     * Condizione speciale: vera quando la richiesta fallisce
     * (errore di rete, timeout, nessuna risposta). Serve per rilevare la
     * perdita di connettivita' o un servizio down (spec §3.6).
     */
    @Serializable
    @SerialName("connection_error")
    data object ConnectionError : ConditionParams()
}

enum class StatusOperator { EQUALS, NOT_EQUALS, RANGE }

enum class CompareOperator { EQUALS, NOT_EQUALS, GREATER, LESS, GREATER_OR_EQUAL, LESS_OR_EQUAL, CONTAINS }

// ---------------------------------------------------------------------------
// Configurazioni dei trigger (spec §3.3)
// ---------------------------------------------------------------------------

/**
 * Configurazione di un trigger. Anche qui gerarchia sealed con
 * discriminatore "type" per import/export leggibili.
 */
@Serializable
sealed class TriggerConfig {

    /**
     * Chiamata HTTP in uscita verso un'altra API.
     * `urlTemplate` e `bodyTemplate` supportano placeholder (vedi TemplateEngine):
     * {{monitor}}, {{status}}, {{error}}, {{timestamp}}, {{json:$.percorso}}.
     */
    @Serializable
    @SerialName("http_call")
    data class HttpCall(
        val urlTemplate: String,
        val method: HttpMethod = HttpMethod.POST,
        val headers: List<HeaderEntry> = emptyList(),
        val bodyTemplate: String? = null,
        val bodyContentType: String? = "application/json",
        val timeoutSeconds: Int = 15,
    ) : TriggerConfig()

    /**
     * Riproduzione di un file audio scelto via Storage Access Framework.
     * `uri` e' la stringa dell'URI content:// con permesso persistente.
     */
    @Serializable
    @SerialName("play_sound")
    data class PlaySound(
        val uri: String,
        val volumePercent: Int = 100,
    ) : TriggerConfig()

    /** Notifica di sistema sul channel degli alert. */
    @Serializable
    @SerialName("notification")
    data class Notify(
        val title: String,
        val message: String,
    ) : TriggerConfig()

    /**
     * Vibrazione con pattern: [attesa, vibra, attesa, vibra, ...] in ms.
     * `repeatIndex` = -1 significa "non ripetere".
     */
    @Serializable
    @SerialName("vibration")
    data class Vibrate(
        val pattern: List<Long> = listOf(0, 600, 300, 600),
        val repeatIndex: Int = -1,
    ) : TriggerConfig()

    /**
     * Avviso visivo a schermo pieno con fade-out automatico.
     * `colorHex` nel formato "#RRGGBB".
     */
    @Serializable
    @SerialName("full_screen_alert")
    data class FullScreenAlert(
        val title: String,
        val message: String,
        val colorHex: String = "#D32F2F",
        val durationSeconds: Int = 30,
    ) : TriggerConfig()
}

// ---------------------------------------------------------------------------
// Aggregati di dominio
// ---------------------------------------------------------------------------

/** Definizione di un trigger agganciato a una regola, con la sua policy di ripetizione. */
@Serializable
data class TriggerDef(
    val id: Long = 0,
    val ruleId: Long = 0,
    val config: TriggerConfig,
    val enabled: Boolean = true,
    // Comportamento edge-trigger (spec §3.3): di default il trigger scatta solo
    // sulla transizione falso->vero, con un cooldown anti-rimbalzo.
    val cooldownSeconds: Int = 300,
    // Alternativa: ripeti l'esecuzione ogni N secondi finche' la condizione resta vera.
    val repeatWhileTrue: Boolean = false,
    val repeatIntervalSeconds: Int = 60,
)

/** Regola di valutazione della risposta di un monitor. */
@Serializable
data class Rule(
    val id: Long = 0,
    val monitorId: Long = 0,
    val name: String = "",
    val condition: ConditionParams,
    val enabled: Boolean = true,
    val triggers: List<TriggerDef> = emptyList(),
)

/** Configurazione completa di un monitor di polling. */
@Serializable
data class Monitor(
    val id: Long = 0,
    val name: String,
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: List<HeaderEntry> = emptyList(),
    val body: String? = null,
    val bodyContentType: String? = null,
    val intervalSeconds: Int = 60,
    val timeoutSeconds: Int = 15,
    val maxRetries: Int = 2,
    val enabled: Boolean = true,
    val rules: List<Rule> = emptyList(),
) {
    companion object {
        /** Intervallo minimo ragionevole (spec §3.1): evita di martellare le API. */
        const val MIN_INTERVAL_SECONDS = 5
    }
}
