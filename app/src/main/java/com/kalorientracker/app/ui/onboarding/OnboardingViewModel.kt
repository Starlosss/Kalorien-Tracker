package com.kalorientracker.app.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.backup.BackupManager
import com.kalorientracker.app.data.repository.ProfileRepository
import com.kalorientracker.app.data.repository.WorkoutRepository
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.domain.model.Difficulty
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.model.SportActivity
import com.kalorientracker.app.domain.model.UserProfile
import com.kalorientracker.app.domain.usecase.CalculateMacroTargetsUseCase
import com.kalorientracker.app.domain.usecase.CalculateTdeeUseCase
import com.kalorientracker.app.domain.usecase.GenerateWorkoutPlanUseCase
import com.kalorientracker.app.domain.usecase.TdeeResult
import com.kalorientracker.app.ui.common.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class ActivityDraft(val name: String, val sessionsPerWeek: Int = 2, val minutesPerSession: Int = 45)

data class OnboardingState(
    val step: Int = 0,
    val goal: Goal? = null,
    val sex: Sex? = null,
    val age: String = "",
    val height: String = "",
    val weight: String = "",
    val targetWeight: String = "",
    val dailySteps: Int = 7_000,
    val activities: List<ActivityDraft> = emptyList(),
    val createWorkout: Boolean = true,
    val workoutFrequency: Int = 3,
    val workoutMinutes: Int = 20,
    val workoutDifficulty: Difficulty = Difficulty.EASY,
    val tdee: TdeeResult? = null,
    val suggested: GoalTargets? = null,
    val targetKcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val saving: Boolean = false,
    val message: String? = null,
) {
    val ageValue get() = age.toIntOrNull()
    val heightValue get() = Fmt.parse(height)
    val weightValue get() = Fmt.parse(weight)
    val targetWeightValue get() = Fmt.parse(targetWeight)

    val bodyError: String?
        get() = when {
            sex == null -> "Bitte Geschlecht für die Berechnung wählen."
            ageValue == null || ageValue!! !in 14..100 -> "Bitte ein Alter zwischen 14 und 100 eingeben."
            heightValue == null || heightValue!! !in 120.0..230.0 -> "Bitte die Größe in cm eingeben (120–230)."
            weightValue == null || weightValue!! !in 35.0..300.0 -> "Bitte das Gewicht in kg eingeben (35–300)."
            targetWeight.isNotBlank() && (targetWeightValue == null || targetWeightValue!! !in 35.0..300.0) -> "Das Zielgewicht ist ungültig."
            else -> null
        }

    val canContinue: Boolean
        get() = when (step) {
            0 -> goal != null
            1 -> bodyError == null
            4 -> targetKcal.toIntOrNull()?.let { it in 1000..6000 } == true
            else -> true
        }

    companion object {
        const val STEP_COUNT = 5
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val workouts: WorkoutRepository,
    private val settings: SettingsRepository,
    private val backup: BackupManager,
) : ViewModel() {

    private val calculateTdee = CalculateTdeeUseCase()
    private val calculateTargets = CalculateMacroTargetsUseCase()
    private val generateWorkout = GenerateWorkoutPlanUseCase()

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setGoal(goal: Goal) = _state.update { it.copy(goal = goal) }
    fun setSex(sex: Sex) = _state.update { it.copy(sex = sex) }
    fun setAge(v: String) = _state.update { it.copy(age = v.filter(Char::isDigit).take(3)) }
    fun setHeight(v: String) = _state.update { it.copy(height = v.take(5)) }
    fun setWeight(v: String) = _state.update { it.copy(weight = v.take(5)) }
    fun setTargetWeight(v: String) = _state.update { it.copy(targetWeight = v.take(5)) }
    fun setSteps(v: Int) = _state.update { it.copy(dailySteps = v) }

    fun toggleActivity(name: String) = _state.update { s ->
        val exists = s.activities.any { it.name == name }
        s.copy(activities = if (exists) s.activities.filterNot { it.name == name } else s.activities + ActivityDraft(name))
    }

    fun updateActivity(name: String, sessions: Int? = null, minutes: Int? = null) = _state.update { s ->
        s.copy(
            activities = s.activities.map {
                if (it.name != name) it else it.copy(
                    sessionsPerWeek = sessions?.coerceIn(1, 14) ?: it.sessionsPerWeek,
                    minutesPerSession = minutes?.coerceIn(5, 300) ?: it.minutesPerSession,
                )
            },
        )
    }

    fun setCreateWorkout(v: Boolean) = _state.update { it.copy(createWorkout = v) }
    fun setWorkoutFrequency(v: Int) = _state.update { it.copy(workoutFrequency = v.coerceIn(1, 6)) }
    fun setWorkoutMinutes(v: Int) = _state.update { it.copy(workoutMinutes = v) }
    fun setWorkoutDifficulty(v: Difficulty) = _state.update { it.copy(workoutDifficulty = v) }

    fun next() {
        val s = _state.value
        if (!s.canContinue) return
        if (s.step == 2) computePlan()
        if (s.step < OnboardingState.STEP_COUNT - 1) _state.update { it.copy(step = it.step + 1) }
    }

    fun back(): Boolean {
        if (_state.value.step == 0) return false
        _state.update { it.copy(step = it.step - 1) }
        return true
    }

    private fun profileOrNull(s: OnboardingState): UserProfile? {
        return UserProfile(
            age = s.ageValue ?: return null,
            heightCm = s.heightValue ?: return null,
            weightKg = s.weightValue ?: return null,
            targetWeightKg = s.targetWeightValue,
            sex = s.sex ?: return null,
            goal = s.goal ?: return null,
            dailySteps = s.dailySteps,
            activities = s.activities.map { SportActivity(it.name, it.sessionsPerWeek, it.minutesPerSession) },
        )
    }

    private fun workoutKcal(s: OnboardingState, profile: UserProfile): Int {
        if (s.goal != Goal.MUSCLE_BUILD || !s.createWorkout) return 0
        val plan = generateWorkout(s.workoutFrequency, s.workoutMinutes, s.workoutDifficulty)
        return GenerateWorkoutPlanUseCase.estimatedKcalPerDay(plan, profile.weightKg)
    }

    private fun computePlan() {
        val s = _state.value
        val profile = profileOrNull(s) ?: return
        val tdee = calculateTdee(profile, workoutKcal(s, profile))
        val targets = calculateTargets(profile, tdee.tdee, LocalDate.now().toEpochDay())
        _state.update {
            it.copy(
                tdee = tdee,
                suggested = targets,
                targetKcal = targets.targetKcal.toString(),
                protein = targets.proteinG.toString(),
                carbs = targets.carbsG.toString(),
                fat = targets.fatG.toString(),
            )
        }
    }

    /** Changing the calorie target redistributes the macros with the same rules. */
    fun setTargetKcal(text: String) {
        val clean = text.filter(Char::isDigit).take(4)
        _state.update { it.copy(targetKcal = clean) }
        val s = _state.value
        val kcal = clean.toIntOrNull() ?: return
        val profile = profileOrNull(s) ?: return
        val tdee = s.tdee ?: return
        if (kcal !in 1000..6000) return
        val t = calculateTargets.forKcal(profile, tdee.tdee, kcal, LocalDate.now().toEpochDay(), isUserAdjusted = true)
        _state.update { it.copy(protein = t.proteinG.toString(), carbs = t.carbsG.toString(), fat = t.fatG.toString()) }
    }

    fun setProtein(v: String) = _state.update { it.copy(protein = v.filter(Char::isDigit).take(3)) }
    fun setCarbs(v: String) = _state.update { it.copy(carbs = v.filter(Char::isDigit).take(3)) }
    fun setFat(v: String) = _state.update { it.copy(fat = v.filter(Char::isDigit).take(3)) }

    fun resetToSuggestion() = computePlan()

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        val profile = profileOrNull(s) ?: return
        val suggested = s.suggested ?: return
        val kcal = s.targetKcal.toIntOrNull() ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            val base = calculateTargets.forKcal(profile, suggested.maintenanceKcal, kcal, today, isUserAdjusted = kcal != suggested.targetKcal)
            val targets = base.copy(
                proteinG = s.protein.toIntOrNull() ?: base.proteinG,
                carbsG = s.carbs.toIntOrNull() ?: base.carbsG,
                fatG = s.fat.toIntOrNull() ?: base.fatG,
                isUserAdjusted = base.isUserAdjusted || s.protein != suggested.proteinG.toString() ||
                    s.carbs != suggested.carbsG.toString() || s.fat != suggested.fatG.toString(),
            )
            profiles.saveProfile(profile)
            profiles.saveTargets(targets)
            profiles.saveWeight(today, profile.weightKg)
            if (profile.goal == Goal.MUSCLE_BUILD && s.createWorkout) {
                workouts.savePlan(generateWorkout(s.workoutFrequency, s.workoutMinutes, s.workoutDifficulty))
            }
            settings.setOnboardingCompleted(true)
            onDone()
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            val message = runCatching { backup.import(uri) }.fold(
                onSuccess = { "Sicherung wiederhergestellt: ${it.meals} Mahlzeiten, ${it.weights} Gewichtseinträge." },
                onFailure = { "Wiederherstellung fehlgeschlagen: ${it.message ?: "unbekannter Fehler"}" },
            )
            _state.update { it.copy(message = message) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    companion object {
        /** Explains a manual target change in plain words. */
        fun adjustmentExplanation(suggestedKcal: Int, chosenKcal: Int): String? {
            val diff = chosenKcal - suggestedKcal
            if (diff == 0) return null
            val perWeek = CalculateMacroTargetsUseCase.weeklyWeightChangeKg(diff)
            val direction = if (diff < 0) "unter" else "über"
            val effect = if (diff < 0) "zusätzliche Abnahme" else "zusätzliche Zunahme"
            return "Du liegst ${Fmt.int(kotlin.math.abs(diff))} kcal $direction dem Vorschlag. " +
                "Das entspricht etwa ${Fmt.one(kotlin.math.abs(perWeek))} kg $effect pro Woche."
        }

        fun stepsLabel(steps: Int): String = when {
            steps < 3_000 -> "Überwiegend sitzend"
            steps < 5_000 -> "Wenig Bewegung"
            steps < 7_500 -> "Leicht aktiv"
            steps < 10_000 -> "Aktiv"
            steps < 12_500 -> "Sehr aktiv"
            else -> "Extrem aktiv"
        }

        fun roundedSteps(value: Float): Int = ((value / 500f).roundToInt() * 500)
    }
}
