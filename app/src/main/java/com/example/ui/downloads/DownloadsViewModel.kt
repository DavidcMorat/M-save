package com.example.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadTaskEntity
import com.example.downloader.DownloaderEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadDao: DownloadDao,
    private val downloaderEngine: DownloaderEngine
) : ViewModel() {

    val allTasks = downloadDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks = downloadDao.getActiveTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks = downloadDao.getCompletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveProgress = downloaderEngine.activeProgress

    fun pauseDownload(taskId: String) {
        downloaderEngine.pauseDownload(taskId, viewModelScope)
    }

    fun resumeDownload(taskId: String) {
        downloaderEngine.resumeDownload(taskId, viewModelScope)
    }

    fun cancelDownload(taskId: String) {
        downloaderEngine.cancelDownload(taskId, viewModelScope)
    }

    fun deleteTask(task: DownloadTaskEntity) {
        viewModelScope.launch {
            downloadDao.deleteTask(task)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            downloadDao.clearCompleted()
        }
    }
}
