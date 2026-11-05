package com.example.ui.browser.sniffer

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DetectedStream(
    val url: String,
    val mimeType: String,
    val title: String? = null,
    val sourcePageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class MediaSniffer {
    private val _detectedStreams = MutableStateFlow<List<DetectedStream>>(emptyList())
    val detectedStreams = _detectedStreams.asStateFlow()

    // shouldInterceptRequest runs on a WebView worker thread while the JS bridge
    // and page callbacks run on the UI thread.
    @Volatile
    private var currentPageUrl: String = ""

    fun reset() {
        _detectedStreams.value = emptyList()
    }

    fun setCurrentPage(url: String) {
        currentPageUrl = url
    }

    fun inspectRequest(request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        // Fast path: ignore non-http, short URLs, or obvious web assets
        if (!url.startsWith("http") || url.length < 10) return false

        val lower = url.lowercase()
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp") ||
            lower.endsWith(".svg") || lower.endsWith(".gif") || lower.endsWith(".css") ||
            lower.endsWith(".js") || lower.endsWith(".woff") || lower.endsWith(".woff2") ||
            lower.contains("google-analytics") || lower.contains("doubleclick")
        ) {
            return false
        }

        val isMedia = lower.contains(".mp4") || lower.contains(".webm") ||
                lower.contains(".m3u8") || lower.contains(".mp3") ||
                lower.contains(".m4a") || lower.contains(".mpd") ||
                lower.contains("/videoplayback") || lower.contains("mime=video") ||
                lower.contains("mime=audio")

        if (isMedia) {
            val mime = when {
                lower.contains(".mp4") || lower.contains("video/mp4") -> "video/mp4"
                lower.contains(".webm") || lower.contains("video/webm") -> "video/webm"
                lower.contains(".m3u8") || lower.contains("application/x-mpegurl") -> "application/x-mpegURL"
                lower.contains(".mpd") || lower.contains("dash+xml") -> "application/dash+xml"
                lower.contains(".mp3") || lower.contains("audio/mpeg") -> "audio/mpeg"
                lower.contains(".m4a") || lower.contains("audio/mp4") -> "audio/mp4"
                else -> "video/*"
            }
            registerStream(url, mime, null, currentPageUrl)
            return true
        }
        return false
    }

    fun registerStream(url: String, mimeType: String, title: String?, sourcePage: String? = null) {
        _detectedStreams.update { current ->
            if (current.any { it.url == url }) {
                current
            } else {
                current + DetectedStream(
                    url = url,
                    mimeType = mimeType,
                    title = title,
                    sourcePageUrl = sourcePage ?: currentPageUrl
                )
            }
        }
    }

    fun injectDetectionScript(webView: WebView) {
        val js = """
            (function() {
                try {
                    // Idempotent: onPageFinished/onPageCommitVisible can both fire.
                    if (window.__meowSnifferInstalled) { scan(); return; }
                    window.__meowSnifferInstalled = true;

                    function report(el) {
                        var sources = [];
                        if (el.currentSrc) sources.push(el.currentSrc);
                        if (el.src) sources.push(el.src);
                        if (el.querySelectorAll) {
                            var nodes = el.querySelectorAll('source');
                            for (var i = 0; i < nodes.length; i++) {
                                if (nodes[i].src) sources.push(nodes[i].src);
                            }
                        }
                        for (var j = 0; j < sources.length; j++) {
                            var s = sources[j];
                            if (!s || s.length < 6 || s.indexOf('blob:') === 0 || s.indexOf('data:') === 0) continue;
                            var mime = el.tagName === 'AUDIO' ? 'audio/mpeg' :
                                (s.indexOf('.m3u8') >= 0 ? 'application/x-mpegURL' :
                                (s.indexOf('.mpd') >= 0 ? 'application/dash+xml' : 'video/mp4'));
                            window.MeowMediaBridge.onMediaDetected(s, mime, document.title || '');
                        }
                    }

                    function scan() {
                        var media = document.querySelectorAll('video, audio');
                        for (var i = 0; i < media.length; i++) report(media[i]);
                    }

                    // Passive event-driven inspection (no polling)
                    var events = ['play', 'loadeddata', 'loadedmetadata', 'canplay', 'playing'];
                    for (var e = 0; e < events.length; e++) {
                        document.addEventListener(events[e], function(ev) {
                            if (ev.target) report(ev.target);
                        }, true);
                    }

                    // Detect players that create media nodes after the initial scan
                    if (window.MutationObserver) {
                        var scheduled = false;
                        new MutationObserver(function() {
                            if (scheduled) return;
                            scheduled = true;
                            setTimeout(function() { scheduled = false; scan(); }, 700);
                        }).observe(document.documentElement, { childList: true, subtree: true });
                    }

                    scan();
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    inner class MeowJsBridge {
        @JavascriptInterface
        fun onMediaDetected(url: String, mimeType: String, title: String?) {
            registerStream(url, mimeType, title, currentPageUrl)
        }
    }
}
