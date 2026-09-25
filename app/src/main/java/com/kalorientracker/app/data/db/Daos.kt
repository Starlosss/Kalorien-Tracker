package com.kalorientracker.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR barcode = :query
        ORDER BY CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END, length(name)
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE name = :name COLLATE NOCASE ORDER BY source = 'BASE_DB' DESC LIMIT 1")
    suspend fun byName(name: String): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Query("SELECT COUNT(*) FROM foods WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query(
        """
        SELECT f.* FROM foods f
        JOIN (SELECT foodId, COUNT(*) AS uses FROM meal_ingredients WHERE foodId IS NOT NULL GROUP BY foodId) u
        ON u.foodId = f.id
        ORDER BY u.uses DESC
        LIMIT :limit
        """,
    )
    fun observeFrequent(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods")
    suspend fun all(): List<FoodEntity>
}

@Dao
abstract class MealDao {
    @Insert
    abstract suspend fun insertMeal(meal: MealEntity): Long

    @Update
    abstract suspend fun updateMeal(meal: MealEntity)

    @Insert
    abstract suspend fun insertIngredients(items: List<MealIngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMeals(meals: List<MealEntity>)

    @Query("DELETE FROM meal_ingredients WHERE mealId = :mealId")
    abstract suspend fun deleteIngredients(mealId: Long)

    @Query("DELETE FROM meals WHERE id = :id")
    abstract suspend fun deleteMeal(id: Long)

    @Transaction
    open suspend fun insertMealWithIngredients(meal: MealEntity, items: List<MealIngredientEntity>): Long {
        val id = insertMeal(meal.copy(id = 0))
        insertIngredients(items.mapIndexed { index, item -> item.copy(id = 0, mealId = id, position = index) })
        return id
    }

    @Transaction
    open suspend fun replaceMeal(meal: MealEntity, items: List<MealIngredientEntity>) {
        updateMeal(meal)
        deleteIngredients(meal.id)
        insertIngredients(items.mapIndexed { index, item -> item.copy(id = 0, mealId = meal.id, position = index) })
    }

    @Transaction
    @Query("SELECT * FROM meals WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY loggedAt")
    abstract fun observeMeals(startDay: Long, endDay: Long): Flow<List<MealWithIngredients>>

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :id")
    abstract fun observeMeal(id: Long): Flow<MealWithIngredients?>

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :id")
    abstract suspend fun getMeal(id: Long): MealWithIngredients?

    @Transaction
    @Query("SELECT * FROM meals ORDER BY loggedAt DESC LIMIT :limit")
    abstract suspend fun recentMeals(limit: Int): List<MealWithIngredients>

    @Query(
        """
        SELECT m.epochDay AS epochDay,
            SUM(i.kcal100 * i.grams / 100.0) AS kcal,
            SUM(i.protein100 * i.grams / 100.0) AS protein,
            SUM(i.carbs100 * i.grams / 100.0) AS carbs,
            SUM(i.fat100 * i.grams / 100.0) AS fat,
            SUM(i.fiber100 * i.grams / 100.0) AS fiber,
            SUM(i.sugar100 * i.grams / 100.0) AS sugar,
            SUM(i.saturatedFat100 * i.grams / 100.0) AS saturatedFat,
            SUM(i.salt100 * i.grams / 100.0) AS salt,
            COUNT(DISTINCT m.id) AS mealCount
        FROM meals m JOIN meal_ingredients i ON i.mealId = m.id
        WHERE m.epochDay BETWEEN :startDay AND :endDay
        GROUP BY m.epochDay
        ORDER BY m.epochDay
        """,
    )
    abstract fun observeDailyTotals(startDay: Long, endDay: Long): Flow<List<DailyTotalsRow>>

    @Query("SELECT DISTINCT epochDay FROM meals")
    abstract fun observeTrackedDays(): Flow<List<Long>>

    @Query("SELECT MIN(epochDay) FROM meals")
    abstract suspend fun firstDay(): Long?

    @Query("SELECT grams FROM meal_ingredients WHERE foodId = :foodId ORDER BY id DESC LIMIT 1")
    abstract suspend fun lastGramsForFood(foodId: Long): Double?

    @Query("SELECT * FROM meals")
    abstract suspend fun allMeals(): List<MealEntity>

    @Query("SELECT * FROM meal_ingredients")
    abstract suspend fun allIngredients(): List<MealIngredientEntity>

    @Query("UPDATE meals SET photoPaths = :paths WHERE id = :mealId")
    abstract suspend fun setPhotoPaths(mealId: Long, paths: String)

    @Query("UPDATE meals SET photoPaths = ''")
    abstract suspend fun clearAllPhotoPaths()
}

@Dao
interface WeightDao {
    @Upsert
    suspend fun upsert(entry: WeightEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeightEntryEntity>)

    @Query("DELETE FROM weights WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("SELECT * FROM weights ORDER BY epochDay")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weights ORDER BY epochDay DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weights")
    suspend fun all(): List<WeightEntryEntity>
}

@Dao
abstract class ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    abstract fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    abstract suspend fun getProfile(): UserProfileEntity?

    @Upsert
    abstract suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM activities ORDER BY id")
    abstract fun observeActivities(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY id")
    abstract suspend fun getActivities(): List<ActivityEntity>

    @Query("DELETE FROM activities")
    abstract suspend fun deleteActivities()

    @Insert
    abstract suspend fun insertActivities(items: List<ActivityEntity>)

    @Transaction
    open suspend fun saveProfile(profile: UserProfileEntity, activities: List<ActivityEntity>) {
        upsertProfile(profile)
        deleteActivities()
        insertActivities(activities.map { it.copy(id = 0) })
    }
}

@Dao
interface GoalTargetsDao {
    @Insert
    suspend fun insert(targets: GoalTargetsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GoalTargetsEntity>)

    @Query("SELECT * FROM goal_targets ORDER BY effectiveFrom DESC, id DESC LIMIT 1")
    fun observeCurrent(): Flow<GoalTargetsEntity?>

    @Query("SELECT * FROM goal_targets ORDER BY effectiveFrom DESC, id DESC LIMIT 1")
    suspend fun getCurrent(): GoalTargetsEntity?

    @Query("SELECT * FROM goal_targets ORDER BY effectiveFrom, id")
    suspend fun all(): List<GoalTargetsEntity>
}

@Dao
abstract class WorkoutDao {
    @Query("SELECT * FROM workout_plan WHERE id = 0")
    abstract fun observePlan(): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM workout_plan WHERE id = 0")
    abstract suspend fun getPlan(): WorkoutPlanEntity?

    @Query("SELECT * FROM workout_exercises ORDER BY position")
    abstract fun observeExercises(): Flow<List<WorkoutExerciseEntity>>

    @Upsert
    abstract suspend fun upsertPlan(plan: WorkoutPlanEntity)

    @Query("DELETE FROM workout_plan")
    abstract suspend fun deletePlan()

    @Query("DELETE FROM workout_exercises")
    abstract suspend fun deleteExercises()

    @Insert
    abstract suspend fun insertExercises(items: List<WorkoutExerciseEntity>)

    @Transaction
    open suspend fun replacePlan(plan: WorkoutPlanEntity, exercises: List<WorkoutExerciseEntity>) {
        upsertPlan(plan)
        deleteExercises()
        insertExercises(exercises)
    }

    @Transaction
    open suspend fun removePlan() {
        deletePlan()
        deleteExercises()
    }

    @Query("SELECT * FROM workout_log WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay")
    abstract fun observeLog(startDay: Long, endDay: Long): Flow<List<WorkoutLogEntity>>

    @Upsert
    abstract suspend fun upsertLog(entry: WorkoutLogEntity)

    @Query("DELETE FROM workout_log WHERE epochDay = :epochDay")
    abstract suspend fun deleteLog(epochDay: Long)

    @Query("SELECT * FROM workout_exercises ORDER BY position")
    abstract suspend fun allExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_log")
    abstract suspend fun allLogs(): List<WorkoutLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLogs(items: List<WorkoutLogEntity>)
}

@Dao
interface LearningDao {
    @Insert
    suspend fun insertAll(items: List<LearningCorrectionEntity>)

    @Query("SELECT * FROM learning_corrections ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LearningCorrectionEntity>

    @Query("SELECT COUNT(*) FROM learning_corrections")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM learning_corrections")
    suspend fun clear()

    @Query("SELECT * FROM learning_corrections")
    suspend fun all(): List<LearningCorrectionEntity>
}
