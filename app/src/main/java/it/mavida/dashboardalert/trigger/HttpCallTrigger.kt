package it.mavida.dashboardalert.trigger

import it.mavida.dashboardalert.data.repository.MonitorRepository
import it.mavida.dashboardalert.domain.model.HttpMethod
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.trigger.TemplateEngine
import it.mavida.dashboardalert.domain.trigger.Trigger
import it.mavida.dashboardalert.domain.trigger.TriggerContext
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Trigger "chiamata HTTP in uscita" (spec §3.3.1): notifica un'altra API
 * quando una regola scatta. URL e body passano dal TemplateEngine, cosi'
 * possono contenere valori della risposta del monitor.
 *
 * Come per il polling: header segreti risolti solo in memoria, mai nei log;
 * il Result non contiene mai il body della richiesta.
 */
class HttpCallTrigger(
    private val baseClient: OkHttpClient,
    private val repository: MonitorRepository,
) : Trigger {

    override suspend fun execute(
        config: TriggerConfig,
        context: TriggerContext,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cfg = config as? TriggerConfig.HttpCall
            ?: return@withContext Result.failure(IllegalArgumentException("config non HttpCall"))
        runCatching {
            val now = System.currentTimeMillis()
            val url = TemplateEngine.render(cfg.urlTemplate, context.monitor.name, context.outcome, now)
            require(url.isNotBlank()) { "URL vuota dopo il templating" }

            val builder = Request.Builder().url(url)
            repository.resolveHeaders(cfg.headers).forEach { (name, value) ->
                builder.header(name, value)
            }

            val body = cfg.bodyTemplate?.let {
                TemplateEngine.render(it, context.monitor.name, context.outcome, now)
            }
            if (body != null && cfg.method != HttpMethod.GET) {
                val mediaType = (cfg.bodyContentType ?: "application/json").toMediaTypeOrNull()
                builder.method(cfg.method.name, body.toRequestBody(mediaType))
            } else {
                builder.method(cfg.method.name, null)
            }

            val client = baseClient.newBuilder()
                .connectTimeout(cfg.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(cfg.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            client.newCall(builder.build()).execute().use { response ->
                // Un 4xx/5xx del webhook NON e' un'eccezione: l'HTTP e' andato a
                // buon fine, ma lo segnaliamo come fallimento per il log.
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} dal server di destinazione")
                }
            }
        }
    }

    override fun describe(config: TriggerConfig): String {
        val cfg = config as? TriggerConfig.HttpCall
        return "HTTP ${cfg?.method ?: "?"} ${cfg?.urlTemplate ?: ""}"
    }
}
