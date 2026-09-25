package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.DailyTotals
import com.kalorientracker.app.domain.model.Difficulty
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Ingredient
import com.kalorientracker.app.domain.model.LearningCorrection
import com.kalorientracker.app.domain.model.MealCategory
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TrackingLogicTest {

    @Test
    fun mealTotalsRecalculateWhenGramsChange() {
        val rice = Ingredient(name = "Reis", grams = 200.0, per100g = Nutrients(kcal = 130.0, protein = 2.7))
        val chicken = Ingredient(name = "Hähnchen", grams = 160.0, per100g = Nutrients(kcal = 165.0, protein = 31.0))
        val recalc = RecalculateMealTotalsUseCase()
        assertEquals(260.0 + 264.0, recalc(listOf(rice, chicken)).kcal, 0.001)
        assertEquals(260.0 + 330.0, recalc(listOf(rice, chicken.copy(grams = 200.0))).kcal, 0.001)
        assertEquals((260.0 + 264.0) * 1.15, recalc(listOf(rice, chicken), portionFactor = 1.15).kcal, 0.001)
    }

    @Test
    fun mealCategoryFollowsTimeOfDay() {
        val suggest = SuggestMealCategoryUseCase()
        assertEquals(MealCategory.BREAKFAST, suggest(LocalTime.of(8, 12)))
        assertEquals(MealCategory.LUNCH, suggest(LocalTime.of(13, 24)))
        assertEquals(MealCategory.SNACK, suggest(LocalTime.of(16, 40)))
        assertEquals(MealCategory.DINNER, suggest(LocalTime.of(19, 31)))
        assertEquals(MealCategory.SNACK, suggest(LocalTime.of(23, 10)))
    }

    @Test
    fun portionScaleHasSevenStepsAroundNormal() {
        assertEquals(7, PortionScale.steps.size)
        assertEquals(1.0, PortionScale.factor(PortionScale.NORMAL_INDEX), 0.0)
        assertEquals(5, PortionScale.nearestIndex(1.3))
        assertEquals(0, PortionScale.nearestIndex(0.1))
    }

    @Test
    fun streakCountsConsecutiveDays() {
        val streak = ComputeStreakUseCase()
        assertEquals(3, streak(setOf(10L, 9L, 8L, 6L), todayEpochDay = 10))
        assertEquals(3, streak(setOf(9L, 8L, 7L), todayEpochDay = 10))
        assertEquals(0, streak(setOf(7L), todayEpochDay = 10))
        assertEquals(0, streak(emptySet(), todayEpochDay = 10))
    }

    @Test
    fun foodKeyIgnoresPreparationDetails() {
        assertEquals("reis", FoodKey.of("Reis (gekocht)"))
        assertEquals("hähnchenbrust", FoodKey.of("  Hähnchenbrust (gebraten) "))
    }

    @Test
    fun learningUsesMedianOfRecentCorrections() {
        val use = ApplyLearningCorrectionsUseCase()
        val hints = use(
            listOf(
                LearningCorrection("reis", 150.0, 220.0, 1),
                LearningCorrection("reis", 150.0, 210.0, 2),
                LearningCorrection("öl", 10.0, 10.5, 3),
                LearningCorrection("öl", 10.0, 10.2, 4),
                LearningCorrection("nudeln", 200.0, 400.0, 5),
            ),
        )
        assertEquals(1, hints.size)
        assertEquals("reis", hints[0].foodKey)
        assertEquals((220.0 / 150 + 210.0 / 150) / 2, hints[0].ratio, 0.0001)
        assertTrue(ApplyLearningCorrectionsUseCase.isMeaningful(150.0, 220.0))
        assertTrue(!ApplyLearningCorrectionsUseCase.isMeaningful(150.0, 155.0))
    }

    private fun targets(maintenance: Int) = GoalTargets(0, maintenance, maintenance, 150, 250, 70, 30, 55, 24, 6.0)

    @Test
    fun recalibrationDetectsHigherRealExpenditure() {
        val today = 100L
        val intake = (73L..100L).map { DailyTotals(it, Nutrients(kcal = 2000.0), 3) }
        val weights = (73L..100L step 3).map { WeightEntry(it, 80.0 - (it - 73) * (0.5 / 7.0)) }
        val suggestion = SuggestRecalibrationUseCase()(weights, intake, targets(2200), Goal.LOSE, Sex.MALE, today)
        assertNotNull(suggestion)
        suggestion!!
        assertEquals(2550, suggestion.observedMaintenanceKcal)
        assertEquals(2050, suggestion.suggestedTargetKcal)
        assertEquals(-0.5, suggestion.weeklyWeightChangeKg, 0.01)
    }

    @Test
    fun recalibrationNeedsEnoughDataAndDifference() {
        val use = SuggestRecalibrationUseCase()
        val intake = (73L..100L).map { DailyTotals(it, Nutrients(kcal = 2200.0), 3) }
        val stable = (73L..100L step 3).map { WeightEntry(it, 80.0) }
        assertNull(use(stable, intake, targets(2200), Goal.MAINTAIN, Sex.MALE, 100))
        assertNull(use(stable.take(2), intake, targets(1800), Goal.MAINTAIN, Sex.MALE, 100))
        assertNull(use(stable, intake.take(5), targets(1800), Goal.MAINTAIN, Sex.MALE, 100))
    }

    @Test
    fun workoutPlanFitsAvailableTime() {
        val use = GenerateWorkoutPlanUseCase()
        val short = use(3, 10, Difficulty.EASY)
        assertEquals(3, short.exercises.size)
        assertEquals("Liegestütze auf Knien", short.exercises[0].name)
        val medium = use(3, 20, Difficulty.MEDIUM)
        assertEquals(5, medium.exercises.size)
        assertTrue(medium.exercises.all { it.sets * medium.exercises.size * GenerateWorkoutPlanUseCase.MINUTES_PER_SET <= 20 })
        val hard = use(4, 45, Difficulty.HARD)
        assertEquals(8, hard.exercises.size)
        assertEquals(3, hard.exercises[0].sets)
        assertEquals(60, hard.exercises.first { it.name == "Plank" }.durationSeconds)
        assertEquals(60, GenerateWorkoutPlanUseCase.estimatedKcalPerDay(medium.copy(minutesPerSession = 30), 80.0))
    }

    @Test
    fun statisticsBucketsAndAverages() {
        val stats = BuildStatisticsUseCase()
        assertEquals(1, stats.bucketDays(30))
        assertEquals(7, stats.bucketDays(182))
        assertEquals(30, stats.bucketDays(800))

        val daily = listOf(
            DailyTotals(1, Nutrients(kcal = 2000.0, protein = 100.0), 3),
            DailyTotals(2, Nutrients(kcal = 2400.0, protein = 140.0), 4),
            DailyTotals(4, Nutrients(kcal = 1000.0), 0),
        )
        val series = stats.series(Metric.CALORIES, 1, 4, daily, emptyList())
        assertEquals(listOf(2000.0, 2400.0, null, null), series.map { it.value })

        val summary = stats.summary(1, 7, daily, listOf(WeightEntry(1, 81.0), WeightEntry(6, 80.2)), targets(2200))
        assertEquals(2, summary.trackedDays)
        assertEquals(2200.0, summary.averages.kcal, 0.001)
        assertEquals(120.0, summary.averages.protein, 0.001)
        assertEquals(2, summary.daysOnTarget)
        assertEquals(-0.8, summary.weightChange!!, 0.0001)

        val weekly = stats.series(Metric.WEIGHT, 0, 69, emptyList(), listOf(WeightEntry(0, 80.0), WeightEntry(3, 81.0)))
        assertEquals(10, weekly.size)
        assertEquals(80.5, weekly[0].value!!, 0.0001)
    }
}
