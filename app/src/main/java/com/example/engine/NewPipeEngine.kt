package com.example.engine

import com.example.engine.model.MediaExtractionResult
import com.example.engine.model.MediaFormatOption
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/**
 * Motor de extracción **real** basado en NewPipeExtractor (GPLv3).
 *
 * Soporta: YouTube, SoundCloud, PeerTube, MediaCCC y Bandcamp.
 * Resuelve firmas de reproducción en proceso con Rhino: no necesita
 * binarios externos ni runtime de JavaScript.
 *
 * TikTok / Instagram / Facebook no los soporta NewPipe: para esos sitios
 * la app usa la vista previa del navegador (sniffer + Media3).
 */
class NewPipeEngine {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {
        ensureInitialized(client)
    }

    companion object {
        @Volatile
        private var initialized = false
        private val initLock = Any()

        private fun ensureInitialized(client: OkHttpClient) {
            if (initialized) return
            synchronized(initLock) {
                if (!initialized) {
                    NewPipe.init(OkHttpDownloader(client))
                    initialized = true
                }
            }
        }
    }

    /** ¿Puede este motor resolver la URL (YouTube, SoundCloud, …)? */
    fun supports(url: String): Boolean {
        return try {
            NewPipe.getServiceByUrl(url)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun extract(url: String): Result<MediaExtractionResult> {
        return try {
            val info = StreamInfo.getInfo(url)
            val formats = buildFormats(info)
            if (formats.isEmpty()) {
                return Result.failure(Exception("El motor no devolvió ningún formato descargable"))
            }
            Result.success(
                MediaExtractionResult(
                    title = info.name ?: url,
                    webpageUrl = url,
                    thumbnail = info.thumbnails.firstOrNull()?.url,
                    author = info.uploaderName?.takeIf { it.isNotBlank() },
                    durationSeconds = if (info.duration > 0) info.duration else 0L,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildFormats(info: StreamInfo): List<MediaFormatOption> {
        val formats = mutableListOf<MediaFormatOption>()

        // Streams progresivos: video + audio, descarga directa normal.
        info.videoStreams
            .filter { it.isUrl && it.content.isNotBlank() && !it.content.isManifestUrl() }
            .sortedByDescending { it.bitrate }
            .take(8)
            .forEach { s ->
                formats += MediaFormatOption(
                    formatId = "npe_video_${s.id.ifBlank { formats.size }}",
                    formatNote = "Video + Audio",
                    resolution = s.resolution.takeIf { it.isNotBlank() } ?: "Original",
                    ext = s.format?.suffix ?: "mp4",
                    approximateSizeBytes = 0L,
                    isAudioOnly = false,
                    directUrl = s.content
                )
            }

        // Solo video (sin audio): se ofrece como tal, sin prometer lo que no es.
        info.videoOnlyStreams
            .filter { it.isUrl && it.content.isNotBlank() && !it.content.isManifestUrl() }
            .sortedByDescending { it.bitrate }
            .distinctBy { it.resolution }
            .take(8)
            .forEach { s ->
                formats += MediaFormatOption(
                    formatId = "npe_videoonly_${s.id.ifBlank { formats.size }}",
                    formatNote = "Solo video (sin audio)",
                    resolution = s.resolution.takeIf { it.isNotBlank() } ?: "Original",
                    ext = s.format?.suffix ?: "mp4",
                    approximateSizeBytes = 0L,
                    isAudioOnly = false,
                    directUrl = s.content
                )
            }

        // Audio.
        info.audioStreams
            .filter { it.isUrl && it.content.isNotBlank() && !it.content.isManifestUrl() }
            .sortedByDescending { it.averageBitrate }
            .take(6)
            .forEach { s ->
                val track = s.audioTrackName?.takeIf { it.isNotBlank() }
                formats += MediaFormatOption(
                    formatId = "npe_audio_${s.id.ifBlank { formats.size }}",
                    formatNote = buildString {
                        append("Audio")
                        if (s.averageBitrate > 0) append(" · ${s.averageBitrate}kbps")
                        if (track != null) append(" · $track")
                    },
                    resolution = "Audio",
                    ext = s.format?.suffix ?: "m4a",
                    approximateSizeBytes = 0L,
                    isAudioOnly = true,
                    directUrl = s.content
                )
            }

        // Manifests: solo para vista previa con Media3 (la descarga
        // segmentada HLS/DASH es trabajo futuro; no se promete).
        info.hlsUrl?.takeIf { it.isNotBlank() }?.let { hls ->
            formats += MediaFormatOption(
                formatId = "npe_hls",
                formatNote = "Stream HLS (solo vista previa)",
                resolution = "Original",
                ext = "m3u8",
                approximateSizeBytes = 0L,
                isAudioOnly = false,
                directUrl = hls
            )
        }
        info.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { dash ->
            formats += MediaFormatOption(
                formatId = "npe_dash",
                formatNote = "Stream DASH (solo vista previa)",
                resolution = "Original",
                ext = "mpd",
                approximateSizeBytes = 0L,
                isAudioOnly = false,
                directUrl = dash
            )
        }

        return formats
    }

    private fun String.isManifestUrl(): Boolean {
        val lower = lowercase()
        return lower.contains(".m3u8") || lower.contains(".mpd") ||
            lower.contains("dash+xml")
    }

    /** Adaptador de OkHttp al transporte que espera NewPipeExtractor. */
    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val data = request.dataToSend()
            val contentType = request.headers()["Content-Type"]?.firstOrNull()
            val body = data?.toRequestBody(contentType?.toMediaTypeOrNull())

            val builder = OkHttpRequest.Builder()
                .url(request.url())
                .method(request.httpMethod(), body)
            request.headers().forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }
            if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                builder.header("User-Agent", USER_AGENT)
            }

            client.newCall(builder.build()).execute().use { response ->
                val bodyString =
                    if (response.code == 204 || response.code == 304) {
                        ""
                    } else {
                        response.body?.string() ?: ""
                    }
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    bodyString,
                    response.request.url.toString()
                )
            }
        }

        companion object {
            private const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
    }
}
