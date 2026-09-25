package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.SportActivity
import com.kalorientracker.app.domain.model.UserProfile
import kotlin.math.roundToInt

data class TdeeResult(
    val bmr: Int,
    val stepMultiplier: Double,
    val everydayKcal: Int,
    val sportKcalPerDay: Int,
    val workoutKcalPerDay: Int,
    val tdee: Int,
)

/** Metabolic equivalents for the activities offered in onboarding. */
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
 * Mifflin-St Jeor BMR, scaled by an everyday-movement factor derived from daily steps,
 * plus the net energy of planned sport sessions averaged over the week.
 */
class CalculateTdeeUseCase {

    operator fun invoke(profile: UserProfile, workoutKcalPerDay: Int = 0): TdeeResult {
        val bmr = bmr(profile.weightKg, profile.heightCm, profile.age, profile.sex)
        val multiplier = stepMultiplier(profile.dailySteps)
        val everyday = bmr * multiplier
        val sport = profile.activities.sumOf { sportKcalPerDay(it, profile.weightKg) }
        val tdee = everyday + sport + workoutKcalPerDay
        return TdeeResult(
            bmr = bmr.roundToInt(),
            stepMultiplier = multiplier,
            everydayKcal = everyday.roundToInt(),
            sportKcalPerDay = sport.roundToInt(),
            workoutKcalPerDay = workoutKcalPerDay,
            tdee = tdee.roundToInt(),
        )
    }

    companion object {
        fun bmr(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
            val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
            return when (sex) {
                Sex.MALE -> base + 5.0
                Sex.FEMALE -> base - 161.0
            }
        }

        fun stepMultiplier(steps: Int): Double = when {
            steps < 3_000 -> 1.2
            steps < 5_000 -> 1.3
            steps < 7_500 -> 1.4
            steps < 10_000 -> 1.5
            steps < 12_500 -> 1.6
            else -> 1.7
        }

        /** Net kcal above resting (MET − 1) so the BMR is not counted twice. */
        fun sportKcalPerDay(activity: SportActivity, weightKg: Double): Double {
            val met = ActivityCatalog.metFor(activity.name)
            val perSession = (met - 1.0) * weightKg * (activity.minutesPerSession / 60.0)
            return perSession * activity.sessionsPerWeek / 7.0
        }
    }
}
