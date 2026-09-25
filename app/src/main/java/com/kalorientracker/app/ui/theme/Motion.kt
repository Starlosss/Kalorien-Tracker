package com.kalorientracker.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** One small set of curves for the whole app so every movement feels related. Short and calm. */
object Motion {
    /** Numbers, rings and bars settling on a new value. */
    fun <T> gentle(): AnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 180f)

    /** Direct manipulation feedback (press, toggle). */
    fun <T> snappy(): AnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** Chart morph between time ranges. */
    fun <T> chartRange(): AnimationSpec<T> = tween(durationMillis = 520, easing = FastOutSlowInEasing)

    /** Card and list entrances. */
    fun <T> enter(): AnimationSpec<T> = tween(durationMillis = 280, easing = FastOutSlowInEasing)

    const val SCREEN_MS = 260
}

/** Returns [spec] or an instant snap when the user disabled animations. */
@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: AnimationSpec<T>): AnimationSpec<T> =
    if (LocalMotion.current.animationsEnabled) spec else snap()
