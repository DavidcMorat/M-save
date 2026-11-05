package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.engine.model.MediaExtractionResult
import com.example.engine.model.MediaFormatOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YtDlpEngine(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val updater = YtDlpUpdater(context)

    // Motor real: NewPipeExtractor (YouTube, SoundCloud, PeerTube, MediaCCC, Bandcamp).
    private val newPipeEngine = NewPipeEngine()

    suspend fun extractMedia(url: String): Result<MediaExtractionResult> = withContext(Dispatchers.IO) {
        try {
            // First check if the URL is a direct media stream or playlist
            if (isDirectMediaUrl(url)) {
                return@withContext extractDirectStream(url)
            }

            // Real extraction: NewPipeExtractor resolves the actual stream
            // URLs (YouTube, SoundCloud, PeerTube, MediaCCC, Bandcamp).
            if (newPipeEngine.supports(url)) {
                return@withContext newPipeEngine.extract(url)
            }

            // Sites NewPipe does not cover (TikTok, Instagram, ...): honest
            // page probing; only real <video>/<source> URLs are offered.
            val pageRequest = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val pageResponse = client.newCall(pageRequest).execute()
            val html = pageResponse.body?.string() ?: ""

            val title = extractTitle(html, url)
            val thumbnail = extractThumbnail(html)
            val mediaStreams = extractEmbeddedStreams(html, url)

            val formats = mutableListOf<MediaFormatOption>()

            if (mediaStreams.isNotEmpty()) {
                mediaStreams.forEachIndexed { index, streamUrl ->
                    val head = probeStreamHeaders(streamUrl)
                    // One entry per real URL found in the page. No invented
                    // "720p"/"audio" variants: the previous ones downloaded the
                    // same bytes again with the wrong extension.
                    formats.add(
                        MediaFormatOption(
                            formatId = "fmt_stream_$index",
                            formatNote = "Enlace detectado en la página",
                            resolution = if (head.isAudio) "Audio" else "Original",
                            ext = head.extension,
                            approximateSizeBytes = head.contentLength,
                            isAudioOnly = head.isAudio,
                            directUrl = streamUrl
                        )
                    )
                }
            } else {
                // No <video>/og:video found. Do NOT invent qualities pointing to
                // the HTML page: that only produced corrupt downloads. The real
                // extractor (Fase C) is still missing for JS-heavy sites.
                return@withContext Result.failure(
                    IllegalStateException(
                        "No se encontró ningún medio reproducible en esta página"
                    )
                )
            }

            Result.success(
                MediaExtractionResult(
                    title = title,
                    webpageUrl = url,
                    thumbnail = thumbnail,
                    author = Uri.parse(url).host,
                    formats = formats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".webm") ||
                lower.contains(".mkv") || lower.contains(".mp3") ||
                lower.contains(".m4a") || lower.contains(".m3u8") ||
                lower.contains(".mpd") || lower.contains(".flv") ||
                lower.contains(".ogg")
    }

    private suspend fun extractDirectStream(url: String): Result<MediaExtractionResult> = withContext(Dispatchers.IO) {
        val headers = probeStreamHeaders(url)
        val uri = Uri.parse(url)
        val fileName = uri.lastPathSegment?.substringBefore("?") ?: "media_${System.currentTimeMillis()}.${headers.extension}"

        val formats = listOf(
            MediaFormatOption(
                formatId = "direct_original",
                formatNote = "Enlace original",
                resolution = if (headers.isAudio) "Audio" else "1080p",
                ext = headers.extension,
                approximateSizeBytes = headers.contentLength,
                isAudioOnly = headers.isAudio,
                directUrl = url
            )
            // No fake "extract audio" option: without a real muxer it would only
            // download the video bytes again with an .mp3 extension.
        )

        Result.success(
            MediaExtractionResult(
                title = URLDecoder.decode(fileName, "UTF-8"),
                webpageUrl = url,
                thumbnail = null,
                author = uri.host,
                formats = formats
            )
        )
    }

    private data class StreamHeaderInfo(
        val contentLength: Long,
        val contentType: String,
        val extension: String,
        val isAudio: Boolean
    )

    private fun probeStreamHeaders(url: String): StreamHeaderInfo {
        try {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()
            val resp = client.newCall(req).execute()
            val len = resp.header("Content-Length")?.toLongOrNull() ?: 0L
            val type = resp.header("Content-Type")?.lowercase() ?: ""
            val isAudio = type.startsWith("audio/")
            val ext = when {
                type.contains("mpegurl") || url.contains(".m3u8") -> "m3u8"
                type.contains("dash") || url.contains(".mpd") -> "mpd"
                type.contains("mp4") -> "mp4"
                type.contains("webm") -> "webm"
                type.contains("mpeg") || type.contains("mp3") -> "mp3"
                type.contains("m4a") || type.contains("aac") -> "m4a"
                url.contains(".mp4") -> "mp4"
                url.contains(".mp3") -> "mp3"
                url.contains(".webm") -> "webm"
                else -> if (isAudio) "mp3" else "mp4"
            }
            return StreamHeaderInfo(len, type, ext, isAudio)
        } catch (_: Exception) {
            // HEAD is often rejected (403/405). Fall back to the URL itself
            // instead of assuming MP4: guessing MP4 for a playlist is what
            // produced corrupt ".mp4" files in the previous build.
            val lower = url.lowercase()
            val isAudio = lower.contains(".mp3") || lower.contains(".m4a")
            val ext = when {
                lower.contains(".m3u8") -> "m3u8"
                lower.contains(".mpd") -> "mpd"
                lower.contains(".webm") -> "webm"
                lower.contains(".m4a") -> "m4a"
                lower.contains(".mp3") -> "mp3"
                isAudio -> "mp3"
                else -> "mp4"
            }
            return StreamHeaderInfo(0L, if (isAudio) "audio/mpeg" else "video/mp4", ext, isAudio)
        }
    }

    private fun extractTitle(html: String, fallbackUrl: String): String {
        // Look for <meta property="og:title" content="...">
        val ogMatcher = Pattern.compile("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        if (ogMatcher.find()) {
            return ogMatcher.group(1)?.trim() ?: fallbackUrl
        }
        // Look for <title>...</title>
        val titleMatcher = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
        if (titleMatcher.find()) {
            return titleMatcher.group(1)?.trim() ?: fallbackUrl
        }
        return Uri.parse(fallbackUrl).lastPathSegment ?: fallbackUrl
    }

    private fun extractThumbnail(html: String): String? {
        val ogThumb = Pattern.compile("<meta\\s+property=[\"']og:image[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        if (ogThumb.find()) {
            return ogThumb.group(1)?.trim()
        }
        val twitterThumb = Pattern.compile("<meta\\s+name=[\"']twitter:image[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        if (twitterThumb.find()) {
            return twitterThumb.group(1)?.trim()
        }
        return null
    }

    private fun extractEmbeddedStreams(html: String, baseUrl: String): List<String> {
        val streams = mutableListOf<String>()
        // Check og:video
        val ogVideo = Pattern.compile("<meta\\s+property=[\"']og:video(:secure_url)?[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        while (ogVideo.find()) {
            ogVideo.group(2)?.let { streams.add(it) }
        }

        // Check <source src="..." type="video/...">
        val sourceMatcher = Pattern.compile("<source[^>]+src=[\"']([^\"']+\\.(mp4|webm|m3u8|mp3)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        while (sourceMatcher.find()) {
            sourceMatcher.group(1)?.let { streams.add(it) }
        }

        // Check <video src="...">
        val videoMatcher = Pattern.compile("<video[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
        while (videoMatcher.find()) {
            videoMatcher.group(1)?.let { streams.add(it) }
        }

        return streams.distinct()
    }
}
