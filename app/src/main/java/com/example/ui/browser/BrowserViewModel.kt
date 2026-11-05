package com.example.ui.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserDao
import com.example.data.local.BrowserHistoryEntity
import com.example.downloader.DownloaderEngine
import com.example.engine.YtDlpEngine
import com.example.engine.model.MediaExtractionResult
import com.example.engine.model.MediaFormatOption
import com.example.ui.browser.sniffer.DetectedStream
import com.example.ui.browser.sniffer.MediaSniffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val context: Context,
    private val browserDao: BrowserDao,
    private val ytDlpEngine: YtDlpEngine,
    val downloaderEngine: DownloaderEngine
) : ViewModel() {

    val mediaSniffer = MediaSniffer()

    // Clean Home State: starts in home mode
    private val _isHomeMode = MutableStateFlow(true)
    val isHomeMode = _isHomeMode.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("MeowSave")
    val pageTitle = _pageTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress = _loadingProgress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward = _canGoForward.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked = _isBookmarked.asStateFlow()

    private val _extractionResult = MutableStateFlow<MediaExtractionResult?>(null)
    val extractionResult = _extractionResult.asStateFlow()

    private val _isQualitySheetOpen = MutableStateFlow(false)
    val isQualitySheetOpen = _isQualitySheetOpen.asStateFlow()

    private val _isResolvingMedia = MutableStateFlow(false)
    val isResolvingMedia = _isResolvingMedia.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _pageError = MutableStateFlow<String?>(null)
    val pageError = _pageError.asStateFlow()

    private val prefs = context.getSharedPreferences("meowsave_settings_prefs", Context.MODE_PRIVATE)

    init {
        // Collect detected streams from MediaSniffer during browsing
        viewModelScope.launch {
            mediaSniffer.detectedStreams.collect { streams ->
                if (streams.isNotEmpty()) {
                    resolveStreamsToOptions(streams)
                }
            }
        }
    }

    fun goHome() {
        _isHomeMode.value = true
        _extractionResult.value = null
        mediaSniffer.reset()
    }

    fun setHomeMode(home: Boolean) {
        _isHomeMode.value = home
    }

    fun handleInput(input: String, onNavigateToWeb: (String) -> Unit) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        when {
            // A direct file (.mp4, .m3u8, .mp3...) goes straight to the picker.
            isDirectMediaFile(trimmed) -> resolveDirectMediaUrl(trimmed)

            // Everything else is a page: open it in the WebView. Media sites
            // (YouTube, TikTok...) must be browsed, not "extracted": the fake
            // regex engine has nothing useful to offer and a page URL is not
            // a downloadable file.
            isWebUrl(trimmed) -> {
                val url = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                    "https://$trimmed"
                } else {
                    trimmed
                }
                navigateToUrl(url, onNavigateToWeb)
            }

            // It's a search term -> use the configured engine.
            // Google inside a WebView almost always triggers a CAPTCHA, so DuckDuckGo
            // Lite is the default (and works without a JS-heavy page).
            else -> navigateToUrl(buildSearchUrl(trimmed), onNavigateToWeb)
        }
    }

    private fun buildSearchUrl(query: String): String {
        val encoded = Uri.encode(query)
        return when (prefs.getString("pref_search_engine", "duckduckgo")) {
            "google" -> "https://www.google.com/search?q=$encoded"
            "bing" -> "https://www.bing.com/search?q=$encoded"
            else -> "https://lite.duckduckgo.com/lite/?q=$encoded"
        }
    }

    fun navigateToUrl(url: String, onNavigateToWeb: (String) -> Unit) {
        _isHomeMode.value = false
        _currentUrl.value = url
        mediaSniffer.reset()
        _extractionResult.value = null
        onNavigateToWeb(url)
    }

    fun resolveDirectMediaUrl(url: String) {
        val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        _isResolvingMedia.value = true
        _statusMessage.value = "Detectando opciones de descarga..."

        viewModelScope.launch {
            val result = ytDlpEngine.extractMedia(targetUrl)
            result.onSuccess { extraction ->
                _extractionResult.value = extraction
                _isQualitySheetOpen.value = true
                _statusMessage.value = null
            }.onFailure { error ->
                // Be honest: one "original" entry for the URL that was given,
                // never three invented qualities pointing at the same page.
                val lower = targetUrl.lowercase()
                val ext = when {
                    lower.contains(".m3u8") -> "m3u8"
                    lower.contains(".mpd") -> "mpd"
                    lower.contains(".mp3") || lower.contains(".m4a") -> "mp3"
                    lower.contains(".webm") -> "webm"
                    else -> "mp4"
                }
                val title = Uri.parse(targetUrl).lastPathSegment
                    ?.substringBefore("?")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Contenido Multimedia"

                _extractionResult.value = MediaExtractionResult(
                    title = title,
                    webpageUrl = targetUrl,
                    formats = listOf(
                        MediaFormatOption(
                            formatId = "direct_original",
                            formatNote = "Enlace original",
                            resolution = if (ext == "mp3") "Audio" else "Original",
                            ext = ext,
                            approximateSizeBytes = 0L,
                            isAudioOnly = ext == "mp3",
                            directUrl = targetUrl
                        )
                    )
                )
                _isQualitySheetOpen.value = true
                _statusMessage.value = error.localizedMessage ?: "No se pudo analizar el enlace"
            }
            _isResolvingMedia.value = false
        }
    }

    /** A real media file (as opposed to an HTML page that merely mentions it). */
    private fun isDirectMediaFile(url: String): Boolean {
        val lower = url.lowercase()
        return listOf(
            ".mp4", ".webm", ".mkv", ".mp3", ".m4a", ".m3u8", ".mpd", ".flv", ".ogg"
        ).any { lower.contains(it) }
    }

    private fun isWebUrl(input: String): Boolean {
        val lower = input.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") ||
                (lower.contains(".") && !lower.contains(" ") && lower.indexOf('.') < lower.length - 2)
    }

    fun onPageStarted(url: String) {
        _currentUrl.value = url
        _isLoading.value = true
        _loadingProgress.value = 10
        _pageError.value = null
        mediaSniffer.reset()
        _extractionResult.value = null
    }

    fun onPageFinished(url: String, title: String?) {
        _currentUrl.value = url
        _pageTitle.value = title ?: url
        _isLoading.value = false
        _loadingProgress.value = 100

        // Persist in history
        viewModelScope.launch {
            if (url.isNotEmpty() && !url.startsWith("about:")) {
                browserDao.insertHistory(
                    BrowserHistoryEntity(
                        url = url,
                        title = title ?: url
                    )
                )
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        _loadingProgress.value = progress
        _isLoading.value = progress in 1..99
    }

    fun updateNavigationState(canBack: Boolean, canForward: Boolean) {
        _canGoBack.value = canBack
        _canGoForward.value = canForward
    }

    fun onPageError(url: String?, description: String?) {
        _isLoading.value = false
        _loadingProgress.value = 100
        _pageError.value = description ?: "No se pudo cargar la página"
    }

    fun clearPageError() {
        _pageError.value = null
    }

    fun openQualitySheet() {
        if (_extractionResult.value != null) {
            _isQualitySheetOpen.value = true
        } else {
            // Do not invent fake qualities for a page: the sniffer fills the
            // list only with real streams it sees the page request.
            Toast.makeText(
                context,
                "Aún no se ha detectado ningún medio. Reproduce el video en la página " +
                    "y vuelve a tocar el botón.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun closeQualitySheet() {
        _isQualitySheetOpen.value = false
    }

    private fun resolveStreamsToOptions(streams: List<DetectedStream>) {
        val formats = streams.mapIndexed { index, s ->
            val mime = s.mimeType.lowercase()
            val isAudio = mime.contains("audio")
            val ext = when {
                mime.contains("mpegurl") -> "m3u8"
                mime.contains("dash") -> "mpd"
                mime.contains("audio/mp4") -> "m4a"
                isAudio -> "mp3"
                mime.contains("webm") -> "webm"
                else -> "mp4"
            }
            MediaFormatOption(
                formatId = "stream_$index",
                formatNote = when (ext) {
                    "m3u8" -> "Stream HLS"
                    "mpd" -> "Stream DASH"
                    else -> if (isAudio) "Audio detectado" else "Stream directo"
                },
                // The real resolution lives inside the manifest; do not pretend
                // every detected stream is 1080p.
                resolution = if (isAudio) "Audio" else "Original",
                ext = ext,
                approximateSizeBytes = 0L,
                isAudioOnly = isAudio,
                directUrl = s.url
            )
        }

        _extractionResult.value = MediaExtractionResult(
            title = _pageTitle.value,
            webpageUrl = _currentUrl.value,
            formats = formats
        )
    }

    private val _previewMediaUrl = MutableStateFlow<String?>(null)
    val previewMediaUrl = _previewMediaUrl.asStateFlow()

    private val _previewMediaTitle = MutableStateFlow<String?>(null)
    val previewMediaTitle = _previewMediaTitle.asStateFlow()

    private val _previewMediaReferer = MutableStateFlow<String?>(null)
    val previewMediaReferer = _previewMediaReferer.asStateFlow()

    fun openPreview(url: String, title: String) {
        _previewMediaUrl.value = url
        _previewMediaTitle.value = title
        // Many CDNs reject requests without the originating page as Referer.
        _previewMediaReferer.value = _currentUrl.value.takeIf { it.startsWith("http") }
    }

    fun closePreview() {
        _previewMediaUrl.value = null
        _previewMediaTitle.value = null
        _previewMediaReferer.value = null
    }

    /**
     * Fallback for regular file links clicked inside the WebView. Media streams
     * keep using the format picker; direct files are handed to Android's
     * DownloadManager, which already knows how to handle scoped storage,
     * notifications, retries and resuming.
     */
    fun enqueueBrowserDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(
                context,
                "Este reproductor usa un enlace interno (blob) que todavía no se puede " +
                    "descargar. Prueba la vista previa del reproductor.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (url.isBlank()) return
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return

        if (url.contains(".m3u8", ignoreCase = true) ||
            mimeType?.contains("mpegurl", ignoreCase = true) == true
        ) {
            // A playlist is not a video file: resolve it and open the format picker.
            resolveDirectMediaUrl(url)
            return
        }

        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                .ifBlank { "meowsave_${System.currentTimeMillis()}" }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Descarga iniciada desde MeowSave")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "MeowSave/$fileName"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            if (!userAgent.isNullOrBlank()) {
                request.addRequestHeader("User-Agent", userAgent)
            }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                request.addRequestHeader("Cookie", it)
            }
            val referer = _currentUrl.value
            if (referer.startsWith("http")) {
                request.addRequestHeader("Referer", referer)
                runCatching {
                    val uri = Uri.parse(referer)
                    if (uri.scheme != null && uri.host != null) {
                        request.addRequestHeader("Origin", "${uri.scheme}://${uri.host}")
                    }
                }
            }

            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                .enqueue(request)
            Toast.makeText(context, "Descarga iniciada: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No se pudo iniciar la descarga: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun startDownload(format: MediaFormatOption) {
        val title = _extractionResult.value?.title ?: _pageTitle.value
        val sourcePage = _extractionResult.value?.webpageUrl ?: _currentUrl.value

        // Fase C will add segmented HLS/DASH downloading and a real extractor.
        // Until then, fail loudly instead of saving a playlist as an .mp4.
        if (format.ext.equals("m3u8", ignoreCase = true) ||
            format.ext.equals("mpd", ignoreCase = true)
        ) {
            Toast.makeText(
                context,
                "La descarga de streams HLS/DASH llegará con el motor de descargas.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val isPageUrl = format.directUrl == sourcePage && !isDirectMediaFile(format.directUrl)
        if (isPageUrl) {
            Toast.makeText(
                context,
                "Este sitio requiere el motor de extracción real (Fase C). " +
                    "La vista previa ya está disponible.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // MIME real según contenedor: un WebM nombrado ".mp4" no abría.
        val mimeType = when {
            !format.isAudioOnly -> if (format.ext.equals("webm", ignoreCase = true)) "video/webm" else "video/mp4"
            else -> when (format.ext.lowercase()) {
                "m4a" -> "audio/mp4"
                "webm" -> "audio/webm"
                "opus" -> "audio/opus"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                else -> "audio/mpeg"
            }
        }

        downloaderEngine.enqueueDownload(
            url = format.directUrl,
            title = title,
            quality = format.resolution,
            mimeType = mimeType,
            extension = format.ext,
            thumbnailUrl = _extractionResult.value?.thumbnail,
            sourcePageUrl = sourcePage,
            scope = viewModelScope
        )

        _isQualitySheetOpen.value = false
    }

    fun startDirectDownload(url: String, title: String) {
        val lower = url.lowercase()
        val isAudio = lower.contains(".mp3") || lower.contains(".m4a")
        val ext = when {
            lower.contains(".m3u8") -> "m3u8"
            lower.contains(".mpd") -> "mpd"
            isAudio -> "mp3"
            else -> "mp4"
        }
        val format = MediaFormatOption(
            formatId = "direct_preview",
            formatNote = "Descarga Directa",
            resolution = if (isAudio) "Audio" else "Original",
            ext = ext,
            approximateSizeBytes = 0L,
            isAudioOnly = isAudio,
            directUrl = url
        )
        startDownload(format)
    }

    fun toggleBookmark() {
        val url = _currentUrl.value
        val title = _pageTitle.value
        val bookmarked = _isBookmarked.value

        viewModelScope.launch {
            if (!bookmarked) {
                browserDao.insertBookmark(BookmarkEntity(url = url, title = title))
                _isBookmarked.value = true
            } else {
                _isBookmarked.value = false
            }
        }
    }
}
