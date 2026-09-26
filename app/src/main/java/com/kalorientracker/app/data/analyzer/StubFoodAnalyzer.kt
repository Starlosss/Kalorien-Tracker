package com.kalorientracker.app.data.analyzer

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.CorrectionHint
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.usecase.FoodKey
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Placeholder for the on-device vision model. It does not look at pixels; it derives a plausible
 * ingredient list from the description, the number of photos and the user's learned corrections.
 * Output is deterministic for the same input so behaviour is reproducible and testable.
 */
class StubFoodAnalyzer(
    private val randomFactory: (seed: Int) -> Random = { Random(it) },
) : FoodAnalyzer {

    override suspend fun analyze(input: AnalysisInput): AnalysisResult {
        val text = input.description.lowercase()
        val tokens = tokenize(text)
        val random = randomFactory(text.hashCode() * 31 + input.photoPaths.size)
        val explicitGrams = parseExplicitGrams(text)
        val sizeFactor = sizeFactor(tokens)

        val matched = LinkedHashMap<String, StubCatalog.Template>()
        for (template in StubCatalog.templates) {
            if (template.group in matched) continue
            if (template.keywords.any { matches(tokens, it) }) matched[template.group] = template
        }
        if (matched.containsKey("poultry") && tokens.any { it.startsWith("paniert") }) {
            matched["poultry"] = StubCatalog.byId("chicken_breaded")
        }

        val notes = mutableListOf<String>()
        val ingredients = mutableListOf<RecognizedIngredient>()
        val usedCorrections = mutableSetOf<String>()

        for (template in matched.values) {
            val explicit = explicitGrams[template.id]
            val grams: Double
            var confidence = template.confidence
            if (explicit != null) {
                grams = explicit
                confidence = Confidence.HIGH
            } else {
                val jitter = 0.9 + random.nextDouble() * 0.2
                var estimate = template.defaultGrams * sizeFactor * jitter
                val hint = input.corrections.firstOrNull { it.foodKey == FoodKey.of(template.name) }
                if (hint != null) {
                    estimate *= hint.ratio
                    usedCorrections += template.name
                }
                grams = roundTo5(estimate)
                confidence = adjustConfidence(confidence, input.photoPaths.size, random)
            }
            ingredients += template.toIngredient(grams, confidence)
        }

        if (ingredients.isEmpty()) {
            val fallback = StubCatalog.byId("mixed_dish")
            ingredients += fallback.toIngredient(roundTo5(fallback.defaultGrams * sizeFactor), Confidence.LOW)
            notes += "Die App hat das Gericht nicht sicher erkannt. Bitte die Zutaten prüfen oder ergänzen."
        }

        val needsOil = needsImplicitOil(tokens, matched.keys)
        val oilMentioned = tokens.any { it == "öl" || it.endsWith("öl") && it.length <= 12 }
        if (needsOil || oilMentioned) {
            val oil = StubCatalog.byId("oil")
            ingredients += oil.toIngredient(10.0, if (oilMentioned) Confidence.MEDIUM else Confidence.LOW)
        }

        if (usedCorrections.isNotEmpty()) {
            notes += "Deine üblichen Portionen sind eingerechnet: ${usedCorrections.joinToString()}."
        }
        if (input.photoPaths.size >= 2) {
            notes += "${input.photoPaths.size} Fotos wurden gemeinsam ausgewertet."
        }

        val questions = buildQuestions(tokens, matched.keys, needsOil && !oilMentioned).take(MAX_QUESTIONS)
        val context = AnalysisContext(input.description, input.photoPaths.size, ingredients, input.corrections, questions)
        return AnalysisResult(
            mealName = mealName(input.description, ingredients),
            ingredients = ingredients,
            followUpQuestions = questions,
            context = context,
            notes = notes,
        )
    }

    override suspend fun answerFollowUp(context: AnalysisContext, answers: List<FollowUpAnswer>): AnalysisResult {
        var ingredients = context.ingredients.toMutableList()
        for (answer in answers) {
            when (answer.questionId) {
                Q_SAUCE -> {
                    val index = ingredients.indexOfFirst { it.name == StubCatalog.byId("sauce_unknown").name }
                    if (index >= 0) {
                        val grams = ingredients[index].estimatedGrams
                        val replacement = StubCatalog.templates.firstOrNull { it.name == answer.option }
                        if (replacement == null) ingredients.removeAt(index)
                        else ingredients[index] = replacement.toIngredient(grams, Confidence.MEDIUM)
                    }
                }
                Q_BREADED -> {
                    val plain = StubCatalog.byId("chicken").name
                    val index = ingredients.indexOfFirst { it.name == plain }
                    if (index >= 0) {
                        val current = ingredients[index]
                        ingredients[index] = if (answer.option == YES) {
                            StubCatalog.byId("chicken_breaded").toIngredient(current.estimatedGrams, Confidence.MEDIUM)
                        } else {
                            current.copy(confidence = Confidence.HIGH)
                        }
                    }
                }
                Q_OIL -> {
                    val grams = OIL_OPTIONS[answer.option] ?: continue
                    val oilName = StubCatalog.byId("oil").name
                    ingredients = if (grams == 0.0) {
                        ingredients.filterNot { it.name == oilName }.toMutableList()
                    } else {
                        ingredients.map {
                            if (it.name == oilName) it.copy(estimatedGrams = grams, confidence = Confidence.MEDIUM) else it
                        }.toMutableList()
                    }
                }
            }
        }
        val newContext = context.copy(ingredients = ingredients)
        return AnalysisResult(
            mealName = mealName(context.description, ingredients),
            ingredients = ingredients,
            followUpQuestions = emptyList(),
            context = newContext,
        )
    }

    private fun adjustConfidence(base: Confidence, photoCount: Int, random: Random): Confidence {
        var c = base
        if (photoCount == 0 && c == Confidence.HIGH) c = Confidence.MEDIUM
        if (photoCount >= 2 && c == Confidence.LOW) c = Confidence.MEDIUM
        if (random.nextDouble() < JITTER_PROBABILITY) {
            c = when (c) {
                Confidence.HIGH -> Confidence.MEDIUM
                Confidence.MEDIUM -> Confidence.LOW
                Confidence.LOW -> Confidence.LOW
            }
        }
        return c
    }

    private fun buildQuestions(tokens: List<String>, groups: Set<String>, askOil: Boolean): List<FollowUpQuestion> {
        val questions = mutableListOf<FollowUpQuestion>()
        if ("sauce" in groups && StubCatalog.genericSauceMatched(tokens)) {
            questions += FollowUpQuestion(
                id = Q_SAUCE,
                text = "Welche Soße war es?",
                options = listOf("Sahnesoße", "Tomatensoße", "Bratensoße", "Currysoße", NO_SAUCE),
            )
        }
        val poultryStated = tokens.any { it.startsWith("paniert") || it == "natur" || it.startsWith("gegrillt") }
        if ("poultry" in groups && !poultryStated) {
            questions += FollowUpQuestion(Q_BREADED, "War das Hähnchen paniert?", listOf(YES, NO))
        }
        if (askOil) {
            questions += FollowUpQuestion(Q_OIL, "Wie viel Öl wurde zum Braten verwendet?", OIL_OPTIONS.keys.toList())
        }
        return questions
    }

    private fun needsImplicitOil(tokens: List<String>, groups: Set<String>): Boolean {
        if (tokens.any { it.startsWith("gekocht") || it.startsWith("roh") }) return false
        val fried = tokens.any { it.contains("brat") || it.contains("pfanne") || it.startsWith("gebraten") }
        return fried || groups.any { it in setOf("poultry", "meat", "fish", "egg", "schnitzel") }
    }

    companion object {
        const val Q_SAUCE = "sauce_type"
        const val Q_BREADED = "breaded"
        const val Q_OIL = "oil_amount"
        const val YES = "Ja"
        const val NO = "Nein"
        const val NO_SAUCE = "Keine Soße"
        const val MAX_QUESTIONS = 3
        const val JITTER_PROBABILITY = 0.15

        val OIL_OPTIONS = linkedMapOf(
            "Kein Öl" to 0.0,
            "Wenig (1 TL)" to 5.0,
            "Normal (1 EL)" to 10.0,
            "Viel (2 EL)" to 20.0,
        )

        private val tokenSplit = Regex("[^\\p{L}0-9]+")
        private val gramPattern = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:g|gr|gramm)\\b\\s*([\\p{L}-]+)?")

        fun tokenize(text: String): List<String> = text.lowercase().split(tokenSplit).filter { it.isNotBlank() }

        /** Short keywords must match a whole word ("ei" must not match "reis"); longer ones may be part of a compound. */
        fun matches(tokens: List<String>, keyword: String): Boolean =
            tokens.any { it == keyword || (keyword.length >= 4 && it.contains(keyword)) }

        fun parseExplicitGrams(text: String): Map<String, Double> {
            val result = mutableMapOf<String, Double>()
            for (match in gramPattern.findAll(text.lowercase())) {
                val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
                val word = match.groupValues[2]
                if (word.isBlank()) continue
                val template = StubCatalog.templates.firstOrNull { t -> t.keywords.any { matches(listOf(word), it) } }
                if (template != null) result[template.id] = amount
            }
            return result
        }

        fun sizeFactor(tokens: List<String>): Double = when {
            tokens.any { it in setOf("riesig", "riesige", "riesiger", "doppelt", "doppelte") } -> 1.5
            tokens.any { it in setOf("groß", "große", "großer", "großes", "viel", "reichlich") } -> 1.3
            tokens.any { it in setOf("klein", "kleine", "kleiner", "kleines", "wenig", "halbe", "halb") } -> 0.75
            else -> 1.0
        }

        fun roundTo5(value: Double): Double = ((value / 5.0).roundToInt() * 5).coerceAtLeast(5).toDouble()

        fun mealName(description: String, ingredients: List<RecognizedIngredient>): String {
            val trimmed = description.trim()
            if (trimmed.isNotEmpty()) {
                val short = if (trimmed.length > 48) trimmed.take(45).trimEnd() + "…" else trimmed
                return short.replaceFirstChar { it.uppercase() }
            }
            val names = ingredients
                .filter { it.name != StubCatalog.byId("oil").name }
                .map { StubCatalog.shortName(it.name) }
            return when (names.size) {
                0 -> "Mahlzeit"
                1 -> names[0]
                2 -> "${names[0]} mit ${names[1]}"
                else -> "${names[0]} mit ${names.subList(1, names.size - 1).joinToString(", ")} und ${names.last()}"
            }
        }
    }
}

/** Canned nutrition knowledge of the stub. Names match entries of the bundled base food database. */
internal object StubCatalog {

    data class Template(
        val id: String,
        val group: String,
        val name: String,
        val shortName: String,
        val keywords: List<String>,
        val defaultGrams: Double,
        val confidence: Confidence,
        val per100g: Nutrients,
    ) {
        fun toIngredient(grams: Double, confidence: Confidence) = RecognizedIngredient(
            name = name,
            foodKey = FoodKey.of(name),
            estimatedGrams = grams,
            confidence = confidence,
            per100g = per100g,
        )
    }

    private fun n(kcal: Double, p: Double, c: Double, f: Double, fib: Double, sug: Double, sat: Double, salt: Double) =
        Nutrients(kcal, p, c, f, fib, sug, sat, salt)

    val templates: List<Template> = listOf(
        Template("rice", "rice", "Reis (gekocht)", "Reis", listOf("reis", "risotto"), 200.0, Confidence.HIGH, n(130.0, 2.7, 28.2, 0.3, 0.4, 0.1, 0.1, 0.0)),
        Template("pasta", "pasta", "Nudeln (gekocht)", "Nudeln", listOf("nudel", "pasta", "spaghetti", "penne", "fusilli", "tagliatelle", "makkaroni", "lasagne"), 220.0, Confidence.HIGH, n(150.0, 5.5, 30.0, 0.9, 1.8, 0.6, 0.2, 0.0)),
        Template("fries", "potato", "Pommes frites", "Pommes", listOf("pommes", "fritten"), 150.0, Confidence.MEDIUM, n(290.0, 3.4, 36.0, 15.0, 3.3, 0.3, 1.5, 0.5)),
        Template("potato", "potato", "Kartoffeln (gekocht)", "Kartoffeln", listOf("kartoffel", "erdäpfel", "salzkartoffel", "püree", "stampf"), 250.0, Confidence.HIGH, n(72.0, 2.0, 15.6, 0.1, 1.8, 0.8, 0.0, 0.01)),
        Template("chicken_breaded", "poultry_breaded", "Hähnchen (paniert)", "Hähnchen", listOf("chicken nugget", "nuggets"), 150.0, Confidence.MEDIUM, n(250.0, 18.0, 14.0, 13.0, 0.7, 0.5, 2.5, 1.0)),
        Template("chicken", "poultry", "Hähnchenbrust (gebraten)", "Hähnchen", listOf("hähnchen", "haehnchen", "hühnchen", "huhn", "chicken", "geflügel", "pute", "truthahn"), 150.0, Confidence.HIGH, n(165.0, 31.0, 0.0, 3.6, 0.0, 0.0, 1.0, 0.2)),
        Template("schnitzel", "schnitzel", "Schnitzel (paniert)", "Schnitzel", listOf("schnitzel"), 180.0, Confidence.MEDIUM, n(240.0, 20.0, 12.0, 12.0, 0.5, 0.5, 3.0, 1.0)),
        Template("steak", "meat", "Rindersteak (gebraten)", "Steak", listOf("steak", "rind", "rinder"), 200.0, Confidence.HIGH, n(200.0, 27.0, 0.0, 10.0, 0.0, 0.0, 4.0, 0.15)),
        Template("mince", "meat", "Hackfleisch (gebraten)", "Hackfleisch", listOf("hack", "bolognese", "frikadelle", "bulette"), 125.0, Confidence.MEDIUM, n(260.0, 22.0, 0.0, 19.0, 0.0, 0.0, 7.5, 0.2)),
        Template("salmon", "fish", "Lachs (gebraten)", "Lachs", listOf("lachs", "fisch", "forelle", "thunfisch"), 150.0, Confidence.HIGH, n(210.0, 22.0, 0.0, 13.0, 0.0, 0.0, 2.5, 0.15)),
        Template("egg", "egg", "Ei (gekocht)", "Ei", listOf("ei", "eier", "spiegelei", "rührei", "omelett"), 110.0, Confidence.MEDIUM, n(155.0, 13.0, 1.1, 11.0, 0.0, 1.1, 3.3, 0.35)),
        Template("sauce_cream", "sauce", "Sahnesoße", "Sahnesoße", listOf("sahnesoße", "sahnesauce", "rahmsoße", "rahmsauce", "sahne", "carbonara"), 90.0, Confidence.MEDIUM, n(150.0, 1.8, 5.0, 13.5, 0.2, 2.0, 8.0, 0.9)),
        Template("sauce_tomato", "sauce", "Tomatensoße", "Tomatensoße", listOf("tomatensoße", "tomatensauce", "arrabbiata", "napoli", "pomodoro"), 120.0, Confidence.MEDIUM, n(50.0, 1.5, 7.0, 1.5, 1.5, 5.0, 0.2, 0.8)),
        Template("sauce_gravy", "sauce", "Bratensoße", "Bratensoße", listOf("bratensoße", "bratensauce", "jus"), 80.0, Confidence.MEDIUM, n(45.0, 1.0, 5.0, 2.3, 0.2, 1.0, 1.0, 1.2)),
        Template("sauce_curry", "sauce", "Currysoße", "Currysoße", listOf("curry", "currysoße", "kokos"), 120.0, Confidence.MEDIUM, n(110.0, 1.5, 9.0, 7.5, 1.0, 5.0, 4.0, 1.0)),
        Template("sauce_unknown", "sauce", "Soße", "Soße", listOf("soße", "sosse", "sauce", "dip"), 90.0, Confidence.LOW, n(100.0, 1.5, 6.0, 7.5, 0.5, 3.0, 4.0, 1.0)),
        Template("pizza", "pizza", "Pizza Margherita", "Pizza", listOf("pizza"), 350.0, Confidence.MEDIUM, n(245.0, 10.0, 30.0, 9.0, 2.0, 3.0, 4.5, 1.3)),
        Template("burger", "burger", "Hamburger", "Burger", listOf("burger"), 230.0, Confidence.MEDIUM, n(250.0, 13.0, 22.0, 12.0, 1.5, 4.5, 4.5, 1.2)),
        Template("doener", "doener", "Döner Kebab", "Döner", listOf("döner", "doener", "kebab", "dürüm"), 400.0, Confidence.MEDIUM, n(215.0, 12.0, 20.0, 10.0, 1.5, 2.5, 3.5, 1.3)),
        Template("soup", "soup", "Gemüsesuppe", "Suppe", listOf("suppe", "eintopf", "brühe"), 350.0, Confidence.MEDIUM, n(40.0, 1.5, 5.0, 1.5, 1.0, 2.0, 0.5, 0.8)),
        Template("salad", "salad", "Blattsalat", "Salat", listOf("salat"), 100.0, Confidence.HIGH, n(15.0, 1.3, 1.5, 0.2, 1.3, 1.0, 0.0, 0.02)),
        Template("dressing", "dressing", "Salatdressing", "Dressing", listOf("dressing", "vinaigrette", "salat"), 30.0, Confidence.LOW, n(300.0, 0.5, 6.0, 30.0, 0.0, 5.0, 3.0, 2.0)),
        Template("broccoli", "vegetable", "Brokkoli (gegart)", "Brokkoli", listOf("brokkoli", "broccoli"), 150.0, Confidence.HIGH, n(35.0, 2.8, 4.0, 0.4, 3.0, 1.5, 0.1, 0.03)),
        Template("vegetables", "vegetable", "Gemüse (gemischt, gegart)", "Gemüse", listOf("gemüse", "paprika", "zucchini", "möhre", "karotte", "erbsen", "bohnen"), 150.0, Confidence.MEDIUM, n(35.0, 2.0, 5.0, 0.3, 2.8, 3.0, 0.1, 0.05)),
        Template("roll", "bread", "Brötchen", "Brötchen", listOf("brötchen", "semmel", "schrippe"), 60.0, Confidence.HIGH, n(270.0, 9.0, 52.0, 2.0, 3.0, 2.5, 0.4, 1.3)),
        Template("bread", "bread", "Mischbrot", "Brot", listOf("brot", "toast", "stulle"), 100.0, Confidence.MEDIUM, n(230.0, 7.0, 45.0, 1.5, 5.0, 2.0, 0.3, 1.2)),
        Template("butter", "butter", "Butter", "Butter", listOf("butter"), 10.0, Confidence.LOW, n(740.0, 0.7, 0.6, 82.0, 0.0, 0.6, 52.0, 0.02)),
        Template("cheese", "cheese", "Gouda", "Käse", listOf("käse", "gouda", "cheddar", "mozzarella", "parmesan"), 30.0, Confidence.MEDIUM, n(356.0, 25.0, 0.0, 28.0, 0.0, 0.0, 18.0, 2.0)),
        Template("sausage", "sausage", "Salami", "Wurst", listOf("wurst", "salami", "schinken", "bratwurst", "würstchen"), 40.0, Confidence.MEDIUM, n(380.0, 20.0, 1.0, 33.0, 0.0, 1.0, 12.0, 4.0)),
        Template("oats", "cereal", "Haferflocken", "Haferflocken", listOf("hafer", "porridge", "haferbrei", "oatmeal"), 60.0, Confidence.MEDIUM, n(370.0, 13.5, 59.0, 7.0, 10.0, 1.0, 1.2, 0.0)),
        Template("muesli", "cereal", "Müsli", "Müsli", listOf("müsli", "muesli", "granola", "cornflakes"), 60.0, Confidence.MEDIUM, n(360.0, 9.0, 62.0, 6.0, 8.0, 20.0, 1.2, 0.1)),
        Template("yogurt", "dairy", "Naturjoghurt 3,5 %", "Joghurt", listOf("joghurt", "jogurt", "skyr"), 150.0, Confidence.MEDIUM, n(62.0, 3.5, 4.5, 3.5, 0.0, 4.5, 2.3, 0.1)),
        Template("quark", "dairy", "Magerquark", "Quark", listOf("quark"), 250.0, Confidence.MEDIUM, n(67.0, 12.0, 4.0, 0.2, 0.0, 4.0, 0.1, 0.1)),
        Template("milk", "milk", "Milch 1,5 %", "Milch", listOf("milch"), 200.0, Confidence.MEDIUM, n(47.0, 3.4, 4.9, 1.5, 0.0, 4.9, 1.0, 0.1)),
        Template("apple", "fruit", "Apfel", "Apfel", listOf("apfel", "äpfel"), 150.0, Confidence.HIGH, n(52.0, 0.3, 12.0, 0.2, 2.4, 10.0, 0.0, 0.0)),
        Template("banana", "fruit2", "Banane", "Banane", listOf("banane"), 120.0, Confidence.HIGH, n(90.0, 1.1, 20.0, 0.3, 2.0, 17.0, 0.1, 0.0)),
        Template("oil", "oil", "Speiseöl", "Öl", emptyList(), 10.0, Confidence.LOW, n(884.0, 0.0, 0.0, 100.0, 0.0, 0.0, 9.0, 0.0)),
        Template("mixed_dish", "mixed", "Gemischtes Gericht", "Gericht", emptyList(), 350.0, Confidence.LOW, n(150.0, 7.0, 15.0, 6.5, 2.0, 3.0, 2.0, 0.8)),
    )

    fun byId(id: String): Template = templates.first { it.id == id }

    fun shortName(name: String): String = templates.firstOrNull { it.name == name }?.shortName ?: name

    /** True when a sauce was mentioned but its type is unclear. */
    fun genericSauceMatched(tokens: List<String>): Boolean {
        val specific = templates.filter { it.group == "sauce" && it.id != "sauce_unknown" }
        val specificMatched = specific.any { t -> t.keywords.any { StubFoodAnalyzer.matches(tokens, it) } }
        return !specificMatched
    }
}
