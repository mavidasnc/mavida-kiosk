package it.mavida.dashboardalert.data

import it.mavida.dashboardalert.core.json.AppJson
import it.mavida.dashboardalert.data.db.MonitorEntity
import it.mavida.dashboardalert.data.db.MonitorWithRelations
import it.mavida.dashboardalert.data.db.RuleEntity
import it.mavida.dashboardalert.data.db.RuleWithTriggers
import it.mavida.dashboardalert.data.db.TriggerEntity
import it.mavida.dashboardalert.domain.model.ConditionParams
import it.mavida.dashboardalert.domain.model.HeaderEntry
import it.mavida.dashboardalert.domain.model.HttpMethod
import it.mavida.dashboardalert.domain.model.Monitor
import it.mavida.dashboardalert.domain.model.Rule
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.model.TriggerDef
import kotlinx.serialization.builtins.ListSerializer

/**
 * Conversioni entity Room <-> modelli di dominio.
 *
 * I JSON malformati (es. dopo un downgrade dell'app) non devono crashare:
 * in quel caso si ripiega su valori di default sicuri e la regola/trigger
 * risulta di fatto inerte.
 */

fun MonitorWithRelations.toDomain(): Monitor = Monitor(
    id = monitor.id,
    name = monitor.name,
    url = monitor.url,
    method = runCatching { HttpMethod.valueOf(monitor.method) }.getOrDefault(HttpMethod.GET),
    headers = decodeHeaders(monitor.headersJson),
    body = monitor.body,
    bodyContentType = monitor.bodyContentType,
    intervalSeconds = monitor.intervalSeconds,
    timeoutSeconds = monitor.timeoutSeconds,
    maxRetries = monitor.maxRetries,
    enabled = monitor.enabled,
    rules = rules.map { it.toDomain() },
)

fun RuleWithTriggers.toDomain(): Rule = Rule(
    id = rule.id,
    monitorId = rule.monitorId,
    name = rule.name,
    condition = runCatching {
        AppJson.decodeFromString<ConditionParams>(rule.conditionJson)
    }.getOrDefault(ConditionParams.ConnectionError),
    enabled = rule.enabled,
    triggers = triggers.map { it.toDomain() },
)

fun TriggerEntity.toDomain(): TriggerDef = TriggerDef(
    id = id,
    ruleId = ruleId,
    config = runCatching {
        AppJson.decodeFromString<TriggerConfig>(configJson)
    }.getOrDefault(
        // Fallback inerte: notifica vuota, non fa danni se eseguita per errore.
        TriggerConfig.Notify(title = "Configurazione non valida", message = configJson.take(100)),
    ),
    enabled = enabled,
    cooldownSeconds = cooldownSeconds,
    repeatWhileTrue = repeatWhileTrue,
    repeatIntervalSeconds = repeatIntervalSeconds,
)

fun Monitor.toEntity(): MonitorEntity = MonitorEntity(
    id = id,
    name = name,
    url = url,
    method = method.name,
    headersJson = encodeHeaders(headers),
    body = body,
    bodyContentType = bodyContentType,
    intervalSeconds = intervalSeconds,
    timeoutSeconds = timeoutSeconds,
    maxRetries = maxRetries,
    enabled = enabled,
    updatedAt = System.currentTimeMillis(),
)

fun Rule.toEntity(monitorId: Long): RuleEntity = RuleEntity(
    id = id,
    monitorId = monitorId,
    name = name,
    conditionJson = AppJson.encodeToString(ConditionParams.serializer(), condition),
    enabled = enabled,
)

fun TriggerDef.toEntity(ruleId: Long): TriggerEntity = TriggerEntity(
    id = id,
    ruleId = ruleId,
    configJson = AppJson.encodeToString(TriggerConfig.serializer(), config),
    enabled = enabled,
    cooldownSeconds = cooldownSeconds,
    repeatWhileTrue = repeatWhileTrue,
    repeatIntervalSeconds = repeatIntervalSeconds,
)

fun decodeHeaders(json: String): List<HeaderEntry> =
    runCatching {
        AppJson.decodeFromString(ListSerializer(HeaderEntry.serializer()), json)
    }.getOrDefault(emptyList())

fun encodeHeaders(headers: List<HeaderEntry>): String =
    AppJson.encodeToString(ListSerializer(HeaderEntry.serializer()), headers)
