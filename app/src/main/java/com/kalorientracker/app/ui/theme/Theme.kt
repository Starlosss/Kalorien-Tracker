package com.kalorientracker.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalorientracker.app.domain.model.Metric

/** Black, white and very dark grays. Color is reserved for data identity. */
object Palette {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0E)
    val SurfaceRaised = Color(0xFF161616)
    val SurfaceHigh = Color(0xFF1F1F1F)
    val Outline = Color(0xFF2A2A2A)
    val OutlineStrong = Color(0xFF3A3A3A)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB4B4B4)
    val TextTertiary = Color(0xFF6E6E6E)

    val Track = Color(0xFF232323)
    val Grid = Color(0xFF1C1C1C)

    /** Validated as a categorical set on the dark surface (CVD ΔE ≥ 9.4, contrast ≥ 3:1). */
    val Protein = Color(0xFF3987E5)
    val Carbs = Color(0xFFD95926)
    val Fat = Color(0xFF199E70)

    /** Recording dot / destructive affordances only. */
    val Signal = Color(0xFFE66767)

    fun forMetric(metric: Metric): Color = when (metric) {
        Metric.CALORIES -> TextPrimary
        Metric.PROTEIN -> Protein
        Metric.CARBS -> Carbs
        Metric.FAT -> Fat
        Metric.WEIGHT -> TextPrimary
    }
}

private val colorScheme = darkColorScheme(
    primary = Palette.TextPrimary,
    onPrimary = Palette.Background,
    primaryContainer = Palette.SurfaceHigh,
    onPrimaryContainer = Palette.TextPrimary,
    secondary = Palette.TextSecondary,
    onSecondary = Palette.Background,
    secondaryContainer = Palette.SurfaceRaised,
    onSecondaryContainer = Palette.TextPrimary,
    tertiary = Palette.TextSecondary,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Background,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceRaised,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceRaised,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.OutlineStrong,
    outlineVariant = Palette.Outline,
    error = Palette.Signal,
    onError = Palette.Background,
)

/** Monospaced accents give the dot-matrix system feel; content stays in the system sans. */
val MonoFamily = FontFamily.Monospace

private val typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-2.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

data class MotionSettings(val animationsEnabled: Boolean = true)

val LocalMotion = staticCompositionLocalOf { MotionSettings() }

@Composable
fun KalorienTheme(
    animationsEnabled: Boolean = true,
    haptics: Haptics = Haptics.None,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
        CompositionLocalProvider(
            LocalMotion provides MotionSettings(animationsEnabled),
            LocalHaptics provides haptics,
            content = content,
        )
    }
}
