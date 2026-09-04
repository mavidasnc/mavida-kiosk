package it.mavida.dashboardalert.polling

import it.mavida.dashboardalert.data.repository.MonitorRepository
import it.mavida.dashboardalert.domain.model.HttpMethod
import it.mavida.dashboardalert.domain.model.Monitor
import it.mavida.dashboardalert.domain.rule.PollOutcome
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Esegue la singola richiesta HTTP di un monitor, con retry e backoff esponenziale.
 *
 * Sicurezza: questo codice non logga MAI il valore degli header (possono
 * contenere token/API key). Il body della risposta viene passato al chiamante,
 * che ne logga solo uno snippet.
 *
 * TLS: usiamo il client OkHttp di default, quindi la verifica dei certificati
 * resta attiva. Per endpoint con certificati self-signed la via corretta e'
 * installare il CA sul dispositivo (networkSecurityConfig), NON disabilitare
 * la verifica (spec §4).
 */
class HttpPoller(
    private val baseClient: OkHttpClient,
    private val repository: MonitorRepository,
) {

    suspend fun poll(monitor: Monitor): PollOutcome {
        var attempt = 0
        var lastFailure: PollOutcome.Failure? = null
        // Tentativo iniziale + maxRetries retry con backoff esponenziale (1s, 2s, 4s...).
        while (attempt <= monitor.maxRetries) {
            if (attempt > 0) {
                delay(1000L shl (attempt - 1))
            }
            try {
                return executeOnce(monitor)
            } catch (e: SocketTimeoutException) {
                lastFailure = PollOutcome.Failure("Timeout dopo ${monitor.timeoutSeconds}s", isTimeout = true)
            } catch (e: IOException) {
                lastFailure = PollOutcome.Failure(e.message ?: e.javaClass.simpleName)
            } catch (e: IllegalArgumentException) {
                // URL malformato o header non validi: errore di configurazione,
                // ritentare non serve.
                return PollOutcome.Failure("Configurazione non valida: ${e.message}")
            }
            attempt++
        }
        return lastFailure ?: PollOutcome.Failure("Errore sconosciuto")
    }

    private fun executeOnce(monitor: Monitor): PollOutcome {
        val request = buildRequest(monitor)
        // Timeout per-monitor: cloniamo il client base (condivide connection pool
        // e dispatcher, quindi e' economico).
        val client = baseClient.newBuilder()
            .connectTimeout(monitor.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(monitor.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(monitor.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val headers = response.headers.toMultimap()
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.joinToString(", ") }
            return PollOutcome.Success(
                statusCode = response.code,
                body = body,
                responseHeaders = headers,
            )
        }
    }

    private fun buildRequest(monitor: Monitor): Request {
        val builder = Request.Builder().url(monitor.url)

        // resolveHeaders reinserisce i segreti dallo storage cifrato (solo in memoria).
        repository.resolveHeaders(monitor.headers).forEach { (name, value) ->
            builder.header(name, value)
        }

        val body = monitor.body
        if (body != null && monitor.method != HttpMethod.GET) {
            val mediaType = (monitor.bodyContentType ?: "application/json").toMediaTypeOrNull()
            builder.method(monitor.method.name, body.toRequestBody(mediaType))
        } else {
            builder.method(monitor.method.name, null)
        }
        return builder.build()
    }
}
