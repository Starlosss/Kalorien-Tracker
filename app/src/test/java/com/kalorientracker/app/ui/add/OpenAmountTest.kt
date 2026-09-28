package com.kalorientracker.app.ui.add

import com.kalorientracker.app.data.analyzer.DescriptionAnchors
import com.kalorientracker.app.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An ingredient the user named but never gave an amount for carries a placeholder gram, not a
 * weighed amount. The review step says so on screen ("Menge offen, zählt noch nicht mit"), and
 * nothing after that step may quietly treat the placeholder as a real amount: the saved meal
 * would show a flat 1 g, and the learning would offer 1 g as the usual portion from then on.
 *
 * Both places read [AddFlowState.firstOpenAmount], so this is where that agreement is pinned.
 */
class OpenAmountTest {

    private fun ingredient(name: String, grams: Double, open: Boolean) = DraftIngredient(
        key = name,
        name = name,
        foodId = null,
        foodKey = name.lowercase(),
        per100g = Nutrients(kcal = 100.0, protein = 1.0, carbs = 1.0, fat = 1.0),
        baseGrams = grams,
        grams = grams,
        amountOpen = open,
    )

    @Test
    fun `a draft with every amount answered is ready`() {
        val state = AddFlowState(
            ingredients = listOf(
                ingredient("Reis (gekocht)", 200.0, open = false),
                ingredient("Brokkoli", 120.0, open = false),
            ),
        )

        assertNull(state.firstOpenAmount)
    }

    @Test
    fun `an open amount is reported, and it is the one still carrying the placeholder`() {
        val open = ingredient("Brokkoli", DescriptionAnchors.UNKNOWN_AMOUNT_GRAMS, open = true)
        val state = AddFlowState(
            ingredients = listOf(ingredient("Reis (gekocht)", 200.0, open = false), open),
        )

        assertEquals(open, state.firstOpenAmount)
        assertEquals(DescriptionAnchors.UNKNOWN_AMOUNT_GRAMS, state.firstOpenAmount!!.grams, 0.0)
    }

    /** The meal portion scale must not turn the placeholder into a plausible-looking amount. */
    @Test
    fun `scaling the whole meal does not answer an open amount`() {
        val state = AddFlowState(
            ingredients = listOf(ingredient("Brokkoli", DescriptionAnchors.UNKNOWN_AMOUNT_GRAMS, open = true)),
            mealPortionIndex = 0,
        )

        assertEquals("Brokkoli", state.firstOpenAmount?.name)
    }

    @Test
    fun `an empty draft has nothing open`() {
        assertNull(AddFlowState().firstOpenAmount)
    }
}
