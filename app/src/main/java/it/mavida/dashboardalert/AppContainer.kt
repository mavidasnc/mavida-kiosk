package it.mavida.dashboardalert

import android.content.Context
import it.mavida.dashboardalert.data.SecretsStore
import it.mavida.dashboardalert.data.db.AppDatabase
import it.mavida.dashboardalert.data.repository.LogRepository
import it.mavida.dashboardalert.data.repository.MonitorRepository

/**
 * Dependency injection manuale (niente Hilt: una dipendenza in meno e
 * il grafo degli oggetti e' piccolo e leggibile).
 * Istanziato una sola volta in [DashboardAlertApp].
 */
class AppContainer(context: Context) {

    val database: AppDatabase = AppDatabase.get(context)
    val secretsStore: SecretsStore = SecretsStore(context)
    val monitorRepository: MonitorRepository = MonitorRepository(database, secretsStore)
    val logRepository: LogRepository = LogRepository(database)
}
