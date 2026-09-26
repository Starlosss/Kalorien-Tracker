package com.kalorientracker.app.data.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kalorientracker.app.MainActivity
import com.kalorientracker.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the model download running while the app is in the background and shows its progress in a
 * notification. Without a foreground service Android stops a long download as soon as the user
 * leaves the app – which is exactly what happens during a 2.6 GB transfer.
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject lateinit var models: ModelManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: Job? = null
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            models.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(getString(R.string.model_download_starting), 0))
        if (downloadJob?.isActive != true) {
            downloadJob = scope.launch {
                models.runDownload()
                stopSelf()
            }
            observerJob = scope.launch {
                models.state.collectLatest { state ->
                    if (state is ModelState.Downloading) notify(state)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        downloadJob?.cancel()
        super.onDestroy()
    }

    private fun notify(state: ModelState.Downloading) {
        val text = when {
            state.waitingForWifi -> getString(R.string.model_download_waiting_wifi)
            else -> getString(R.string.model_download_progress, Formats.size(state.downloadedBytes), Formats.size(state.totalBytes), Formats.speed(state.bytesPerSecond))
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text, (state.progress * 100).toInt()))
    }

    private fun notification(text: String, percent: Int): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.model_download_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.model_download_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .addAction(0, getString(R.string.model_download_cancel), cancel)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_CANCEL = "com.kalorientracker.app.CANCEL_MODEL_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ModelDownloadService::class.java))
        }
    }
}

/** Short, readable sizes and speeds for notification and settings screen. */
object Formats {
    fun size(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        if (gb >= 1.0) return String.format(java.util.Locale.GERMANY, "%.1f GB", gb)
        return String.format(java.util.Locale.GERMANY, "%.0f MB", bytes / 1_048_576.0)
    }

    fun speed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "–"
        val mb = bytesPerSecond / 1_048_576.0
        return if (mb >= 1.0) String.format(java.util.Locale.GERMANY, "%.1f MB/s", mb)
        else String.format(java.util.Locale.GERMANY, "%.0f kB/s", bytesPerSecond / 1024.0)
    }

    /** Remaining time, rounded to something a person would actually say. */
    fun remaining(bytesLeft: Long, bytesPerSecond: Long): String? {
        if (bytesPerSecond <= 0) return null
        val seconds = bytesLeft / bytesPerSecond
        return when {
            seconds < 60 -> "noch weniger als 1 Minute"
            seconds < 3600 -> "noch etwa ${seconds / 60} Minuten"
            else -> "noch etwa ${seconds / 3600} Stunden"
        }
    }
}
