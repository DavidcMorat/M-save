package com.example.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val latestVersion: String, val currentVersion: String, val downloadUrl: String, val releaseNotes: String) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progressPercent: Int) : UpdateState()
    data class Success(val newVersion: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class YtDlpUpdater(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("ytdlp_engine_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_EMBEDDED_VERSION = "2025.02.19"
        private const val PREF_KEY_VERSION = "installed_engine_version"
        private const val PREF_KEY_LAST_CHECK = "last_check_timestamp"
        private const val GITHUB_API_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    }

    fun getInstalledVersion(): String {
        return prefs.getString(PREF_KEY_VERSION, DEFAULT_EMBEDDED_VERSION) ?: DEFAULT_EMBEDDED_VERSION
    }

    fun getLastCheckTime(): Long {
        return prefs.getLong(PREF_KEY_LAST_CHECK, 0L)
    }

    suspend fun checkForUpdates(): UpdateState = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("User-Agent", "MeowSave-Android/${getInstalledVersion()}")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateState.Error("Error al consultar GitHub: Código ${response.code}")
            }

            val body = response.body?.string() ?: return@withContext UpdateState.Error("Respuesta vacía del servidor")
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseNotes = json.optString("body", "Sin notas de versión disponibles.")

            prefs.edit().putLong(PREF_KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            val current = getInstalledVersion()
            if (tagName.isNotEmpty() && tagName != current) {
                // Find yt-dlp asset download URL
                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name == "yt-dlp" || name == "yt-dlp.tar.gz" || name == "yt-dlp_linux") {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                if (downloadUrl.isEmpty()) {
                    downloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/$tagName/yt-dlp"
                }

                return@withContext UpdateState.UpdateAvailable(
                    latestVersion = tagName,
                    currentVersion = current,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes.take(300)
                )
            } else {
                return@withContext UpdateState.UpToDate
            }
        } catch (e: Exception) {
            return@withContext UpdateState.Error(e.localizedMessage ?: "Fallo de conexión al buscar actualizaciones")
        }
    }

    suspend fun downloadAndUpdate(
        downloadUrl: String,
        targetVersion: String,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val engineDir = File(context.filesDir, "ytdlp")
            if (!engineDir.exists()) engineDir.mkdirs()

            val tempFile = File(engineDir, "yt-dlp.download")
            val targetFile = File(engineDir, "yt-dlp")

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "MeowSave-Android")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Fallo al descargar actualización: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Cuerpo de descarga vacío"))
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (totalBytes > 0) {
                            val percent = ((totalRead * 100) / totalBytes).toInt()
                            onProgress(percent)
                        }
                    }
                    output.flush()
                }
            }

            // Rename to target file and grant executable permissions
            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                targetFile.setExecutable(true, false)
            }

            // Update persisted version
            prefs.edit().putString(PREF_KEY_VERSION, targetVersion).apply()
            Result.success(targetVersion)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
