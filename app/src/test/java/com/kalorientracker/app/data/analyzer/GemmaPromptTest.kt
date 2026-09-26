package com.kalorientracker.app.data.analyzer

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.CorrectionHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real outputs of Gemma 4 E2B on Food-101 photos, recorded with the production prompt. */
class GemmaPromptTest {

    private val nachos = """{"gericht":"Nachos mit Käse, Guacamole und Salsa","zutaten":[{"name":"Tortilla Chips (gebacken)","gramm":200,"sicherheit":"hoch","kcal":600,"protein":8,"kohlenhydrate":100,"fett":30},{"name":"Käse (gerieben)","gramm":50,"sicherheit":"hoch","kcal":250,"protein":15,"kohlenhydrate":5,"fett":20},{"name":"Guacamole","gramm":100,"sicherheit":"mittel","kcal":250,"protein":3,"kohlenhydrate":25,"fett":20},{"name":"Salsa","gramm":50,"sicherheit":"mittel","kcal":100,"protein":1,"kohlenhydrate":20,"fett":5}],"rueckfragen":[{"frage":"Welche Art von Käse wurde verwendet (z.B. Cheddar, Mozzarella)?","optionen":["Cheddar","Mozzarella","Monterey Jack"]}]}"""

    /** Constrained decoding often puts every token on its own line. */
    private val multiline = """
        {
        "gericht":
        "Sushi-Platte",
        "zutaten":
        [
        {
        "name":
        "Reis (gekocht)",
        "gramm":
        150,
        "sicherheit":
        "hoch",
        "kcal":
        210,
        "protein":
        4,
        "kohlenhydrate":
        45,
        "fett":
        0
        }
        ]
        ,
        "rueckfragen":
        []
        }
    """.trimIndent()

    @Test
    fun parsesRealModelOutput() {
        val result = GemmaPrompt.parse(nachos, "Nachos mit Käse", photoCount = 1, corrections = emptyList())
        assertNotNull(result)
        result!!
        assertEquals(listOf("Tortilla Chips (gebacken)", "Käse (gerieben)", "Guacamole", "Salsa"), result.ingredients.map { it.name })
        assertEquals(200.0, result.ingredients[0].estimatedGrams, 0.0)
        assertEquals(Confidence.HIGH, result.ingredients[0].confidence)
        assertEquals(Confidence.MEDIUM, result.ingredients[2].confidence)
        assertEquals("Nachos mit Käse", result.mealName)
        assertEquals(GemmaPrompt.SOURCE, result.context.source)
        val question = result.followUpQuestions.single()
        assertEquals(listOf("Cheddar", "Mozzarella", "Monterey Jack"), question.options)
        assertEquals(result.followUpQuestions, result.context.questions)
    }

    @Test
    fun usesModelDishNameWithoutDescription() {
        val result = GemmaPrompt.parse(nachos, "", photoCount = 1, corrections = emptyList())!!
        assertEquals("Nachos mit Käse, Guacamole und Salsa", result.mealName)
    }

    @Test
    fun imageOnlyResultsAreNeverShownAsVerySure() {
        val result = GemmaPrompt.parse(nachos, "", photoCount = 1, corrections = emptyList())!!
        assertTrue(result.ingredients.none { it.confidence == Confidence.HIGH })
    }

    @Test
    fun toleratesLineBrokenJsonAndSurroundingText() {
        val result = GemmaPrompt.parse("Hier ist die Analyse:\n$multiline\nGuten Appetit", "", 1, emptyList())
        assertNotNull(result)
        assertEquals("Reis (gekocht)", result!!.ingredients.single().name)
        // A photo without a description is never just accepted silently.
        assertEquals("Wie viel Reis war dabei?", result.followUpQuestions.single().text)
    }

    @Test
    fun returnsNullForUnusableOutput() {
        assertNull(GemmaPrompt.parse("Ich kann das Bild nicht erkennen.", "", 1, emptyList()))
        assertNull(GemmaPrompt.parse("""{"gericht":"x","zutaten":[],"rueckfragen":[]}""", "", 1, emptyList()))
        assertNull(GemmaPrompt.parse("""{"gericht":"x","zutaten":[{"name":"Reis""", "", 1, emptyList()))
    }

    @Test
    fun sanitizesImplausibleValues() {
        val raw = """{"gericht":"Test","zutaten":[
            {"name":"Reis (gekocht)","gramm":200,"sicherheit":"hoch","kcal":260,"protein":45,"kohlenhydrate":45,"fett":1.5},
            {"name":"Reis (gekocht)","gramm":100,"sicherheit":"hoch","kcal":260,"protein":45,"kohlenhydrate":45,"fett":1.5},
            {"name":"Soße","gramm":9000,"sicherheit":"egal","kcal":5000,"protein":0,"kohlenhydrate":0,"fett":0},
            {"name":"Nichts","gramm":0,"sicherheit":"hoch","kcal":10,"protein":0,"kohlenhydrate":0,"fett":0},
            {"name":"X","gramm":10,"sicherheit":"hoch","kcal":10,"protein":0,"kohlenhydrate":0,"fett":0}
        ],"rueckfragen":[{"frage":"Wurde es gebraten?","optionen":["Ja","Nein","Nein"]},{"frage":"Welche?","optionen":["Nur eine"]}]}"""
        val result = GemmaPrompt.parse(raw, "Reis mit Soße", 1, emptyList())!!
        assertEquals(2, result.ingredients.size)
        val rice = result.ingredients[0]
        assertEquals(300.0, rice.estimatedGrams, 0.0)
        assertEquals(373.5, rice.per100g.kcal, 0.01)
        val sauce = result.ingredients[1]
        assertEquals(GemmaPrompt.MAX_GRAMS, sauce.estimatedGrams, 0.0)
        assertEquals(900.0, sauce.per100g.kcal, 0.0)
        assertEquals(Confidence.MEDIUM, sauce.confidence)
        assertEquals(listOf("Ja", "Nein"), result.followUpQuestions.single().options)
    }

    @Test
    fun appliesPersonalCorrections() {
        val result = GemmaPrompt.parse(nachos, "", 1, listOf(CorrectionHint("guacamole", 1.5, 3)))!!
        assertEquals(150.0, result.ingredients.first { it.name == "Guacamole" }.estimatedGrams, 0.0)
        assertTrue(result.notes.any { it.contains("Guacamole") })
    }

    @Test
    fun followUpRoundHasNoNewQuestions() {
        val result = GemmaPrompt.parse(nachos, "", 1, emptyList(), allowQuestions = false)!!
        assertTrue(result.followUpQuestions.isEmpty())
    }

    @Test
    fun promptsCarryDescriptionCorrectionsAndAnswers() {
        val prompt = GemmaPrompt.analysisPrompt("Reis mit Hähnchen", 2, listOf(CorrectionHint("reis", 1.4, 3)))
        assertTrue(prompt.contains("Reis mit Hähnchen"))
        assertTrue(prompt.contains("2 Fotos"))
        assertTrue(prompt.contains("reis ×1,40"))
        assertTrue(GemmaPrompt.analysisPrompt("", 0, emptyList()).contains("(keine)"))

        val result = GemmaPrompt.parse(nachos, "Nachos", 1, emptyList())!!
        val followUp = GemmaPrompt.followUpPrompt(result.context, listOf(FollowUpAnswer("gemma_0", "Cheddar")))
        assertTrue(followUp.contains("Welche Art von Käse"))
        assertTrue(followUp.contains("→ Cheddar"))
        assertTrue(followUp.contains("Tortilla Chips (gebacken) 200 g"))
    }

    @Test
    fun addsBackFoodTheModelOverlookedAndAsksForItsPortion() {
        val raw = """{"gericht":"Reis mit Hähnchen","zutaten":[
            {"name":"Reis (gekocht)","gramm":200,"sicherheit":"hoch","kcal":130,"protein":2.7,"kohlenhydrate":28,"fett":0.3},
            {"name":"Hähnchenbrust (gebraten)","gramm":150,"sicherheit":"hoch","kcal":165,"protein":31,"kohlenhydrate":0,"fett":3.6}
        ],"rueckfragen":[]}"""
        val result = GemmaPrompt.parse(raw, "Reis mit Hähnchen und Brokkoli", 1, emptyList())!!

        assertEquals(listOf("Reis (gekocht)", "Hähnchenbrust (gebraten)", "Brokkoli (gegart)"), result.ingredients.map { it.name })
        val broccoli = result.ingredients.last()
        assertEquals(Confidence.LOW, broccoli.confidence)
        assertTrue(result.notes.any { it.contains("Brokkoli") })

        val question = result.followUpQuestions.single()
        assertEquals("Wie viel Brokkoli war dabei?", question.text)
        assertEquals(DescriptionAnchors.QUESTION_PREFIX + broccoli.foodKey, question.id)
    }

    @Test
    fun statedGramsNeedNoQuestion() {
        val raw = """{"gericht":"Reis","zutaten":[{"name":"Reis (gekocht)","gramm":200,"sicherheit":"hoch","kcal":130,"protein":2.7,"kohlenhydrate":28,"fett":0.3}],"rueckfragen":[]}"""
        val result = GemmaPrompt.parse(raw, "Reis mit 30g Butter", 1, emptyList())!!
        val butter = result.ingredients.first { it.name == "Butter" }
        assertEquals(30.0, butter.estimatedGrams, 0.0)
        assertEquals(Confidence.HIGH, butter.confidence)
        assertTrue(result.followUpQuestions.isEmpty())
    }

    @Test
    fun asksAboutTheShakiestIngredientWhenNothingElseIsOpen() {
        val raw = """{"gericht":"Teller","zutaten":[{"name":"Gemischtes Gericht","gramm":300,"sicherheit":"niedrig","kcal":150,"protein":7,"kohlenhydrate":15,"fett":6.5}],"rueckfragen":[]}"""
        val question = GemmaPrompt.parse(raw, "", 1, emptyList())!!.followUpQuestions.single()
        assertTrue(question.text.startsWith("Wie viel"))
        assertEquals(4, question.options.size)
    }

    @Test
    fun asksAboutTheIngredientThatMovesTheCaloriesMost() {
        val raw = """{"gericht":"Reis mit Hähnchen","zutaten":[
            {"name":"Reis (gekocht)","gramm":300,"sicherheit":"hoch","kcal":130,"protein":2.7,"kohlenhydrate":28,"fett":0.3},
            {"name":"Paprika","gramm":50,"sicherheit":"mittel","kcal":30,"protein":1,"kohlenhydrate":6,"fett":0.2},
            {"name":"Sahnesoße","gramm":100,"sicherheit":"mittel","kcal":150,"protein":1.8,"kohlenhydrate":5,"fett":13.5}
        ],"rueckfragen":[]}"""
        val result = GemmaPrompt.parse(raw, "Reis mit Paprika und Soße", 1, emptyList())!!
        assertEquals("Wie viel Sahnesoße war dabei?", result.followUpQuestions.single().text)
    }

    @Test
    fun asksNothingWhenTheModelIsSureAboutEverything() {
        val raw = """{"gericht":"Reis","zutaten":[{"name":"Reis (gekocht)","gramm":200,"sicherheit":"hoch","kcal":130,"protein":2.7,"kohlenhydrate":28,"fett":0.3}],"rueckfragen":[]}"""
        assertTrue(GemmaPrompt.parse(raw, "200g Reis", 1, emptyList())!!.followUpQuestions.isEmpty())
    }

    @Test
    fun usersOwnPortionAnswerWinsOverTheModel() {
        val raw = """{"gericht":"Reis","zutaten":[{"name":"Reis (gekocht)","gramm":200,"sicherheit":"hoch","kcal":130,"protein":2.7,"kohlenhydrate":28,"fett":0.3}],"rueckfragen":[]}"""
        val result = GemmaPrompt.parse(raw, "Reis mit Brokkoli", 1, emptyList())!!
        val question = result.followUpQuestions.single()

        val answered = GemmaPrompt.applyPortionAnswers(result, listOf(FollowUpAnswer(question.id, "Viel (240 g)")), "Reis mit Brokkoli")
        val broccoli = answered.ingredients.first { it.name.startsWith("Brokkoli") }
        assertEquals(240.0, broccoli.estimatedGrams, 0.0)
        assertEquals(Confidence.HIGH, broccoli.confidence)
        assertEquals(answered.ingredients, answered.context.ingredients)
    }

    @Test
    fun promptNamesTheFoodsFromTheDescription() {
        val prompt = GemmaPrompt.analysisPrompt("Reis mit Brokkoli", 1, emptyList())
        assertTrue(prompt.contains("Der Nutzer nennt ausdrücklich: Reis, Brokkoli"))
    }

    @Test
    fun schemaIsValidJson() {
        val schema = kotlinx.serialization.json.Json.parseToJsonElement(GemmaPrompt.SCHEMA)
        assertTrue(schema.toString().contains("additionalProperties"))
    }
}
