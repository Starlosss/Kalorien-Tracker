package com.kalorientracker.app.data.backup

import com.kalorientracker.app.data.db.FoodEntity
import com.kalorientracker.app.data.db.MealIngredientEntity
import com.kalorientracker.app.domain.model.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards which links a restore cuts. Getting this wrong is silent in both directions: cutting too
 * much loses remembered portions, cutting too little leaves an ingredient pointing at a food that
 * only happens to have inherited its number.
 */
class OrphanedIngredientIdsTest {

    private fun food(id: Long) = FoodEntity(
        id = id,
        name = "Essen $id",
        kcal = 100.0,
        protein = 0.0,
        carbs = 0.0,
        fat = 0.0,
        fiber = 0.0,
        sugar = 0.0,
        saturatedFat = 0.0,
        salt = 0.0,
        source = FoodSource.USER_ADDED.name,
    )

    private fun ingredient(id: Long, foodId: Long?) = MealIngredientEntity(
        id = id,
        mealId = 1,
        name = "Zutat $id",
        grams = 100.0,
        kcal100 = 100.0,
        protein100 = 0.0,
        carbs100 = 0.0,
        fat100 = 0.0,
        fiber100 = 0.0,
        sugar100 = 0.0,
        saturatedFat100 = 0.0,
        salt100 = 0.0,
        foodId = foodId,
    )

    @Test
    fun `only a link to a food the file does not carry is cut`() {
        val foods = listOf(food(7), food(9))
        val ingredients = listOf(
            ingredient(1, foodId = 7),      // restored by this file, keeps its link
            ingredient(2, foodId = null),   // never had one, nothing to do
            ingredient(3, foodId = 40_701), // a number this file does not explain
            ingredient(4, foodId = 9),      // restored by this file
            ingredient(5, foodId = 8),      // a gap between two restored ids is still a stranger
        )

        assertEquals(listOf(3L, 5L), orphanedIngredientIds(foods, ingredients))
    }

    /** An older backup carries the curated and catalogue rows too, so nothing needs cutting. */
    @Test
    fun `a file that carries every food it references cuts nothing`() {
        val foods = (1L..5L).map { food(it) }
        val ingredients = (1L..5L).map { ingredient(it, foodId = it) }

        assertEquals(emptyList<Long>(), orphanedIngredientIds(foods, ingredients))
    }

    @Test
    fun `a file with no foods at all still keeps the unlinked ingredients alone`() {
        val ingredients = listOf(ingredient(1, foodId = null), ingredient(2, foodId = 3))

        assertEquals(listOf(2L), orphanedIngredientIds(emptyList(), ingredients))
    }
}
