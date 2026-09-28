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

    private fun food(name: String, source: FoodSource, kcal: Double = 100.0) = FoodEntity(
        name = name,
        kcal = kcal,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        fiber = 0.0,
        sugar = 0.0,
        saturatedFat = 0.0,
        salt = 0.0,
        source = source.name,
    )

    /**
     * The restore bug in full. A backup file stores `foodId` values that only mean anything
     * together with the foods the same file carries. Everything else in `foods` is rebuilt from
     * scratch and numbered by whatever the autoincrement counter offers at that moment, which has
     * no relation to the numbering on the machine that wrote the file. `clearAllTables` leaves
     * the counter at its high-water mark, so on the same device re-seeding after a wipe hands the
     * old catalogue's numbers straight to fresh curated rows. The ingredient then quietly shows
     * the wrong food behind it and offers the wrong remembered portion.
     *
     * Here the ingredient "Haferflocken" arrives with foodId 40.701 from the old catalogue, and
     * 40.701 is then handed to "Zucker". The link has to end up on the Haferflocken row. The id
     * is placed explicitly so the collision happens on the first row rather than after 40.700
     * inserts; the mechanism under test is the relink, not the counter.
     */
    @Test
    fun restoredIngredientNeverPointsAtAStrangersFood() = runTest {
        val foods = db.foodDao()
        val meals = db.mealDao()
        val staleId = 40_701L

        // Restored from the file: the ingredient still carries the id from the old install.
        val mealId = meals.insertMeal(meal(1, 1))
        meals.insertIngredients(
            listOf(ingredient("Haferflocken", 60.0, 370.0).copy(mealId = mealId, foodId = staleId)),
        )
        val ingredientId = meals.allIngredients().single().id

        // Cut before anything new is minted, exactly as the restore does it.
        meals.clearFoodLinks(listOf(ingredientId))

        // The counter hands 40.701 to an unrelated food, and the real Haferflocken row is curated.
        foods.insert(food("Zucker", FoodSource.BASE_DB).copy(id = staleId))
        val oatsId = foods.insert(food("Haferflocken", FoodSource.BASE_DB, kcal = 370.0))

        meals.relinkIngredientsByName(listOf(ingredientId))

        val restored = meals.allIngredients().single()
        assertEquals("the link must follow the name, not the old id", oatsId, restored.foodId)
    }

    /** Nothing of that name came back, so the ingredient stays unlinked rather than guessing. */
    @Test
    fun anIngredientWithNoMatchingFoodStaysUnlinked() = runTest {
        val meals = db.mealDao()
        val mealId = meals.insertMeal(meal(1, 1))
        meals.insertIngredients(
            listOf(ingredient("Selbstgebackenes Brot", 80.0, 250.0).copy(mealId = mealId, foodId = 4711L)),
        )
        val ingredientId = meals.allIngredients().single().id

        meals.clearFoodLinks(listOf(ingredientId))
        db.foodDao().insert(food("Brot", FoodSource.BASE_DB))
        meals.relinkIngredientsByName(listOf(ingredientId))

        assertNull(meals.allIngredients().single().foodId)
    }

    /**
     * `byName` feeds `bestMatch`, which takes the single top row, and `search` fills the list the
     * person scrolls. Both have to rank the same way, or the food shown at the top of the list is
     * not the one an analyzer ingredient resolves to.
     *
     * The order under test, top to bottom: what the person typed in themselves, then the curated
     * table, then anything picked up automatically, then the bundled branded bulk.
     */
    @Test
    fun bothLookupsRankTheSameSourcesTheSameWay() = runTest {
        val foods = db.foodDao()
        val branded = foods.insert(food("Milch", FoodSource.CATALOG, kcal = 47.0))
        val guessed = foods.insert(food("Milch", FoodSource.AI_ESTIMATED, kcal = 50.0))
        val curated = foods.insert(food("Milch", FoodSource.BASE_DB, kcal = 49.0))
        val own = foods.insert(food("Milch", FoodSource.USER_ADDED, kcal = 48.0))

        assertEquals(own, foods.byName("milch")!!.id)
        assertEquals("the same query must keep answering the same", own, foods.byName("Milch")!!.id)
        assertEquals(listOf(own, curated, guessed, branded), foods.search("Milch", 10).map { it.id })
    }

    /** Without the curated row the automatic ones still beat the bundled branded bulk. */
    @Test
    fun theBundledBrandedTableRanksLastEvenAgainstAGuess() = runTest {
        val foods = db.foodDao()
        val branded = foods.insert(food("Pizza", FoodSource.CATALOG, kcal = 250.0))
        val guessed = foods.insert(food("Pizza", FoodSource.AI_ESTIMATED, kcal = 260.0))

        assertEquals(guessed, foods.byName("Pizza")!!.id)
        assertEquals(listOf(guessed, branded), foods.search("Pizza", 10).map { it.id })
    }

    /**
     * Writes the tiebreaker down rather than reproducing a failure. Today both queries scan the
     * table, so SQLite hands back tied rows in rowid order on its own and this test also passes
     * without the `id` key. It exists so that a later index on `name`, or any other plan change
     * that reorders a scan, has to break a test instead of silently changing which food an
     * ingredient resolves to.
     */
    @Test
    fun tiedRowsAreSeparatedByTheRowId() = runTest {
        val foods = db.foodDao()
        val first = foods.insert(food("Quark", FoodSource.AI_ESTIMATED, kcal = 67.0))
        foods.insert(food("Quark", FoodSource.ONLINE_CACHED, kcal = 70.0))

        assertEquals(first, foods.byName("Quark")!!.id)
        assertEquals(first, foods.search("Quark", 10).first().id)
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
