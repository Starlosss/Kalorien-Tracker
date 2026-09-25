package com.kalorientracker.app.ui.statistics.charts

/**
 * Chart geometry as a piecewise-linear function over x ∈ [0, 1]. Morphing between two datasets
 * with different point counts works by sampling the old function at the new x positions, so a
 * range switch (7 → 30 days) flows into the new shape instead of cutting.
 */
data class ChartShape(val points: List<Pair<Float, Double>>) {

    fun valueAt(x: Float): Double? {
        if (points.isEmpty()) return null
        if (x <= points.first().first) return points.first().second
        if (x >= points.last().first) return points.last().second
        val upper = points.indexOfFirst { it.first >= x }
        val (x1, y1) = points[upper - 1]
        val (x2, y2) = points[upper]
        val t = if (x2 == x1) 0.0 else ((x - x1) / (x2 - x1)).toDouble()
        return y1 + (y2 - y1) * t
    }

    companion object {
        val EMPTY = ChartShape(emptyList())

        /** Positions each value at its bucket centre; missing buckets are skipped. */
        fun of(values: List<Double?>): ChartShape {
            val n = values.size
            if (n == 0) return EMPTY
            return ChartShape(values.mapIndexedNotNull { i, v -> v?.let { xFor(i, n) to it } })
        }

        fun xFor(index: Int, count: Int): Float = (index + 0.5f) / count
    }
}

/** Current on-screen state of a running morph. */
class ChartMorph(
    val from: ChartShape,
    val to: ChartShape,
) {
    fun valueAt(x: Float, target: Double, progress: Float): Double {
        val start = from.valueAt(x) ?: target
        return start + (target - start) * progress
    }

    /** Freezes the in-between state so an interrupted morph continues smoothly. */
    fun snapshot(progress: Float): ChartShape =
        ChartShape(to.points.map { (x, v) -> x to valueAt(x, v, progress) })
}

data class AxisRange(val min: Double, val max: Double) {
    val span: Double get() = (max - min).takeIf { it > 0 } ?: 1.0

    companion object {
        /** Zero-based range for intake bars; padded, non-zero range for weight lines. */
        fun of(values: List<Double>, extra: Double?, zeroBased: Boolean): AxisRange {
            val all = values + listOfNotNull(extra)
            if (all.isEmpty()) return AxisRange(0.0, 1.0)
            val max = all.max()
            if (zeroBased) return AxisRange(0.0, niceCeil(max * 1.08))
            val min = all.min()
            val pad = ((max - min) * 0.15).coerceAtLeast(0.5)
            return AxisRange(kotlin.math.floor(min - pad), kotlin.math.ceil(max + pad))
        }

        fun niceCeil(value: Double): Double {
            if (value <= 0) return 1.0
            val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(value)))
            val steps = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
            return steps.first { it * magnitude >= value } * magnitude
        }
    }
}
