package it.mavida.dashboardalert.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import it.mavida.dashboardalert.core.json.AppJson
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Auto-aggiornamento via GitHub Releases.
 *
 * Flusso: si interroga l'API `releases/latest` del repo pubblico (nessun token
 * necessario), si confronta il tag (semver, es. "v0.3.0") con la versione
 * installata e, se piu' recente, si scarica l'APK allegato alla release.
 *
 * L'installazione passa dal Package Installer di sistema via FileProvider:
 * Android mostra comunque una conferma all'utente (installazioni silenziose
 * solo con device owner). Serve il permesso REQUEST_INSTALL_PACKAGES, che
 * l'utente concede una sola volta dalle impostazioni di sistema
 * ("installa app da fonti sconosciute").
 *
 * Vincolo: l'APK scaricato deve essere firmato con lo stesso certificato
 * dell'installato, altrimenti l'installazione fallisce.
 */
class AppUpdater(private val client: OkHttpClient) {

    data class UpdateInfo(
        /** Versione della release senza il prefisso "v" (es. "0.3.0"). */
        val version: String,
        /** URL diretto dell'asset APK allegato alla release. */
        val apkUrl: String,
    )

    /**
     * Restituisce le info sull'aggiornamento se su GitHub esiste una release
     * piu' recente di [currentVersion], null se siamo gia' all'ultima.
     * Lancia IOException in caso di errore di rete o risposta malformata.
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            // GitHub richiede uno User-Agent; Accept esplicito per l'API v3.
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "mavida-kiosk/$currentVersion")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub API: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Risposta vuota da GitHub")
            val json = AppJson.parseToJsonElement(body).jsonObject
            val tag = json["tag_name"]?.jsonPrimitive?.content
                ?: throw IOException("Release senza tag_name")
            val latest = tag.removePrefix("v")
            if (!isNewer(latest, currentVersion)) return@use null
            val apkUrl = json["assets"]?.jsonArray
                ?.mapNotNull { asset ->
                    val obj = asset.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    val url = obj["browser_download_url"]?.jsonPrimitive?.content
                    if (name.endsWith(".apk")) url else null
                }
                ?.firstOrNull()
                ?: throw IOException("Nessun APK allegato alla release $tag")
            UpdateInfo(latest, apkUrl)
        }
    }

    /** Scarica l'APK nella directory degli aggiornamenti (cache). */
    suspend fun downloadApk(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val destDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(destDir, APK_FILE_NAME)
        // Clone con timeout piu' lunghi: l'APK puo' essere di qualche MB.
        val downloadClient = client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download APK: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Download APK: corpo vuoto")
            body.byteStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        dest
    }

    /** Lancia il Package Installer di sistema sull'APK scaricato. */
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** True se l'app puo' installare pacchetti (permesso concesso dall'utente). */
    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Intent verso la pagina di sistema per concedere "fonti sconosciute" a questa app. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val GITHUB_REPO = "mavidasnc/mavida-kiosk"
        const val APK_FILE_NAME = "mavida-kiosk-update.apk"

        /**
         * Confronto semver semplice (solo numeri, es. "0.10.0" > "0.9.9").
         * Componenti mancanti contano come 0; parti non numeriche come 0.
         */
        fun isNewer(latest: String, current: String): Boolean {
            val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
