package com.kalorientracker.app.domain.model

import java.time.LocalDate

data class Food(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val per100g: Nutrients,
    val servingGrams: Double? = null,
    val source: FoodSource = FoodSource.USER_ADDED,
) {
    val displayName: String get() = if (brand.isNullOrBlank()) name else "$name · $brand"
}

data class Ingredient(
    val id: Long = 0,
    val foodId: Long? = null,
    val name: String,
    val grams: Double,
    val per100g: Nutrients,
    val estimatedGrams: Double? = null,
    val confidence: Confidence? = null,
) {
    val nutrients: Nutrients get() = per100g.forGrams(grams)
}

data class Meal(
    val id: Long = 0,
    val name: String,
    val loggedAtMillis: Long,
    val epochDay: Long,
    val category: MealCategory,
    val photoPaths: List<String> = emptyList(),
    val description: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
) {
    val totals: Nutrients get() = ingredients.map { it.nutrients }.sum()
}

data class WeightEntry(
    val epochDay: Long,
    val weightKg: Double,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

data class SportActivity(
    val name: String,
    val sessionsPerWeek: Int,
    val minutesPerSession: Int,
)

data class UserProfile(
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double?,
    val sex: Sex,
    val goal: Goal,
    val dailySteps: Int,
    val activities: List<SportActivity> = emptyList(),
)

data class GoalTargets(
    val effectiveFromEpochDay: Long,
    val maintenanceKcal: Int,
    val targetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    val sugarMaxG: Int,
    val saturatedFatMaxG: Int,
    val saltMaxG: Double,
    val isUserAdjusted: Boolean = false,
) {
    fun targetFor(metric: Metric): Double? = when (metric) {
        Metric.CALORIES -> targetKcal.toDouble()
        Metric.PROTEIN -> proteinG.toDouble()
        Metric.CARBS -> carbsG.toDouble()
        Metric.FAT -> fatG.toDouble()
        Metric.WEIGHT -> null
    }
}

/** Aggregated intake of one calendar day. */
data class DailyTotals(
    val epochDay: Long,
    val nutrients: Nutrients,
    val mealCount: Int,
)

data class WorkoutExercise(
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
) {
    val prescription: String
        get() = when {
            reps != null -> "$sets × $reps"
            durationSeconds != null -> "$sets × ${durationSeconds} s"
            else -> "$sets Sätze"
        }
}

data class WorkoutPlan(
    val frequencyPerWeek: Int,
    val minutesPerSession: Int,
    val difficulty: Difficulty,
    val exercises: List<WorkoutExercise>,
)

/** Personal bias for a food: multiply the analyzer's gram estimate by [ratio]. */
data class CorrectionHint(
    val foodKey: String,
    val ratio: Double,
    val sampleCount: Int,
)

/** A stored correction: the analyzer estimated [originalGrams], the user saved [correctedGrams]. */
data class LearningCorrection(
    val foodKey: String,
    val originalGrams: Double,
    val correctedGrams: Double,
    val createdAtMillis: Long,
)
