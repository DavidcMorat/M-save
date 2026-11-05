package com.example.ui.downloads

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DownloadTaskEntity
import com.example.ui.components.MeowNativePlayerDialog
import com.example.ui.downloads.components.DownloadItemCard
import com.example.ui.theme.MeowAmber
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowEmerald
import com.example.ui.theme.MeowGraphite
import com.example.ui.theme.MeowTextMuted
import com.example.ui.theme.MeowTextPrimary
import com.example.ui.theme.MeowTextSecondary

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    modifier: Modifier = Modifier
) {
    val activeTasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val liveProgress by viewModel.liveProgress.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var playingTask by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    val tabs = listOf("Activas (${activeTasks.size})", "Completadas (${completedTasks.size})")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeowBlack)
            .statusBarsPadding()
    ) {
        // Screen Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = MeowAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Gestor de Descargas",
                    color = MeowTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (selectedTabIndex == 1 && completedTasks.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearCompleted() },
                    modifier = Modifier.testTag("clear_completed_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Limpiar completadas",
                        tint = MeowTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Custom M3 Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MeowGraphite,
            contentColor = MeowAmber,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MeowAmber,
                    height = 3.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTabIndex == index) MeowAmber else MeowTextSecondary
                        )
                    },
                    modifier = Modifier.testTag("download_tab_$index")
                )
            }
        }

        // Display Active or Completed Content
        val currentList = if (selectedTabIndex == 0) activeTasks else completedTasks

        if (currentList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedTabIndex == 0) Icons.Default.HourglassEmpty else Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = MeowTextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedTabIndex == 0) "No hay descargas activas en curso" else "No hay descargas completadas aún",
                        color = MeowTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Navega a cualquier video y pulsa el botón flotante insignia para descargarlo.",
                        color = MeowTextMuted,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentList, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        liveProgress = liveProgress[task.id],
                        onPause = { viewModel.pauseDownload(it) },
                        onResume = { viewModel.resumeDownload(it) },
                        onCancel = { viewModel.cancelDownload(it) },
                        onDelete = { viewModel.deleteTask(it) },
                        onPlay = { playingTask = it }
                    )
                }
            }
        }
    }

    // Native Media Player Dialog for Completed Items
    if (playingTask != null) {
        val targetPath = playingTask!!.filePath ?: playingTask!!.url
        MeowNativePlayerDialog(
            mediaUrl = targetPath,
            title = playingTask!!.title,
            onDismiss = { playingTask = null }
        )
    }
}
