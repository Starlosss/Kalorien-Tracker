package com.kalorientracker.app.data.image

import kotlin.math.sqrt

enum class ImageIssue(val message: String) {
    TOO_DARK("Das Bild ist zu dunkel. Bitte mache ein weiteres Foto."),
    TOO_BRIGHT("Das Bild ist überbelichtet. Bitte mache ein weiteres Foto ohne direktes Licht."),
    NO_DETAIL("Im Rahmen ist kaum etwas zu erkennen. Bitte das Essen innerhalb des Rahmens platzieren."),
    BLURRY("Das Bild ist unscharf. Bitte halte die Kamera ruhig und mache ein weiteres Foto."),
}

/**
 * Cheap on-device plausibility checks on a downscaled grayscale image. Only the central area
 * (where the capture frame is) is judged, so a dark table edge does not trigger a warning.
 */
object ImageQualityAnalyzer {
    const val DARK_MEAN = 40.0
    const val BRIGHT_MEAN = 235.0
    const val MIN_CONTRAST = 8.0
    const val MIN_SHARPNESS = 12.0

    data class Metrics(val mean: Double, val contrast: Double, val sharpness: Double)

    fun metrics(luma: IntArray, width: Int, height: Int): Metrics {
        require(luma.size == width * height && width >= 3 && height >= 3)
        val x0 = width / 6
        val x1 = width - width / 6
        val y0 = height / 6
        val y1 = height - height / 6

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val v = luma[y * width + x].toDouble()
            sum += v
            sumSq += v * v
            count++
        }
        val mean = sum / count
        val contrast = sqrt((sumSq / count - mean * mean).coerceAtLeast(0.0))

        var lapSum = 0.0
        var lapSq = 0.0
        var lapCount = 0
        for (y in maxOf(y0, 1) until minOf(y1, height - 1)) for (x in maxOf(x0, 1) until minOf(x1, width - 1)) {
            val c = luma[y * width + x]
            val lap = (4 * c - luma[y * width + x - 1] - luma[y * width + x + 1] -
                luma[(y - 1) * width + x] - luma[(y + 1) * width + x]).toDouble()
            lapSum += lap
            lapSq += lap * lap
            lapCount++
        }
        val lapMean = lapSum / lapCount
        val sharpness = lapSq / lapCount - lapMean * lapMean
        return Metrics(mean, contrast, sharpness)
    }

    fun analyze(luma: IntArray, width: Int, height: Int): ImageIssue? {
        val m = metrics(luma, width, height)
        return when {
            m.mean < DARK_MEAN -> ImageIssue.TOO_DARK
            m.mean > BRIGHT_MEAN -> ImageIssue.TOO_BRIGHT
            m.contrast < MIN_CONTRAST -> ImageIssue.NO_DETAIL
            m.sharpness < MIN_SHARPNESS -> ImageIssue.BLURRY
            else -> null
        }
    }

    /** ITU-R BT.601 luma from packed ARGB pixels. */
    fun lumaFromArgb(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        (r * 299 + g * 587 + b * 114) / 1000
    }
}
