package com.kalorientracker.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

/** Nutrient columns are always per 100 g. */
@Serializable
@Entity(
    tableName = "foods",
    indices = [Index(value = ["barcode"], unique = true), Index(value = ["name"])],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val saturatedFat: Double,
    val salt: Double,
    val servingGrams: Double? = null,
    val source: String,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "meals", indices = [Index(value = ["epochDay"])])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val loggedAt: Long,
    val epochDay: Long,
    val category: String,
    /** Absolute paths of the meal's photos, newline separated. */
    val photoPaths: String = "",
    val description: String? = null,
)

/**
 * Nutrients are snapshotted per 100 g when the ingredient is added, so later edits of a food
 * never change historical meals. Totals are always grams × per-100 g values.
 */
@Serializable
@Entity(
    tableName = "meal_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mealId"]), Index(value = ["foodId"])],
)
data class MealIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodId: Long? = null,
    val position: Int = 0,
    val name: String,
    val grams: Double,
    val estimatedGrams: Double? = null,
    val confidence: String? = null,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val fiber100: Double,
    val sugar100: Double,
    val saturatedFat100: Double,
    val salt100: Double,
)

data class MealWithIngredients(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val ingredients: List<MealIngredientEntity>,
)

data class DailyTotalsRow(
    val epochDay: Long,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val sugar: Double,
    val saturatedFat: Double,
    val salt: Double,
    val mealCount: Int,
)

@Serializable
@Entity(tableName = "weights")
data class WeightEntryEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Double,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val targetWeightKg: Double? = null,
    val sex: String,
    val goal: String,
    val dailySteps: Int,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}

@Serializable
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sessionsPerWeek: Int,
    val minutesPerSession: Int,
)

/** Versioned: a new row is added whenever the plan changes, so statistics keep the history. */
@Serializable
@Entity(tableName = "goal_targets", indices = [Index(value = ["effectiveFrom"])])
data class GoalTargetsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveFrom: Long,
    val maintenanceKcal: Int,
    val targetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    val sugarMaxG: Int,
    val saturatedFatMaxG: Int,
    val saltMaxG: Double,
    val isUserAdjusted: Boolean,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "workout_plan")
data class WorkoutPlanEntity(
    @PrimaryKey val id: Int = 0,
    val frequencyPerWeek: Int,
    val minutesPerSession: Int,
    val difficulty: String,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "workout_exercises")
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
)

@Serializable
@Entity(tableName = "workout_log")
data class WorkoutLogEntity(
    @PrimaryKey val epochDay: Long,
    val completedAt: Long,
)

/** Personal learning layer. Never modifies [FoodEntity]; only biases future estimates. */
@Serializable
@Entity(tableName = "learning_corrections", indices = [Index(value = ["foodKey"])])
data class LearningCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodKey: String,
    val originalGrams: Double,
    val correctedGrams: Double,
    val createdAt: Long,
)
