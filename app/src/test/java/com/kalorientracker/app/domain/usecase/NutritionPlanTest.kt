package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.SportActivity
import com.kalorientracker.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NutritionPlanTest {

    private val tdee = CalculateTdeeUseCase()
    private val macros = CalculateMacroTargetsUseCase()

    private fun profile(
        goal: Goal = Goal.MAINTAIN,
        sex: Sex = Sex.MALE,
        weight: Double = 80.0,
        steps: Int = 8_000,
        activities: List<SportActivity> = emptyList(),
        target: Double? = null,
    ) = UserProfile(
        age = 30,
        heightCm = 180.0,
        weightKg = weight,
        targetWeightKg = target,
        sex = sex,
        goal = goal,
        dailySteps = steps,
        activities = activities,
    )

    @Test
    fun bmrUsesMifflinStJeor() {
        assertEquals(1780.0, CalculateTdeeUseCase.bmr(80.0, 180.0, 30, Sex.MALE), 0.001)
        assertEquals(1345.25, CalculateTdeeUseCase.bmr(60.0, 165.0, 25, Sex.FEMALE), 0.001)
    }

    @Test
    fun stepsScaleEverydayEnergy() {
        val result = tdee(profile(steps = 8_000))
        assertEquals(1.5, result.stepMultiplier, 0.0)
        assertEquals(2670, result.tdee)
        assertTrue(tdee(profile(steps = 2_000)).tdee < tdee(profile(steps = 13_000)).tdee)
    }

    @Test
    fun sportAddsNetEnergyAveragedOverWeek() {
        val running = SportActivity("Laufen", sessionsPerWeek = 3, minutesPerSession = 45)
        val result = tdee(profile(activities = listOf(running)))
        assertEquals(206, result.sportKcalPerDay)
        assertEquals(2670 + 206, result.tdee)
    }

    @Test
    fun workoutPlanEnergyIsIncluded() {
        assertEquals(2670 + 150, tdee(profile(), workoutKcalPerDay = 150).tdee)
    }

    @Test
    fun goalShiftsTargetAndRespectsMinimum() {
        assertEquals(2170, CalculateMacroTargetsUseCase.targetKcalFor(Goal.LOSE, 2670, Sex.MALE))
        assertEquals(2670, CalculateMacroTargetsUseCase.targetKcalFor(Goal.MAINTAIN, 2670, Sex.MALE))
        assertEquals(2920, CalculateMacroTargetsUseCase.targetKcalFor(Goal.MUSCLE_BUILD, 2670, Sex.MALE))
        assertEquals(1200, CalculateMacroTargetsUseCase.targetKcalFor(Goal.LOSE, 1500, Sex.FEMALE))
    }

    @Test
    fun macrosAddUpToTarget() {
        for (goal in Goal.entries) {
            val p = profile(goal = goal)
            val targets = macros(p, maintenanceKcal = 2670, effectiveFromEpochDay = 0)
            val kcalFromMacros = targets.proteinG * 4 + targets.carbsG * 4 + targets.fatG * 9
            assertTrue("$goal: $kcalFromMacros vs ${targets.targetKcal}", abs(kcalFromMacros - targets.targetKcal) <= 15)
            assertTrue(targets.proteinG >= 120)
        }
    }

    @Test
    fun proteinFollowsGoal() {
        val maintain = macros(profile(goal = Goal.MAINTAIN), 2670, 0)
        val muscle = macros(profile(goal = Goal.MUSCLE_BUILD), 2670, 0)
        assertEquals(128, maintain.proteinG)
        assertEquals(160, muscle.proteinG)
    }

    @Test
    fun manualOverrideIsMarkedAsUserAdjusted() {
        val targets = macros(profile(goal = Goal.LOSE), 2670, 0, overrideTargetKcal = 2000)
        assertEquals(2000, targets.targetKcal)
        assertTrue(targets.isUserAdjusted)
    }

    @Test
    fun weeklyWeightChangeFromDailyDelta() {
        assertEquals(-0.4545, CalculateMacroTargetsUseCase.weeklyWeightChangeKg(-500), 0.001)
    }
}
