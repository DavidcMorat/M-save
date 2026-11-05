package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val fileName: String,
    val filePath: String? = null,
    val mimeType: String = "video/mp4",
    val quality: String = "1080p",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: String = DownloadStatus.QUEUED.name,
    val errorMessage: String? = null,
    val sourcePageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val downloadStatus: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED)
}
