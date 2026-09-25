package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns a maintenance estimate into a goal-specific calorie target and nutrient targets. */
class CalculateMacroTargetsUseCase {

    operator fun invoke(
        profile: UserProfile,
        maintenanceKcal: Int,
        effectiveFromEpochDay: Long,
        overrideTargetKcal: Int? = null,
    ): GoalTargets {
        val target = overrideTargetKcal ?: targetKcalFor(profile.goal, maintenanceKcal, profile.sex)
        return forKcal(profile, maintenanceKcal, target, effectiveFromEpochDay, isUserAdjusted = overrideTargetKcal != null)
    }

    fun forKcal(
        profile: UserProfile,
        maintenanceKcal: Int,
        targetKcal: Int,
        effectiveFromEpochDay: Long,
        isUserAdjusted: Boolean,
    ): GoalTargets {
        val kcal = targetKcal.toDouble()
        val proteinPerKg = proteinPerKg(profile.goal)
        val protein = (proteinPerKg * referenceWeight(profile)).coerceAtMost(kcal * MAX_PROTEIN_SHARE / 4.0)
        val fat = max(kcal * FAT_SHARE / 9.0, MIN_FAT_PER_KG * profile.weightKg)
        val carbs = ((kcal - protein * 4.0 - fat * 9.0) / 4.0).coerceAtLeast(MIN_CARBS_G)
        return GoalTargets(
            effectiveFromEpochDay = effectiveFromEpochDay,
            maintenanceKcal = maintenanceKcal,
            targetKcal = targetKcal,
            proteinG = protein.roundToInt(),
            carbsG = carbs.roundToInt(),
            fatG = fat.roundToInt(),
            fiberG = (kcal / 1000.0 * 14.0).roundToInt(),
            sugarMaxG = (kcal * 0.10 / 4.0).roundToInt(),
            saturatedFatMaxG = (kcal * 0.10 / 9.0).roundToInt(),
            saltMaxG = 6.0,
            isUserAdjusted = isUserAdjusted,
        )
    }

    companion object {
        const val FAT_SHARE = 0.27
        const val MAX_PROTEIN_SHARE = 0.35
        const val MIN_FAT_PER_KG = 0.6
        const val MIN_CARBS_G = 50.0

        fun goalDelta(goal: Goal): Int = when (goal) {
            Goal.LOSE -> -500
            Goal.MAINTAIN -> 0
            Goal.GAIN -> 350
            Goal.MUSCLE_BUILD -> 250
        }

        fun minimumKcal(sex: Sex): Int = when (sex) {
            Sex.MALE -> 1500
            Sex.FEMALE -> 1200
        }

        fun targetKcalFor(goal: Goal, maintenanceKcal: Int, sex: Sex): Int {
            val raw = maintenanceKcal + goalDelta(goal)
            val floor = minOf(minimumKcal(sex), maintenanceKcal)
            return roundToTen(max(raw, floor))
        }

        fun proteinPerKg(goal: Goal): Double = when (goal) {
            Goal.LOSE -> 2.0
            Goal.MAINTAIN -> 1.6
            Goal.GAIN -> 1.8
            Goal.MUSCLE_BUILD -> 2.0
        }

        /** For high body weights protein is based on the target weight (if lower) to avoid inflated targets. */
        fun referenceWeight(profile: UserProfile): Double {
            val target = profile.targetWeightKg
            return if (target != null && target < profile.weightKg) (profile.weightKg + target) / 2.0 else profile.weightKg
        }

        fun roundToTen(value: Int): Int = ((value + 5) / 10) * 10

        /** Approximate weekly weight change caused by a daily calorie difference (7700 kcal ≈ 1 kg). */
        fun weeklyWeightChangeKg(dailyDeltaKcal: Int): Double = dailyDeltaKcal * 7.0 / 7700.0
    }
}
