package com.kalorientracker.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodEntity::class,
        MealEntity::class,
        MealIngredientEntity::class,
        WeightEntryEntity::class,
        UserProfileEntity::class,
        ActivityEntity::class,
        GoalTargetsEntity::class,
        WorkoutPlanEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutLogEntity::class,
        LearningCorrectionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun weightDao(): WeightDao
    abstract fun profileDao(): ProfileDao
    abstract fun goalTargetsDao(): GoalTargetsDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun learningDao(): LearningDao

    companion object {
        const val NAME = "kalorien.db"
    }
}
