package com.kalorientracker.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.backup.BackupManager
import com.kalorientracker.app.data.image.PhotoStorage
import com.kalorientracker.app.data.repository.MealRepository
import com.kalorientracker.app.data.repository.ProfileRepository
import com.kalorientracker.app.data.repository.WorkoutRepository
import com.kalorientracker.app.data.settings.AppSettings
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.UserProfile
import com.kalorientracker.app.domain.model.WorkoutPlan
import com.kalorientracker.app.domain.usecase.CalculateMacroTargetsUseCase
import com.kalorientracker.app.domain.usecase.CalculateTdeeUseCase
import com.kalorientracker.app.domain.usecase.GenerateWorkoutPlanUseCase
import com.kalorientracker.app.domain.usecase.SuggestRecalibrationUseCase
import com.kalorientracker.app.ui.common.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val profile: UserProfile? = null,
    val targets: GoalTargets? = null,
    val workoutPlan: WorkoutPlan? = null,
    val learningCount: Int = 0,
)

/** A proposed plan change that is only applied after explicit confirmation. */
data class PlanProposal(val reason: String, val targets: GoalTargets, val newGoal: Goal? = null)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profiles: ProfileRepository,
    private val workouts: WorkoutRepository,
    private val meals: MealRepository,
    private val backup: BackupManager,
    private val photos: PhotoStorage,
) : ViewModel() {

    private val tdee = CalculateTdeeUseCase()
    private val macros = CalculateMacroTargetsUseCase()
    private val recalibrate = SuggestRecalibrationUseCase()

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        profiles.observeProfile(),
        profiles.observeTargets(),
        workouts.observePlan(),
        meals.observeLearningCount(),
    ) { s, profile, targets, plan, learning -> SettingsUiState(s, profile, targets, plan, learning) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _proposal = MutableStateFlow<PlanProposal?>(null)
    val proposal: StateFlow<PlanProposal?> = _proposal.asStateFlow()

    private val _photoStats = MutableStateFlow(0 to 0L)
    val photoStats: StateFlow<Pair<Int, Long>> = _photoStats.asStateFlow()

    fun refreshPhotoStats() {
        _photoStats.value = photos.stats()
    }

    fun clearMessage() {
        _message.value = null
    }

    // --- Appearance & privacy ---------------------------------------------------------------

    fun setAnimations(v: Boolean) {
        viewModelScope.launch { settingsRepository.setAnimations(v) }
    }

    fun setHaptics(v: Boolean) {
        viewModelScope.launch { settingsRepository.setHaptics(v) }
    }

    fun setSmoothCharts(v: Boolean) {
        viewModelScope.launch { settingsRepository.setSmoothCharts(v) }
    }

    fun setTargetWeightLine(v: Boolean) {
        viewModelScope.launch { settingsRepository.setShowTargetWeightLine(v) }
    }

    fun setOnlineLookup(v: Boolean) {
        viewModelScope.launch { settingsRepository.setOnlineLookup(v) }
    }

    // --- Profile & plan ---------------------------------------------------------------------

    private fun workoutKcal(profile: UserProfile, plan: WorkoutPlan?): Int =
        plan?.let { GenerateWorkoutPlanUseCase.estimatedKcalPerDay(it, profile.weightKg) } ?: 0

    private fun calculated(profile: UserProfile, plan: WorkoutPlan?): GoalTargets {
        val result = tdee(profile, workoutKcal(profile, plan))
        return macros(profile, result.tdee, LocalDate.now().toEpochDay())
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            profiles.saveProfile(profile)
            val plan = workouts.observePlan().first()
            val proposal = calculated(profile, plan)
            val current = profiles.getTargets()
            _message.value = "Profil gespeichert."
            if (current == null || current.targetKcal != proposal.targetKcal) {
                _proposal.value = PlanProposal("Mit deinen neuen Profildaten ergibt sich ein neuer Vorschlag.", proposal)
            }
        }
    }

    fun proposeGoal(goal: Goal) {
        val profile = state.value.profile ?: return
        if (goal == profile.goal) return
        val updated = profile.copy(goal = goal)
        _proposal.value = PlanProposal(
            "Ziel „${goal.label}“: Die Zielwerte werden neu berechnet. Bisherige Daten bleiben erhalten.",
            calculated(updated, state.value.workoutPlan),
            newGoal = goal,
        )
    }

    fun proposeRecalculation() {
        val profile = state.value.profile ?: return
        _proposal.value = PlanProposal("Neu berechnet aus Profil, Schritten, Sport und Home-Workout.", calculated(profile, state.value.workoutPlan))
    }

    fun checkAdaptive() {
        val profile = state.value.profile ?: return
        val targets = state.value.targets ?: return
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            val weights = profiles.observeWeights().first()
            val totals = meals.observeDailyTotals(today - SuggestRecalibrationUseCase.WINDOW_DAYS, today).first()
            val suggestion = recalibrate(weights, totals, targets, profile.goal, profile.sex, today)
            if (suggestion == null) {
                _message.value = "Noch keine Anpassung nötig – oder zu wenige Daten (mind. 14 Tage mit Gewicht und Mahlzeiten)."
            } else {
                _proposal.value = PlanProposal(
                    "Beobachtet: Ø ${Fmt.int(suggestion.averageIntakeKcal)} kcal bei ${Fmt.signedKg(suggestion.weeklyWeightChangeKg)} pro Woche. " +
                        "Dein Erhaltungsbedarf liegt eher bei ${Fmt.int(suggestion.observedMaintenanceKcal)} kcal.",
                    macros.forKcal(profile, suggestion.observedMaintenanceKcal, suggestion.suggestedTargetKcal, today, isUserAdjusted = false),
                )
            }
        }
    }

    fun acceptProposal() {
        val proposal = _proposal.value ?: return
        viewModelScope.launch {
            proposal.newGoal?.let { goal -> state.value.profile?.let { profiles.saveProfile(it.copy(goal = goal)) } }
            profiles.saveTargets(proposal.targets)
            _proposal.value = null
            _message.value = "Neuer Plan übernommen: ${Fmt.int(proposal.targets.targetKcal)} kcal."
        }
    }

    fun dismissProposal() {
        _proposal.value = null
    }

    fun saveManualTargets(kcal: Int, protein: Int, carbs: Int, fat: Int) {
        val profile = state.value.profile ?: return
        val current = state.value.targets
        viewModelScope.launch {
            val base = macros.forKcal(
                profile,
                current?.maintenanceKcal ?: kcal,
                kcal,
                LocalDate.now().toEpochDay(),
                isUserAdjusted = true,
            )
            profiles.saveTargets(base.copy(proteinG = protein, carbsG = carbs, fatG = fat))
            _message.value = "Zielwerte gespeichert."
        }
    }

    // --- Data -------------------------------------------------------------------------------

    fun export(uri: Uri, includePhotos: Boolean) {
        viewModelScope.launch {
            _message.value = runCatching { backup.export(uri, includePhotos) }.fold(
                onSuccess = {
                    if (includePhotos) "Backup erstellt: ${it.meals} Mahlzeiten, ${it.photos} Fotos." else "Export erstellt: ${it.meals} Mahlzeiten."
                },
                onFailure = { "Export fehlgeschlagen: ${it.message ?: "unbekannter Fehler"}" },
            )
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching { backup.import(uri) }.fold(
                onSuccess = { "Wiederhergestellt: ${it.meals} Mahlzeiten, ${it.weights} Gewichtseinträge, ${it.photos} Fotos." },
                onFailure = { "Import fehlgeschlagen: ${it.message ?: "unbekannter Fehler"}" },
            )
            refreshPhotoStats()
        }
    }

    fun deleteAll() {
        viewModelScope.launch { backup.deleteAllData() }
    }

    fun deleteAllPhotos() {
        viewModelScope.launch {
            backup.deleteAllPhotos()
            refreshPhotoStats()
            _message.value = "Alle Fotos wurden gelöscht."
        }
    }

    fun resetLearning() {
        viewModelScope.launch {
            meals.clearLearning()
            _message.value = "Gelernte Korrekturen wurden zurückgesetzt."
        }
    }
}
