package com.kalorientracker.app.ui.statistics.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Motion
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.motionSpec
import kotlin.math.roundToInt

enum class ChartType { BAR, LINE }

data class ChartPoint(val label: String, val value: Double?)

/**
 * Single-series chart with one y-axis. Several metrics are shown as stacked small multiples,
 * never on a shared dual axis. Touch and drag shows a crosshair with the exact value.
 */
@Composable
fun MorphingChart(
    title: String,
    points: List<ChartPoint>,
    type: ChartType,
    color: Color,
    modifier: Modifier = Modifier,
    target: Double? = null,
    targetLabel: String = "Ziel",
    zeroBased: Boolean = true,
    smooth: Boolean = true,
    height: Dp = 180.dp,
    summary: String? = null,
    showTitle: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    format: (Double) -> String = { it.roundToInt().toString() },
) {
    val haptics = LocalHaptics.current
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = Palette.TextTertiary)

    val values = points.map { it.value }
    val newShape = remember(values) { ChartShape.of(values) }
    var morph by remember { mutableStateOf(ChartMorph(ChartShape.EMPTY, newShape)) }
    val progress = remember { Animatable(1f) }
    val rangeSpec = motionSpec(Motion.chartRange<Float>())

    LaunchedEffect(newShape, type) {
        val frozen = morph.snapshot(progress.value)
        morph = ChartMorph(frozen, newShape)
        progress.snapTo(0f)
        progress.animateTo(1f, rangeSpec)
    }

    val range = remember(values, target, zeroBased) {
        AxisRange.of(values.filterNotNull(), target, zeroBased)
    }
    val axisMin by animateFloatAsState(range.min.toFloat(), rangeSpec, label = "axisMin")
    val axisMax by animateFloatAsState(range.max.toFloat(), rangeSpec, label = "axisMax")

    var selected by remember(points.size) { mutableStateOf<Int?>(null) }
    val selectedPoint = selected?.let { points.getOrNull(it) }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showTitle) SectionLabel(title) else Spacer(Modifier.height(0.dp))
            Spacer(Modifier.weight(1f))
            val readout = when {
                selectedPoint != null -> "${selectedPoint.label} · ${selectedPoint.value?.let(format) ?: "–"}"
                summary != null -> summary
                else -> ""
            }
            Text(readout, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        val empty = values.all { it == null }
        Box(Modifier.fillMaxWidth().height(height)) {
            if (empty) {
                Text(
                    "Noch keine Daten in diesem Zeitraum",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(points.size) {
                        fun update(x: Float) {
                            val plotLeft = AXIS_WIDTH.toPx()
                            val w = size.width - plotLeft
                            if (points.isEmpty() || w <= 0) return
                            val i = (((x - plotLeft) / w) * points.size).toInt().coerceIn(0, points.size - 1)
                            if (i != selected) haptics.perform(HapticEvent.Tick)
                            selected = i
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            update(down.position.x)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                update(change.position.x)
                            }
                            selected = null
                        }
                    },
            ) {
                val plotLeft = AXIS_WIDTH.toPx()
                val plotBottom = size.height - LABEL_HEIGHT.toPx()
                val plotTop = 6.dp.toPx()
                val plotWidth = size.width - plotLeft
                val plotHeight = plotBottom - plotTop
                val span = (axisMax - axisMin).takeIf { it > 0f } ?: 1f
                fun yFor(v: Double): Float = plotBottom - ((v.toFloat() - axisMin) / span) * plotHeight

                val n = points.size
                if (empty) {
                    drawLine(Palette.Grid, Offset(plotLeft, plotBottom), Offset(size.width, plotBottom), strokeWidth = 1.dp.toPx())
                    return@Canvas
                }
                drawGrid(plotLeft, plotTop, plotBottom, axisMin, axisMax, measurer, axisStyle, format)

                if (n > 0) {
                    when (type) {
                        ChartType.BAR -> drawBarSeries(points, color, morph, progress.value, plotLeft, plotWidth, ::yFor, yFor(axisMin.toDouble()), selected)
                        ChartType.LINE -> drawLineSeries(points, color, morph, progress.value, plotLeft, plotWidth, ::yFor, smooth, selected)
                    }
                    drawXLabels(points, plotLeft, plotWidth, size.height, measurer, axisStyle)
                }

                if (target != null && target.toFloat() in axisMin..axisMax) {
                    val y = yFor(target)
                    drawLine(
                        Palette.TextTertiary,
                        Offset(plotLeft, y),
                        Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    )
                    val layout = measurer.measure(targetLabel, axisStyle)
                    // Keep the label clear of the screen edge; without the inset it sits flush against it.
                    drawText(layout, topLeft = Offset(size.width - layout.size.width - 4.dp.toPx(), y - layout.size.height - 2.dp.toPx()))
                }

                selected?.let { i ->
                    val x = plotLeft + ChartShape.xFor(i, n) * plotWidth
                    drawLine(Palette.TextSecondary, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1.dp.toPx())
                }
            }
        }
    }
}

private val AXIS_WIDTH = 40.dp
private val LABEL_HEIGHT = 18.dp

private fun DrawScope.drawGrid(
    left: Float,
    top: Float,
    bottom: Float,
    min: Float,
    max: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    format: (Double) -> String,
) {
    drawLine(Palette.Grid, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
    for ((value, y) in listOf(min to bottom, max to top)) {
        val layout = measurer.measure(format(value.toDouble()), style)
        val textY = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
        drawText(layout, topLeft = Offset(left - layout.size.width - 8.dp.toPx(), textY))
    }
}

private fun DrawScope.drawXLabels(
    points: List<ChartPoint>,
    left: Float,
    width: Float,
    height: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
) {
    val n = points.size
    val indices = if (n <= 2) (0 until n).toList() else listOf(0, n / 2, n - 1)
    for (i in indices) {
        val layout = measurer.measure(points[i].label, style)
        val cx = left + ChartShape.xFor(i, n) * width
        val x = (cx - layout.size.width / 2f).coerceIn(left, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, height - layout.size.height))
    }
}

private fun DrawScope.drawBarSeries(
    points: List<ChartPoint>,
    color: Color,
    morph: ChartMorph,
    progress: Float,
    left: Float,
    width: Float,
    yFor: (Double) -> Float,
    baseline: Float,
    selected: Int?,
) {
    val n = points.size
    val slot = width / n
    val barWidth = minOf(slot * 0.56f, 14.dp.toPx()).coerceAtLeast(1f)
    val gap = slot - barWidth
    val radius = minOf(4.dp.toPx(), barWidth / 2)
    points.forEachIndexed { i, point ->
        val value = point.value ?: return@forEachIndexed
        val xNorm = ChartShape.xFor(i, n)
        val shown = morph.valueAt(xNorm, value, progress)
        val top = yFor(shown).coerceAtMost(baseline)
        val x = left + i * slot + gap / 2
        val alpha = if (selected == null || selected == i) 1f else 0.45f
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(x, top, x + barWidth, baseline),
                    topLeft = CornerRadius(radius, radius),
                    topRight = CornerRadius(radius, radius),
                    bottomRight = CornerRadius.Zero,
                    bottomLeft = CornerRadius.Zero,
                ),
            )
        }
        drawPath(path, color.copy(alpha = alpha))
    }
}

private fun DrawScope.drawLineSeries(
    points: List<ChartPoint>,
    color: Color,
    morph: ChartMorph,
    progress: Float,
    left: Float,
    width: Float,
    yFor: (Double) -> Float,
    smooth: Boolean,
    selected: Int?,
) {
    val n = points.size
    val segments = mutableListOf<MutableList<Offset>>()
    var current: MutableList<Offset>? = null
    points.forEachIndexed { i, point ->
        val value = point.value
        if (value == null) {
            current = null
            return@forEachIndexed
        }
        val xNorm = ChartShape.xFor(i, n)
        val offset = Offset(left + xNorm * width, yFor(morph.valueAt(xNorm, value, progress)))
        val segment = current ?: mutableListOf<Offset>().also { segments += it; current = it }
        segment += offset
    }
    // Short gaps (days without data) are bridged so trends stay readable.
    val merged = if (segments.size > 1) listOf(segments.flatten().toMutableList()) else segments
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (segment in merged) {
        if (segment.size == 1) {
            drawCircle(color, radius = 4.dp.toPx(), center = segment[0])
            continue
        }
        val path = Path().apply {
            moveTo(segment[0].x, segment[0].y)
            for (k in 1 until segment.size) {
                val p0 = segment[k - 1]
                val p1 = segment[k]
                if (smooth) {
                    val mx = (p0.x + p1.x) / 2
                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                } else {
                    lineTo(p1.x, p1.y)
                }
            }
        }
        drawPath(path, color, style = stroke)
    }
    val last = merged.lastOrNull()?.lastOrNull()
    if (last != null) {
        drawCircle(Palette.Background, radius = 6.dp.toPx(), center = last)
        drawCircle(color, radius = 4.dp.toPx(), center = last)
    }
    selected?.let { i ->
        val value = points.getOrNull(i)?.value ?: return@let
        val xNorm = ChartShape.xFor(i, n)
        val center = Offset(left + xNorm * width, yFor(morph.valueAt(xNorm, value, progress)))
        drawCircle(Palette.Background, radius = 7.dp.toPx(), center = center)
        drawCircle(color, radius = 5.dp.toPx(), center = center)
    }
}
