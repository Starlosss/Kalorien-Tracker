package com.kalorientracker.app.data.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Downloads the model file with several connections at once.
 *
 * A single stream through Android's DownloadManager took hours for the 2.6 GB model: one
 * connection rarely gets the full line speed, and the system throttles background downloads.
 * Hugging Face serves byte ranges, so the file is fetched in parallel parts written straight into
 * their final position. Each part remembers how far it got, so an interrupted download resumes
 * instead of starting over.
 */
@Singleton
class ModelDownloader @Inject constructor() {

    data class Progress(val downloadedBytes: Long, val totalBytes: Long, val bytesPerSecond: Long)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Downloads [url] into [target], resuming from whatever earlier parts are on disk.
     * Throws on failure; cancelling the coroutine leaves the parts in place for the next attempt.
     */
    suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        state: PartState,
        onProgress: suspend (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        RandomAccessFile(target, "rw").use { it.setLength(expectedBytes) }

        val parts = partsOf(expectedBytes)
        val done = AtomicLong(parts.indices.sumOf { state.doneBytes(it).coerceAtMost(parts[it].size) })
        val reporter = async { report(done, expectedBytes, onProgress) }
        try {
            coroutineScope {
                parts.mapIndexed { index, part ->
                    async { fetchPart(url, target, index, part, state, done) }
                }.awaitAll()
            }
        } finally {
            reporter.cancel()
        }
        val length = target.length()
        if (length != expectedBytes) error("Unerwartete Dateigröße: $length statt $expectedBytes")
        state.clear()
    }

    private data class Part(val start: Long, val endInclusive: Long) {
        val size: Long get() = endInclusive - start + 1
    }

    private fun partsOf(total: Long): List<Part> {
        val count = PART_COUNT.coerceAtMost(maxOf(1, (total / MIN_PART_BYTES).toInt()))
        val size = total / count
        return (0 until count).map { index ->
            val start = index * size
            val end = if (index == count - 1) total - 1 else start + size - 1
            Part(start, end)
        }
    }

    private suspend fun fetchPart(
        url: String,
        target: File,
        index: Int,
        part: Part,
        state: PartState,
        done: AtomicLong,
    ) {
        var attempt = 0
        while (true) {
            val alreadyDone = state.doneBytes(index).coerceIn(0, part.size)
            if (alreadyDone >= part.size) return
            try {
                streamPart(url, target, index, part, alreadyDone, state, done)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt > MAX_ATTEMPTS) throw e
                Log.w(TAG, "Part $index failed (attempt $attempt), retrying", e)
                delay(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private suspend fun streamPart(
        url: String,
        target: File,
        index: Int,
        part: Part,
        alreadyDone: Long,
        state: PartState,
        done: AtomicLong,
    ) {
        val from = part.start + alreadyDone
        val request = Request.Builder().url(url)
            .header("Range", "bytes=$from-${part.endInclusive}")
            .header("Accept-Encoding", "identity")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Leere Antwort")
            RandomAccessFile(target, "rw").use { file ->
                file.seek(from)
                val buffer = ByteArray(BUFFER_BYTES)
                var written = alreadyDone
                var sinceCheckpoint = 0L
                body.byteStream().use { input ->
                    while (written < part.size) {
                        coroutineContext.ensureActive()
                        val wanted = minOf(buffer.size.toLong(), part.size - written).toInt()
                        val read = input.read(buffer, 0, wanted)
                        if (read <= 0) break
                        file.write(buffer, 0, read)
                        written += read
                        sinceCheckpoint += read
                        done.addAndGet(read.toLong())
                        if (sinceCheckpoint >= CHECKPOINT_BYTES) {
                            state.setDoneBytes(index, written)
                            sinceCheckpoint = 0
                        }
                    }
                }
                state.setDoneBytes(index, written)
                if (written < part.size) error("Verbindung endete nach $written von ${part.size} Bytes")
            }
        }
    }

    /** Reports progress on a steady beat, with a smoothed speed so the estimate does not jump. */
    private suspend fun report(done: AtomicLong, total: Long, onProgress: suspend (Progress) -> Unit) {
        var lastBytes = done.get()
        var lastTime = System.nanoTime()
        var speed = 0.0
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            val bytes = done.get()
            val now = System.nanoTime()
            val seconds = (now - lastTime) / 1_000_000_000.0
            if (seconds > 0) {
                val current = (bytes - lastBytes) / seconds
                speed = if (speed == 0.0) current else speed * 0.7 + current * 0.3
            }
            lastBytes = bytes
            lastTime = now
            onProgress(Progress(bytes.coerceAtMost(total), total, speed.toLong().coerceAtLeast(0)))
        }
    }

    /** Per-part progress, so a cancelled or crashed download continues where it stopped. */
    interface PartState {
        fun doneBytes(index: Int): Long
        fun setDoneBytes(index: Int, bytes: Long)
        fun clear()
    }

    companion object {
        private const val TAG = "ModelDownloader"
        const val PART_COUNT = 6
        private const val MIN_PART_BYTES = 8L * 1024 * 1024
        private const val BUFFER_BYTES = 128 * 1024
        private const val CHECKPOINT_BYTES = 4L * 1024 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MAX_ATTEMPTS = 4
        private const val RETRY_DELAY_MS = 1_500L
    }
}
