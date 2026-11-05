package com.example.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.browser.components.BrowserAddressBar
import com.example.ui.browser.components.FormatPickerBottomSheet
import com.example.ui.browser.components.MediaDetectionFab
import com.example.ui.browser.components.MeowHomeScreen
import com.example.ui.components.MeowNativePlayerDialog
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary

private class WebViewHolder {
    var webView: WebView? = null
    var customView: View? = null
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
}

/** Pauses any <video>/<audio> playing inside the page. */
private const val PAUSE_PAGE_MEDIA_JS =
    "(function(){try{document.querySelectorAll('video,audio').forEach(" +
        "function(m){try{m.pause()}catch(e){}})}catch(e){}})();"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MeowBrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val isHomeMode by viewModel.isHomeMode.collectAsState()
    val currentUrl by viewModel.currentUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val isQualitySheetOpen by viewModel.isQualitySheetOpen.collectAsState()
    val extractionResult by viewModel.extractionResult.collectAsState()
    val isResolvingMedia by viewModel.isResolvingMedia.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val detectedStreams by viewModel.mediaSniffer.detectedStreams.collectAsState()
    val previewMediaUrl by viewModel.previewMediaUrl.collectAsState()
    val previewMediaTitle by viewModel.previewMediaTitle.collectAsState()
    val previewMediaReferer by viewModel.previewMediaReferer.collectAsState()
    val pageError by viewModel.pageError.collectAsState()

    // The WebView survives tab changes and home/browser switches. Compose only
    // detaches it from the hierarchy, so session, scroll and history are kept.
    val holder = remember { WebViewHolder() }
    var webViewGeneration by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun navigate(url: String) {
        holder.webView?.loadUrl(url)
    }

    fun hideCustomView() {
        val activity = context as? Activity ?: return
        val decor = activity.window.decorView as? FrameLayout ?: return
        holder.customView?.let { custom ->
            if (custom.parent === decor) decor.removeView(custom)
        }
        holder.customView = null
        // Clear the reference before notifying the WebView: onCustomViewHidden()
        // can re-enter onHideCustomView() and would otherwise recurse.
        val callback = holder.customViewCallback
        holder.customViewCallback = null
        callback?.onCustomViewHidden()
        WindowCompat.getInsetsController(activity.window, decor)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback?) {
        val activity = context as? Activity ?: return
        val decor = activity.window.decorView as? FrameLayout ?: return
        if (holder.customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        holder.customView = view
        holder.customViewCallback = callback
        decor.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        WindowCompat.getInsetsController(activity.window, decor)
            .hide(WindowInsetsCompat.Type.systemBars())
    }

    // Android system back press
    BackHandler(enabled = isActive && !isHomeMode) {
        val webView = holder.webView
        if (holder.customView != null) {
            hideCustomView()
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            viewModel.goHome()
        }
    }

    // Pause the WebView when another tab is on top or when we are at home,
    // instead of destroying it. This is what caused the session/scroll loss.
    LaunchedEffect(isActive, isHomeMode) {
        val webView = holder.webView ?: return@LaunchedEffect
        if (isActive && !isHomeMode) {
            webView.onResume()
        } else {
            webView.onPause()
            // onPause() does not stop HTML5 media playback.
            webView.evaluateJavascript(PAUSE_PAGE_MEDIA_JS, null)
        }
    }

    // Do not keep the page audio playing behind the native player.
    LaunchedEffect(previewMediaUrl) {
        if (previewMediaUrl != null) {
            holder.webView?.evaluateJavascript(PAUSE_PAGE_MEDIA_JS, null)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive, isHomeMode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isActive && !isHomeMode) holder.webView?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    holder.webView?.onPause()
                    CookieManager.getInstance().flush()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MeowBlack)
    ) {
        if (isHomeMode) {
            // Clean Home Screen with centered pill bar and quick shortcuts
            MeowHomeScreen(
                isResolvingMedia = isResolvingMedia,
                statusMessage = statusMessage,
                onSubmit = { input ->
                    viewModel.handleInput(input) { urlToLoad -> navigate(urlToLoad) }
                },
                onShortcutClick = { shortcutUrl ->
                    viewModel.navigateToUrl(shortcutUrl) { urlToLoad -> navigate(urlToLoad) }
                }
            )
        } else {
            // Active Web Browser Screen
            Column(modifier = Modifier.fillMaxSize()) {
                BrowserAddressBar(
                    currentUrl = currentUrl,
                    isLoading = isLoading,
                    loadingProgress = loadingProgress,
                    canGoBack = canGoBack,
                    canGoForward = canGoForward,
                    isBookmarked = isBookmarked,
                    onNavigate = { newUrl ->
                        viewModel.handleInput(newUrl) { urlToLoad -> navigate(urlToLoad) }
                    },
                    onBack = { holder.webView?.goBack() },
                    onForward = { holder.webView?.goForward() },
                    onReload = { holder.webView?.reload() },
                    onHome = { viewModel.goHome() },
                    onToggleBookmark = { viewModel.toggleBookmark() }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    key(webViewGeneration) {
                        AndroidView(
                            factory = { viewContext ->
                                holder.webView?.also { existing ->
                                    // The cached WebView may still be attached to the
                                    // previous AndroidView container during a switch.
                                    (existing.parent as? ViewGroup)?.removeView(existing)
                                } ?: WebView(viewContext).apply {
                                    holder.webView = this
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.BLACK)

                                    // Full third-party & session support
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        loadsImagesAutomatically = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        setSupportMultipleWindows(false)
                                        javaScriptCanOpenWindowsAutomatically = false
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        builtInZoomControls = false
                                        displayZoomControls = false
                                        setSupportZoom(true)
                                        allowFileAccess = false
                                        setGeolocationEnabled(false)

                                        // Keep the real Chromium/WebView version but
                                        // remove the "; wv" marker. The previous code
                                        // replaced the whole UA with a made-up version,
                                        // which made the fingerprint inconsistent.
                                        val defaultUa = userAgentString
                                        if (defaultUa.contains("; wv")) {
                                            userAgentString = defaultUa.replace("; wv", "")
                                        }
                                    }

                                    addJavascriptInterface(
                                        viewModel.mediaSniffer.MeowJsBridge(),
                                        "MeowMediaBridge"
                                    )

                                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                        url?.let {
                                            viewModel.enqueueBrowserDownload(
                                                url = it,
                                                userAgent = userAgent,
                                                contentDisposition = contentDisposition,
                                                mimeType = mimeType
                                            )
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?
                                        ) {
                                            super.onPageStarted(view, url, favicon)
                                            url?.takeIf { !it.startsWith("about:") }?.let {
                                                viewModel.onPageStarted(it)
                                                viewModel.mediaSniffer.setCurrentPage(it)
                                            }
                                            viewModel.updateNavigationState(
                                                view?.canGoBack() == true,
                                                view?.canGoForward() == true
                                            )
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            url?.takeIf { !it.startsWith("about:") }?.let {
                                                viewModel.onPageFinished(it, view?.title)
                                                viewModel.mediaSniffer.setCurrentPage(it)
                                                CookieManager.getInstance().flush()
                                            }
                                            viewModel.updateNavigationState(
                                                view?.canGoBack() == true,
                                                view?.canGoForward() == true
                                            )
                                            view?.let { viewModel.mediaSniffer.injectDetectionScript(it) }
                                        }

                                        override fun onPageCommitVisible(
                                            view: WebView?,
                                            url: String?
                                        ) {
                                            super.onPageCommitVisible(view, url)
                                            url?.takeIf { !it.startsWith("about:") }?.let {
                                                viewModel.mediaSniffer.setCurrentPage(it)
                                            }
                                            view?.let { viewModel.mediaSniffer.injectDetectionScript(it) }
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            request?.let { viewModel.mediaSniffer.inspectRequest(it) }
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val uri = request?.url ?: return false
                                            return handleExternalScheme(viewContext, uri)
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: android.webkit.WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                viewModel.onPageError(
                                                    request.url?.toString(),
                                                    error?.description?.toString()
                                                )
                                            }
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?
                                        ): Boolean {
                                            // The renderer died: drop the dead instance and
                                            // force Compose to build a fresh WebView.
                                            holder.webView = null
                                            try {
                                                view?.destroy()
                                            } catch (_: Exception) {
                                            }
                                            webViewGeneration++
                                            return true
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(
                                            view: WebView?,
                                            newProgress: Int
                                        ) {
                                            super.onProgressChanged(view, newProgress)
                                            viewModel.onProgressChanged(newProgress)
                                            viewModel.updateNavigationState(
                                                view?.canGoBack() == true,
                                                view?.canGoForward() == true
                                            )
                                        }

                                        override fun onShowCustomView(
                                            view: View?,
                                            callback: WebChromeClient.CustomViewCallback?
                                        ) {
                                            if (view != null) showCustomView(view, callback)
                                        }

                                        override fun onHideCustomView() {
                                            hideCustomView()
                                        }
                                    }

                                    if (currentUrl.isNotEmpty()) {
                                        loadUrl(currentUrl)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("browser_webview")
                        )
                    }

                    // Friendly error page instead of an endless spinner
                    if (pageError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MeowBlack.copy(alpha = 0.94f))
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No se pudo cargar la página",
                                    color = MeowTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pageError ?: "",
                                    color = MeowTextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.clearPageError()
                                        holder.webView?.reload()
                                    }
                                ) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button with detected media count badge
        if (!isHomeMode) {
            MediaDetectionFab(
                detectedCount = detectedStreams.size,
                onClick = { viewModel.openQualitySheet() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp)
            )
        }

        // Quality & Format Picker Modal Bottom Sheet
        if (isQualitySheetOpen) {
            FormatPickerBottomSheet(
                extractionResult = extractionResult,
                sheetState = sheetState,
                onDismiss = { viewModel.closeQualitySheet() },
                onPreview = { format ->
                    viewModel.openPreview(
                        format.directUrl,
                        extractionResult?.title ?: "Reproducción"
                    )
                },
                onStartDownload = { format ->
                    viewModel.startDownload(format)
                }
            )
        }

        // Native Video/Audio Player Dialog (Media3, outside the WebView)
        if (previewMediaUrl != null) {
            MeowNativePlayerDialog(
                mediaUrl = previewMediaUrl!!,
                title = previewMediaTitle ?: "Reproductor MeowSave",
                referer = previewMediaReferer,
                userAgent = holder.webView?.settings?.userAgentString,
                onDismiss = { viewModel.closePreview() },
                onDownload = {
                    val url = previewMediaUrl!!
                    val title = previewMediaTitle ?: "Contenido MeowSave"
                    viewModel.closePreview()
                    viewModel.startDirectDownload(url, title)
                }
            )
        }
    }

    // The browser composable stays alive across tabs (see MainActivity), so this
    // only runs when the activity is being destroyed.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { hideCustomView() }
            CookieManager.getInstance().flush()
            holder.webView?.destroy()
            holder.webView = null
        }
    }
}

private fun handleExternalScheme(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme == "http" || scheme == "https") return false
    if (scheme == "about" || scheme == "javascript" || scheme == "blob") return true

    return try {
        val intent = if (scheme == "intent") {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        // Unknown scheme: swallow it instead of crashing the WebView.
        true
    }
}
