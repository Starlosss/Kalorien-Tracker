package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a maintenance estimate into a goal-specific calorie target and nutrient targets.
 *
 * The reference values follow the DGE: fat 30 % of energy (35 % for physically active people),
 * carbohydrates the remainder (> 50 %), fibre at least 30 g and 14.6 g per 1000 kcal, free sugars
 * and saturated fat at most 10 % of energy each, salt 6 g. Protein is the one place where the app
 * aims higher than the DGE's 0.8 g/kg: for the training and weight goals it uses the range of the
 * ISSN position stand (1.4–2.0 g/kg), never less than the DGE reference.
 */
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
        // The share cap keeps the plan balanced, but the DGE minimum wins over it.
        val protein = (proteinPerKg * referenceWeight(profile))
            .coerceAtMost(kcal * MAX_PROTEIN_SHARE / 4.0)
            .coerceAtLeast(DGE_PROTEIN_PER_KG * profile.weightKg)
        val fatShare = if (isPhysicallyActive(profile)) FAT_SHARE_ACTIVE else FAT_SHARE
        val fat = max(kcal * fatShare / 9.0, MIN_FAT_PER_KG * profile.weightKg)
        val carbs = ((kcal - protein * 4.0 - fat * 9.0) / 4.0).coerceAtLeast(MIN_CARBS_G)
        return GoalTargets(
            effectiveFromEpochDay = effectiveFromEpochDay,
            maintenanceKcal = maintenanceKcal,
            targetKcal = targetKcal,
            proteinG = protein.roundToInt(),
            carbsG = carbs.roundToInt(),
            fatG = fat.roundToInt(),
            fiberG = max(MIN_FIBER_G, kcal / 1000.0 * FIBER_PER_1000_KCAL).roundToInt(),
            sugarMaxG = (kcal * 0.10 / 4.0).roundToInt(),
            saturatedFatMaxG = (kcal * 0.10 / 9.0).roundToInt(),
            saltMaxG = 6.0,
            isUserAdjusted = isUserAdjusted,
        )
    }

    companion object {
        /** DGE guideline value for fat, and the higher one it grants physically active people. */
        const val FAT_SHARE = 0.30
        const val FAT_SHARE_ACTIVE = 0.35
        const val MAX_PROTEIN_SHARE = 0.35
        const val MIN_FAT_PER_KG = 0.6
        const val MIN_CARBS_G = 50.0

        /** The DGE grants the higher fat share to people who move a lot or train regularly. */
        fun isPhysicallyActive(profile: UserProfile): Boolean {
            val pal = CalculateTdeeUseCase.palForSteps(profile.dailySteps)
            val sport = profile.activities.sumOf { CalculateTdeeUseCase.sportKcalPerDay(it, profile.weightKg) }
            return CalculateTdeeUseCase.isPhysicallyActive(pal, sport.roundToInt())
        }

        /** DGE reference value for adults aged 19 to 65; the app never targets less. */
        const val DGE_PROTEIN_PER_KG = 0.8
        const val FIBER_PER_1000_KCAL = 14.6
        const val MIN_FIBER_G = 30.0

        const val SOURCE_NUTRIENTS = "DGE-Referenzwerte für die Nährstoffzufuhr"
        const val SOURCE_PROTEIN_SPORT = "ISSN Position Stand: Protein and Exercise (2017)"
        const val SOURCE_DEFICIT = "S3-Leitlinie Adipositas (DAG u. a.)"

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

        /**
         * Approximate weekly weight change from a daily calorie difference (7700 kcal ≈ 1 kg fat).
         * The S3 obesity guideline expects about 0.5 kg per week from a 500 kcal daily deficit,
         * which this matches.
         */
        fun weeklyWeightChangeKg(dailyDeltaKcal: Int): Double = dailyDeltaKcal * 7.0 / 7700.0
    }
}
