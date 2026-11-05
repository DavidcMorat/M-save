package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "meowsave_downloads_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND"
        const val ACTION_UPDATE_PROGRESS = "ACTION_UPDATE_PROGRESS"
        const val ACTION_STOP_FOREGROUND = "ACTION_STOP_FOREGROUND"

        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"
        const val EXTRA_SPEED = "EXTRA_SPEED"

        fun startService(context: Context, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_FOREGROUND
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, title: String, progress: Int, speed: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_SPEED, speed)
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP_FOREGROUND
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Descargando contenido"
                startAsForeground(title, 0, "-- KB/s")
            }
            ACTION_UPDATE_PROGRESS -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Descargando contenido"
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val speed = intent.getStringExtra(EXTRA_SPEED) ?: ""
                updateNotification(title, progress, speed)
            }
            ACTION_STOP_FOREGROUND -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(title: String, progress: Int, speed: String) {
        val notification = buildNotification(title, progress, speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, progress: Int, speed: String) {
        val notification = buildNotification(title, progress, speed)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, progress: Int, speed: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (speed.isNotEmpty()) "$progress% • $speed" else "$progress%"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeowSave: $title")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
