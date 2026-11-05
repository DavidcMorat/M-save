package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.theme.MeowAmber
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowCardBg
import com.example.ui.theme.MeowCardBorder
import com.example.ui.theme.MeowCyan
import com.example.ui.theme.MeowEmerald
import com.example.ui.theme.MeowRuby
import com.example.ui.theme.MeowTextMuted
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary
import okhttp3.OkHttpClient
import android.webkit.CookieManager
import java.util.concurrent.TimeUnit

private const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@OptIn(UnstableApi::class)
@Composable
fun MeowNativePlayerDialog(
    mediaUrl: String,
    title: String,
    referer: String? = null,
    userAgent: String? = null,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    val mediaItem = remember(mediaUrl) { buildMediaItem(mediaUrl) }

    // One player per dialog. Recreated only when the URL or a retry changes it.
    val exoPlayer = remember(mediaUrl, retryKey, referer, userAgent) {
        buildPlayer(context, referer, userAgent ?: PLAYER_USER_AGENT)
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        isBuffering = false
                        playbackError = null
                    }
                    Player.STATE_ENDED -> isBuffering = false
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = buildString {
                    append("No se pudo reproducir el contenido")
                    error.localizedMessage?.takeIf { it.isNotBlank() }?.let {
                        append(": ").append(it)
                    }
                }
            }
        }

        exoPlayer.addListener(listener)
        // Attach the media only after the listener is registered, so an
        // immediate error (403/404) is shown instead of an endless spinner.
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MeowBlack.copy(alpha = 0.96f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MeowCardBg)
                    .border(1.5.dp, MeowCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                // Header: Title and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MeowAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MeowAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = title,
                            color = MeowTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).testTag("close_player_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar reproductor",
                            tint = MeowTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Video Surface Container (Media3 / ExoPlayer, hardware accelerated)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeowBlack)
                        .border(1.dp, MeowCardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                player = exoPlayer
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                                setBackgroundColor(android.graphics.Color.BLACK)
                                keepScreenOn = true
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                        },
                        onRelease = { view ->
                            view.player = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Buffering Indicator
                    if (isBuffering && playbackError == null) {
                        CircularProgressIndicator(
                            color = MeowAmber,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                    }

                    // Playback Error Overlay
                    if (playbackError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MeowBlack.copy(alpha = 0.88f))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = playbackError ?: "",
                                    color = MeowRuby,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            playbackError = null
                                            isBuffering = true
                                            retryKey++
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MeowAmber
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MeowAmber
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reintentar", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            openExternally(context, mediaUrl)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MeowCyan
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MeowCyan
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Abrir con...", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reproductor Media3 • HLS/DASH • hardware",
                        color = MeowTextMuted,
                        fontSize = 11.sp
                    )

                    if (onDownload != null) {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeowEmerald,
                                contentColor = MeowBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("player_download_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Descargar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context, referer: String?, userAgent: String): ExoPlayer {
    val headers = buildMap {
        put("User-Agent", userAgent)
        put("Accept", "*/*")
        if (!referer.isNullOrBlank() && referer.startsWith("http")) {
            put("Referer", referer)
            runCatching {
                val uri = Uri.parse(referer)
                if (uri.scheme != null && uri.host != null) {
                    put("Origin", "${uri.scheme}://${uri.host}")
                }
            }
        }
    }

    // Cookies are resolved per-request because media CDNs use signed,
    // short-lived URLs and the session lives in the WebView cookie jar.
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            CookieManager.getInstance().getCookie(request.url.toString())
                ?.takeIf { it.isNotBlank() }
                ?.let { builder.header("Cookie", it) }
            chain.proceed(builder.build())
        }
        .build()

    val httpFactory = OkHttpDataSource.Factory(httpClient)
        .setDefaultRequestProperties(headers)

    // DefaultDataSource keeps content://, file:// and asset:// working for
    // already downloaded files while delegating HTTP(S) to OkHttp.
    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
}

private fun buildMediaItem(mediaUrl: String): MediaItem {
    val lower = mediaUrl.lowercase()
    return MediaItem.Builder()
        .setUri(mediaUrl)
        .apply {
            when {
                lower.contains(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                lower.contains(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }
        .build()
}

private fun openExternally(context: Context, mediaUrl: String) {
    try {
        val uri = Uri.parse(mediaUrl)
        val mime = when {
            mediaUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            mediaUrl.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
            mediaUrl.contains(".m4a", ignoreCase = true) -> "audio/mp4"
            else -> "video/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir con"))
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "No hay ninguna app compatible: ${e.localizedMessage}",
            Toast.LENGTH_LONG
        ).show()
    }
}
