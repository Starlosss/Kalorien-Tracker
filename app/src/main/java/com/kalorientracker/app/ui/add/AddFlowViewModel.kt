package com.kalorientracker.app.ui.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.analyzer.AnalysisContext
import com.kalorientracker.app.data.analyzer.AnalysisInput
import com.kalorientracker.app.data.analyzer.AnalysisResult
import com.kalorientracker.app.data.analyzer.FollowUpAnswer
import com.kalorientracker.app.data.analyzer.FollowUpQuestion
import com.kalorientracker.app.data.analyzer.FoodAnalyzer
import com.kalorientracker.app.data.analyzer.RecognizedIngredient
import com.kalorientracker.app.data.image.ImageIssue
import com.kalorientracker.app.data.image.PhotoStorage
import com.kalorientracker.app.data.repository.FoodRepository
import com.kalorientracker.app.data.repository.MealRepository
import com.kalorientracker.app.data.repository.ProductLookup
import com.kalorientracker.app.data.repository.ProfileRepository
import com.kalorientracker.app.domain.model.Confidence
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.Ingredient
import com.kalorientracker.app.domain.model.LearningCorrection
import com.kalorientracker.app.domain.model.Meal
import com.kalorientracker.app.domain.model.MealCategory
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.model.sum
import com.kalorientracker.app.domain.usecase.ApplyLearningCorrectionsUseCase
import com.kalorientracker.app.domain.usecase.FoodKey
import com.kalorientracker.app.domain.usecase.PortionScale
import com.kalorientracker.app.domain.usecase.SuggestMealCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** One ingredient while the meal is being edited. [grams] is before the meal-wide portion factor. */
data class DraftIngredient(
    val key: String = UUID.randomUUID().toString(),
    val name: String,
    val foodId: Long?,
    val foodKey: String,
    val per100g: Nutrients,
    /** Reference amount the per-ingredient slider scales (analyzer estimate or first chosen amount). */
    val baseGrams: Double,
    val grams: Double,
    /** Set only for analyzer results; used to learn from corrections. */
    val estimatedGrams: Double? = null,
    val confidence: Confidence? = null,
    val portionIndex: Int? = PortionScale.NORMAL_INDEX,
)

enum class AnalysisPhase { IDLE, RUNNING, QUESTIONS, DONE, FAILED }

sealed interface BarcodeState {
    data object Scanning : BarcodeState
    data class Loading(val code: String) : BarcodeState
    data class Found(val food: Food, val fromOnline: Boolean, val defaultGrams: Double) : BarcodeState
    data class NotFound(val code: String, val reason: String) : BarcodeState
}

data class AddFlowState(
    val photos: List<String> = emptyList(),
    val photoIssues: Map<String, ImageIssue> = emptyMap(),
    val description: String = "",
    val phase: AnalysisPhase = AnalysisPhase.IDLE,
    val analysisStep: Int = 0,
    val notes: List<String> = emptyList(),
    val questions: List<FollowUpQuestion> = emptyList(),
    val answers: Map<String, String> = emptyMap(),
    val context: AnalysisContext? = null,
    val ingredients: List<DraftIngredient> = emptyList(),
    val mealPortionIndex: Int = PortionScale.NORMAL_INDEX,
    val mealName: String = "",
    val mealNameEdited: Boolean = false,
    val category: MealCategory = SuggestMealCategoryUseCase()(LocalTime.now()),
    val loggedTime: LocalTime = LocalTime.now(),
    val recentMeals: List<Meal> = emptyList(),
    val barcode: BarcodeState = BarcodeState.Scanning,
    val saving: Boolean = false,
) {
    val portionFactor: Double get() = PortionScale.factor(mealPortionIndex)

    fun effectiveGrams(ingredient: DraftIngredient): Double = ingredient.grams * portionFactor

    val totals: Nutrients
        get() = ingredients.map { it.per100g.forGrams(effectiveGrams(it)) }.sum()

    val hasDraft: Boolean get() = ingredients.isNotEmpty() || photos.isNotEmpty() || description.isNotBlank()

    val suggestedName: String
        get() = when {
            mealNameEdited -> mealName
            mealName.isNotBlank() -> mealName
            ingredients.isEmpty() -> "Mahlzeit"
            ingredients.size == 1 -> ingredients[0].name
            else -> ingredients.take(3).joinToString(", ") { it.name.substringBefore(" (") }
        }
}

@HiltViewModel
class AddFlowViewModel @Inject constructor(
    private val analyzer: FoodAnalyzer,
    private val foods: FoodRepository,
    private val meals: MealRepository,
    private val profiles: ProfileRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(AddFlowState())
    val state: StateFlow<AddFlowState> = _state.asStateFlow()

    private val suggestCategory = SuggestMealCategoryUseCase()

    init {
        loadRecentMeals()
    }

    fun loadRecentMeals() {
        viewModelScope.launch {
            val recent = meals.recentDistinctMeals()
            _state.update { it.copy(recentMeals = recent) }
        }
    }

    // --- Photos -----------------------------------------------------------------------------

    fun newPhotoFile() = photoStorage.newPhotoFile()

    fun onPhotoCaptured(path: String) {
        _state.update { it.copy(photos = it.photos + path) }
        checkQuality(path)
    }

    fun importGallery(uris: List<Uri>, onDone: () -> Unit) {
        viewModelScope.launch {
            for (uri in uris.take(MAX_PHOTOS)) {
                val path = photoStorage.importFromUri(uri) ?: continue
                _state.update { it.copy(photos = it.photos + path) }
                checkQuality(path)
            }
            if (_state.value.photos.isNotEmpty()) onDone()
        }
    }

    private fun checkQuality(path: String) {
        viewModelScope.launch {
            val issue = photoStorage.checkQuality(path) ?: return@launch
            _state.update { it.copy(photoIssues = it.photoIssues + (path to issue)) }
        }
    }

    fun removePhoto(path: String) {
        photoStorage.delete(path)
        _state.update { it.copy(photos = it.photos - path, photoIssues = it.photoIssues - path) }
    }

    fun setDescription(text: String) = _state.update { it.copy(description = text.take(200)) }

    // --- Analysis ---------------------------------------------------------------------------

    /** Runs the analyzer in the background while the visible steps advance calmly. */
    fun analyze() {
        val s = _state.value
        if (s.phase == AnalysisPhase.RUNNING) return
        _state.update { it.copy(phase = AnalysisPhase.RUNNING, analysisStep = 0, questions = emptyList(), answers = emptyMap()) }
        viewModelScope.launch {
            val job = async(Dispatchers.Default) {
                runCatching {
                    analyzer.analyze(AnalysisInput(s.photos, s.description, meals.learningHints()))
                }
            }
            for (step in 0 until ANALYSIS_STEPS) {
                _state.update { it.copy(analysisStep = step) }
                delay(STEP_MS)
            }
            val result = job.await().getOrNull()
            if (result == null) {
                _state.update { it.copy(phase = AnalysisPhase.FAILED) }
                return@launch
            }
            applyResult(result)
            _state.update {
                it.copy(
                    analysisStep = ANALYSIS_STEPS,
                    phase = if (result.followUpQuestions.isEmpty()) AnalysisPhase.DONE else AnalysisPhase.QUESTIONS,
                    questions = result.followUpQuestions,
                )
            }
        }
    }

    private suspend fun applyResult(result: AnalysisResult) {
        val drafts = result.ingredients.map { toDraft(it) }
        _state.update {
            it.copy(
                ingredients = drafts,
                context = result.context,
                notes = result.notes,
                mealName = if (it.mealNameEdited) it.mealName else result.mealName,
                mealPortionIndex = PortionScale.NORMAL_INDEX,
            )
        }
    }

    /** Prefers the local database's values; falls back to the analyzer's own estimate. */
    private suspend fun toDraft(r: RecognizedIngredient): DraftIngredient {
        val food = foods.bestMatch(r.name)
        return DraftIngredient(
            name = r.name,
            foodId = food?.id,
            foodKey = r.foodKey,
            per100g = food?.per100g ?: r.per100g,
            baseGrams = r.estimatedGrams,
            grams = r.estimatedGrams,
            estimatedGrams = r.estimatedGrams,
            confidence = r.confidence,
        )
    }

    fun answer(questionId: String, option: String) =
        _state.update { it.copy(answers = it.answers + (questionId to option)) }

    fun submitAnswers(onDone: () -> Unit) {
        val s = _state.value
        val context = s.context ?: return onDone()
        viewModelScope.launch {
            val answers = s.answers.map { (id, option) -> FollowUpAnswer(id, option) }
            val result = withContext(Dispatchers.Default) { analyzer.answerFollowUp(context, answers) }
            applyResult(result.copy(notes = s.notes))
            _state.update { it.copy(phase = AnalysisPhase.DONE, questions = emptyList()) }
            onDone()
        }
    }

    // --- Ingredient editing -----------------------------------------------------------------

    private fun updateIngredient(key: String, transform: (DraftIngredient) -> DraftIngredient) =
        _state.update { s -> s.copy(ingredients = s.ingredients.map { if (it.key == key) transform(it) else it }) }

    /** [effectiveGrams] is what the user sees (after the meal-wide portion factor). */
    fun setGrams(key: String, effectiveGrams: Double) {
        val factor = _state.value.portionFactor
        updateIngredient(key) { it.copy(grams = (effectiveGrams / factor).coerceIn(0.0, 5000.0), portionIndex = null) }
    }

    fun setIngredientPortion(key: String, index: Int) =
        updateIngredient(key) { it.copy(grams = it.baseGrams * PortionScale.factor(index), portionIndex = index) }

    fun rename(key: String, name: String) = updateIngredient(key) { it.copy(name = name.take(60)) }

    fun remove(key: String) = _state.update { s -> s.copy(ingredients = s.ingredients.filterNot { it.key == key }) }

    fun setMealPortion(index: Int) = _state.update { it.copy(mealPortionIndex = index.coerceIn(PortionScale.steps.indices)) }

    fun addFood(food: Food, effectiveGrams: Double) {
        val factor = _state.value.portionFactor
        val grams = effectiveGrams / factor
        _state.update { s ->
            s.copy(
                ingredients = s.ingredients + DraftIngredient(
                    name = food.name,
                    foodId = food.id.takeIf { it > 0 },
                    foodKey = FoodKey.of(food.name),
                    per100g = food.per100g,
                    baseGrams = grams,
                    grams = grams,
                ),
            )
        }
    }

    /** Replaces the food of an ingredient but keeps the amount — the user verified it, so no confidence badge. */
    fun swapFood(key: String, food: Food) = updateIngredient(key) {
        it.copy(
            name = food.name,
            foodId = food.id.takeIf { id -> id > 0 },
            per100g = food.per100g,
            confidence = null,
        )
    }

    fun ingredient(key: String): DraftIngredient? = _state.value.ingredients.firstOrNull { it.key == key }

    /** Re-logs a previous meal: ingredients are copied, the analysis step is skipped. */
    fun relog(meal: Meal) {
        _state.update {
            it.copy(
                ingredients = meal.ingredients.map { i ->
                    DraftIngredient(
                        name = i.name,
                        foodId = i.foodId,
                        foodKey = FoodKey.of(i.name),
                        per100g = i.per100g,
                        baseGrams = i.grams,
                        grams = i.grams,
                    )
                },
                mealName = meal.name,
                mealNameEdited = true,
                mealPortionIndex = PortionScale.NORMAL_INDEX,
            )
        }
    }

    // --- Barcode ----------------------------------------------------------------------------

    fun resetBarcode() = _state.update { it.copy(barcode = BarcodeState.Scanning) }

    fun onBarcode(code: String) {
        if (_state.value.barcode !is BarcodeState.Scanning) return
        _state.update { it.copy(barcode = BarcodeState.Loading(code)) }
        viewModelScope.launch {
            val next = when (val result = foods.lookupBarcode(code)) {
                is ProductLookup.Found -> {
                    val last = result.food.id.takeIf { it > 0 }?.let { foods.lastGrams(it) }
                    BarcodeState.Found(result.food, result.fromOnline, last ?: result.food.servingGrams ?: 100.0)
                }
                ProductLookup.NotFound -> BarcodeState.NotFound(code, "Produkt nicht gefunden – weder lokal noch online.")
                ProductLookup.OnlineDisabled -> BarcodeState.NotFound(code, "Nicht in der lokalen Datenbank. Die Online-Recherche ist in den Einstellungen deaktiviert.")
                ProductLookup.Offline -> BarcodeState.NotFound(code, "Nicht in der lokalen Datenbank und keine Internetverbindung.")
            }
            _state.update { it.copy(barcode = next) }
        }
    }

    // --- Summary & save ---------------------------------------------------------------------

    fun setMealName(name: String) = _state.update { it.copy(mealName = name.take(60), mealNameEdited = true) }

    fun setCategory(category: MealCategory) = _state.update { it.copy(category = category) }

    fun setLoggedTime(time: LocalTime) = _state.update { it.copy(loggedTime = time, category = suggestCategory(time)) }

    data class SaveOutcome(val goalReached: Boolean)

    fun save(onSaved: (SaveOutcome) -> Unit) {
        val s = _state.value
        if (s.ingredients.isEmpty() || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val loggedAt = today.atTime(s.loggedTime).atZone(zone).toInstant().toEpochMilli()
            val ingredients = s.ingredients.map {
                Ingredient(
                    foodId = it.foodId,
                    name = it.name,
                    grams = s.effectiveGrams(it),
                    per100g = it.per100g,
                    estimatedGrams = it.estimatedGrams,
                    confidence = it.confidence,
                )
            }
            val corrections = s.ingredients.mapNotNull { draft ->
                val estimate = draft.estimatedGrams ?: return@mapNotNull null
                val final = s.effectiveGrams(draft)
                if (!ApplyLearningCorrectionsUseCase.isMeaningful(estimate, final)) return@mapNotNull null
                LearningCorrection(draft.foodKey, estimate, final, System.currentTimeMillis())
            }
            val meal = Meal(
                name = s.suggestedName.ifBlank { "Mahlzeit" },
                loggedAtMillis = loggedAt,
                epochDay = today.toEpochDay(),
                category = s.category,
                photoPaths = s.photos,
                description = s.description.takeIf { it.isNotBlank() },
                ingredients = ingredients,
            )

            val targets = profiles.getTargets()
            val before = meals.observeMeals(today.toEpochDay(), today.toEpochDay()).first().map { it.totals }.sum()
            meals.save(meal, corrections)
            val after = before + meal.totals
            val goalReached = targets != null && (
                crossed(before.protein, after.protein, targets.proteinG.toDouble()) ||
                    crossed(before.kcal, after.kcal, targets.targetKcal * 0.95)
                )
            _state.value = AddFlowState()
            loadRecentMeals()
            onSaved(SaveOutcome(goalReached))
        }
    }

    /** Leaves the flow; photos of an unsaved draft are removed so nothing orphaned stays on disk. */
    fun discard() {
        _state.value.photos.forEach { photoStorage.delete(it) }
        _state.value = AddFlowState(recentMeals = _state.value.recentMeals)
    }

    private fun crossed(before: Double, after: Double, threshold: Double) = threshold > 0 && before < threshold && after >= threshold

    companion object {
        const val ANALYSIS_STEPS = 5
        const val STEP_MS = 420L
        const val MAX_PHOTOS = 6

        val analysisStepLabels = listOf(
            "Bilder analysieren",
            "Lebensmittel erkennen",
            "Portion abschätzen",
            "Nährwerte berechnen",
            "Unsicherheiten prüfen",
        )
    }
}
