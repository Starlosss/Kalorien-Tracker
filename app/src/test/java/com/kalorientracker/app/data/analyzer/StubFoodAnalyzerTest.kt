package com.kalorientracker.app.data.analyzer

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.CorrectionHint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StubFoodAnalyzerTest {

    private val analyzer = StubFoodAnalyzer()

    private fun analyze(text: String, photos: Int = 1, corrections: List<CorrectionHint> = emptyList()) = runBlocking {
        analyzer.analyze(AnalysisInput(List(photos) { "/photo$it.jpg" }, text, corrections))
    }

    private fun AnalysisResult.names() = ingredients.map { it.name }

    @Test
    fun recognizesSpecExampleMeal() {
        val result = analyze("Reis mit Hähnchen in Sahnesoße")
        assertEquals(
            listOf("Reis (gekocht)", "Hähnchenbrust (gebraten)", "Sahnesoße", "Speiseöl"),
            result.names(),
        )
        assertEquals("Reis mit Hähnchen in Sahnesoße", result.mealName)
        val ids = result.followUpQuestions.map { it.id }
        assertEquals(listOf(StubFoodAnalyzer.Q_BREADED, StubFoodAnalyzer.Q_OIL), ids)
        assertEquals(Confidence.LOW, result.ingredients.last().confidence)
    }

    @Test
    fun isDeterministicForSameInput() {
        assertEquals(analyze("Nudeln mit Tomatensoße"), analyze("Nudeln mit Tomatensoße"))
    }

    @Test
    fun shortKeywordsNeedWholeWords() {
        val rice = analyze("Reis")
        assertFalse(rice.names().any { it.startsWith("Ei") })
        assertTrue(analyze("Brot mit Ei").names().contains("Ei (gekocht)"))
    }

    @Test
    fun explicitGramsAreTrusted() {
        val result = analyze("300g Reis und 150 g Brokkoli")
        val rice = result.ingredients.first { it.name == "Reis (gekocht)" }
        assertEquals(300.0, rice.estimatedGrams, 0.0)
        assertEquals(Confidence.HIGH, rice.confidence)
        assertEquals(150.0, result.ingredients.first { it.name.startsWith("Brokkoli") }.estimatedGrams, 0.0)
    }

    @Test
    fun personalCorrectionsScaleEstimates() {
        val plain = analyze("Reis").ingredients.first().estimatedGrams
        val learned = analyze("Reis", corrections = listOf(CorrectionHint("reis", 1.5, 3))).ingredients.first().estimatedGrams
        assertEquals(plain * 1.5, learned, 7.5)
        assertTrue(analyze("Reis", corrections = listOf(CorrectionHint("reis", 1.5, 3))).notes.any { it.contains("Portionen") })
    }

    @Test
    fun unknownDishFallsBackWithLowConfidence() {
        val result = analyze("xyz")
        assertEquals(listOf("Gemischtes Gericht"), result.names())
        assertEquals(Confidence.LOW, result.ingredients.single().confidence)
        assertTrue(result.notes.isNotEmpty())
        assertTrue(result.followUpQuestions.isEmpty())
    }

    @Test
    fun genericSauceTriggersQuestionAndAnswerReplacesIt() = runBlocking {
        val result = analyze("Nudeln mit Soße")
        val question = result.followUpQuestions.single()
        assertEquals(StubFoodAnalyzer.Q_SAUCE, question.id)
        val answered = analyzer.answerFollowUp(result.context, listOf(FollowUpAnswer(question.id, "Tomatensoße")))
        assertEquals(listOf("Nudeln (gekocht)", "Tomatensoße"), answered.names())
        assertTrue(answered.followUpQuestions.isEmpty())

        val none = analyzer.answerFollowUp(result.context, listOf(FollowUpAnswer(question.id, StubFoodAnalyzer.NO_SAUCE)))
        assertEquals(listOf("Nudeln (gekocht)"), none.names())
    }

    @Test
    fun breadedAndOilAnswersAdjustIngredients() = runBlocking {
        val result = analyze("Hähnchen mit Reis")
        val answered = analyzer.answerFollowUp(
            result.context,
            listOf(
                FollowUpAnswer(StubFoodAnalyzer.Q_BREADED, StubFoodAnalyzer.YES),
                FollowUpAnswer(StubFoodAnalyzer.Q_OIL, "Kein Öl"),
            ),
        )
        assertTrue(answered.names().contains("Hähnchen (paniert)"))
        assertFalse(answered.names().contains("Speiseöl"))

        val much = analyzer.answerFollowUp(result.context, listOf(FollowUpAnswer(StubFoodAnalyzer.Q_OIL, "Viel (2 EL)")))
        assertEquals(20.0, much.ingredients.first { it.name == "Speiseöl" }.estimatedGrams, 0.0)
    }

    @Test
    fun mentionedPreparationSkipsQuestions() {
        val result = analyze("Paniertes Hähnchen mit Pommes und Olivenöl")
        assertTrue(result.names().contains("Hähnchen (paniert)"))
        assertTrue(result.followUpQuestions.isEmpty())
    }

    @Test
    fun atMostThreeQuestions() {
        val result = analyze("Hähnchen mit Soße und Reis")
        assertTrue(result.followUpQuestions.size <= StubFoodAnalyzer.MAX_QUESTIONS)
        assertEquals(3, result.followUpQuestions.size)
    }

    @Test
    fun sizeWordsScalePortions() {
        val normal = analyze("Pizza").ingredients.first().estimatedGrams
        val big = analyze("große Pizza").ingredients.first().estimatedGrams
        assertTrue(big > normal)
    }

    @Test
    fun mealNameIsBuiltFromIngredientsWithoutDescription() {
        val ingredients = listOf(
            StubCatalog.byId("chicken").toIngredient(150.0, Confidence.HIGH),
            StubCatalog.byId("rice").toIngredient(200.0, Confidence.HIGH),
            StubCatalog.byId("sauce_cream").toIngredient(90.0, Confidence.MEDIUM),
            StubCatalog.byId("oil").toIngredient(10.0, Confidence.LOW),
        )
        assertEquals("Hähnchen mit Reis und Sahnesoße", StubFoodAnalyzer.mealName("", ingredients))
    }
}
