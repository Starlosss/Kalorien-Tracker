package com.kalorientracker.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.repository.ProfileRepository
import com.kalorientracker.app.data.repository.WorkoutRepository
import com.kalorientracker.app.domain.model.Difficulty
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.WorkoutPlan
import com.kalorientracker.app.domain.usecase.GenerateWorkoutPlanUseCase
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.onboarding.Stepper
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.PlainCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class WorkoutUiState(
    val plan: WorkoutPlan? = null,
    val goal: Goal? = null,
    val weightKg: Double? = null,
    val sessionsThisWeek: Int = 0,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    profiles: ProfileRepository,
) : ViewModel() {
    private val generate = GenerateWorkoutPlanUseCase()
    private val weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).toEpochDay()

    val state: StateFlow<WorkoutUiState> = combine(
        workouts.observePlan(),
        profiles.observeProfile(),
        workouts.observeCompletedDays(weekStart, weekStart + 6),
    ) { plan, profile, done ->
        WorkoutUiState(plan, profile?.goal, profile?.weightKg, done.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    fun create(frequency: Int, minutes: Int, difficulty: Difficulty) {
        viewModelScope.launch { workouts.savePlan(generate(frequency, minutes, difficulty)) }
    }

    fun remove() {
        viewModelScope.launch { workouts.removePlan() }
    }
}

/** Deliberately small: a bodyweight routine that supports the nutrition goal, nothing more. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutScreen(onBack: () -> Unit, viewModel: WorkoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan = state.plan
    var frequency by remember(plan) { mutableIntStateOf(plan?.frequencyPerWeek ?: 3) }
    var minutes by remember(plan) { mutableIntStateOf(plan?.minutesPerSession ?: 20) }
    var difficulty by remember(plan) { mutableStateOf(plan?.difficulty ?: Difficulty.EASY) }
    var editing by remember(plan) { mutableStateOf(plan == null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Home-Workout", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.goal != null && state.goal != Goal.MUSCLE_BUILD) {
                Text(
                    "Der Plan ist für Muskelaufbau gedacht. Du kannst ihn aber mit jedem Ziel nutzen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextTertiary,
                )
            }
            if (plan != null && !editing) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel("${plan.frequencyPerWeek}× pro Woche · ${plan.minutesPerSession} min · ${plan.difficulty.label}")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Diese Woche: ${state.sessionsThisWeek} von ${plan.frequencyPerWeek} Einheiten",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.TextPrimary,
                        )
                        state.weightKg?.let {
                            Text(
                                "≈ ${GenerateWorkoutPlanUseCase.estimatedKcalPerDay(plan, it)} kcal pro Tag im Tagesbedarf berücksichtigt",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextTertiary,
                            )
                        }
                    }
                }
                PlainCard(Modifier.fillMaxWidth()) {
                    Column {
                        plan.exercises.forEachIndexed { index, exercise ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary, modifier = Modifier.padding(end = 14.dp))
                                Text(exercise.name, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                                Text(exercise.prescription, style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("60–90 s Pause zwischen den Sätzen.", style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
                    }
                }
            } else {
                PlainCard(Modifier.fillMaxWidth()) {
                    Column {
                        Stepper("Einheiten pro Woche", "$frequency×", { frequency = (frequency - 1).coerceAtLeast(1) }, { frequency = (frequency + 1).coerceAtMost(6) })
                        Spacer(Modifier.height(10.dp))
                        SectionLabel("Verfügbare Zeit")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 15, 20, 30, 45).forEach { m -> SelectChip("$m min", minutes == m, { minutes = m }) }
                        }
                        Spacer(Modifier.height(14.dp))
                        SectionLabel("Niveau")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Difficulty.entries.forEach { d -> SelectChip(d.label, difficulty == d, { difficulty = d }) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (plan != null && !editing) {
                SecondaryButton("Entfernen", viewModel::remove, Modifier.weight(1f))
                PrimaryButton("Anpassen", { editing = true }, Modifier.weight(1f))
            } else {
                if (plan != null) SecondaryButton("Abbrechen", { editing = false }, Modifier.weight(1f))
                PrimaryButton(
                    if (plan == null) "Plan erstellen" else "Plan aktualisieren",
                    { viewModel.create(frequency, minutes, difficulty); editing = false },
                    Modifier.weight(1f),
                    haptic = HapticEvent.Save,
                )
            }
        }
    }
}
