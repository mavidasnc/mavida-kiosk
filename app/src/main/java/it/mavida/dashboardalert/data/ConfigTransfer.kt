package it.mavida.dashboardalert.data

import it.mavida.dashboardalert.core.json.AppJson
import it.mavida.dashboardalert.data.repository.MonitorRepository
import it.mavida.dashboardalert.domain.model.Monitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Import/export della configurazione in JSON (spec §3.6: backup/ripristino).
 *
 * Formato del file:
 * ```json
 * {
 *   "app": "dashboard-alert",
 *   "formatVersion": 1,
 *   "exportedAt": 1730000000000,
 *   "monitors": [ ... ]
 * }
 * ```
 *
 * Sicurezza: i valori degli header marcati come "segreti" NON vengono
 * esportati (nel JSON restano vuoti): dopo un ripristino vanno reinseriti
 * a mano. Scelta deliberata: un file di backup gira tipicamente in chiaro.
 */
class ConfigTransfer(
    private val monitorRepository: MonitorRepository,
) {

    @Serializable
    data class ExportFile(
        val app: String = APP_TAG,
        val formatVersion: Int = FORMAT_VERSION,
        val exportedAt: Long = System.currentTimeMillis(),
        val monitors: List<Monitor> = emptyList(),
    )

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val monitors = monitorRepository.observeMonitors().first()
        val file = ExportFile(monitors = monitors)
        AppJson.encodeToString(ExportFile.serializer(), file)
    }

    /**
     * Importa e SOSTITUISCE tutti i monitor esistenti (ripristino da backup).
     * @return numero di monitor importati.
     */
    suspend fun importJson(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val file = AppJson.decodeFromString(ExportFile.serializer(), json)
            require(file.app == APP_TAG) { "File non riconducibile a questa app" }
            require(file.formatVersion <= FORMAT_VERSION) {
                "Formato ${file.formatVersion} piu' recente di quello supportato ($FORMAT_VERSION)"
            }

            // Ripristino = sostituzione completa: prima si cancella l'esistente.
            monitorRepository.observeMonitors().first().forEach {
                monitorRepository.deleteMonitor(it.id)
            }
            // Azzeriamo gli id: alla reimportazione devono essere rigenerati dal DB.
            file.monitors.forEach { monitor ->
                monitorRepository.saveMonitor(
                    monitor.copy(
                        id = 0,
                        rules = monitor.rules.map { rule ->
                            rule.copy(
                                id = 0,
                                monitorId = 0,
                                triggers = rule.triggers.map { it.copy(id = 0, ruleId = 0) },
                            )
                        },
                    ),
                )
            }
            file.monitors.size
        }
    }

    companion object {
        private const val APP_TAG = "dashboard-alert"
        private const val FORMAT_VERSION = 1
    }
}
