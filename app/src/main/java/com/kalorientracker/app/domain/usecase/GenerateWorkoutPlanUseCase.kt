package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.Difficulty
import com.kalorientracker.app.domain.model.WorkoutExercise
import com.kalorientracker.app.domain.model.WorkoutPlan
import kotlin.math.roundToInt

/** Small bodyweight plan without equipment, sized to the available time. */
class GenerateWorkoutPlanUseCase {

    private data class Template(
        val names: Map<Difficulty, String>,
        val reps: Map<Difficulty, Int>? = null,
        val seconds: Map<Difficulty, Int>? = null,
    )

    private fun same(name: String) = Difficulty.entries.associateWith { name }
    private fun values(easy: Int, medium: Int, hard: Int) =
        mapOf(Difficulty.EASY to easy, Difficulty.MEDIUM to medium, Difficulty.HARD to hard)

    private val pool = listOf(
        Template(
            names = mapOf(
                Difficulty.EASY to "Liegestütze auf Knien",
                Difficulty.MEDIUM to "Liegestütze",
                Difficulty.HARD to "Liegestütze (langsam)",
            ),
            reps = values(8, 12, 18),
        ),
        Template(same("Kniebeugen"), reps = values(12, 18, 25)),
        Template(same("Glute Bridges"), reps = values(12, 18, 25)),
        Template(same("Plank"), seconds = values(25, 40, 60)),
        Template(same("Ausfallschritte (je Seite)"), reps = values(8, 12, 16)),
        Template(same("Crunches"), reps = values(12, 18, 25)),
        Template(same("Superman"), reps = values(10, 14, 18)),
        Template(same("Wandsitzen"), seconds = values(30, 45, 60)),
    )

    operator fun invoke(frequencyPerWeek: Int, minutesPerSession: Int, difficulty: Difficulty): WorkoutPlan {
        val exerciseCount = when {
            minutesPerSession <= 10 -> 3
            minutesPerSession <= 15 -> 4
            minutesPerSession <= 20 -> 5
            minutesPerSession <= 30 -> 6
            minutesPerSession <= 40 -> 7
            else -> 8
        }
        val preferredSets = when (difficulty) {
            Difficulty.EASY -> 2
            Difficulty.MEDIUM -> 3
            Difficulty.HARD -> 4
        }
        val fittingSets = (minutesPerSession / (exerciseCount * MINUTES_PER_SET)).toInt()
        val sets = preferredSets.coerceAtMost(fittingSets).coerceAtLeast(2)

        val exercises = pool.take(exerciseCount).map { t ->
            WorkoutExercise(
                name = t.names.getValue(difficulty),
                sets = sets,
                reps = t.reps?.get(difficulty),
                durationSeconds = t.seconds?.get(difficulty),
            )
        }
        return WorkoutPlan(
            frequencyPerWeek = frequencyPerWeek.coerceIn(1, 7),
            minutesPerSession = minutesPerSession,
            difficulty = difficulty,
            exercises = exercises,
        )
    }

    companion object {
        const val MINUTES_PER_SET = 1.75
        const val WORKOUT_MET = 4.5

        fun estimatedKcalPerDay(plan: WorkoutPlan, weightKg: Double): Int =
            ((WORKOUT_MET - 1.0) * weightKg * plan.minutesPerSession / 60.0 * plan.frequencyPerWeek / 7.0).roundToInt()
    }
}
