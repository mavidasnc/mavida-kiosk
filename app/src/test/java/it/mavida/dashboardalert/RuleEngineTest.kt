package it.mavida.dashboardalert

import it.mavida.dashboardalert.domain.model.CompareOperator
import it.mavida.dashboardalert.domain.model.ConditionParams
import it.mavida.dashboardalert.domain.model.StatusOperator
import it.mavida.dashboardalert.domain.rule.ConditionFactory
import it.mavida.dashboardalert.domain.rule.PollOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test del rule engine (spec §4): logica pura, nessun framework Android.
 */
class RuleEngineTest {

    private val jsonBody = """
        {
          "status": "ok",
          "temperature": 23.5,
          "sensors": { "count": 4 },
          "message": "tutto nella norma"
        }
    """.trimIndent()

    private fun success(body: String = jsonBody, code: Int = 200) =
        PollOutcome.Success(statusCode = code, body = body)

    private val failure = PollOutcome.Failure("timeout", isTimeout = true)

    private fun matches(params: ConditionParams, outcome: PollOutcome): Boolean =
        ConditionFactory.create(params).matches(outcome)

    // --- Status code ---

    @Test
    fun `status code equals`() {
        val params = ConditionParams.StatusCode(StatusOperator.EQUALS, value = 200)
        assertTrue(matches(params, success(code = 200)))
        assertFalse(matches(params, success(code = 404)))
        assertFalse(matches(params, failure)) // nessuna risposta -> mai vera
    }

    @Test
    fun `status code not equals`() {
        val params = ConditionParams.StatusCode(StatusOperator.NOT_EQUALS, value = 200)
        assertTrue(matches(params, success(code = 500)))
        assertFalse(matches(params, success(code = 200)))
    }

    @Test
    fun `status code range inclusivo`() {
        val params = ConditionParams.StatusCode(StatusOperator.RANGE, rangeMin = 200, rangeMax = 299)
        assertTrue(matches(params, success(code = 200)))
        assertTrue(matches(params, success(code = 299)))
        assertFalse(matches(params, success(code = 300)))
        assertFalse(matches(params, success(code = 199)))
    }

    // --- JSONPath ---

    @Test
    fun `jsonpath equals numerico`() {
        val params = ConditionParams.JsonPathCondition(
            path = "$.temperature",
            operator = CompareOperator.EQUALS,
            expected = "23.5",
        )
        assertTrue(matches(params, success()))
    }

    @Test
    fun `jsonpath confronti numerici`() {
        val gt = ConditionParams.JsonPathCondition("$.temperature", CompareOperator.GREATER, "20")
        val lt = ConditionParams.JsonPathCondition("$.temperature", CompareOperator.LESS, "20")
        val ge = ConditionParams.JsonPathCondition("$.temperature", CompareOperator.GREATER_OR_EQUAL, "23.5")
        val le = ConditionParams.JsonPathCondition("$.temperature", CompareOperator.LESS_OR_EQUAL, "23.5")
        assertTrue(matches(gt, success()))
        assertFalse(matches(lt, success()))
        assertTrue(matches(ge, success()))
        assertTrue(matches(le, success()))
    }

    @Test
    fun `jsonpath equals testuale e nidificato`() {
        val params = ConditionParams.JsonPathCondition("$.status", CompareOperator.EQUALS, "ok")
        assertTrue(matches(params, success()))

        val nested = ConditionParams.JsonPathCondition("$.sensors.count", CompareOperator.EQUALS, "4")
        assertTrue(matches(nested, success()))
    }

    @Test
    fun `jsonpath contains e not equals`() {
        val contains = ConditionParams.JsonPathCondition("$.message", CompareOperator.CONTAINS, "norma")
        assertTrue(matches(contains, success()))

        val ne = ConditionParams.JsonPathCondition("$.status", CompareOperator.NOT_EQUALS, "errore")
        assertTrue(matches(ne, success()))
    }

    @Test
    fun `jsonpath inesistente o json malformato e' falso`() {
        val params = ConditionParams.JsonPathCondition("$.non.esiste", CompareOperator.EQUALS, "x")
        assertFalse(matches(params, success()))
        // Body non JSON: nessuna eccezione, condizione falsa.
        assertFalse(matches(params, success(body = "non sono json")))
        // Su fallimento di rete: sempre falso.
        assertFalse(matches(params, failure))
    }

    // --- Regex ---

    @Test
    fun `regex sul body`() {
        assertTrue(matches(ConditionParams.RegexMatch("temperature"), success()))
        assertTrue(matches(ConditionParams.RegexMatch("\\d+\\.\\d+"), success()))
        assertFalse(matches(ConditionParams.RegexMatch("assente"), success()))
        assertFalse(matches(ConditionParams.RegexMatch("temperature"), failure))
    }

    @Test
    fun `regex invalida non lancia eccezioni`() {
        assertFalse(matches(ConditionParams.RegexMatch("[a-z"), success()))
    }

    // --- Contains / not contains ---

    @Test
    fun `body contains e not contains`() {
        assertTrue(matches(ConditionParams.BodyContains("sensors"), success()))
        assertFalse(matches(ConditionParams.BodyContains("error"), success()))
        assertTrue(matches(ConditionParams.BodyContains("error", negate = true), success()))
        assertFalse(matches(ConditionParams.BodyContains("sensors", negate = true), success()))
    }

    // --- Errore/timeout/nessuna risposta ---

    @Test
    fun `condizione errore connessione`() {
        assertTrue(matches(ConditionParams.ConnectionError, failure))
        assertFalse(matches(ConditionParams.ConnectionError, success()))
    }
}
