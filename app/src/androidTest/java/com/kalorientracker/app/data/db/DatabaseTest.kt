package com.kalorientracker.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kalorientracker.app.domain.model.FoodSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun ingredient(name: String, grams: Double, kcal100: Double, protein100: Double = 0.0) = MealIngredientEntity(
        mealId = 0,
        name = name,
        grams = grams,
        kcal100 = kcal100,
        protein100 = protein100,
        carbs100 = 0.0,
        fat100 = 0.0,
        fiber100 = 0.0,
        sugar100 = 0.0,
        saturatedFat100 = 0.0,
        salt100 = 0.0,
    )

    private fun meal(day: Long, at: Long) = MealEntity(name = "Test", loggedAt = at, epochDay = day, category = "LUNCH")

    @Test
    fun dailyTotalsSumGramsTimesPer100() = runTest {
        val dao = db.mealDao()
        dao.insertMealWithIngredients(meal(10, 1), listOf(ingredient("Reis", 200.0, 130.0, 2.7), ingredient("Hähnchen", 150.0, 165.0, 31.0)))
        dao.insertMealWithIngredients(meal(10, 2), listOf(ingredient("Apfel", 150.0, 52.0)))
        dao.insertMealWithIngredients(meal(11, 3), listOf(ingredient("Brot", 100.0, 230.0)))

        val totals = dao.observeDailyTotals(10, 11).first()
        assertEquals(2, totals.size)
        assertEquals(260.0 + 247.5 + 78.0, totals[0].kcal, 0.001)
        assertEquals(5.4 + 46.5, totals[0].protein, 0.001)
        assertEquals(2, totals[0].mealCount)
        assertEquals(230.0, totals[1].kcal, 0.001)
    }

    @Test
    fun deletingMealCascadesToIngredients() = runTest {
        val dao = db.mealDao()
        val id = dao.insertMealWithIngredients(meal(1, 1), listOf(ingredient("A", 100.0, 100.0), ingredient("B", 50.0, 50.0)))
        assertEquals(2, dao.allIngredients().size)
        dao.deleteMeal(id)
        assertTrue(dao.allIngredients().isEmpty())
        assertNull(dao.getMeal(id))
    }

    @Test
    fun replaceMealRewritesIngredientsInOrder() = runTest {
        val dao = db.mealDao()
        val id = dao.insertMealWithIngredients(meal(1, 1), listOf(ingredient("A", 100.0, 100.0)))
        val stored = dao.getMeal(id)!!
        dao.replaceMeal(stored.meal.copy(name = "Neu"), listOf(ingredient("C", 10.0, 10.0), ingredient("D", 20.0, 20.0)))
        val updated = dao.getMeal(id)!!
        assertEquals("Neu", updated.meal.name)
        assertEquals(listOf("C", "D"), updated.ingredients.sortedBy { it.position }.map { it.name })
    }

    @Test
    fun weightIsOneEntryPerDay() = runTest {
        val dao = db.weightDao()
        dao.upsert(WeightEntryEntity(5, 81.7))
        dao.upsert(WeightEntryEntity(5, 81.2))
        dao.upsert(WeightEntryEntity(6, 80.9))
        val all = dao.observeAll().first()
        assertEquals(listOf(81.2, 80.9), all.map { it.weightKg })
        assertEquals(6L, dao.observeLatest().first()!!.epochDay)
    }

    @Test
    fun goalTargetsKeepHistoryAndExposeLatest() = runTest {
        val dao = db.goalTargetsDao()
        dao.insert(GoalTargetsEntity(effectiveFrom = 1, maintenanceKcal = 2400, targetKcal = 1900, proteinG = 150, carbsG = 200, fatG = 60, fiberG = 27, sugarMaxG = 47, saturatedFatMaxG = 21, saltMaxG = 6.0, isUserAdjusted = false))
        dao.insert(GoalTargetsEntity(effectiveFrom = 20, maintenanceKcal = 2550, targetKcal = 2050, proteinG = 150, carbsG = 230, fatG = 62, fiberG = 29, sugarMaxG = 51, saturatedFatMaxG = 23, saltMaxG = 6.0, isUserAdjusted = false))
        assertEquals(2050, dao.observeCurrent().first()!!.targetKcal)
        assertEquals(2, dao.all().size)
    }

    @Test
    fun barcodeLookupAndFrequentFoods() = runTest {
        val foods = db.foodDao()
        val id = foods.insert(
            FoodEntity(name = "Skyr", barcode = "4001234567890", kcal = 63.0, protein = 11.0, carbs = 4.0, fat = 0.2, fiber = 0.0, sugar = 4.0, saturatedFat = 0.1, salt = 0.1, source = FoodSource.ONLINE_CACHED.name),
        )
        assertEquals(id, foods.byBarcode("4001234567890")!!.id)
        db.mealDao().insertMealWithIngredients(meal(1, 1), listOf(ingredient("Skyr", 150.0, 63.0).copy(foodId = id)))
        assertEquals(listOf("Skyr"), foods.observeFrequent(5).first().map { it.name })
        assertEquals(150.0, db.mealDao().lastGramsForFood(id)!!, 0.0)
    }

    @Test
    fun seederLoadsBundledBaseDatabaseOnce() = runTest {
        val seeder = FoodSeeder(context, db.foodDao(), Json { ignoreUnknownKeys = true })
        seeder.seedIfNeeded()
        val count = db.foodDao().countBySource(FoodSource.BASE_DB.name)
        assertTrue("base database should contain common foods, had $count", count > 100)
        seeder.seedIfNeeded()
        assertEquals(count, db.foodDao().countBySource(FoodSource.BASE_DB.name))
        assertEquals("Reis (gekocht)", db.foodDao().byName("reis (gekocht)")!!.name)
        assertTrue(db.foodDao().search("Hähnchen", 10).isNotEmpty())
    }
}
