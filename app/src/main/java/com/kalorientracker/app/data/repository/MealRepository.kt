package com.kalorientracker.app.data.repository

import com.kalorientracker.app.data.db.LearningCorrectionEntity
import com.kalorientracker.app.data.db.LearningDao
import com.kalorientracker.app.data.db.MealDao
import com.kalorientracker.app.data.db.joinPaths
import com.kalorientracker.app.data.db.toDomain
import com.kalorientracker.app.data.db.toEntity
import com.kalorientracker.app.data.image.PhotoStorage
import com.kalorientracker.app.domain.model.CorrectionHint
import com.kalorientracker.app.domain.model.DailyTotals
import com.kalorientracker.app.domain.model.LearningCorrection
import com.kalorientracker.app.domain.model.Meal
import com.kalorientracker.app.domain.usecase.ApplyLearningCorrectionsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(
    private val mealDao: MealDao,
    private val learningDao: LearningDao,
    private val photos: PhotoStorage,
) {
    private val applyCorrections = ApplyLearningCorrectionsUseCase()

    fun observeMeals(startDay: Long, endDay: Long): Flow<List<Meal>> =
        mealDao.observeMeals(startDay, endDay).map { list -> list.map { it.toDomain() } }

    fun observeMeal(id: Long): Flow<Meal?> = mealDao.observeMeal(id).map { it?.toDomain() }

    suspend fun getMeal(id: Long): Meal? = mealDao.getMeal(id)?.toDomain()

    fun observeDailyTotals(startDay: Long, endDay: Long): Flow<List<DailyTotals>> =
        mealDao.observeDailyTotals(startDay, endDay).map { rows -> rows.map { it.toDomain() } }

    fun observeTrackedDays(): Flow<Set<Long>> = mealDao.observeTrackedDays().map { it.toSet() }

    suspend fun firstTrackedDay(): Long? = mealDao.firstDay()

    /** Distinct recent meals for quick re-logging. */
    suspend fun recentDistinctMeals(limit: Int = 8): List<Meal> =
        mealDao.recentMeals(60).map { it.toDomain() }
            .filter { it.ingredients.isNotEmpty() }
            .distinctBy { it.name.lowercase() to it.ingredients.map { i -> i.name }.sorted() }
            .take(limit)

    suspend fun save(meal: Meal, corrections: List<LearningCorrection> = emptyList()): Long {
        val entities = meal.ingredients.mapIndexed { i, ingredient -> ingredient.toEntity(0, i) }
        val id = if (meal.id == 0L) {
            mealDao.insertMealWithIngredients(meal.toEntity(), entities)
        } else {
            mealDao.replaceMeal(meal.toEntity(), entities)
            meal.id
        }
        if (corrections.isNotEmpty()) {
            learningDao.insertAll(
                corrections.map {
                    LearningCorrectionEntity(
                        foodKey = it.foodKey,
                        originalGrams = it.originalGrams,
                        correctedGrams = it.correctedGrams,
                        createdAt = it.createdAtMillis,
                    )
                },
            )
        }
        return id
    }

    suspend fun delete(meal: Meal) {
        mealDao.deleteMeal(meal.id)
        meal.photoPaths.forEach { photos.delete(it) }
    }

    suspend fun removePhoto(meal: Meal, path: String) {
        mealDao.setPhotoPaths(meal.id, joinPaths(meal.photoPaths - path))
        photos.delete(path)
    }

    suspend fun learningHints(): List<CorrectionHint> =
        applyCorrections(learningDao.recent(LEARNING_WINDOW).map { it.toDomain() })

    fun observeLearningCount(): Flow<Int> = learningDao.observeCount()

    suspend fun clearLearning() = learningDao.clear()

    companion object {
        private const val LEARNING_WINDOW = 500
    }
}
