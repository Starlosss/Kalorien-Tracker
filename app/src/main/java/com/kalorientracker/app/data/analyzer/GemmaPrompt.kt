package com.kalorientracker.app.data.analyzer

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.CorrectionHint
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.usecase.FoodKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Prompt, output schema and tolerant parsing for the on-device Gemma model. Kept free of Android
 * and runtime classes so it can be unit-tested; the values were tuned against real food photos.
 */
object GemmaPrompt {

    const val SOURCE = "gemma"

    val SYSTEM = """
        Du bist Ernährungsberater und analysierst Fotos von Mahlzeiten für eine Kalorien-App.
        Zerlege die Mahlzeit in einzelne Zutaten mit deutschem Namen (z. B. "Reis (gekocht)", "Hähnchenbrust (gebraten)", "Sahnesoße", "Speiseöl").
        "gramm": geschätztes Gewicht der zubereiteten Zutat auf dem Teller. Nutze Teller (ca. 26 cm), Besteck und Schalen als Größenvergleich.
        "kcal", "protein", "kohlenhydrate", "fett": realistische Durchschnittswerte pro 100 g der zubereiteten Zutat.
        Nenne versteckte Fette wie Bratöl, Butter oder Dressing als eigene Zutat, wenn sie wahrscheinlich sind.
        "sicherheit": "hoch" nur wenn Zutat und Menge klar erkennbar sind, sonst "mittel" oder "niedrig".
        "rueckfragen": höchstens 3, wenn eine Zutat oder eine Menge unklar ist oder die Antwort die Kalorien deutlich ändert (z. B. welche Soße, paniert, Ölmenge), je 2 bis 4 kurze Optionen. Sonst leere Liste.
        Foto und Beschreibung gehören zusammen: Das Foto zeigt die Mengen, die Beschreibung sagt, was es ist.
        Jede in der Beschreibung genannte Zutat muss in "zutaten" stehen, auch wenn sie auf dem Foto verdeckt oder schlecht zu sehen ist.
        Bei Widersprüchen gilt die Beschreibung des Nutzers.
        Antworte nur mit kompaktem JSON in einer Zeile, ohne Zeilenumbrüche, auf Deutsch, in diesem Format:
        {"gericht":"...","zutaten":[{"name":"...","gramm":0,"sicherheit":"hoch","kcal":0,"protein":0,"kohlenhydrate":0,"fett":0}],"rueckfragen":[{"frage":"...","optionen":["...","..."]}]}
    """.trimIndent()

    /** JSON schema for constrained decoding. additionalProperties=false keeps the small model from inventing keys. */
    val SCHEMA: String = """
        {"type":"object","additionalProperties":false,"required":["gericht","zutaten","rueckfragen"],
        "properties":{"gericht":{"type":"string"},
        "zutaten":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,
        "required":["name","gramm","sicherheit","kcal","protein","kohlenhydrate","fett"],
        "properties":{"name":{"type":"string"},"gramm":{"type":"integer"},"sicherheit":{"type":"string","enum":["hoch","mittel","niedrig"]},
        "kcal":{"type":"integer"},"protein":{"type":"number"},"kohlenhydrate":{"type":"number"},"fett":{"type":"number"}}}},
        "rueckfragen":{"type":"array","maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["frage","optionen"],
        "properties":{"frage":{"type":"string"},"optionen":{"type":"array","items":{"type":"string"}}}}}}}
    """.trimIndent().replace("\n", "")

    fun analysisPrompt(description: String, photoCount: Int, corrections: List<CorrectionHint>): String = buildString {
        append("Analysiere diese Mahlzeit.")
        if (photoCount > 1) append(" Die $photoCount Fotos zeigen dieselbe Mahlzeit aus verschiedenen Blickwinkeln.")
        if (photoCount == 0) append(" Es gibt kein Foto, nutze nur die Beschreibung.")
        append("\nBeschreibung des Nutzers: ")
        append(description.trim().ifEmpty { "(keine)" })
        val anchors = DescriptionAnchors.of(description)
        if (anchors.isNotEmpty()) {
            append("\nDer Nutzer nennt ausdrücklich: ")
            append(anchors.joinToString(", ") { it.shortName })
            append(". Diese Zutaten müssen in \"zutaten\" vorkommen, auch wenn du sie im Foto nicht sicher siehst.")
        }
        if (corrections.isNotEmpty()) {
            append("\nErfahrungswerte dieses Nutzers (seine Portionen im Vergleich zu üblichen Schätzungen): ")
            append(corrections.take(8).joinToString(", ") { "${it.foodKey} ×${formatRatio(it.ratio)}" })
        }
    }

    fun followUpPrompt(context: AnalysisContext, answers: List<FollowUpAnswer>): String = buildString {
        append("Du hast diese Mahlzeit bereits analysiert.")
        append("\nBeschreibung des Nutzers: ").append(context.description.trim().ifEmpty { "(keine)" })
        append("\nBisherige Zutaten: ")
        append(context.ingredients.joinToString("; ") { "${it.name} ${it.estimatedGrams.roundToInt()} g" })
        append("\nAntworten des Nutzers auf deine Rückfragen:")
        for (answer in answers) {
            val question = context.questions.firstOrNull { it.id == answer.questionId }?.text ?: continue
            append("\n- ").append(question).append(" → ").append(answer.option)
        }
        append("\nPasse die Zutatenliste an diese Antworten an (Zutaten ändern, entfernen oder ergänzen, Mengen anpassen). Stelle keine neuen Rückfragen.")
    }

    private fun formatRatio(ratio: Double): String = String.format(java.util.Locale.GERMANY, "%.2f", ratio)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Parses and sanitizes the model output. Returns null when nothing usable was produced so the
     * caller can fall back to the description-based estimate.
     */
    fun parse(
        raw: String,
        description: String,
        photoCount: Int,
        corrections: List<CorrectionHint>,
        allowQuestions: Boolean = true,
    ): AnalysisResult? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1).replace('\n', ' ').replace('\r', ' ')) as? JsonObject
        }.getOrNull() ?: return null

        val merged = LinkedHashMap<String, RecognizedIngredient>()
        val usedCorrections = mutableSetOf<String>()
        for (element in (root["zutaten"] as? JsonArray).orEmpty()) {
            val o = element as? JsonObject ?: continue
            val name = o.string("name")?.trim()?.take(MAX_NAME)?.takeIf { it.length >= 2 } ?: continue
            var grams = o.number("gramm") ?: continue
            if (grams <= 0) continue
            grams = grams.coerceIn(1.0, MAX_GRAMS)
            val key = FoodKey.of(name)
            corrections.firstOrNull { it.foodKey == key }?.let {
                grams *= it.ratio
                usedCorrections += name
            }
            val per100g = sanitizeNutrients(o.number("kcal"), o.number("protein"), o.number("kohlenhydrate"), o.number("fett"))
            // From the image alone the small model is overconfident; without a description it never claims "hoch".
            val confidence = when (o.string("sicherheit")?.lowercase()) {
                "hoch" -> if (description.isBlank()) Confidence.MEDIUM else Confidence.HIGH
                "niedrig" -> Confidence.LOW
                else -> Confidence.MEDIUM
            }
            val existing = merged[key]
            merged[key] = if (existing == null) {
                RecognizedIngredient(name, key, roundTo5(grams), confidence, per100g)
            } else {
                existing.copy(estimatedGrams = roundTo5(existing.estimatedGrams + grams))
            }
            if (merged.size >= MAX_INGREDIENTS) break
        }
        if (merged.isEmpty()) return null

        // Whatever the user named themselves is never dropped, even when the model overlooked it.
        val anchors = DescriptionAnchors.of(description)
        val missing = anchors.filterNot { DescriptionAnchors.covered(it, merged.values.toList()) }
        val ingredients = merged.values.toList() +
            missing.map { it.toIngredient(if (it.statedGrams) Confidence.HIGH else Confidence.LOW) }

        val modelQuestions = if (!allowQuestions) emptyList() else (root["rueckfragen"] as? JsonArray).orEmpty()
            .mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val text = o.string("frage")?.trim()?.takeIf { it.length >= 4 } ?: return@mapNotNull null
                val options = (o["optionen"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.take(40) }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .take(4)
                if (options.size < 2) null else text to options
            }
            .mapIndexed { i, (text, options) -> FollowUpQuestion("gemma_$i", text, options) }

        val questions = if (!allowQuestions) emptyList() else buildQuestions(modelQuestions, missing, ingredients, description, photoCount)

        val mealName = root.string("gericht")?.trim()?.takeIf { it.length >= 2 }?.take(MAX_NAME)
            ?: StubFoodAnalyzer.mealName(description, ingredients)
        val notes = buildList {
            add("Erkannt mit Gemma auf deinem Gerät – bitte Mengen kurz prüfen.")
            if (missing.isNotEmpty()) {
                add("Auf dem Foto nicht erkannt, aus deiner Beschreibung ergänzt: ${missing.joinToString { it.shortName }}.")
            }
            if (photoCount >= 2) add("$photoCount Fotos wurden gemeinsam ausgewertet.")
            if (usedCorrections.isNotEmpty()) add("Deine üblichen Portionen wurden berücksichtigt: ${usedCorrections.joinToString()}.")
        }
        return AnalysisResult(
            mealName = if (description.isNotBlank()) StubFoodAnalyzer.mealName(description, ingredients) else mealName,
            ingredients = ingredients,
            followUpQuestions = questions,
            context = AnalysisContext(description, photoCount, ingredients, corrections, questions, SOURCE),
            notes = notes,
        )
    }

    /**
     * Rather than silently guessing, the app asks. Foods the model lost come first, then its own
     * questions, and if nothing is open but the result is shaky it asks for the portion of the one
     * ingredient it is least sure about.
     */
    private fun buildQuestions(
        modelQuestions: List<FollowUpQuestion>,
        missing: List<DescriptionAnchors.Anchor>,
        ingredients: List<RecognizedIngredient>,
        description: String,
        photoCount: Int,
    ): List<FollowUpQuestion> {
        val forced = missing.filterNot { it.statedGrams }
            .map { DescriptionAnchors.portionQuestion(it.name, it.shortName, it.grams) }
        val questions = (forced + modelQuestions).distinctBy { it.text.lowercase() }.take(MAX_QUESTIONS)
        if (questions.isNotEmpty()) return questions

        // Nothing was flagged, but an unclear ingredient or a photo without any description still
        // deserves one question instead of a silent estimate.
        val uncertain = ingredients.firstOrNull { it.confidence == Confidence.LOW }
            ?: ingredients.maxByOrNull { it.estimatedGrams }.takeIf { description.isBlank() && photoCount > 0 }
            ?: return emptyList()
        return listOf(
            DescriptionAnchors.portionQuestion(uncertain.name, StubCatalog.shortName(uncertain.name), uncertain.estimatedGrams),
        )
    }

    /** Applies the app's own portion answers on top of a result; the user's statement wins. */
    fun applyPortionAnswers(result: AnalysisResult, answers: List<FollowUpAnswer>, description: String): AnalysisResult {
        val anchors = DescriptionAnchors.of(description)
        val ingredients = DescriptionAnchors.applyPortionAnswers(result.ingredients, answers, anchors)
        if (ingredients == result.ingredients) return result
        return result.copy(
            ingredients = ingredients,
            mealName = StubFoodAnalyzer.mealName(description, ingredients),
            context = result.context.copy(ingredients = ingredients),
        )
    }

    /**
     * Small models sometimes return kcal that do not match their own macros. Values are clamped
     * and kcal is recomputed from the macros when the two disagree clearly.
     */
    fun sanitizeNutrients(kcal: Double?, protein: Double?, carbs: Double?, fat: Double?): Nutrients {
        val p = (protein ?: 0.0).coerceIn(0.0, 100.0)
        val c = (carbs ?: 0.0).coerceIn(0.0, 100.0)
        val f = (fat ?: 0.0).coerceIn(0.0, 100.0)
        val fromMacros = 4 * p + 4 * c + 9 * f
        var k = (kcal ?: fromMacros).coerceIn(0.0, 900.0)
        if (fromMacros > 0 && abs(k - fromMacros) > maxOf(40.0, 0.25 * maxOf(k, fromMacros))) k = fromMacros.coerceAtMost(900.0)
        return Nutrients(kcal = k, protein = p, carbs = c, fat = f)
    }

    private fun roundTo5(value: Double): Double = ((value / 5.0).roundToInt() * 5).coerceAtLeast(5).toDouble()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        return p.doubleOrNull ?: p.contentOrNull?.replace(',', '.')?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
    }

    const val MAX_INGREDIENTS = 10
    const val MAX_QUESTIONS = 3
    const val MAX_GRAMS = 2000.0
    private const val MAX_NAME = 60
}
