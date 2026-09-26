package com.kalorientracker.app.data.analyzer

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionAnchorsTest {

    private fun ingredient(name: String, grams: Double = 100.0) = RecognizedIngredient(
        name = name,
        foodKey = com.kalorientracker.app.domain.usecase.FoodKey.of(name),
        estimatedGrams = grams,
        confidence = Confidence.MEDIUM,
        per100g = Nutrients(100.0, 5.0, 10.0, 3.0),
    )

    @Test
    fun findsEveryNamedFoodOncePerGroup() {
        val anchors = DescriptionAnchors.of("Reis mit Hähnchen und Brokkoli")
        assertEquals(listOf("Reis", "Hähnchen", "Brokkoli"), anchors.map { it.shortName })
    }

    @Test
    fun usesStatedGramsAndSizeWords() {
        val stated = DescriptionAnchors.of("200g Reis").single()
        assertEquals(200.0, stated.grams, 0.0)
        assertTrue(stated.statedGrams)

        val large = DescriptionAnchors.of("große Portion Reis").single()
        assertEquals(260.0, large.grams, 0.0)
        assertFalse(large.statedGrams)
    }

    @Test
    fun ignoresNegatedAndImplicitFoods() {
        assertEquals(listOf("Nudeln"), DescriptionAnchors.of("Nudeln ohne Soße").map { it.shortName })
        assertTrue(DescriptionAnchors.of("mit Öl gebraten").isEmpty())
        assertTrue(DescriptionAnchors.of("").isEmpty())
    }

    @Test
    fun recognizesAnchorsUnderTheModelsOwnWording() {
        val chicken = DescriptionAnchors.of("Hähnchen").single()
        assertTrue(DescriptionAnchors.covered(chicken, listOf(ingredient("Hähnchenbrust (gegrillt)"))))
        assertTrue(DescriptionAnchors.covered(chicken, listOf(ingredient("Hähnchenbrust (gebraten)"))))
        assertFalse(DescriptionAnchors.covered(chicken, listOf(ingredient("Reis (gekocht)"))))
    }

    @Test
    fun portionQuestionCarriesGramsInItsOptions() {
        val question = DescriptionAnchors.portionQuestion("Reis (gekocht)", "Reis", 200.0)
        assertTrue(question.id.startsWith(DescriptionAnchors.QUESTION_PREFIX))
        assertEquals("Wie viel Reis war dabei?", question.text)
        assertEquals(listOf("Wenig (100 g)", "Normal (200 g)", "Viel (320 g)", DescriptionAnchors.OPTION_ABSENT), question.options)
        assertEquals(200.0, DescriptionAnchors.gramsOf(question.options[1])!!, 0.0)
        assertEquals(null, DescriptionAnchors.gramsOf(DescriptionAnchors.OPTION_ABSENT))
    }

    @Test
    fun answersSetAddOrRemoveAnIngredient() {
        val anchors = DescriptionAnchors.of("Reis mit Brokkoli")
        val rice = anchors.first { it.shortName == "Reis" }
        val broccoli = anchors.first { it.shortName == "Brokkoli" }

        val updated = DescriptionAnchors.applyPortionAnswers(
            listOf(ingredient(rice.name, 150.0)),
            listOf(
                FollowUpAnswer(DescriptionAnchors.QUESTION_PREFIX + rice.foodKey, "Viel (320 g)"),
                FollowUpAnswer(DescriptionAnchors.QUESTION_PREFIX + broccoli.foodKey, "Normal (150 g)"),
            ),
            anchors,
        )
        assertEquals(listOf(rice.name, broccoli.name), updated.map { it.name })
        assertEquals(320.0, updated[0].estimatedGrams, 0.0)
        assertEquals(Confidence.HIGH, updated[0].confidence)
        assertEquals(150.0, updated[1].estimatedGrams, 0.0)

        val removed = DescriptionAnchors.applyPortionAnswers(
            updated,
            listOf(FollowUpAnswer(DescriptionAnchors.QUESTION_PREFIX + broccoli.foodKey, DescriptionAnchors.OPTION_ABSENT)),
            anchors,
        )
        assertEquals(listOf(rice.name), removed.map { it.name })
    }
}
