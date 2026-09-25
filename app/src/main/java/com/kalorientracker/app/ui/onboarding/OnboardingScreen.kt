package com.kalorientracker.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.domain.model.Difficulty
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.model.Sex
import com.kalorientracker.app.domain.usecase.ActivityCatalog
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.NutrientRow
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.common.ToggleRow
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Motion
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.PlainCard
import com.kalorientracker.app.ui.theme.motionSpec

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    BackHandler(enabled = state.step > 0) { viewModel.back() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.step > 0) {
                IconButton(onClick = { haptics.perform(HapticEvent.Tap); viewModel.back() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = Palette.TextPrimary)
                }
            }
            Spacer(Modifier.weight(1f))
            StepDots(state.step, OnboardingState.STEP_COUNT)
        }

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboardingStep",
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> GoalStep(state, viewModel)
                    1 -> BodyStep(state, viewModel)
                    2 -> ActivityStep(state, viewModel)
                    3 -> ResultStep(state)
                    else -> ConfirmStep(state, viewModel)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        val isLast = state.step == OnboardingState.STEP_COUNT - 1
        PrimaryButton(
            text = when {
                isLast -> "Plan übernehmen"
                state.step == 3 -> "Weiter zum Plan"
                else -> "Weiter"
            },
            enabled = state.canContinue && !state.saving,
            haptic = if (isLast) HapticEvent.Save else HapticEvent.Tap,
            onClick = { if (isLast) viewModel.finish(onFinished) else viewModel.next() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateFloatAsState(if (i == current) 22f else 6f, motionSpec(Motion.gentle()), label = "dot")
            Box(
                Modifier
                    .height(6.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(if (i <= current) Palette.TextPrimary else Palette.OutlineStrong),
            )
        }
    }
}

@Composable
private fun StepTitle(label: String, title: String, text: String? = null) {
    Spacer(Modifier.height(12.dp))
    SectionLabel(label)
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.TextPrimary)
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GoalStep(state: OnboardingState, vm: OnboardingViewModel) {
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri)
    }
    StepTitle("Schritt 1 · Ziel", "Was möchtest du erreichen?")
    Goal.entries.forEach { goal ->
        SelectableCard(
            title = goal.label,
            text = goal.description,
            selected = state.goal == goal,
            onClick = { vm.setGoal(goal) },
        )
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { restore.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }) {
        Text("Vorhandenes Backup wiederherstellen", color = Palette.TextSecondary)
    }
    state.message?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
    }
}

@Composable
private fun SelectableCard(title: String, text: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
            .border(1.dp, if (selected) Palette.TextPrimary else Palette.Outline, shape)
            .clickable { haptics.perform(HapticEvent.Toggle); onClick() }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (selected) Palette.TextPrimary else Palette.OutlineStrong, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) Palette.TextPrimary else Palette.Surface),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepTitle("Schritt 2 · Körperdaten", "Ein paar Eckdaten", "Nur für die Berechnung deines Bedarfs. Alles bleibt auf dem Gerät.")
    SectionLabel("Geschlecht (für den Grundumsatz)")
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { SelectChip(it.label, state.sex == it, { vm.setSex(it) }) }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(state.age, vm::setAge, "Alter", Modifier.weight(1f), suffix = "Jahre", decimal = false)
        NumberField(state.height, vm::setHeight, "Größe", Modifier.weight(1f), suffix = "cm")
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(state.weight, vm::setWeight, "Gewicht", Modifier.weight(1f), suffix = "kg")
        NumberField(state.targetWeight, vm::setTargetWeight, "Zielgewicht (optional)", Modifier.weight(1f), suffix = "kg")
    }
    val error = state.bodyError
    if (error != null && (state.age.isNotEmpty() || state.height.isNotEmpty() || state.weight.isNotEmpty())) {
        Spacer(Modifier.height(12.dp))
        Text(error, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityStep(state: OnboardingState, vm: OnboardingViewModel) {
    val haptics = LocalHaptics.current
    StepTitle("Schritt 3 · Bewegung", "Wie aktiv bist du?")
    PlainCard(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("Schritte pro Tag")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.int(state.dailySteps), style = MaterialTheme.typography.displaySmall, color = Palette.TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(OnboardingViewModel.stepsLabel(state.dailySteps), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }
            var lastTick by remember { mutableIntStateOf(state.dailySteps) }
            Slider(
                value = state.dailySteps.toFloat(),
                onValueChange = { raw ->
                    val rounded = OnboardingViewModel.roundedSteps(raw)
                    if (rounded != lastTick) {
                        lastTick = rounded
                        if (rounded % 2_500 == 0) haptics.perform(HapticEvent.Tick)
                        vm.setSteps(rounded)
                    }
                },
                valueRange = 0f..20_000f,
                colors = SliderDefaults.colors(
                    thumbColor = Palette.TextPrimary,
                    activeTrackColor = Palette.TextPrimary,
                    inactiveTrackColor = Palette.Track,
                ),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    SectionLabel("Welche sportlichen Aktivitäten machst du?")
    Spacer(Modifier.height(10.dp))
    val options = ActivityCatalog.defaults + "Andere"
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { name ->
            SelectChip(name, state.activities.any { it.name == name }, { vm.toggleActivity(name) })
        }
    }
    state.activities.forEach { activity ->
        Spacer(Modifier.height(12.dp))
        PlainCard(Modifier.fillMaxWidth()) {
            Column {
                Text(activity.name, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Stepper("Pro Woche", "${activity.sessionsPerWeek}×", { vm.updateActivity(activity.name, sessions = activity.sessionsPerWeek - 1) }, { vm.updateActivity(activity.name, sessions = activity.sessionsPerWeek + 1) })
                Stepper("Dauer pro Einheit", "${activity.minutesPerSession} min", { vm.updateActivity(activity.name, minutes = activity.minutesPerSession - 5) }, { vm.updateActivity(activity.name, minutes = activity.minutesPerSession + 5) })
            }
        }
    }
    if (state.goal == Goal.MUSCLE_BUILD) {
        Spacer(Modifier.height(24.dp))
        PlainCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow(
                    title = "Kleinen Home-Workout-Plan erstellen",
                    subtitle = "Ohne Fitnessstudio, nur mit Körpergewicht",
                    checked = state.createWorkout,
                    onCheckedChange = vm::setCreateWorkout,
                )
                if (state.createWorkout) {
                    Stepper("Einheiten pro Woche", "${state.workoutFrequency}×", { vm.setWorkoutFrequency(state.workoutFrequency - 1) }, { vm.setWorkoutFrequency(state.workoutFrequency + 1) })
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Zeit pro Einheit")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 20, 30, 45).forEach { m -> SelectChip("$m min", state.workoutMinutes == m, { vm.setWorkoutMinutes(m) }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Niveau")
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.entries.forEach { d -> SelectChip(d.label, state.workoutDifficulty == d, { vm.setWorkoutDifficulty(d) }) }
                    }
                }
            }
        }
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val haptics = LocalHaptics.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        IconButton(onClick = { haptics.perform(HapticEvent.Tick); onMinus() }) {
            Icon(Icons.Outlined.Remove, contentDescription = "Weniger", tint = Palette.TextPrimary)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { haptics.perform(HapticEvent.Tick); onPlus() }) {
            Icon(Icons.Outlined.Add, contentDescription = "Mehr", tint = Palette.TextPrimary)
        }
    }
}

@Composable
private fun ResultStep(state: OnboardingState) {
    val tdee = state.tdee
    val targets = state.suggested
    StepTitle("Schritt 4 · Berechnung", "Dein Tagesbedarf")
    if (tdee == null || targets == null) {
        Text("Bitte Körperdaten prüfen.", color = Palette.TextSecondary)
        return
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("Erhaltungsbedarf")
            Text("${Fmt.int(targets.maintenanceKcal)} kcal", style = MaterialTheme.typography.headlineMedium, color = Palette.TextSecondary)
            Spacer(Modifier.height(16.dp))
            SectionLabel("Dein Ziel")
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.int(targets.targetKcal), style = MaterialTheme.typography.displayLarge, color = Palette.TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text("kcal", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
            }
            Spacer(Modifier.height(16.dp))
            MacroLine(Metric.PROTEIN, targets.proteinG)
            MacroLine(Metric.CARBS, targets.carbsG)
            MacroLine(Metric.FAT, targets.fatG)
        }
    }
    Spacer(Modifier.height(16.dp))
    PlainCard(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("Weitere Richtwerte")
            NutrientRow("Ballaststoffe", "mind. ${targets.fiberG} g")
            NutrientRow("Zucker", "max. ${targets.sugarMaxG} g")
            NutrientRow("Gesättigte Fettsäuren", "max. ${targets.saturatedFatMaxG} g")
            NutrientRow("Salz", "max. ${Fmt.small(targets.saltMaxG)} g")
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("So wurde gerechnet")
    Spacer(Modifier.height(8.dp))
    val parts = buildList {
        add("Grundumsatz ${Fmt.int(tdee.bmr)} kcal (Mifflin-St Jeor)")
        add("× ${Fmt.one(tdee.stepMultiplier)} für ${Fmt.int(state.dailySteps)} Schritte = ${Fmt.int(tdee.everydayKcal)} kcal")
        if (tdee.sportKcalPerDay > 0) add("+ ${Fmt.int(tdee.sportKcalPerDay)} kcal/Tag durch Sport")
        if (tdee.workoutKcalPerDay > 0) add("+ ${Fmt.int(tdee.workoutKcalPerDay)} kcal/Tag durch Home-Workout")
        val delta = targets.targetKcal - targets.maintenanceKcal
        if (delta != 0) add("${if (delta > 0) "+" else "−"} ${Fmt.int(kotlin.math.abs(delta))} kcal für dein Ziel „${state.goal?.label}“")
    }
    parts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.padding(vertical = 2.dp)) }
}

@Composable
private fun MacroLine(metric: Metric, grams: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.forMetric(metric)))
        Spacer(Modifier.width(10.dp))
        Text(metric.label, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        Text("$grams g", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
    }
}

@Composable
private fun ConfirmStep(state: OnboardingState, vm: OnboardingViewModel) {
    StepTitle("Schritt 5 · Plan", "Passt das so?", "Du kannst den Vorschlag übernehmen oder anpassen. Die Makros werden automatisch neu verteilt.")
    NumberField(state.targetKcal, vm::setTargetKcal, "Kalorienziel", Modifier.fillMaxWidth(), suffix = "kcal", decimal = false)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(state.protein, vm::setProtein, "Protein", Modifier.weight(1f), suffix = "g", decimal = false)
        NumberField(state.carbs, vm::setCarbs, "Kohlenh.", Modifier.weight(1f), suffix = "g", decimal = false)
        NumberField(state.fat, vm::setFat, "Fett", Modifier.weight(1f), suffix = "g", decimal = false)
    }
    val suggested = state.suggested
    val chosen = state.targetKcal.toIntOrNull()
    if (suggested != null && chosen != null) {
        val explanation = OnboardingViewModel.adjustmentExplanation(suggested.targetKcal, chosen)
        Spacer(Modifier.height(16.dp))
        Text(
            explanation ?: "Das ist der berechnete Vorschlag für dein Ziel.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
        if (explanation != null) {
            TextButton(onClick = vm::resetToSuggestion) { Text("Vorschlag wiederherstellen", color = Palette.TextPrimary) }
        }
        val macroKcal = (state.protein.toIntOrNull() ?: 0) * 4 + (state.carbs.toIntOrNull() ?: 0) * 4 + (state.fat.toIntOrNull() ?: 0) * 9
        if (kotlin.math.abs(macroKcal - chosen) > 50) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Die Makros ergeben ${Fmt.int(macroKcal)} kcal – das weicht vom Kalorienziel ab.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextTertiary,
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        "Die App beobachtet später dein Gewicht und schlägt Anpassungen vor – geändert wird nie ohne deine Bestätigung.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextTertiary,
    )
}
