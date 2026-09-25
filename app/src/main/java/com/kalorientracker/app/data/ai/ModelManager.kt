package com.kalorientracker.app.data.ai

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long, val waitingForWifi: Boolean) : ModelState {
        val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    data object Ready : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * The Gemma model is too large for the APK, so it is downloaded once through the system
 * DownloadManager (resumable, survives leaving the app) and afterwards used fully offline.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("model_download", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val directory: File?
        get() = context.getExternalFilesDir(DIR)

    val modelFile: File?
        get() = directory?.let { File(it, FILE_NAME) }

    init {
        refresh()
    }

    fun isReady(): Boolean = modelFile?.let { it.exists() && it.length() == EXPECTED_BYTES } == true

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    /** Total device memory in GB; the model needs roughly 2 GB of free RAM while analysing. */
    val deviceRamGb: Double
        get() {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            return info.totalMem / 1_073_741_824.0
        }

    val freeStorageBytes: Long
        get() = directory?.let { runCatching { StatFs(it.path).availableBytes }.getOrNull() } ?: 0L

    fun refresh() {
        if (isReady()) {
            _state.value = ModelState.Ready
            return
        }
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) startPolling(id) else _state.value = ModelState.NotDownloaded
    }

    fun start() {
        if (isReady()) {
            _state.value = ModelState.Ready
            return
        }
        val dir = directory ?: run {
            _state.value = ModelState.Failed("Kein Speicher für das Modell verfügbar.")
            return
        }
        if (freeStorageBytes < EXPECTED_BYTES + SPACE_MARGIN_BYTES) {
            _state.value = ModelState.Failed("Zu wenig Speicherplatz. Benötigt werden etwa 2,8 GB freier Speicher.")
            return
        }
        cancelDownloadOnly()
        File(dir, FILE_NAME).delete()
        val request = DownloadManager.Request(Uri.parse(DOWNLOAD_URL))
            .setTitle("KI-Modell für die Foto-Erkennung")
            .setDescription("Gemma 4 E2B · einmalig, danach offline")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, DIR, FILE_NAME)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        val id = downloads.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        _state.value = ModelState.Downloading(0, EXPECTED_BYTES, waitingForWifi = false)
        startPolling(id)
    }

    fun cancel() {
        cancelDownloadOnly()
        modelFile?.delete()
        _state.value = ModelState.NotDownloaded
    }

    fun delete() {
        cancel()
    }

    private fun cancelDownloadOnly() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) downloads.remove(id)
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        pollJob?.cancel()
    }

    private fun startPolling(id: Long) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val next = query(id)
                _state.value = next
                if (next !is ModelState.Downloading) {
                    if (next is ModelState.Ready || next is ModelState.Failed) prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                    break
                }
                delay(POLL_MS)
            }
        }
    }

    private fun query(id: Long): ModelState {
        downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                return if (isReady()) ModelState.Ready else ModelState.NotDownloaded
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: EXPECTED_BYTES
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    if (isReady()) ModelState.Ready else ModelState.Failed("Der Download ist unvollständig. Bitte erneut versuchen.")
                DownloadManager.STATUS_FAILED -> {
                    modelFile?.delete()
                    ModelState.Failed(failureText(reason))
                }
                DownloadManager.STATUS_PAUSED ->
                    ModelState.Downloading(done, total, waitingForWifi = reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI || reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK)
                else -> ModelState.Downloading(done, total, waitingForWifi = false)
            }
        }
    }

    private fun failureText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Zu wenig Speicherplatz auf dem Gerät."
        DownloadManager.ERROR_CANNOT_RESUME, DownloadManager.ERROR_HTTP_DATA_ERROR -> "Die Verbindung wurde unterbrochen. Bitte erneut versuchen."
        else -> "Der Download ist fehlgeschlagen (Code $reason). Bitte erneut versuchen."
    }

    companion object {
        const val MODEL_NAME = "Gemma 4 E2B"
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val EXPECTED_BYTES = 2_588_147_712L
        const val DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE_NAME"
        const val RECOMMENDED_RAM_GB = 6.0
        private const val DIR = "models"
        private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024
        private const val POLL_MS = 1_000L
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_WIFI_ONLY = "wifi_only"
    }
}
