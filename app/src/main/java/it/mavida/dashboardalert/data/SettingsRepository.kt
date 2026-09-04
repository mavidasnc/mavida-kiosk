package it.mavida.dashboardalert.data

import android.content.Context
import android.content.SharedPreferences
import it.mavida.dashboardalert.core.json.AppJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Impostazioni applicative (non segrete) in SharedPreferences semplici.
 *
 * I segreti NON passano mai di qui (c'e' SecretsStore per quelli).
 * Espone uno StateFlow che la UI osserva: ogni modifica e' subito reattiva.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    data class Settings(
        /** URL delle dashboard per la modalita' browser (rotazione, spec §3.6). */
        val browserUrls: List<String> = emptyList(),
        /** Ricarica automatica della WebView, in secondi (0 = disattivata). */
        val browserReloadSeconds: Int = 0,
        /** Secondi di permanenza su ogni dashboard nella rotazione. */
        val browserRotationSeconds: Int = 30,
        /** Mantieni lo schermo acceso (anti-standby kiosk, spec §3.5). */
        val keepScreenOn: Boolean = true,
    )

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun load(): Settings = Settings(
        browserUrls = runCatching {
            AppJson.decodeFromString(
                ListSerializer(String.serializer()),
                prefs.getString(KEY_URLS, "[]").orEmpty(),
            )
        }.getOrDefault(emptyList()),
        browserReloadSeconds = prefs.getInt(KEY_RELOAD, 0),
        browserRotationSeconds = prefs.getInt(KEY_ROTATION, 30),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN, true),
    )

    private fun refresh() {
        _settings.value = load()
    }

    fun setBrowserUrls(urls: List<String>) {
        prefs.edit().putString(
            KEY_URLS,
            AppJson.encodeToString(ListSerializer(String.serializer()), urls),
        ).apply()
        refresh()
    }

    fun setBrowserReloadSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_RELOAD, seconds.coerceAtLeast(0)).apply()
        refresh()
    }

    fun setBrowserRotationSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_ROTATION, seconds.coerceAtLeast(5)).apply()
        refresh()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN, enabled).apply()
        refresh()
    }

    companion object {
        private const val FILE_NAME = "dashboard_alert_settings"
        private const val KEY_URLS = "browser_urls"
        private const val KEY_RELOAD = "browser_reload_seconds"
        private const val KEY_ROTATION = "browser_rotation_seconds"
        private const val KEY_KEEP_SCREEN = "keep_screen_on"
    }
}
