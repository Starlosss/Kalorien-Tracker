package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.Ingredient
import com.kalorientracker.app.domain.model.MealCategory
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.model.sum
import java.time.LocalTime

class RecalculateMealTotalsUseCase {
    operator fun invoke(ingredients: List<Ingredient>, portionFactor: Double = 1.0): Nutrients =
        ingredients.map { it.per100g.forGrams(it.grams * portionFactor) }.sum()
}

class SuggestMealCategoryUseCase {
    operator fun invoke(time: LocalTime): MealCategory {
        val minutes = time.hour * 60 + time.minute
        return when (minutes) {
            in 5 * 60 until 10 * 60 + 30 -> MealCategory.BREAKFAST
            in 10 * 60 + 30 until 14 * 60 + 30 -> MealCategory.LUNCH
            in 14 * 60 + 30 until 17 * 60 + 30 -> MealCategory.SNACK
            in 17 * 60 + 30 until 21 * 60 + 30 -> MealCategory.DINNER
            else -> MealCategory.SNACK
        }
    }
}

/** The seven-step qualitative portion slider. */
object PortionScale {
    data class Step(val label: String, val factor: Double)

    val steps: List<Step> = listOf(
        Step("Sehr klein", 0.5),
        Step("Klein", 0.7),
        Step("Eher klein", 0.85),
        Step("Normal", 1.0),
        Step("Eher groß", 1.15),
        Step("Groß", 1.35),
        Step("Sehr groß", 1.6),
    )

    const val NORMAL_INDEX = 3

    fun factor(index: Int): Double = steps[index.coerceIn(steps.indices)].factor

    fun label(index: Int): String = steps[index.coerceIn(steps.indices)].label

    /** Closest slider step for an arbitrary factor, e.g. after the user typed an exact gram amount. */
    fun nearestIndex(factor: Double): Int =
        steps.indices.minBy { kotlin.math.abs(steps[it].factor - factor) }
}

/** Normalized key used to match ingredients across analyses for the personal learning layer. */
object FoodKey {
    private val bracketed = Regex("\\(.*?\\)")
    private val whitespace = Regex("\\s+")

    fun of(name: String): String =
        name.lowercase()
            .replace(bracketed, " ")
            .replace(whitespace, " ")
            .trim()
}
