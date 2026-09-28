package com.kalorientracker.app.data.repository

import android.app.Application
import android.content.Context
import com.kalorientracker.app.data.db.FoodDao
import com.kalorientracker.app.data.db.FoodEntity
import com.kalorientracker.app.data.db.MealDao
import com.kalorientracker.app.data.remote.OnlineProductSource
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.FoodSource
import com.kalorientracker.app.domain.model.Nutrients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RememberUnknownFoodTest {
    @Test
    fun anUnknownFoodIsStoredOnce() = runTest {
        val repo = repositoryWithFoods(emptyList())
        val first = repo.remember("Ofenkartoffel", nutrients(93.0), 250.0)
        val second = repo.remember("ofenkartoffel", nutrients(93.0), 250.0)
        assertEquals(first.id, second.id)
        assertEquals(FoodSource.AI_ESTIMATED, first.source)
    }

    @Test
    fun aKnownFoodIsNeverDuplicated() = runTest {
        val repo = repositoryWithFoods(listOf(food("Reis (gekocht)", 130.0)))
        val result = repo.remember("Reis (gekocht)", nutrients(150.0), 200.0)
        assertEquals(130.0, result.per100g.kcal, 0.0)
    }
}

private fun nutrients(kcal: Double) = Nutrients(kcal = kcal)

private fun food(name: String, kcal: Double) = FoodEntity(
    name = name,
    kcal = kcal,
    protein = 0.0,
    carbs = 0.0,
    fat = 0.0,
    fiber = 0.0,
    sugar = 0.0,
    saturatedFat = 0.0,
    salt = 0.0,
    source = FoodSource.BASE_DB.name,
)

/** Wires a real [FoodRepository] against an in-memory [FoodDao] fake; the other collaborators are
 * never touched by [FoodRepository.remember], so they only need to exist, not to do anything. */
private fun repositoryWithFoods(foods: List<FoodEntity>): FoodRepository {
    val foodDao = FakeFoodDao(foods.toMutableList())
    val context = InertContext()
    return FoodRepository(
        foodDao = foodDao,
        mealDao = UnusedMealDao(),
        online = UnusedOnlineProductSource(),
        settings = SettingsRepository(context),
    )
}

/** A [Context] whose construction never reaches into the (unmocked) Android framework; nothing
 * else on it is ever called by the code under test here. */
private class InertContext : Application() {
    override fun getApplicationContext(): Context = this
}

private class UnusedMealDao : MealDao() {
    override suspend fun insertMeal(meal: com.kalorientracker.app.data.db.MealEntity): Long = error("not used")
    override suspend fun updateMeal(meal: com.kalorientracker.app.data.db.MealEntity) = error("not used")
    override suspend fun insertIngredients(items: List<com.kalorientracker.app.data.db.MealIngredientEntity>) = error("not used")
    override suspend fun insertMeals(meals: List<com.kalorientracker.app.data.db.MealEntity>) = error("not used")
    override suspend fun deleteIngredients(mealId: Long) = error("not used")
    override suspend fun deleteMeal(id: Long) = error("not used")
    override fun observeMeals(startDay: Long, endDay: Long) = error("not used")
    override fun observeMeal(id: Long) = error("not used")
    override suspend fun getMeal(id: Long) = error("not used")
    override suspend fun recentMeals(limit: Int) = error("not used")
    override fun observeDailyTotals(startDay: Long, endDay: Long) = error("not used")
    override fun observeTrackedDays(): Flow<List<Long>> = error("not used")
    override suspend fun firstDay(): Long? = error("not used")
    override suspend fun lastGramsForFood(foodId: Long): Double? = error("not used")
    override suspend fun clearFoodLinksChunk(ingredientIds: List<Long>) = error("not used")
    override suspend fun relinkIngredientsByNameChunk(ingredientIds: List<Long>) = error("not used")
    override suspend fun allMeals() = error("not used")
    override suspend fun allIngredients() = error("not used")
    override suspend fun setPhotoPaths(mealId: Long, paths: String) = error("not used")
    override suspend fun clearAllPhotoPaths() = error("not used")
}

private class UnusedOnlineProductSource : OnlineProductSource {
    override suspend fun byBarcode(barcode: String): Food? = error("not used")
    override suspend fun search(query: String): List<Food> = error("not used")
}

/** In-memory stand-in for [FoodDao], backed by a plain [MutableList] as instructed by the task. */
private class FakeFoodDao(private val items: MutableList<FoodEntity>) : FoodDao {
    private var nextId = (items.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun search(query: String, limit: Int): List<FoodEntity> =
        items.filter { it.name.contains(query, ignoreCase = true) || it.brand?.contains(query, ignoreCase = true) == true }
            .take(limit)

    override suspend fun byBarcode(barcode: String): FoodEntity? = items.firstOrNull { it.barcode == barcode }

    override suspend fun byId(id: Long): FoodEntity? = items.firstOrNull { it.id == id }

    override suspend fun byName(name: String): FoodEntity? =
        items.filter { it.name.equals(name, ignoreCase = true) }
            .sortedBy { it.source == FoodSource.CATALOG.name }
            .firstOrNull()

    override suspend fun insert(food: FoodEntity): Long {
        val id = if (food.id != 0L) food.id else nextId++
        val stored = food.copy(id = id)
        val existingIndex = items.indexOfFirst { it.id == id }
        if (existingIndex >= 0) items[existingIndex] = stored else items.add(stored)
        return id
    }

    override suspend fun insertAll(foods: List<FoodEntity>) {
        foods.forEach { insert(it) }
    }

    override suspend fun insertAllIgnoring(foods: List<FoodEntity>) {
        foods.forEach { food -> if (items.none { it.id == food.id }) insert(food) }
    }

    override suspend fun countBySource(source: String): Int = items.count { it.source == source }

    override suspend fun allUserFoods(): List<FoodEntity> =
        items.filter { it.source != FoodSource.BASE_DB.name && it.source != FoodSource.CATALOG.name }

    override suspend fun relabelChunk(barcodes: List<String>) {
        items.replaceAll { item ->
            if (item.source == FoodSource.ONLINE_CACHED.name && item.barcode in barcodes) {
                item.copy(source = FoodSource.CATALOG.name)
            } else {
                item
            }
        }
    }

    override fun observeFrequent(limit: Int): Flow<List<FoodEntity>> = flowOf(emptyList())

    override suspend fun all(): List<FoodEntity> = items.toList()
}
