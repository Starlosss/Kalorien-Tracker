package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.DailyTotals
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.model.WeightEntry
import com.kalorientracker.app.domain.model.sum
import kotlin.math.abs

enum class StatsRange(val label: String, val days: Long?) {
    WEEK("7 Tage", 7),
    MONTH("Monat", 30),
    QUARTER("3 Monate", 91),
    HALF_YEAR("6 Monate", 182),
    YEAR("1 Jahr", 365),
    ALL("Alles", null),
    CUSTOM("Eigener", null),
}

/** One chart point. [value] is null when nothing was tracked in that bucket. */
data class SeriesPoint(val startEpochDay: Long, val value: Double?)

data class PeriodSummary(
    val trackedDays: Int,
    val totalDays: Int,
    val averages: Nutrients,
    val daysOnTarget: Int,
    val weightStart: Double?,
    val weightEnd: Double?,
) {
    val weightChange: Double? get() = if (weightStart != null && weightEnd != null) weightEnd - weightStart else null
}

class BuildStatisticsUseCase {

    fun bucketDays(spanDays: Long): Int = when {
        spanDays <= 62 -> 1
        spanDays <= 400 -> 7
        else -> 30
    }

    fun series(
        metric: Metric,
        startEpochDay: Long,
        endEpochDay: Long,
        daily: List<DailyTotals>,
        weights: List<WeightEntry>,
    ): List<SeriesPoint> {
        val bucket = bucketDays(endEpochDay - startEpochDay + 1)
        val values: Map<Long, Double> = if (metric == Metric.WEIGHT) {
            weights.associate { it.epochDay to it.weightKg }
        } else {
            daily.filter { it.mealCount > 0 }.associate { it.epochDay to it.nutrients.valueOf(metric) }
        }
        val points = ArrayList<SeriesPoint>()
        var day = startEpochDay
        while (day <= endEpochDay) {
            val last = minOf(day + bucket - 1, endEpochDay)
            val inBucket = (day..last).mapNotNull { values[it] }
            points += SeriesPoint(day, if (inBucket.isEmpty()) null else inBucket.average())
            day += bucket
        }
        return points
    }

    fun summary(
        startEpochDay: Long,
        endEpochDay: Long,
        daily: List<DailyTotals>,
        weights: List<WeightEntry>,
        targets: GoalTargets?,
    ): PeriodSummary {
        val tracked = daily.filter { it.epochDay in startEpochDay..endEpochDay && it.mealCount > 0 }
        val averages = if (tracked.isEmpty()) Nutrients.ZERO else tracked.map { it.nutrients }.sum() * (1.0 / tracked.size)
        val onTarget = if (targets == null) 0 else tracked.count { isOnTarget(it.nutrients.kcal, targets.targetKcal) }
        val rangeWeights = weights.filter { it.epochDay in startEpochDay..endEpochDay }.sortedBy { it.epochDay }
        return PeriodSummary(
            trackedDays = tracked.size,
            totalDays = (endEpochDay - startEpochDay + 1).toInt(),
            averages = averages,
            daysOnTarget = onTarget,
            weightStart = rangeWeights.firstOrNull()?.weightKg,
            weightEnd = rangeWeights.lastOrNull()?.weightKg,
        )
    }

    companion object {
        const val TARGET_TOLERANCE = 0.10

        fun isOnTarget(kcal: Double, targetKcal: Int): Boolean =
            targetKcal > 0 && abs(kcal - targetKcal) <= targetKcal * TARGET_TOLERANCE
    }
}
