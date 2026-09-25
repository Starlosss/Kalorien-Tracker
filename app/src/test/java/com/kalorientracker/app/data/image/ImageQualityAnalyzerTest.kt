package com.kalorientracker.app.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ImageQualityAnalyzerTest {

    private val w = 96
    private val h = 96

    private fun image(f: (x: Int, y: Int) -> Int) = IntArray(w * h) { i -> f(i % w, i / w).coerceIn(0, 255) }

    @Test
    fun darkImageIsRejected() {
        val random = Random(1)
        assertEquals(ImageIssue.TOO_DARK, ImageQualityAnalyzer.analyze(image { _, _ -> random.nextInt(0, 30) }, w, h))
    }

    @Test
    fun overexposedImageIsRejected() {
        assertEquals(ImageIssue.TOO_BRIGHT, ImageQualityAnalyzer.analyze(image { _, _ -> 250 }, w, h))
    }

    @Test
    fun flatImageHasNoDetail() {
        assertEquals(ImageIssue.NO_DETAIL, ImageQualityAnalyzer.analyze(image { _, _ -> 128 }, w, h))
    }

    @Test
    fun smoothGradientIsBlurry() {
        assertEquals(ImageIssue.BLURRY, ImageQualityAnalyzer.analyze(image { x, _ -> 60 + x * 2 }, w, h))
    }

    @Test
    fun texturedImageIsAccepted() {
        val random = Random(7)
        assertNull(ImageQualityAnalyzer.analyze(image { x, y -> 90 + ((x / 3 + y / 3) % 2) * 60 + random.nextInt(0, 20) }, w, h))
    }

    @Test
    fun lumaUsesBt601Weights() {
        val pixels = intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt())
        assertEquals(listOf(255, 0, 76), ImageQualityAnalyzer.lumaFromArgb(pixels).toList())
    }
}
