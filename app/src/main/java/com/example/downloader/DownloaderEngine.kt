package com.example.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTaskEntity
import com.example.service.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DownloadProgressInfo(
    val taskId: String,
    val title: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val status: DownloadStatus
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val speedFormatted: String
        get() = when {
            speedBytesPerSec <= 0 -> "-- KB/s"
            speedBytesPerSec < 1024 * 1024 -> String.format("%.1f KB/s", speedBytesPerSec / 1024.0)
            else -> String.format("%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
        }

    val etaFormatted: String
        get() {
            if (speedBytesPerSec <= 0 || totalBytes <= 0 || downloadedBytes >= totalBytes) return "--"
            val remainingBytes = totalBytes - downloadedBytes
            val seconds = remainingBytes / speedBytesPerSec
            return when {
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
                else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            }
        }
}

class DownloaderEngine(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedTasks = ConcurrentHashMap.newKeySet<String>()

    private val _activeProgress = MutableStateFlow<Map<String, DownloadProgressInfo>>(emptyMap())
    val activeProgress = _activeProgress.asStateFlow()

    fun enqueueDownload(
        url: String,
        title: String,
        quality: String = "1080p",
        mimeType: String = "video/mp4",
        extension: String? = null,
        thumbnailUrl: String? = null,
        sourcePageUrl: String? = null,
        scope: CoroutineScope
    ) {
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(50)
        // Usar la extensión real del contenedor (mp4, webm, m4a, opus...).
        // Adivinar "mp4" para un stream WebM producía archivos que no abrían.
        val ext = extension?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.take(6)
            ?.takeIf { it.isNotBlank() }
            ?: if (mimeType.contains("audio")) "mp3" else "mp4"
        val fileName = "${sanitizedTitle}_${System.currentTimeMillis()}.$ext"
        val taskId = "task_${System.currentTimeMillis()}_${(1000..9999).random()}"

        val task = DownloadTaskEntity(
            id = taskId,
            url = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            fileName = fileName,
            filePath = null,
            mimeType = mimeType,
            quality = quality,
            totalBytes = 0L,
            downloadedBytes = 0L,
            speedBytesPerSec = 0L,
            status = DownloadStatus.QUEUED.name,
            sourcePageUrl = sourcePageUrl
        )

        scope.launch {
            downloadDao.insertTask(task)
            startDownload(taskId, scope)
        }
    }

    fun startDownload(taskId: String, scope: CoroutineScope) {
        if (activeJobs.containsKey(taskId)) return
        pausedTasks.remove(taskId)

        // LAZY + start() guarantees the job is registered in activeJobs before
        // it can finish and check whether the last download is done.
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            executeDownload(taskId)
        }
        activeJobs[taskId] = job
        job.start()
    }

    fun pauseDownload(taskId: String, scope: CoroutineScope) {
        pausedTasks.add(taskId)
        activeJobs.remove(taskId)?.cancel()
        scope.launch {
            downloadDao.updateStatus(taskId, DownloadStatus.PAUSED.name)
            updateProgressMap(taskId, DownloadStatus.PAUSED, 0L)
        }
    }

    fun resumeDownload(taskId: String, scope: CoroutineScope) {
        startDownload(taskId, scope)
    }

    fun cancelDownload(taskId: String, scope: CoroutineScope) {
        pausedTasks.remove(taskId)
        activeJobs.remove(taskId)?.cancel()
        scope.launch {
            downloadDao.deleteTaskById(taskId)
            val current = _activeProgress.value.toMutableMap()
            current.remove(taskId)
            _activeProgress.value = current
        }
    }

    private suspend fun executeDownload(taskId: String) {
        val task = downloadDao.getTaskById(taskId) ?: return
        downloadDao.updateStatus(taskId, DownloadStatus.DOWNLOADING.name)
        // The notification is a nicety: never let it abort the download.
        runCatching { DownloadService.startService(context, task.title) }

        // El archivo .tmp es la fuente de verdad: Room solo guarda una copia
        // con throttle y puede ir por detrás de lo que realmente llegó a disco.
        val tempFile = File(context.cacheDir, "dl_${taskId}.tmp")
        var downloaded = if (tempFile.exists()) tempFile.length() else 0L
        var total = task.totalBytes

        try {
            val requestBuilder = Request.Builder()
                .url(task.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")

            // Inject Referer and Origin from source page
            val ref = task.sourcePageUrl
            if (!ref.isNullOrEmpty()) {
                requestBuilder.header("Referer", ref)
                try {
                    val uri = Uri.parse(ref)
                    if (uri.scheme != null && uri.host != null) {
                        requestBuilder.header("Origin", "${uri.scheme}://${uri.host}")
                    }
                } catch (_: Exception) {}
            }

            // Inject session cookies from CookieManager
            val cookie = runCatching {
                CookieManager.getInstance().getCookie(task.url)
                    ?: if (!ref.isNullOrEmpty()) CookieManager.getInstance().getCookie(ref) else null
            }.getOrNull()

            if (!cookie.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookie)
            }

            if (downloaded > 0) {
                requestBuilder.header("Range", "bytes=$downloaded-")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                val errorMsg = when (response.code) {
                    403 -> "Acceso denegado (403): el enlace requiere autenticación o expiró"
                    404 -> "Recurso no encontrado (404)"
                    410 -> "El enlace ha caducado (410)"
                    else -> "Error HTTP ${response.code}"
                }
                downloadDao.updateStatus(taskId, DownloadStatus.FAILED.name, errorMsg)
                updateProgressMap(taskId, DownloadStatus.FAILED, 0L, downloaded, total, task.title)
                return
            }

            val body = response.body ?: throw Exception("Cuerpo de respuesta vacío")

            // Si el servidor ignoró el Range (200 en vez de 206), el cuerpo
            // vuelve a empezar desde el byte 0: se trunca el .tmp en vez de
            // anexar y corromper el archivo. El código anterior anexaba
            // siempre que downloaded > 0, incluso con un 200 completo.

            // Validate Content-Type: reject HTML error pages saved as media
            val contentType = body.contentType()?.toString()?.lowercase() ?: ""
            if (contentType.contains("text/html") || contentType.contains("application/json")) {
                throw Exception("El servidor devolvió una página web/error en lugar de un archivo multimedia.")
            }

            val contentLength = body.contentLength()
            val resumed = response.code == 206
            val append = resumed && downloaded > 0
            if (!append) {
                downloaded = 0L
                if (tempFile.exists()) tempFile.delete()
            }
            if (contentLength > 0) {
                total = if (append) downloaded + contentLength else contentLength
            }

            val inputStream: InputStream = body.byteStream()
            val outputStream: OutputStream = FileOutputStream(tempFile, append)

            val buffer = ByteArray(32768)
            var bytesRead: Int
            var lastUiUpdateTime = System.currentTimeMillis()
            var lastDbUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var currentSpeed = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (pausedTasks.contains(taskId)) {
                            throw CancellationException("Pausado por usuario")
                        }

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        bytesSinceLastUpdate += bytesRead

                        val now = System.currentTimeMillis()
                        val diff = now - lastUiUpdateTime

                        // UI progress update every 400ms for smooth 60fps Compose
                        if (diff >= 400) {
                            currentSpeed = (bytesSinceLastUpdate * 1000) / diff
                            lastUiUpdateTime = now
                            bytesSinceLastUpdate = 0L

                            updateProgressMap(
                                taskId = taskId,
                                status = DownloadStatus.DOWNLOADING,
                                speed = currentSpeed,
                                downloaded = downloaded,
                                total = total,
                                title = task.title
                            )

                            // Keep the foreground notification in sync, otherwise
                            // it stays stuck at "0%" until the download ends.
                            val percent = if (total > 0) {
                                ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            runCatching {
                                DownloadService.updateProgress(
                                    context = context,
                                    title = task.title,
                                    progress = percent,
                                    speed = formatSpeed(currentSpeed)
                                )
                            }
                        }

                        // Throttled Database update every 2000ms to eliminate SQLite locking
                        if (now - lastDbUpdateTime >= 2000) {
                            lastDbUpdateTime = now
                            downloadDao.updateProgress(
                                id = taskId,
                                downloaded = downloaded,
                                total = total,
                                speed = currentSpeed,
                                status = DownloadStatus.DOWNLOADING.name
                            )
                        }
                    }
                    output.flush()
                }
            }

            // Post-download validation: file must exist and have valid size
            if (!tempFile.exists() || tempFile.length() < 1024L) {
                throw Exception("El archivo descargado está vacío o no contiene datos válidos.")
            }

            // Move completed file to destination public Downloads
            val savedPath = saveToMediaStore(tempFile, task.fileName, task.mimeType)

            downloadDao.markCompleted(taskId, savedPath)
            updateProgressMap(taskId, DownloadStatus.COMPLETED, 0L, downloaded, total, task.title)
            tempFile.delete()

        } catch (e: CancellationException) {
            downloadDao.updateStatus(taskId, DownloadStatus.PAUSED.name)
            updateProgressMap(taskId, DownloadStatus.PAUSED, 0L, downloaded, total, task.title)
        } catch (e: Exception) {
            val errorText = e.localizedMessage ?: "Error desconocido en la descarga"
            downloadDao.updateStatus(taskId, DownloadStatus.FAILED.name, errorText)
            updateProgressMap(taskId, DownloadStatus.FAILED, 0L, downloaded, total, task.title)
        } finally {
            activeJobs.remove(taskId)
            // Only drop the foreground service when nothing else is downloading.
            if (activeJobs.isEmpty()) {
                runCatching { DownloadService.stopService(context) }
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return "-- KB/s"
        val kb = bytesPerSecond / 1024.0
        return if (kb >= 1024) {
            String.format("%.1f MB/s", kb / 1024.0)
        } else {
            String.format("%.0f KB/s", kb)
        }
    }

    private fun updateProgressMap(
        taskId: String,
        status: DownloadStatus,
        speed: Long = 0L,
        downloaded: Long = 0L,
        total: Long = 0L,
        title: String = ""
    ) {
        val current = _activeProgress.value.toMutableMap()
        current[taskId] = DownloadProgressInfo(
            taskId = taskId,
            title = title,
            downloadedBytes = downloaded,
            totalBytes = total,
            speedBytesPerSec = speed,
            status = status
        )
        _activeProgress.value = current
    }

    private fun saveToMediaStore(sourceFile: File, fileName: String, mimeType: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MeowSave")
                }

                val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    uri.toString()
                } else {
                    fallbackSave(sourceFile, fileName)
                }
            } else {
                fallbackSave(sourceFile, fileName)
            }
        } catch (_: Exception) {
            fallbackSave(sourceFile, fileName)
        }
    }

    private fun fallbackSave(sourceFile: File, fileName: String): String {
        val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MeowSave")
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, fileName)
        sourceFile.copyTo(destFile, overwrite = true)
        return destFile.absolutePath
    }
}
