package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.MeowDatabase
import com.example.downloader.DownloaderEngine
import com.example.engine.YtDlpEngine
import com.example.ui.browser.BrowserViewModel
import com.example.ui.browser.MeowBrowserScreen
import com.example.ui.components.MeowBottomBar
import com.example.ui.components.MeowScreen
import com.example.ui.downloads.DownloadsScreen
import com.example.ui.downloads.DownloadsViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.MeowBlack
import com.example.ui.theme.MeowSaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MeowDatabase.getDatabase(applicationContext)
        val downloadDao = database.downloadDao()
        val browserDao = database.browserDao()
        val ytDlpEngine = YtDlpEngine(applicationContext)
        val downloaderEngine = DownloaderEngine(applicationContext, downloadDao)

        setContent {
            MeowSaveTheme {
                MeowSaveApp(
                    database = database,
                    ytDlpEngine = ytDlpEngine,
                    downloaderEngine = downloaderEngine
                )
            }
        }
    }
}

@Composable
fun MeowSaveApp(
    database: MeowDatabase,
    ytDlpEngine: YtDlpEngine,
    downloaderEngine: DownloaderEngine
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    var currentScreen by remember { mutableStateOf(MeowScreen.BROWSER) }

    // ViewModels with constructor injection
    val browserViewModel: BrowserViewModel = viewModel {
        BrowserViewModel(
            context = appContext,
            browserDao = database.browserDao(),
            ytDlpEngine = ytDlpEngine,
            downloaderEngine = downloaderEngine
        )
    }

    val downloadsViewModel: DownloadsViewModel = viewModel {
        DownloadsViewModel(
            downloadDao = database.downloadDao(),
            downloaderEngine = downloaderEngine
        )
    }

    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            context = appContext,
            browserDao = database.browserDao(),
            ytDlpEngine = ytDlpEngine
        )
    }

    // Request notification permission for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Scoped storage does not exist before Android 10; saving to
            // Downloads/MeowSave needs the legacy write permission.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    // Back handler to navigate back to BROWSER from secondary screens
    BackHandler(enabled = currentScreen != MeowScreen.BROWSER) {
        currentScreen = MeowScreen.BROWSER
    }

    val activeTasks by downloadsViewModel.activeTasks.collectAsState()

    Scaffold(
        bottomBar = {
            MeowBottomBar(
                currentScreen = currentScreen,
                activeDownloadsCount = activeTasks.size,
                onScreenSelected = { currentScreen = it }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // The browser is always composed (even behind Downloads/Settings).
            // Destroying and recreating the WebView on every tab switch was the
            // main source of session loss, reloads and instability.
            MeowBrowserScreen(
                viewModel = browserViewModel,
                isActive = currentScreen == MeowScreen.BROWSER
            )

            if (currentScreen == MeowScreen.DOWNLOADS) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MeowBlack)
                ) {
                    DownloadsScreen(viewModel = downloadsViewModel)
                }
            }

            if (currentScreen == MeowScreen.SETTINGS) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MeowBlack)
                ) {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}
