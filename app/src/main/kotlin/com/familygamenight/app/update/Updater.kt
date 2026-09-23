package com.familygamenight.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.familygamenight.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates straight from this project's GitHub Releases. Android won't let a side-loaded app
 * replace itself silently, so we download the APK and hand it to the system installer (one tap).
 */
object Updater {
    data class Release(val versionCode: Int, val title: String, val notes: String, val apkUrl: String)

    private val json = Json { ignoreUnknownKeys = true }

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Latest release on GitHub, or null if there isn't one with an APK attached. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "FamilyGameNight/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            when (conn.responseCode) {
                404 -> return@withContext null // no releases yet
                !in 200..299 -> error("GitHub said ${conn.responseCode}")
            }
            val body = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(o: JsonObject): Release? {
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        // Tags look like v1.0.<build number>; the build number is the Android versionCode.
        val code = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: return null
        val apk = o["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: return null
        return Release(
            versionCode = code,
            title = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: tag,
            notes = o["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apkUrl = apk,
        )
    }

    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "family-game-night-${release.versionCode}.apk")
            var url = URL(release.apkUrl)
            var conn: HttpURLConnection
            // GitHub redirects to its download CDN; follow by hand in case the host changes.
            var hops = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "FamilyGameNight/${BuildConfig.VERSION_NAME}")
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val code = conn.responseCode
                if (code in 300..399 && hops++ < 5) {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) error("Download failed ($code)")
                break
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            target
        }

    /** True if Android will let us open the installer; otherwise send the user to the setting. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
