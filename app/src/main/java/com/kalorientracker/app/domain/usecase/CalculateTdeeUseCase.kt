package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.SportActivity
import com.kalorientracker.app.domain.model.UserProfile
import kotlin.math.roundToInt

data class TdeeResult(
    /** Resting energy expenditure in kcal per day, as the DGE derives its reference values. */
    val restingKcal: Int,
    val pal: Double,
    val everydayKcal: Int,
    val sportKcalPerDay: Int,
    val workoutKcalPerDay: Int,
    val tdee: Int,
)

/**
 * Metabolic equivalents from the Compendium of Physical Activities (Ainsworth et al.),
 * the reference table behind practically every MET-based estimate.
 */
object ActivityCatalog {
    const val HOME_WORKOUT = "Home-Workout"

    val defaults: List<String> = listOf("Laufen", "Radfahren", "Fußball", "Schwimmen", HOME_WORKOUT)

    private val metByName = mapOf(
        "laufen" to 9.0,
        "joggen" to 8.0,
        "radfahren" to 7.0,
        "fußball" to 7.5,
        "fussball" to 7.5,
        "schwimmen" to 6.0,
        "home-workout" to 4.5,
        "krafttraining" to 5.0,
        "wandern" to 5.5,
        "tanzen" to 5.0,
        "yoga" to 3.0,
        "tennis" to 7.0,
        "basketball" to 6.5,
        "klettern" to 7.5,
    )

    const val DEFAULT_MET = 5.0

    fun metFor(name: String): Double = metByName[name.trim().lowercase()] ?: DEFAULT_MET
}

/**
 * Daily energy need the way the German Nutrition Society (DGE) derives its reference values:
 * resting energy expenditure × PAL (physical activity level).
 *
 * The app picks the PAL from the daily step count and adds planned sport on top, which is what
 * the DGE allows for people who train several times a week.
 */
class CalculateTdeeUseCase {

    operator fun invoke(profile: UserProfile, workoutKcalPerDay: Int = 0): TdeeResult {
        val resting = restingKcal(profile.weightKg, profile.age, profile.sex)
        val pal = palForSteps(profile.dailySteps)
        val everyday = resting * pal
        val sport = profile.activities.sumOf { sportKcalPerDay(it, profile.weightKg) }
        val tdee = everyday + sport + workoutKcalPerDay
        return TdeeResult(
            restingKcal = resting.roundToInt(),
            pal = pal,
            everydayKcal = everyday.roundToInt(),
            sportKcalPerDay = sport.roundToInt(),
            workoutKcalPerDay = workoutKcalPerDay,
            tdee = tdee.roundToInt(),
        )
    }

    companion object {
        /** Sources shown in the app so every number can be checked. */
        const val SOURCE_ENERGY = "DGE: Fragen und Antworten zur Energiezufuhr (2015)"
        const val SOURCE_MET = "Compendium of Physical Activities (Ainsworth et al.)"

        /**
         * Resting energy expenditure in kcal per day, exactly the equation the DGE publishes
         * (result in MJ, converted with 239 kcal/MJ). It deliberately uses weight and age only.
         */
        fun restingKcal(weightKg: Double, age: Int, sex: Sex): Double {
            val megajoules = when (sex) {
                Sex.MALE -> 0.047 * weightKg + 1.009 - 0.01452 * age + 3.21
                Sex.FEMALE -> 0.047 * weightKg - 0.01452 * age + 3.21
            }
            return megajoules * KCAL_PER_MJ
        }

        /**
         * PAL for everyday movement. The DGE's categories: 1.4–1.5 almost only sitting,
         * 1.6–1.7 sitting plus some walking and standing, 1.8–1.9 mostly standing and walking.
         * Sport is not part of this – it is added separately.
         */
        fun palForSteps(steps: Int): Double = when {
            steps < 3_000 -> 1.4
            steps < 5_000 -> 1.5
            steps < 7_500 -> 1.6
            steps < 10_000 -> 1.7
            steps < 12_500 -> 1.8
            else -> 1.9
        }

        fun palLabel(pal: Double): String = when {
            pal <= 1.5 -> "überwiegend sitzend"
            pal <= 1.7 -> "sitzend mit etwas Bewegung"
            else -> "viel auf den Beinen"
        }

        /** True from the activity level at which the DGE allows a higher fat share. */
        fun isPhysicallyActive(pal: Double, sportKcalPerDay: Int): Boolean = pal >= 1.7 || sportKcalPerDay >= 300

        /** Net kcal above resting (MET − 1) so the resting energy is not counted twice. */
        fun sportKcalPerDay(activity: SportActivity, weightKg: Double): Double {
            val met = ActivityCatalog.metFor(activity.name)
            val perSession = (met - 1.0) * weightKg * (activity.minutesPerSession / 60.0)
            return perSession * activity.sessionsPerWeek / 7.0
        }

        private const val KCAL_PER_MJ = 239.0
    }
}
