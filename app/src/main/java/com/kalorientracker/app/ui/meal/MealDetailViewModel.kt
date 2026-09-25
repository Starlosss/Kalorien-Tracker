package com.kalorientracker.app.ui.meal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.repository.MealRepository
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.Ingredient
import com.kalorientracker.app.domain.model.LearningCorrection
import com.kalorientracker.app.domain.model.Meal
import com.kalorientracker.app.domain.model.MealCategory
import com.kalorientracker.app.domain.usecase.ApplyLearningCorrectionsUseCase
import com.kalorientracker.app.domain.usecase.FoodKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class MealDetailState(
    val original: Meal? = null,
    val draft: Meal? = null,
    val notFound: Boolean = false,
) {
    val dirty: Boolean get() = original != null && draft != null && original != draft
}

@HiltViewModel
class MealDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meals: MealRepository,
) : ViewModel() {

    private val mealId: Long = checkNotNull(savedStateHandle.get<Long>("id"))

    private val _state = MutableStateFlow(MealDetailState())
    val state: StateFlow<MealDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val meal = meals.getMeal(mealId)
            _state.value = if (meal == null) MealDetailState(notFound = true) else MealDetailState(meal, meal)
        }
    }

    private fun edit(transform: (Meal) -> Meal) = _state.update { s -> s.copy(draft = s.draft?.let(transform)) }

    fun setName(name: String) = edit { it.copy(name = name.take(60)) }
    fun setCategory(category: MealCategory) = edit { it.copy(category = category) }

    fun setTime(time: LocalTime) = edit {
        val millis = LocalDate.ofEpochDay(it.epochDay).atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        it.copy(loggedAtMillis = millis)
    }

    fun setGrams(index: Int, grams: Double) = edit { meal ->
        meal.copy(ingredients = meal.ingredients.mapIndexed { i, ing -> if (i == index) ing.copy(grams = grams.coerceIn(0.0, 5000.0)) else ing })
    }

    fun removeIngredient(index: Int) = edit { meal ->
        meal.copy(ingredients = meal.ingredients.filterIndexed { i, _ -> i != index })
    }

    fun addFood(food: Food, grams: Double) = edit { meal ->
        meal.copy(ingredients = meal.ingredients + Ingredient(foodId = food.id.takeIf { it > 0 }, name = food.name, grams = grams, per100g = food.per100g))
    }

    /** Photos are removed immediately; everything else is saved explicitly. */
    fun removePhoto(path: String) {
        val original = _state.value.original ?: return
        viewModelScope.launch {
            meals.removePhoto(original, path)
            _state.update { s ->
                s.copy(
                    original = s.original?.copy(photoPaths = s.original.photoPaths - path),
                    draft = s.draft?.copy(photoPaths = s.draft.photoPaths - path),
                )
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val draft = _state.value.draft ?: return
        if (draft.ingredients.isEmpty()) return
        val corrections = draft.ingredients.mapNotNull { ing ->
            val estimate = ing.estimatedGrams ?: return@mapNotNull null
            if (!ApplyLearningCorrectionsUseCase.isMeaningful(estimate, ing.grams)) return@mapNotNull null
            LearningCorrection(FoodKey.of(ing.name), estimate, ing.grams, System.currentTimeMillis())
        }
        viewModelScope.launch {
            val stripped = draft.copy(ingredients = draft.ingredients.map { it.copy(estimatedGrams = if (corrections.any { c -> c.foodKey == FoodKey.of(it.name) }) null else it.estimatedGrams) })
            meals.save(stripped, corrections)
            _state.update { it.copy(original = stripped, draft = stripped) }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val original = _state.value.original ?: return
        viewModelScope.launch {
            meals.delete(original)
            onDeleted()
        }
    }
}
