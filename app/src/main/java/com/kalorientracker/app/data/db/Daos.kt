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
    /**
     * Ranks a hit by four keys: a name that starts with the query, then the source tier, then the
     * shortest name, then the row id.
     *
     * The source tier is the shared rule. [byName] and [relinkIngredientsByNameChunk] repeat it
     * verbatim, because Room has no way to share a SQL fragment, and every lookup has to agree
     * about which row is the best match for a name:
     *
     * | tier | source | why |
     * |---|---|---|
     * | 0 | `USER_ADDED` | the person typed this in themselves, about their own food |
     * | 1 | `BASE_DB` | curated, and the only rows with complete secondary nutrients |
     * | 2 | `AI_ESTIMATED`, `ONLINE_CACHED` | picked up automatically, an estimate or one brand |
     * | 3 | `CATALOG` | the bundled branded bulk table |
     *
     * The bottom tier is the one that made this necessary: the catalogue holds about 39.960
     * branded rows against 725 curated ones, so without it a search for "Milch" returns seven
     * dairy brands whose name is literally "Milch" and pushes the curated "Milch 1,5 %" off the
     * first screen, and `FoodRepository.bestMatch`, which takes the single top hit, resolves an
     * analyzer ingredient to a branded row that often carries no fibre, sugar, saturated fat or
     * salt. The top tier is the other end of the same argument: a row the person created on
     * purpose says more about what they eat than any table we shipped, so it must not be buried
     * under curated rows with similar names either.
     *
     * A source added later has to be placed here deliberately; the `ELSE` bucket is the automatic
     * middle, not the bulk one, so forgetting is the cheap mistake rather than the expensive one.
     *
     * The trailing `id` is what makes this a *total* order. Without it two rows that tie on every
     * other key come back in whatever order SQLite happens to produce, so the same database could
     * answer the same lookup differently once a query plan changes.
     */
    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' OR barcode = :query
        ORDER BY CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
                 CASE source WHEN 'USER_ADDED' THEN 0 WHEN 'BASE_DB' THEN 1 WHEN 'CATALOG' THEN 3 ELSE 2 END,
                 length(name),
                 id
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    /** The same source tier and the same `id` tiebreaker as [search]; see its table. */
    @Query(
        """
        SELECT * FROM foods WHERE name = :name COLLATE NOCASE
        ORDER BY CASE source WHEN 'USER_ADDED' THEN 0 WHEN 'BASE_DB' THEN 1 WHEN 'CATALOG' THEN 3 ELSE 2 END,
                 id
        LIMIT 1
        """,
    )
    suspend fun byName(name: String): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<FoodEntity>)

    /** Used by the catalogue import: an existing barcode is never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoring(foods: List<FoodEntity>)

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

    /**
     * The foods a backup has to carry: everything the user built up, without the two bundled
     * tables. `BASE_DB` and `CATALOG` ship inside the APK and are re-created on any device, so
     * writing them out only inflated the export (37 KB to 10,8 MB once the catalogue landed).
     * `MealIngredientEntity` has no foreign key to `foods` and snapshots its own per-100 g
     * values, so a `foodId` left dangling by this costs a re-pick, never a wrong total.
     */
    @Query("SELECT * FROM foods WHERE source NOT IN ('BASE_DB', 'CATALOG')")
    suspend fun allUserFoods(): List<FoodEntity>

    /**
     * Relabels rows that an older build imported from `products.csv` under the `ONLINE_CACHED`
     * name it shared with genuine online hits. Matched by barcode against the bundled asset, so
     * a product the user really did fetch online keeps its label and its values; nothing is
     * deleted.
     *
     * Room binds one variable per barcode and the importer hands over a whole chunk at a time,
     * which is far more than a statement may bind on API 26 to 30; see [SQLITE_MAX_BIND_ARGS].
     * The split happens here rather than at the call site so no caller can forget it.
     */
    suspend fun relabelAsCatalog(barcodes: List<String>) =
        forEachBindBatch(barcodes) { relabelChunk(it) }

    /** One statement's worth of [relabelAsCatalog]. Call that, not this. */
    @Query("UPDATE foods SET source = 'CATALOG' WHERE source = 'ONLINE_CACHED' AND barcode IN (:barcodes)")
    suspend fun relabelChunk(barcodes: List<String>)
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

    /**
     * Drops the food link of the named ingredients. Used by a restore before anything mints new
     * food ids, because a `foodId` from the backup file is only meaningful together with the
     * foods the file itself restored. Everything else in `foods` is rebuilt from scratch, and
     * nothing ties the rebuilt rows to the numbers they carried on the machine that wrote the
     * file: seeding and the catalogue import hand out whatever the autoincrement counter offers
     * at that moment. `clearAllTables` does not reset that counter, which is what makes this
     * concrete rather than theoretical: on the same device the counter keeps its high-water mark,
     * so re-seeding after a wipe hands the old catalogue's numbers straight to fresh curated
     * rows. A dangling id costs a re-pick, an id pointing at the wrong food is a correctness bug,
     * so these are cut first and offered back by name afterwards.
     *
     * One bound variable per id, so the list is split here; see [SQLITE_MAX_BIND_ARGS].
     */
    suspend fun clearFoodLinks(ingredientIds: List<Long>) =
        forEachBindBatch(ingredientIds) { clearFoodLinksChunk(it) }

    /** One statement's worth of [clearFoodLinks]. Call that, not this. */
    @Query("UPDATE meal_ingredients SET foodId = NULL WHERE id IN (:ingredientIds)")
    abstract suspend fun clearFoodLinksChunk(ingredientIds: List<Long>)

    /**
     * Re-points the named ingredients at the food that now carries their own name, or leaves them
     * unlinked when nothing matches. Ranked by the same source tier and `id` tiebreaker as
     * [FoodDao.search], so the answer does not depend on row order.
     *
     * It can only ever choose a food whose name equals the name stored on the ingredient, so it
     * cannot produce the silent mismatch that a stale id can.
     *
     * One bound variable per id, so the list is split here; see [SQLITE_MAX_BIND_ARGS].
     */
    suspend fun relinkIngredientsByName(ingredientIds: List<Long>) =
        forEachBindBatch(ingredientIds) { relinkIngredientsByNameChunk(it) }

    /** One statement's worth of [relinkIngredientsByName]. Call that, not this. */
    @Query(
        """
        UPDATE meal_ingredients
        SET foodId = (
            SELECT f.id FROM foods f
            WHERE f.name = meal_ingredients.name COLLATE NOCASE
            ORDER BY CASE f.source WHEN 'USER_ADDED' THEN 0 WHEN 'BASE_DB' THEN 1 WHEN 'CATALOG' THEN 3 ELSE 2 END,
                     f.id
            LIMIT 1
        )
        WHERE id IN (:ingredientIds)
        """,
    )
    abstract suspend fun relinkIngredientsByNameChunk(ingredientIds: List<Long>)

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
