package com.example.ui.downloads.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTaskEntity
import com.example.downloader.DownloadProgressInfo
import com.example.ui.theme.MeowAmber
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowCardBg
import com.example.ui.theme.MeowCardBorder
import com.example.ui.theme.MeowCyan
import com.example.ui.theme.MeowEmerald
import com.example.ui.theme.MeowGraphite
import com.example.ui.theme.MeowRuby
import com.example.ui.theme.MeowTextMuted
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary

@Composable
fun DownloadItemCard(
    task: DownloadTaskEntity,
    liveProgress: DownloadProgressInfo?,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (DownloadTaskEntity) -> Unit,
    onPlay: ((DownloadTaskEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val status = liveProgress?.status ?: task.downloadStatus
    val downloaded = liveProgress?.downloadedBytes ?: task.downloadedBytes
    val total = if (liveProgress?.totalBytes != null && liveProgress.totalBytes > 0) liveProgress.totalBytes else task.totalBytes
    val speedFormatted = liveProgress?.speedFormatted ?: if (task.speedBytesPerSec > 0) String.format("%.1f KB/s", task.speedBytesPerSec / 1024.0) else ""
    val etaFormatted = liveProgress?.etaFormatted ?: ""
    val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else task.progressPercent

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeowCardBg),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MeowCardBorder, RoundedCornerShape(14.dp))
            .testTag("download_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Thumbnail or Type Icon Box
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeowBlack)
                        .border(1.dp, MeowCardBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.thumbnailUrl != null) {
                        AsyncImage(
                            model = task.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Icon(
                            imageVector = if (task.mimeType.contains("audio")) Icons.Default.Audiotrack else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (task.mimeType.contains("audio")) MeowCyan else MeowAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Meta Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = MeowTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quality badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MeowGraphite)
                                .border(0.5.dp, MeowCardBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.quality,
                                color = MeowAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Status text
                        val statusText = when (status) {
                            DownloadStatus.DOWNLOADING -> "Descargando"
                            DownloadStatus.PAUSED -> "Pausado"
                            DownloadStatus.COMPLETED -> "Completado"
                            DownloadStatus.FAILED -> "Error"
                            DownloadStatus.QUEUED -> "En espera"
                            DownloadStatus.CANCELLED -> "Cancelado"
                        }
                        val statusColor = when (status) {
                            DownloadStatus.DOWNLOADING -> MeowAmber
                            DownloadStatus.COMPLETED -> MeowEmerald
                            DownloadStatus.FAILED -> MeowRuby
                            DownloadStatus.PAUSED -> MeowCyan
                            else -> MeowTextSecondary
                        }

                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Control Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (status) {
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(
                                onClick = { onPause(task.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pausar descarga",
                                    tint = MeowAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(
                                onClick = { onResume(task.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reanudar descarga",
                                    tint = MeowEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            if (onPlay != null) {
                                IconButton(
                                    onClick = { onPlay(task) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Reproducir contenido",
                                        tint = MeowEmerald,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MeowEmerald.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completado",
                                        tint = MeowEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        DownloadStatus.FAILED -> {
                            IconButton(
                                onClick = { onResume(task.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Reintentar",
                                    tint = MeowRuby,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        else -> Unit
                    }

                    IconButton(
                        onClick = {
                            if (status == DownloadStatus.COMPLETED) {
                                onDelete(task)
                            } else {
                                onCancel(task.id)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (status == DownloadStatus.COMPLETED) Icons.Default.Delete else Icons.Default.Close,
                            contentDescription = "Eliminar de la lista",
                            tint = MeowTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Progress bar and details (for active/queued/paused/failed)
            if (status != DownloadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { if (total > 0) percent / 100f else 0f },
                    color = when (status) {
                        DownloadStatus.FAILED -> MeowRuby
                        DownloadStatus.PAUSED -> MeowCyan
                        else -> MeowAmber
                    },
                    trackColor = MeowBlack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val downloadedMb = String.format("%.1f", downloaded / (1024.0 * 1024.0))
                    val totalMb = if (total > 0) String.format("%.1f MB", total / (1024.0 * 1024.0)) else "Desconocido"

                    Text(
                        text = "$downloadedMb MB / $totalMb ($percent%)",
                        color = MeowTextSecondary,
                        fontSize = 11.sp
                    )

                    if (status == DownloadStatus.DOWNLOADING && speedFormatted.isNotEmpty()) {
                        Text(
                            text = if (etaFormatted.isNotEmpty()) "$speedFormatted • ETA: $etaFormatted" else speedFormatted,
                            color = MeowEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (status == DownloadStatus.FAILED && task.errorMessage != null) {
                        Text(
                            text = task.errorMessage.take(25),
                            color = MeowRuby,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                // Completed Task Info
                Spacer(modifier = Modifier.height(6.dp))
                val totalMb = if (task.totalBytes > 0) String.format("%.1f MB", task.totalBytes / (1024.0 * 1024.0)) else ""
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (totalMb.isNotEmpty()) "$totalMb • Guardado en Descargas" else "Guardado en Descargas/MeowSave",
                        color = MeowTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
