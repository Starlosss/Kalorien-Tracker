package com.kalorientracker.app.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

enum class HapticEvent {
    /** Slider reaches a new step, chart range changes. */
    Tick,
    /** Button press. */
    Tap,
    /** Switch or chip toggled. */
    Toggle,
    /** Camera shutter. */
    Capture,
    /** Meal saved — slightly stronger, still short. */
    Save,
    /** Weight saved and other quiet confirmations. */
    Confirm,
    /** Daily goal reached — a short, distinct sequence. */
    GoalReached,
    Reject,
}

/** All haptics are intentionally low in intensity: felt, never buzzing. */
interface Haptics {
    fun perform(event: HapticEvent)

    object None : Haptics {
        override fun perform(event: HapticEvent) = Unit
    }
}

class ViewHaptics(
    private val view: View,
    private val enabled: Boolean,
) : Haptics {

    private val vibrator: Vibrator? by lazy {
        val context = view.context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun perform(event: HapticEvent) {
        if (!enabled) return
        when (event) {
            HapticEvent.Tick -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            HapticEvent.Tap -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            HapticEvent.Toggle -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK,
            )
            HapticEvent.Capture -> view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            HapticEvent.Save, HapticEvent.Confirm -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            )
            HapticEvent.Reject -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
            )
            HapticEvent.GoalReached -> goalSequence()
        }
    }

    private fun goalSequence() {
        val v = vibrator
        if (v == null || !v.hasVibrator()) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            v.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 70)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 90)
                    .compose(),
            )
        } else {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 70, 12, 90, 18), intArrayOf(0, 60, 0, 80, 0, 110), -1))
        }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { Haptics.None }
