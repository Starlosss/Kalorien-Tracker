package com.kalorientracker.app.data.ai

import android.util.Log
import com.kalorientracker.app.data.analyzer.AnalysisContext
import com.kalorientracker.app.data.analyzer.AnalysisInput
import com.kalorientracker.app.data.analyzer.AnalysisResult
import com.kalorientracker.app.data.analyzer.FollowUpAnswer
import com.kalorientracker.app.data.analyzer.FoodAnalyzer
import com.kalorientracker.app.data.analyzer.GemmaPrompt
import com.kalorientracker.app.data.analyzer.StubFoodAnalyzer
import com.kalorientracker.app.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uses the on-device Gemma model when it is downloaded and enabled. Otherwise, or if the model
 * fails, the description-based estimate keeps the flow working – there is always a way forward.
 */
@Singleton
class HybridFoodAnalyzer @Inject constructor(
    private val gemma: GemmaFoodAnalyzer,
    private val models: ModelManager,
    private val settings: SettingsRepository,
) : FoodAnalyzer {

    private val fallback = StubFoodAnalyzer()

    suspend fun gemmaActive(): Boolean = models.isReady() && settings.current().aiEnabled

    override suspend fun analyze(input: AnalysisInput): AnalysisResult {
        if (!gemmaActive()) {
            val result = fallback.analyze(input)
            return if (input.photoPaths.isEmpty()) result else result.withNote(NOTE_NOT_SET_UP)
        }
        val gemmaResult = try {
            gemma.analyze(input)
        } catch (t: Throwable) {
            Log.e(TAG, "Gemma analysis failed", t)
            return fallback.analyze(input).withNote(NOTE_FAILED)
        }
        return gemmaResult ?: fallback.analyze(input).withNote(NOTE_UNCLEAR)
    }

    override suspend fun answerFollowUp(context: AnalysisContext, answers: List<FollowUpAnswer>): AnalysisResult {
        if (context.source != GemmaPrompt.SOURCE) return fallback.answerFollowUp(context, answers)
        val updated = try {
            gemma.answerFollowUp(context, answers)
        } catch (t: Throwable) {
            Log.e(TAG, "Gemma follow-up failed", t)
            null
        }
        return updated ?: AnalysisResult(
            mealName = StubFoodAnalyzer.mealName(context.description, context.ingredients),
            ingredients = context.ingredients,
            followUpQuestions = emptyList(),
            context = context,
            notes = listOf("Deine Antworten konnten nicht eingearbeitet werden. Bitte die Zutaten kurz selbst anpassen."),
        )
    }

    private fun AnalysisResult.withNote(note: String) = copy(notes = listOf(note) + notes)

    companion object {
        private const val TAG = "HybridFoodAnalyzer"
        const val NOTE_NOT_SET_UP = "Die Foto-Erkennung ist noch nicht eingerichtet. Die Schätzung kommt nur aus deiner Beschreibung. Einrichten unter Einstellungen → KI-Erkennung."
        const val NOTE_FAILED = "Die Foto-Erkennung konnte das Bild nicht auswerten. Die Schätzung kommt aus deiner Beschreibung."
        const val NOTE_UNCLEAR = "Die Foto-Erkennung war sich unsicher. Die Schätzung kommt aus deiner Beschreibung. Bitte die Zutaten prüfen."
    }
}
