package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt DESC")
    fun getActiveTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET downloadedBytes = :downloaded, totalBytes = :total, speedBytesPerSec = :speed, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, downloaded: Long, total: Long, speed: Long, status: String)

    @Query("UPDATE download_tasks SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE download_tasks SET status = 'COMPLETED', completedAt = :completedAt, filePath = :filePath, speedBytesPerSec = 0, downloadedBytes = totalBytes WHERE id = :id")
    suspend fun markCompleted(id: String, filePath: String?, completedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTask(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
