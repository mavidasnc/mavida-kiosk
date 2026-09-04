package it.mavida.dashboardalert.trigger

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import it.mavida.dashboardalert.domain.model.TriggerConfig
import it.mavida.dashboardalert.domain.trigger.Trigger
import it.mavida.dashboardalert.domain.trigger.TriggerContext

/**
 * Trigger "vibrazione" con pattern configurabile (spec §3.3.4).
 * Pattern: [attesa, vibra, attesa, vibra, ...] in millisecondi.
 */
class VibrationTrigger(private val appContext: Context) : Trigger {

    override suspend fun execute(
        config: TriggerConfig,
        context: TriggerContext,
    ): Result<Unit> {
        val cfg = config as? TriggerConfig.Vibrate
            ?: return Result.failure(IllegalArgumentException("config non Vibrate"))
        return runCatching {
            val vibrator = vibrator()
                ?: error("Vibratore non disponibile su questo dispositivo")
            val pattern = cfg.pattern.toLongArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, cfg.repeatIndex),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, cfg.repeatIndex)
            }
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun describe(config: TriggerConfig): String = "Vibrazione"
}
