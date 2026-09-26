package com.kalorientracker.app.data.analyzer

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.usecase.FoodKey
import kotlin.math.roundToInt

/**
 * Foods the user named in their own words.
 *
 * A small vision model regularly overlooks one component of a plate, especially when it is partly
 * covered or sits at the edge. What the user typed is the more reliable source, so every food they
 * mentioned is anchored: it must appear in the result, and if the model dropped it the app adds it
 * back and asks for the portion instead of silently losing it.
 */
object DescriptionAnchors {

    /** Question ids for the portion questions the app asks on its own, without the model. */
    const val QUESTION_PREFIX = "menge_"
    const val OPTION_ABSENT = "War doch nicht dabei"
    const val MAX_ANCHORS = 6

    data class Anchor(
        val name: String,
        val shortName: String,
        val group: String,
        val keywords: List<String>,
        val grams: Double,
        val per100g: Nutrients,
        /** True when the user stated the weight themselves ("200g Reis") – then nothing needs asking. */
        val statedGrams: Boolean,
    ) {
        val foodKey: String get() = FoodKey.of(name)

        fun toIngredient(confidence: Confidence): RecognizedIngredient =
            RecognizedIngredient(name, foodKey, grams, confidence, per100g)
    }

    /** Groups that are never anchors: implicit fats and catch-alls the user did not really name. */
    private val ignoredGroups = setOf("oil", "mixed", "dressing")

    /** Foods named in [description], at most one per food group, in the order they are catalogued. */
    fun of(description: String): List<Anchor> {
        if (description.isBlank()) return emptyList()
        val text = description.lowercase()
        val tokens = StubFoodAnalyzer.tokenize(text)
        val stated = StubFoodAnalyzer.parseExplicitGrams(text)
        val size = StubFoodAnalyzer.sizeFactor(tokens)
        val byGroup = LinkedHashMap<String, Anchor>()
        for (template in StubCatalog.templates) {
            if (template.group in ignoredGroups || template.group in byGroup) continue
            val named = template.keywords.any { StubFoodAnalyzer.matches(tokens, it) && !isNegated(tokens, it) }
            if (!named) continue
            val grams = stated[template.id]
            byGroup[template.group] = Anchor(
                name = template.name,
                shortName = template.shortName,
                group = template.group,
                keywords = template.keywords,
                grams = grams ?: StubFoodAnalyzer.roundTo5(template.defaultGrams * size),
                per100g = template.per100g,
                statedGrams = grams != null,
            )
        }
        return byGroup.values.take(MAX_ANCHORS)
    }

    /** "Nudeln ohne Soße" must not anchor the sauce. */
    private fun isNegated(tokens: List<String>, keyword: String): Boolean {
        tokens.forEachIndexed { index, token ->
            if (token != "ohne") return@forEachIndexed
            val following = tokens.drop(index + 1).take(2)
            if (following.any { StubFoodAnalyzer.matches(listOf(it), keyword) }) return true
        }
        return false
    }

    /** True when [ingredients] already contains this anchor, under whatever name the model chose. */
    fun covered(anchor: Anchor, ingredients: List<RecognizedIngredient>): Boolean = ingredients.any { ingredient ->
        if (ingredient.foodKey == anchor.foodKey) return@any true
        val tokens = StubFoodAnalyzer.tokenize(ingredient.name)
        anchor.keywords.any { StubFoodAnalyzer.matches(tokens, it) } || groupOf(ingredient.name) == anchor.group
    }

    private fun groupOf(name: String): String? =
        StubCatalog.templates.firstOrNull { it.name.equals(name, ignoreCase = true) }?.group

    /**
     * Asks for the portion of one food. The gram amount is part of the option label so the answer
     * can be applied without the model – and so the user sees what they are choosing.
     */
    fun portionQuestion(name: String, shortName: String, grams: Double): FollowUpQuestion {
        val base = grams.coerceIn(20.0, 800.0)
        val options = listOf(0.5 to "Wenig", 1.0 to "Normal", 1.6 to "Viel")
            .map { (factor, label) -> StubFoodAnalyzer.roundTo5(base * factor) to label }
            .distinctBy { it.first }
            .map { (value, label) -> "$label (${value.roundToInt()} g)" }
        return FollowUpQuestion(QUESTION_PREFIX + FoodKey.of(name), "Wie viel $shortName war dabei?", options + OPTION_ABSENT)
    }

    /** Grams contained in an option label, or null for [OPTION_ABSENT]. */
    fun gramsOf(option: String): Double? = gramPattern.find(option)?.groupValues?.get(1)?.toDoubleOrNull()

    /**
     * Applies the app's own portion answers. The user's own statement always wins over the model,
     * so this runs last – after any model round.
     */
    fun applyPortionAnswers(
        ingredients: List<RecognizedIngredient>,
        answers: List<FollowUpAnswer>,
        anchors: List<Anchor>,
    ): List<RecognizedIngredient> {
        val relevant = answers.filter { it.questionId.startsWith(QUESTION_PREFIX) }
        if (relevant.isEmpty()) return ingredients
        val result = ingredients.toMutableList()
        for (answer in relevant) {
            val key = answer.questionId.removePrefix(QUESTION_PREFIX)
            val grams = gramsOf(answer.option)
            val index = result.indexOfFirst { it.foodKey == key }
            when {
                grams == null -> if (index >= 0) result.removeAt(index)
                index >= 0 -> result[index] = result[index].copy(estimatedGrams = grams, confidence = Confidence.HIGH)
                else -> anchors.firstOrNull { it.foodKey == key }
                    ?.let { result += it.copy(grams = grams).toIngredient(Confidence.HIGH) }
            }
        }
        return result
    }

    private val gramPattern = Regex("(\\d+(?:[.,]\\d+)?)\\s*g\\b")
}
