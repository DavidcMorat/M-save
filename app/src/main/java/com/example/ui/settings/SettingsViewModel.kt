package com.example.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BrowserDao
import com.example.engine.UpdateState
import com.example.engine.YtDlpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val context: Context,
    private val browserDao: BrowserDao,
    private val ytDlpEngine: YtDlpEngine
) : ViewModel() {

    private val prefs = context.getSharedPreferences("meowsave_settings_prefs", Context.MODE_PRIVATE)

    private val _defaultQuality = MutableStateFlow(prefs.getString("pref_default_quality", "1080p") ?: "1080p")
    val defaultQuality = _defaultQuality.asStateFlow()

    private val _maxConcurrentDownloads = MutableStateFlow(prefs.getInt("pref_max_downloads", 2))
    val maxConcurrentDownloads = _maxConcurrentDownloads.asStateFlow()

    private val _preferredAudioFormat = MutableStateFlow(prefs.getString("pref_audio_format", "MP3") ?: "MP3")
    val preferredAudioFormat = _preferredAudioFormat.asStateFlow()

    private val _searchEngine = MutableStateFlow(
        prefs.getString("pref_search_engine", "duckduckgo") ?: "duckduckgo"
    )
    val searchEngine = _searchEngine.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private val _cacheSizeMb = MutableStateFlow(calculateCacheSize())
    val cacheSizeMb = _cacheSizeMb.asStateFlow()

    val installedYtDlpVersion = ytDlpEngine.updater.getInstalledVersion()

    fun setDefaultQuality(quality: String) {
        _defaultQuality.value = quality
        prefs.edit().putString("pref_default_quality", quality).apply()
    }

    fun setMaxConcurrentDownloads(count: Int) {
        _maxConcurrentDownloads.value = count
        prefs.edit().putInt("pref_max_downloads", count).apply()
    }

    fun setPreferredAudioFormat(format: String) {
        _preferredAudioFormat.value = format
        prefs.edit().putString("pref_audio_format", format).apply()
    }

    fun setSearchEngine(engine: String) {
        _searchEngine.value = engine
        prefs.edit().putString("pref_search_engine", engine).apply()
    }

    fun checkForYtDlpUpdates() {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            val state = ytDlpEngine.updater.checkForUpdates()
            _updateState.value = state
        }
    }

    fun performYtDlpUpdate(downloadUrl: String, newVersion: String) {
        _updateState.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            val result = ytDlpEngine.updater.downloadAndUpdate(downloadUrl, newVersion) { percent ->
                _updateState.value = UpdateState.Downloading(percent)
            }
            result.onSuccess {
                _updateState.value = UpdateState.Success(newVersion)
            }.onFailure { err ->
                _updateState.value = UpdateState.Error(err.localizedMessage ?: "Error al actualizar yt-dlp")
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
                _cacheSizeMb.value = calculateCacheSize()
            } catch (_: Exception) {}
        }
    }

    fun clearBrowserHistory() {
        viewModelScope.launch {
            browserDao.clearHistory()
        }
    }

    private fun calculateCacheSize(): String {
        return try {
            val size = getFolderSize(context.cacheDir)
            String.format("%.1f MB", size / (1024.0 * 1024.0))
        } catch (_: Exception) {
            "0.0 MB"
        }
    }

    private fun getFolderSize(file: File): Long {
        var size = 0L
        file.listFiles()?.forEach { f ->
            size += if (f.isDirectory) getFolderSize(f) else f.length()
        }
        return size
    }
}
