package com.kalorientracker.app.ui.statistics.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartAnimatorTest {

    @Test
    fun shapeInterpolatesBetweenPointsAndClampsAtEnds() {
        val shape = ChartShape.of(listOf(10.0, null, 30.0, 40.0))
        assertEquals(3, shape.points.size)
        assertEquals(10.0, shape.valueAt(0f)!!, 1e-9)
        assertEquals(40.0, shape.valueAt(1f)!!, 1e-9)
        assertEquals(20.0, shape.valueAt(0.375f)!!, 1e-6)
        assertNull(ChartShape.EMPTY.valueAt(0.5f))
    }

    @Test
    fun morphStartsFromOldShapeAndEndsAtNewValues() {
        val old = ChartShape.of(listOf(100.0, 200.0))
        val new = ChartShape.of(listOf(300.0, 300.0, 300.0))
        val morph = ChartMorph(old, new)
        val x = new.points[1].first
        assertEquals(150.0, morph.valueAt(x, 300.0, 0f), 1e-6)
        assertEquals(300.0, morph.valueAt(x, 300.0, 1f), 1e-6)
        assertEquals(225.0, morph.valueAt(x, 300.0, 0.5f), 1e-6)
        assertEquals(225.0, morph.snapshot(0.5f).valueAt(x)!!, 1e-6)
    }

    @Test
    fun emptyStartGrowsFromTargetWithoutJump() {
        val morph = ChartMorph(ChartShape.EMPTY, ChartShape.of(listOf(5.0)))
        assertEquals(5.0, morph.valueAt(0.5f, 5.0, 0f), 0.0)
    }

    @Test
    fun axisRangesAreReadable() {
        assertEquals(AxisRange(0.0, 2500.0), AxisRange.of(listOf(1800.0, 2300.0), 2200.0, zeroBased = true))
        assertEquals(1.0, AxisRange.niceCeil(0.0), 0.0)
        val weight = AxisRange.of(listOf(80.2, 81.0), 78.0, zeroBased = false)
        assertEquals(77.0, weight.min, 0.0)
        assertEquals(82.0, weight.max, 0.0)
    }
}
