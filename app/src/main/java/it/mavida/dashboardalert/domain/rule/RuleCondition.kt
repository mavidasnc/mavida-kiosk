package it.mavida.dashboardalert.domain.rule

import com.jayway.jsonpath.JsonPath
import it.mavida.dashboardalert.domain.model.CompareOperator
import it.mavida.dashboardalert.domain.model.ConditionParams
import it.mavida.dashboardalert.domain.model.StatusOperator

/**
 * Rule engine (spec §3.2).
 *
 * [RuleCondition] e' l'interfaccia comune ed estensibile: per aggiungere un
 * nuovo tipo di condizione basta una nuova implementazione + una voce in
 * [ConditionFactory]. Tutto il package `domain.rule` e' Kotlin puro (l'unica
 * dipendenza esterna e' Jayway JsonPath, libreria Java): niente Android,
 * quindi e' interamente coperto da unit test JVM.
 *
 * Contratto: [matches] NON lancia mai eccezioni. JSON malformato, path
 * inesistente, regex invalida -> la condizione e' semplicemente falsa.
 */
interface RuleCondition {
    fun matches(outcome: PollOutcome): Boolean

    /** Descrizione leggibile, usata nei log dello storico. */
    fun describe(): String
}

// ---------------------------------------------------------------------------

/** Confronto sullo status code: uguale / diverso / in un range inclusivo. */
class StatusCodeCondition(
    private val params: ConditionParams.StatusCode,
) : RuleCondition {

    override fun matches(outcome: PollOutcome): Boolean {
        val code = (outcome as? PollOutcome.Success)?.statusCode ?: return false
        return when (params.operator) {
            StatusOperator.EQUALS -> code == params.value
            StatusOperator.NOT_EQUALS -> code != params.value
            StatusOperator.RANGE -> code in params.rangeMin..params.rangeMax
        }
    }

    override fun describe(): String = when (params.operator) {
        StatusOperator.EQUALS -> "status == ${params.value}"
        StatusOperator.NOT_EQUALS -> "status != ${params.value}"
        StatusOperator.RANGE -> "status in ${params.rangeMin}..${params.rangeMax}"
    }
}

// ---------------------------------------------------------------------------

/**
 * Estrae un valore dal body JSON via JSONPath e lo confronta con l'atteso.
 *
 * Confronto numerico quando entrambi i lati sono numerici (altrimenti `>`
 * su stringhe darebbe risultati controintuitivi); confronto testuale altrimenti.
 */
class JsonPathCondition(
    private val params: ConditionParams.JsonPathCondition,
) : RuleCondition {

    override fun matches(outcome: PollOutcome): Boolean {
        val body = (outcome as? PollOutcome.Success)?.body ?: return false
        val extracted: Any = try {
            // La Configuration di default lancia PathNotFoundException se il
            // path non esiste: la catturiamo e la condizione risulta falsa.
            JsonPath.read<Any>(body, params.path)
        } catch (e: Exception) {
            return false
        } ?: return false

        val extractedNum = toNumber(extracted)
        val expectedNum = params.expected.trim().toDoubleOrNull()

        return when (params.operator) {
            CompareOperator.EQUALS ->
                if (extractedNum != null && expectedNum != null) {
                    extractedNum == expectedNum
                } else {
                    extracted.toString() == params.expected
                }
            CompareOperator.NOT_EQUALS ->
                if (extractedNum != null && expectedNum != null) {
                    extractedNum != expectedNum
                } else {
                    extracted.toString() != params.expected
                }
            CompareOperator.GREATER ->
                extractedNum != null && expectedNum != null && extractedNum > expectedNum
            CompareOperator.LESS ->
                extractedNum != null && expectedNum != null && extractedNum < expectedNum
            CompareOperator.GREATER_OR_EQUAL ->
                extractedNum != null && expectedNum != null && extractedNum >= expectedNum
            CompareOperator.LESS_OR_EQUAL ->
                extractedNum != null && expectedNum != null && extractedNum <= expectedNum
            CompareOperator.CONTAINS -> extracted.toString().contains(params.expected)
        }
    }

    private fun toNumber(value: Any): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    override fun describe(): String = "jsonpath ${params.path} ${params.operator} '${params.expected}'"
}

// ---------------------------------------------------------------------------

/** Regex Java sul body testuale. */
class RegexCondition(
    private val pattern: String,
) : RuleCondition {

    // Regex compilata una sola volta; pattern invalido -> null -> sempre falso.
    private val regex: Regex? = try {
        Regex(pattern)
    } catch (e: Exception) {
        null
    }

    override fun matches(outcome: PollOutcome): Boolean {
        val body = (outcome as? PollOutcome.Success)?.body ?: return false
        return regex?.containsMatchIn(body) ?: false
    }

    override fun describe(): String = "body match /$pattern/"
}

// ---------------------------------------------------------------------------

/** Il body contiene (o non contiene) una sottostringa. */
class BodyContainsCondition(
    private val text: String,
    private val negate: Boolean,
) : RuleCondition {

    override fun matches(outcome: PollOutcome): Boolean {
        val body = (outcome as? PollOutcome.Success)?.body ?: return false
        val found = body.contains(text)
        return if (negate) !found else found
    }

    override fun describe(): String =
        if (negate) "body NON contiene '$text'" else "body contiene '$text'"
}

// ---------------------------------------------------------------------------

/**
 * Condizione speciale "errore/timeout/nessuna risposta": vera solo quando la
 * richiesta e' fallita. E' il mattoncino per rilevare la perdita di
 * connettivita' (spec §3.6).
 */
object ConnectionErrorCondition : RuleCondition {
    override fun matches(outcome: PollOutcome): Boolean = outcome is PollOutcome.Failure
    override fun describe(): String = "errore/timeout/nessuna risposta"
}

// ---------------------------------------------------------------------------

/** Costruisce la condizione runtime a partire dai parametri serializzati. */
object ConditionFactory {

    fun create(params: ConditionParams): RuleCondition = when (params) {
        is ConditionParams.StatusCode -> StatusCodeCondition(params)
        is ConditionParams.JsonPathCondition -> JsonPathCondition(params)
        is ConditionParams.RegexMatch -> RegexCondition(params.pattern)
        is ConditionParams.BodyContains -> BodyContainsCondition(params.text, params.negate)
        ConditionParams.ConnectionError -> ConnectionErrorCondition
    }
}
