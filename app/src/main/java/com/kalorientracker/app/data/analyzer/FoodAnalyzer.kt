package com.kalorientracker.app.data.analyzer

import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.CorrectionHint
import com.kalorientracker.app.domain.model.Nutrients

/**
 * Boundary to the on-device food recognition. The rest of the app only talks to this interface,
 * so a real vision model can replace [StubFoodAnalyzer] without UI changes.
 * Implementations must be safe to call off the main thread and must not send data off-device.
 */
interface FoodAnalyzer {
    suspend fun analyze(input: AnalysisInput): AnalysisResult

    suspend fun answerFollowUp(context: AnalysisContext, answers: List<FollowUpAnswer>): AnalysisResult
}

data class AnalysisInput(
    /** Absolute paths of all photos of this meal; analyzed together as one dataset. */
    val photoPaths: List<String>,
    val description: String,
    val corrections: List<CorrectionHint> = emptyList(),
)

data class RecognizedIngredient(
    val name: String,
    /** Search key into the local food database. */
    val foodKey: String,
    val estimatedGrams: Double,
    val confidence: Confidence,
    /** Analyzer's own nutrient estimate, used when no database match exists. */
    val per100g: Nutrients,
)

data class FollowUpQuestion(
    val id: String,
    val text: String,
    val options: List<String>,
)

data class FollowUpAnswer(
    val questionId: String,
    val option: String,
)

/** Everything needed to continue an analysis statelessly. */
data class AnalysisContext(
    val description: String,
    val photoCount: Int,
    val ingredients: List<RecognizedIngredient>,
    val corrections: List<CorrectionHint>,
)

data class AnalysisResult(
    val mealName: String,
    val ingredients: List<RecognizedIngredient>,
    val followUpQuestions: List<FollowUpQuestion>,
    val context: AnalysisContext,
    val notes: List<String> = emptyList(),
)
