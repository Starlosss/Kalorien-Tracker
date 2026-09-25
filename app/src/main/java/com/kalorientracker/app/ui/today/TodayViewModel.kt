package com.kalorientracker.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.repository.MealRepository
import com.kalorientracker.app.data.repository.ProfileRepository
import com.kalorientracker.app.data.repository.WorkoutRepository
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.domain.model.DailyTotals
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Meal
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.model.UserProfile
import com.kalorientracker.app.domain.model.WeightEntry
import com.kalorientracker.app.domain.model.WorkoutPlan
import com.kalorientracker.app.domain.model.sum
import com.kalorientracker.app.domain.usecase.CalculateMacroTargetsUseCase
import com.kalorientracker.app.domain.usecase.ComputeStreakUseCase
import com.kalorientracker.app.domain.usecase.RecalibrationSuggestion
import com.kalorientracker.app.domain.usecase.SuggestRecalibrationUseCase
import com.kalorientracker.app.ui.common.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodayUiState(
    val loading: Boolean = true,
    val epochDay: Long = LocalDate.now().toEpochDay(),
    val meals: List<Meal> = emptyList(),
    val totals: Nutrients = Nutrients.ZERO,
    val targets: GoalTargets? = null,
    val profile: UserProfile? = null,
    val streak: Int = 0,
    val latestWeight: WeightEntry? = null,
    val workoutPlan: WorkoutPlan? = null,
    val workoutDoneToday: Boolean = false,
    val recalibration: RecalibrationSuggestion? = null,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val weightLoggedToday: Boolean get() = latestWeight?.epochDay == epochDay
}

private data class DayData(
    val meals: List<Meal>,
    val trackedDays: Set<Long>,
    val targets: GoalTargets?,
    val profile: UserProfile?,
)

private data class ProgressData(
    val weights: List<WeightEntry>,
    val recentTotals: List<DailyTotals>,
    val plan: WorkoutPlan?,
    val workoutDays: Set<Long>,
    val snoozedUntil: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val meals: MealRepository,
    private val profiles: ProfileRepository,
    private val workouts: WorkoutRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val streak = ComputeStreakUseCase()
    private val recalibrate = SuggestRecalibrationUseCase()
    private val macroTargets = CalculateMacroTargetsUseCase()

    private val today = MutableStateFlow(LocalDate.now().toEpochDay())

    private val dayData = today.flatMapLatest { day ->
        combine(
            meals.observeMeals(day, day),
            meals.observeTrackedDays(),
            profiles.observeTargets(),
            profiles.observeProfile(),
        ) { m, tracked, targets, profile -> DayData(m, tracked, targets, profile) }
    }

    private val progressData = today.flatMapLatest { day ->
        combine(
            profiles.observeWeights(),
            meals.observeDailyTotals(day - SuggestRecalibrationUseCase.WINDOW_DAYS, day),
            workouts.observePlan(),
            workouts.observeCompletedDays(day, day),
            settings.settings,
        ) { weights, totals, plan, done, s -> ProgressData(weights, totals, plan, done, s.recalibrationSnoozedUntilDay) }
    }

    val state: StateFlow<TodayUiState> = combine(today, dayData, progressData) { day, d, p ->
        val suggestion = if (d.targets != null && d.profile != null && p.snoozedUntil <= day) {
            recalibrate(p.weights, p.recentTotals, d.targets, d.profile.goal, d.profile.sex, day)
        } else {
            null
        }
        TodayUiState(
            loading = false,
            epochDay = day,
            meals = d.meals,
            totals = d.meals.map { it.totals }.sum(),
            targets = d.targets,
            profile = d.profile,
            streak = streak(d.trackedDays, day),
            latestWeight = p.weights.maxByOrNull { it.epochDay },
            workoutPlan = p.plan,
            workoutDoneToday = day in p.workoutDays,
            recalibration = suggestion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** Called on resume so the screen rolls over at midnight. */
    fun refreshDay() {
        today.value = LocalDate.now().toEpochDay()
    }

    fun saveWeight(text: String): Boolean {
        val value = Fmt.parse(text) ?: return false
        if (value !in 30.0..350.0) return false
        viewModelScope.launch { profiles.saveWeight(today.value, value) }
        return true
    }

    fun setWorkoutDone(done: Boolean) {
        viewModelScope.launch { workouts.setCompleted(today.value, done) }
    }

    fun acceptRecalibration(suggestion: RecalibrationSuggestion) {
        val profile = state.value.profile ?: return
        viewModelScope.launch {
            profiles.saveTargets(
                macroTargets.forKcal(
                    profile,
                    suggestion.observedMaintenanceKcal,
                    suggestion.suggestedTargetKcal,
                    today.value,
                    isUserAdjusted = false,
                ),
            )
            settings.snoozeRecalibration(today.value + SuggestRecalibrationUseCase.MIN_SPAN_DAYS)
        }
    }

    fun snoozeRecalibration() {
        viewModelScope.launch { settings.snoozeRecalibration(today.value + 7) }
    }
}
