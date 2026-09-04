package it.mavida.dashboardalert

import android.content.Context
import it.mavida.dashboardalert.data.SecretsStore
import it.mavida.dashboardalert.data.db.AppDatabase
import it.mavida.dashboardalert.data.repository.LogRepository
import it.mavida.dashboardalert.data.repository.MonitorRepository
import it.mavida.dashboardalert.polling.HttpPoller
import it.mavida.dashboardalert.polling.LoggingResponseHandler
import it.mavida.dashboardalert.polling.ResponseHandler
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

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

    /**
     * Client OkHttp condiviso: connection pool e dispatcher unici per tutta
     * l'app. I timeout specifici vengono applicati per-richiesta dai cloni
     * (vedi HttpPoller), che riusano comunque pool e dispatcher.
     * Verifica TLS attiva di default (mai disabilitata, spec §4).
     */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val httpPoller: HttpPoller = HttpPoller(okHttpClient, monitorRepository)

    /** Pipeline post-polling: per ora solo logging; regole e trigger arrivano dopo. */
    val responseHandler: ResponseHandler = LoggingResponseHandler(logRepository)
}
