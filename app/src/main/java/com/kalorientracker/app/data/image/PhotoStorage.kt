package com.kalorientracker.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Meal photos live in app-private storage and never leave the device unless the user exports a backup. */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val directory: File
        get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun newPhotoFile(): File = File(directory, "meal_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")

    /** Copies a gallery image into private storage, downscaled and with EXIF rotation applied. */
    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder always returns null; only the stream itself can be missing.
        val stream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return@withContext null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), MAX_EDGE)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext null
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        val bitmap = if (rotation != 0) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } else {
            decoded
        }
        val file = newPhotoFile()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        file.absolutePath
    }

    suspend fun checkQuality(path: String): ImageIssue? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(maxOf(bounds.outWidth, bounds.outHeight), ANALYSIS_EDGE)
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            ImageQualityAnalyzer.analyze(ImageQualityAnalyzer.lumaFromArgb(pixels), bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(path: String) {
        val file = File(path)
        if (file.parentFile?.canonicalPath == directory.canonicalPath) file.delete()
    }

    fun deleteAll() {
        directory.listFiles()?.forEach { it.delete() }
    }

    fun stats(): Pair<Int, Long> {
        val files = directory.listFiles().orEmpty()
        return files.size to files.sumOf { it.length() }
    }

    fun resolve(fileName: String): File = File(directory, File(fileName).name)

    companion object {
        const val DIR = "photos"
        private const val MAX_EDGE = 1600
        private const val ANALYSIS_EDGE = 256

        private fun sampleSize(longestEdge: Int, target: Int): Int {
            var sample = 1
            while (longestEdge / (sample * 2) >= target) sample *= 2
            return sample
        }

        private fun exifRotation(exif: ExifInterface): Int =
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
    }
}
