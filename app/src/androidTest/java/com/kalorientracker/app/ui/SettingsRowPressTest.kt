package com.kalorientracker.app.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kalorientracker.app.ui.settings.SettingsScreen
import com.kalorientracker.app.ui.theme.KalorienTheme
import com.kalorientracker.app.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A pressed settings row has to be visible on the app's near black background, not merely present
 * in the composition. The test holds a finger on a row, steps the clock by hand and measures a
 * strip of the row that holds no text, so what it reads is the pressed state and nothing else.
 *
 * The second test records why the ripple had to be set in the theme: the platform default darkens
 * the row, which on this background cannot be seen at all.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRowPressTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pressedRowLightsUpWithTheThemeRipple() {
        val measured = pressAndMeasure(useThemeIndication = true)
        assertTrue("Row must be dark before the press, was ${measured.first()}", measured.first() < 1.0)
        assertTrue("Pressed row must light up visibly, peak was ${measured.max()}", measured.max() > 15.0)
    }

    @Test
    fun thePlatformDefaultStaysInvisibleOnThisBackground() {
        val measured = pressAndMeasure(useThemeIndication = false)
        assertTrue("Platform default draws nothing visible here, peak was ${measured.max()}", measured.max() < 1.0)
    }

    /** Brightness of the probe strip at 40 ms steps while the finger stays down. */
    private fun pressAndMeasure(useThemeIndication: Boolean): List<Double> {
        compose.setContent {
            // Read outside KalorienTheme: this is the indication the rows had before the change.
            val platformDefault = LocalIndication.current
            KalorienTheme {
                // Mirrors the app's Scaffold, which is what gives the ripple its content color.
                Surface(color = Palette.Background) {
                    if (useThemeIndication) {
                        SettingsScreen(onBack = {}, onOpenSection = {})
                    } else {
                        CompositionLocalProvider(LocalIndication provides platformDefault) {
                            SettingsScreen(onBack = {}, onOpenSection = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val bounds = compose.onNodeWithText("Daten").fetchSemanticsNode().boundsInRoot
        val top = bounds.top.toInt()
        val bottom = bounds.bottom.toInt()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Daten").performTouchInput { down(centerLeft) }

        val series = buildList {
            repeat(STEPS + 1) { step ->
                if (step > 0) compose.mainClock.advanceTimeBy(STEP_MS)
                add(probe(compose.onRoot().captureToImage().asAndroidBitmap(), top, bottom))
            }
        }
        Log.i(LOG_TAG, "themeIndication=$useThemeIndication series=$series")
        return series
    }

    private fun probe(frame: Bitmap, top: Int, bottom: Int): Double {
        var sum = 0L
        var count = 0
        for (y in top until minOf(bottom, frame.height)) {
            for (x in PROBE_LEFT until minOf(PROBE_RIGHT, frame.width)) {
                val pixel = frame.getPixel(x, y)
                sum += ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3
                count++
            }
        }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }

    private companion object {
        const val LOG_TAG = "SettingsRowPress"
        const val STEP_MS = 40L
        const val STEPS = 20
        const val PROBE_LEFT = 560
        const val PROBE_RIGHT = 720
    }
}
