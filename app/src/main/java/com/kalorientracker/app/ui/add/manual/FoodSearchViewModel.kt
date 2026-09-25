package com.kalorientracker.app.ui.add.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.repository.FoodRepository
import com.kalorientracker.app.domain.model.Food
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OnlineResults {
    data object Idle : OnlineResults
    data object Loading : OnlineResults
    data class Loaded(val foods: List<Food>) : OnlineResults
    data class Unavailable(val reason: String) : OnlineResults
}

data class FoodSelection(val food: Food, val defaultGrams: Double)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val foods: FoodRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Food>> = _query
        .debounce(120)
        .distinctUntilChanged()
        .mapLatest { foods.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val frequent: StateFlow<List<Food>> = foods.observeFrequent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _online = MutableStateFlow<OnlineResults>(OnlineResults.Idle)
    val online: StateFlow<OnlineResults> = _online.asStateFlow()

    private val _selection = MutableStateFlow<FoodSelection?>(null)
    val selection: StateFlow<FoodSelection?> = _selection.asStateFlow()

    fun setQuery(text: String) {
        _query.value = text
        _online.value = OnlineResults.Idle
    }

    fun searchOnline() {
        val q = _query.value.trim()
        if (q.length < 2) return
        _online.value = OnlineResults.Loading
        viewModelScope.launch {
            _online.value = when (val r = foods.searchOnline(q)) {
                is FoodRepository.OnlineSearch.Results ->
                    if (r.foods.isEmpty()) OnlineResults.Unavailable("Online nichts gefunden.") else OnlineResults.Loaded(r.foods)
                FoodRepository.OnlineSearch.Disabled -> OnlineResults.Unavailable("Online-Recherche ist in den Einstellungen deaktiviert.")
                FoodRepository.OnlineSearch.Offline -> OnlineResults.Unavailable("Keine Internetverbindung. Die lokale Suche funktioniert weiterhin.")
            }
        }
    }

    /** Online hits are stored locally first, so the product works offline next time. */
    fun select(food: Food) {
        viewModelScope.launch {
            val local = if (food.id == 0L) foods.cache(food) else food
            val last = local.id.takeIf { it > 0 }?.let { foods.lastGrams(it) }
            _selection.value = FoodSelection(local, last ?: local.servingGrams ?: 100.0)
        }
    }

    fun clearSelection() = _selection.update { null }
}
