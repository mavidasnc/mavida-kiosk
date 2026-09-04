package it.mavida.dashboardalert.trigger

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.trigger.Trigger
import it.mavida.dashboardalert.domain.trigger.TriggerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Trigger "riproduzione MP3" (spec §3.3.2) con controllo di stop.
 *
 * Il file viene scelto via Storage Access Framework (URI content:// con
 * permesso persistente, preso nella UI di configurazione): nessun permesso
 * invasivo sull'intero storage.
 *
 * E' un manager e non una semplice funzione perche' MediaPlayer e' una
 * risorsa da rilasciare: una nuova riproduzione ferma sempre quella
 * precedente (edge-trigger + cooldown evitano comunque sovrapposizioni),
 * e la UI puo' chiamare [stop] per silenziare l'allarme.
 */
class SoundAlertManager(private val appContext: Context) : Trigger {

    private var player: MediaPlayer? = null

    override suspend fun execute(
        config: TriggerConfig,
        context: TriggerContext,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cfg = config as? TriggerConfig.PlaySound
            ?: return@withContext Result.failure(IllegalArgumentException("config non PlaySound"))
        runCatching {
            stopInternal()
            val uri = Uri.parse(cfg.uri)
            val volume = (cfg.volumePercent.coerceIn(0, 100)) / 100f
            player = MediaPlayer().apply {
                // Uso il canale "alarm": deve sentirsi anche a volume multimediale basso.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(appContext, uri)
                setVolume(volume, volume)
                setOnCompletionListener { stopInternal() }
                setOnErrorListener { _, _, _ ->
                    stopInternal()
                    true // errore gestito: non propagare
                }
                prepare()
                start()
            }
            Unit
        }
    }

    /** Ferma l'eventuale riproduzione in corso (chiamabile dalla UI). */
    fun stop() = stopInternal()

    @Synchronized
    private fun stopInternal() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }

    override fun describe(config: TriggerConfig): String = "Riproduci suono"
}
