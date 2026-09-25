package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.DailyTotals
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.WeightEntry
import kotlin.math.abs
import kotlin.math.roundToInt

data class RecalibrationSuggestion(
    val observedMaintenanceKcal: Int,
    val currentMaintenanceKcal: Int,
    val suggestedTargetKcal: Int,
    val averageIntakeKcal: Int,
    val weeklyWeightChangeKg: Double,
    val daysAnalyzed: Int,
)

/**
 * Compares logged intake with the actual weight trend. If the implied maintenance differs clearly
 * from the current plan, a new target is suggested — it is never applied without confirmation.
 */
class SuggestRecalibrationUseCase {

    operator fun invoke(
        weights: List<WeightEntry>,
        dailyTotals: List<DailyTotals>,
        current: GoalTargets,
        goal: Goal,
        sex: Sex,
        todayEpochDay: Long,
    ): RecalibrationSuggestion? {
        val windowStart = todayEpochDay - WINDOW_DAYS + 1
        val windowWeights = weights.filter { it.epochDay in windowStart..todayEpochDay }.sortedBy { it.epochDay }
        if (windowWeights.size < MIN_WEIGHT_ENTRIES) return null
        val span = windowWeights.last().epochDay - windowWeights.first().epochDay
        if (span < MIN_SPAN_DAYS) return null

        val intakeDays = dailyTotals.filter { it.epochDay in windowStart..todayEpochDay && it.mealCount > 0 }
        if (intakeDays.size < MIN_INTAKE_DAYS) return null

        val avgIntake = intakeDays.map { it.nutrients.kcal }.average()
        val slopePerDay = slopeKgPerDay(windowWeights)
        val observed = (avgIntake - slopePerDay * KCAL_PER_KG).coerceIn(1200.0, 5000.0).roundToInt()

        if (abs(observed - current.maintenanceKcal) < MIN_DIFFERENCE_KCAL) return null

        val rounded = CalculateMacroTargetsUseCase.roundToTen(observed)
        return RecalibrationSuggestion(
            observedMaintenanceKcal = rounded,
            currentMaintenanceKcal = current.maintenanceKcal,
            suggestedTargetKcal = CalculateMacroTargetsUseCase.targetKcalFor(goal, rounded, sex),
            averageIntakeKcal = avgIntake.roundToInt(),
            weeklyWeightChangeKg = slopePerDay * 7.0,
            daysAnalyzed = intakeDays.size,
        )
    }

    companion object {
        const val WINDOW_DAYS = 28L
        const val MIN_SPAN_DAYS = 14L
        const val MIN_WEIGHT_ENTRIES = 4
        const val MIN_INTAKE_DAYS = 10
        const val MIN_DIFFERENCE_KCAL = 150
        const val KCAL_PER_KG = 7700.0

        /** Least-squares slope, robust against day-to-day water fluctuations. */
        fun slopeKgPerDay(entries: List<WeightEntry>): Double {
            if (entries.size < 2) return 0.0
            val xs = entries.map { it.epochDay.toDouble() }
            val ys = entries.map { it.weightKg }
            val mx = xs.average()
            val my = ys.average()
            var num = 0.0
            var den = 0.0
            for (i in xs.indices) {
                num += (xs[i] - mx) * (ys[i] - my)
                den += (xs[i] - mx) * (xs[i] - mx)
            }
            return if (den == 0.0) 0.0 else num / den
        }
    }
}
