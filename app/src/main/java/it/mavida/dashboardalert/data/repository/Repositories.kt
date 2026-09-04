package it.mavida.dashboardalert.data.repository

import it.mavida.dashboardalert.data.SecretsStore
import it.mavida.dashboardalert.data.db.AppDatabase
import it.mavida.dashboardalert.data.db.LogEntryEntity
import it.mavida.dashboardalert.data.toDomain
import it.mavida.dashboardalert.data.toEntity
import it.mavida.dashboardalert.domain.model.HeaderEntry
import it.mavida.dashboardalert.domain.model.Monitor
import it.mavida.dashboardalert.domain.model.TriggerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository dei monitor: unico punto di accesso alla persistenza
 * della configurazione (MVVM + Repository, spec §2).
 *
 * Gestisce anche la separazione dei segreti: prima di scrivere nel DB
 * sposta i valori degli header marcati come segreti in EncryptedSharedPreferences.
 */
class MonitorRepository(
    private val db: AppDatabase,
    private val secrets: SecretsStore,
) {

    private val dao = db.monitorDao()

    /** Tutti i monitor con regole e trigger, come Flow reattivo. */
    fun observeMonitors(): Flow<List<Monitor>> =
        dao.observeAllWithRelations().map { list -> list.map { it.toDomain() } }

    suspend fun getMonitor(id: Long): Monitor? = withContext(Dispatchers.IO) {
        dao.getWithRelations(id)?.toDomain()
    }

    /**
     * Salvataggio completo (insert o update) di monitor + regole + trigger.
     * Le regole/trigger rimossi dal draft vengono cancellati esplicitamente.
     */
    suspend fun saveMonitor(monitor: Monitor): Long = withContext(Dispatchers.IO) {
        val sanitized = monitor.copy(
            headers = persistSecrets(monitor.headers),
            rules = monitor.rules.map { rule ->
                rule.copy(triggers = rule.triggers.map { trigger ->
                    val cfg = trigger.config
                    if (cfg is TriggerConfig.HttpCall) {
                        trigger.copy(config = cfg.copy(headers = persistSecrets(cfg.headers)))
                    } else {
                        trigger
                    }
                })
            },
        )

        val monitorId = if (sanitized.id == 0L) {
            dao.insertMonitor(sanitized.toEntity())
        } else {
            dao.updateMonitor(sanitized.toEntity())
            sanitized.id
        }

        // Riscriviamo regole e trigger: strategia semplice "cancella e ricrea"
        // per quelli senza id, REPLACE per quelli esistenti.
        val rules = sanitized.rules
        val keepRuleIds = rules.map { it.id }.filter { it != 0L }
        if (keepRuleIds.isEmpty()) {
            dao.deleteAllRulesOf(monitorId)
        } else {
            dao.deleteRulesNotIn(monitorId, keepRuleIds)
        }

        for (rule in rules) {
            val ruleId = if (rule.id == 0L) {
                dao.insertRules(listOf(rule.toEntity(monitorId))).first()
            } else {
                dao.insertRules(listOf(rule.toEntity(monitorId))).first()
            }
            val keepTriggerIds = rule.triggers.map { it.id }.filter { it != 0L }
            if (keepTriggerIds.isEmpty()) {
                dao.deleteAllTriggersOfRules(listOf(ruleId))
            } else {
                dao.deleteTriggersNotIn(listOf(ruleId), keepTriggerIds)
            }
            dao.insertTriggers(rule.triggers.map { it.toEntity(ruleId) })
        }
        monitorId
    }

    suspend fun deleteMonitor(monitorId: Long) = withContext(Dispatchers.IO) {
        getMonitor(monitorId)?.let { monitor ->
            // Pulizia dei segreti associati, altrimenti resterebbero orfani.
            allHeadersOf(monitor).filter { it.secret }.forEach {
                secrets.remove(SecretsStore.headerKey(it.id))
            }
            dao.deleteMonitor(monitor.toEntity())
        }
    }

    suspend fun setEnabled(monitorId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        getMonitor(monitorId)?.let { dao.updateMonitor(it.copy(enabled = enabled).toEntity()) }
    }

    /**
     * Risolve gli header per l'uso a runtime: reinserisce i valori segreti
     * letti dallo storage cifrato. Da usare SOLO nel percorso di esecuzione
     * (polling/trigger HTTP), mai per logging o export.
     */
    fun resolveHeaders(headers: List<HeaderEntry>): List<Pair<String, String>> =
        headers.mapNotNull { header ->
            val value = if (header.secret) {
                secrets.get(SecretsStore.headerKey(header.id))
            } else {
                header.value
            }
            if (header.name.isBlank() || value == null) null else header.name to value
        }

    /** Sposta i valori segreti nello storage cifrato e li rimuove dal modello. */
    private fun persistSecrets(headers: List<HeaderEntry>): List<HeaderEntry> =
        headers.map { header ->
            if (header.secret && header.value.isNotEmpty()) {
                secrets.put(SecretsStore.headerKey(header.id), header.value)
                header.copy(value = "")
            } else {
                header
            }
        }

    private fun allHeadersOf(monitor: Monitor): List<HeaderEntry> =
        monitor.headers + monitor.rules.flatMap { rule ->
            rule.triggers.mapNotNull { (it.config as? TriggerConfig.HttpCall)?.headers }.flatten()
        }
}

/** Repository dello storico eventi. */
class LogRepository(private val db: AppDatabase) {

    private val dao = db.logDao()

    fun observeRecent(limit: Int = 500): Flow<List<LogEntryEntity>> = dao.observeRecent(limit)

    suspend fun log(
        level: String,
        message: String,
        monitorId: Long? = null,
        monitorName: String = "",
        detail: String? = null,
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            LogEntryEntity(
                monitorId = monitorId,
                monitorName = monitorName,
                level = level,
                // Troncamento difensivo: i dettagli (snippet di body) non devono
                // far esplodere il DB.
                message = message.take(500),
                detail = detail?.take(2000),
            ),
        )
        // Rotazione: manteniamo al massimo 2000 voci.
        dao.trimTo(2000)
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }

    companion object {
        const val LEVEL_INFO = "INFO"
        const val LEVEL_ALERT = "ALERT"
        const val LEVEL_ERROR = "ERROR"
    }
}
