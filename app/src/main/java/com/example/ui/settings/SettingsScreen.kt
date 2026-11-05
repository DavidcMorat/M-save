package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.UpdateState
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
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val maxDownloads by viewModel.maxConcurrentDownloads.collectAsState()
    val audioFormat by viewModel.preferredAudioFormat.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val cacheSize by viewModel.cacheSizeMb.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MeowBlack)
            .statusBarsPadding()
    ) {
        item {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MeowAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Ajustes de MeowSave",
                    color = MeowTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Section 1: yt-dlp Engine & In-App OTA Updater
        item {
            SettingsSectionHeader(title = "Motor de Extracción yt-dlp")
            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Versión del Motor yt-dlp",
                                color = MeowTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Instalado: ${viewModel.installedYtDlpVersion}",
                                color = MeowTextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.checkForYtDlpUpdates() },
                            enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeowAmber,
                                contentColor = MeowBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("check_ytdlp_update_button")
                        ) {
                            if (updateState is UpdateState.Checking) {
                                CircularProgressIndicator(
                                    color = MeowBlack,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Comprobar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Update State Status feedback
                    when (val state = updateState) {
                        is UpdateState.UpdateAvailable -> {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MeowEmerald.copy(alpha = 0.12f))
                                    .border(1.dp, MeowEmerald, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "¡Nueva versión disponible: ${state.latestVersion}!",
                                        color = MeowEmerald,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Notas: ${state.releaseNotes}",
                                        color = MeowTextSecondary,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            viewModel.performYtDlpUpdate(state.downloadUrl, state.latestVersion)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MeowEmerald,
                                            contentColor = MeowBlack
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Actualizar Motor Ahora", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is UpdateState.Downloading -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column {
                                Text(
                                    text = "Descargando binario yt-dlp... ${state.progressPercent}%",
                                    color = MeowAmber,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    color = MeowAmber,
                                    trackColor = MeowBlack,
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            }
                        }
                        is UpdateState.Success -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Motor actualizado con éxito a versión ${state.newVersion}",
                                color = MeowEmerald,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        is UpdateState.UpToDate -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "El motor ya está al día en la versión más reciente.",
                                color = MeowTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        is UpdateState.Error -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.message,
                                color = MeowRuby,
                                fontSize = 12.sp
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }

        // Section 2: Browser & Search
        item {
            SettingsSectionHeader(title = "Navegador y Búsqueda")
            SettingsCard {
                Column {
                    Text(
                        text = "Motor de búsqueda",
                        color = MeowTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "DuckDuckGo funciona mejor dentro de la app. Google suele " +
                            "mostrar captchas al buscar desde un WebView.",
                        color = MeowTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "duckduckgo" to "DuckDuckGo",
                            "bing" to "Bing",
                            "google" to "Google"
                        ).forEach { (id, label) ->
                            QualityChip(
                                label = label,
                                isSelected = searchEngine == id,
                                onClick = { viewModel.setSearchEngine(id) }
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Download Preferences
        item {
            SettingsSectionHeader(title = "Preferencias de Descarga")
            SettingsCard {
                Column {
                    // Default Quality
                    Text(
                        text = "Calidad de Video Predeterminada",
                        color = MeowTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("1080p", "720p", "480p", "Audio").forEach { q ->
                            QualityChip(
                                label = q,
                                isSelected = defaultQuality == q,
                                onClick = { viewModel.setDefaultQuality(q) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Audio Format
                    Text(
                        text = "Formato de Audio Preferido",
                        color = MeowTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("MP3", "M4A", "OPUS").forEach { fmt ->
                            QualityChip(
                                label = fmt,
                                isSelected = audioFormat == fmt,
                                onClick = { viewModel.setPreferredAudioFormat(fmt) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Concurrent Downloads
                    Text(
                        text = "Descargas Simultáneas Máximas",
                        color = MeowTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1, 2, 3, 4).forEach { count ->
                            QualityChip(
                                label = "$count",
                                isSelected = maxDownloads == count,
                                onClick = { viewModel.setMaxConcurrentDownloads(count) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Storage Folder
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MeowAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Directorio de Almacenamiento",
                                color = MeowTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Almacenamiento Compartido: Descargas/MeowSave/",
                                color = MeowCyan,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Storage & Cleaning
        item {
            SettingsSectionHeader(title = "Almacenamiento y Limpieza")
            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Caché Temporal de Descargas",
                                color = MeowTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ocupado actualmente: $cacheSize",
                                color = MeowTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.clearCache() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeowGraphite,
                                contentColor = MeowAmber
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeowCardBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Limpiar", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Historial del Navegador",
                                color = MeowTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Páginas visitadas registradas",
                                color = MeowTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.clearBrowserHistory() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeowGraphite,
                                contentColor = MeowRuby
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeowCardBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Borrar", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section 5: App Info
        item {
            SettingsSectionHeader(title = "Información del Producto")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MeowAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MeowSave v1.0.0",
                            color = MeowTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Nativo en Kotlin & Jetpack Compose • APK Autónomo sin Termux ni servidores externos",
                            color = MeowTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = MeowAmber,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MeowCardBg)
            .border(1.dp, MeowCardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun QualityChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MeowAmber else MeowGraphite)
            .border(1.dp, if (isSelected) MeowAmber else MeowCardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) MeowBlack else MeowTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
