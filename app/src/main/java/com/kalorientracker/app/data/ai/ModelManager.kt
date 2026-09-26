package com.kalorientracker.app.data.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val waitingForWifi: Boolean,
        val bytesPerSecond: Long = 0,
    ) : ModelState {
        val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        val remainingText: String? get() = Formats.remaining(totalBytes - downloadedBytes, bytesPerSecond)
    }
    data object Ready : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * The Gemma model is too large for the APK, so it is downloaded once and afterwards used fully
 * offline. The transfer itself runs in [ModelDownloadService] so it survives leaving the app.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: ModelDownloader,
) {
    private val prefs = context.getSharedPreferences("model_download", Context.MODE_PRIVATE)

    /** Per-part progress of the parallel download, kept across app restarts. */
    private val partState = object : ModelDownloader.PartState {
        override fun doneBytes(index: Int): Long = prefs.getLong(KEY_PART + index, 0L)

        override fun setDoneBytes(index: Int, bytes: Long) {
            prefs.edit().putLong(KEY_PART + index, bytes).apply()
        }

        override fun clear() {
            val editor = prefs.edit()
            repeat(ModelDownloader.PART_COUNT) { editor.remove(KEY_PART + it) }
            editor.apply()
        }

        fun downloadedBytes(): Long = (0 until ModelDownloader.PART_COUNT).sumOf { doneBytes(it) }
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val directory: File?
        get() = context.getExternalFilesDir(DIR)

    val modelFile: File?
        get() = directory?.let { File(it, FILE_NAME) }

    init {
        refresh()
    }

    /**
     * The file is pre-allocated to its full size before the parts are fetched, so the length alone
     * says nothing. Only a download with no part progress left is complete.
     */
    fun isReady(): Boolean =
        modelFile?.let { it.exists() && it.length() == EXPECTED_BYTES && partState.downloadedBytes() == 0L } == true

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    /** Set once a device has shown it cannot run the model on its GPU, so later runs start on the CPU. */
    var gpuUnsupported: Boolean
        get() = prefs.getBoolean(KEY_GPU_UNSUPPORTED, false)
        set(value) = prefs.edit().putBoolean(KEY_GPU_UNSUPPORTED, value).apply()

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
        if (_state.value is ModelState.Downloading) return
        val started = partState.downloadedBytes()
        if (started <= 0) {
            _state.value = ModelState.NotDownloaded
            return
        }
        // An unfinished download is picked up again – the app was killed, the user did not stop it.
        _state.value = ModelState.Downloading(started, EXPECTED_BYTES, waitingForWifi = false)
        ModelDownloadService.start(context)
    }

    /** Starts (or resumes) the download; the service does the work and keeps it alive. */
    fun start() {
        if (isReady()) {
            _state.value = ModelState.Ready
            return
        }
        if (freeStorageBytes + partState.downloadedBytes() < EXPECTED_BYTES + SPACE_MARGIN_BYTES) {
            _state.value = ModelState.Failed("Zu wenig Speicherplatz. Die App braucht etwa 2,6 GB freien Speicher.")
            return
        }
        _state.value = ModelState.Downloading(partState.downloadedBytes(), EXPECTED_BYTES, waitingForWifi = false)
        ModelDownloadService.start(context)
    }

    fun cancel() {
        partState.clear()
        modelFile?.delete()
        _state.value = ModelState.NotDownloaded
        ModelDownloadService.stop(context)
    }

    fun delete() = cancel()

    /** Runs the transfer itself. Called by the service, which owns its lifetime. */
    suspend fun runDownload() {
        val target = modelFile ?: run {
            _state.value = ModelState.Failed("Es ist kein Speicherort für das Modell verfügbar. Starte das Gerät neu und versuche es noch einmal.")
            return
        }
        try {
            awaitAllowedNetwork()
            downloader.download(DOWNLOAD_URL, target, EXPECTED_BYTES, partState) { progress ->
                _state.value = ModelState.Downloading(
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    waitingForWifi = false,
                    bytesPerSecond = progress.bytesPerSecond,
                )
            }
            _state.value = if (isReady()) ModelState.Ready else ModelState.Failed("Der Download ist unvollständig. Bitte erneut versuchen.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            _state.value = ModelState.Failed("Der Download wurde unterbrochen. Beim nächsten Versuch geht es dort weiter, wo er stehen geblieben ist.")
        }
    }

    /** Waits while "only on Wi-Fi" is on and the phone is on mobile data. */
    private suspend fun awaitAllowedNetwork() {
        while (wifiOnly && isMetered()) {
            _state.value = ModelState.Downloading(partState.downloadedBytes(), EXPECTED_BYTES, waitingForWifi = true)
            delay(NETWORK_POLL_MS)
        }
    }

    private fun isMetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        private const val TAG = "ModelManager"
        const val MODEL_NAME = "Gemma 4 E2B"
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val EXPECTED_BYTES = 2_588_147_712L
        const val DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE_NAME"
        const val RECOMMENDED_RAM_GB = 6.0
        private const val DIR = "models"
        private const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024
        private const val NETWORK_POLL_MS = 5_000L
        private const val KEY_PART = "part_"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_GPU_UNSUPPORTED = "gpu_unsupported"
    }
}
