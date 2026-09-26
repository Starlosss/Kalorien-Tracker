package com.kalorientracker.app.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.domain.model.Goal
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Meal
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.domain.usecase.RecalibrationSuggestion
import com.kalorientracker.app.ui.common.AnimatedNumber
import com.kalorientracker.app.ui.common.CalorieRing
import com.kalorientracker.app.ui.common.EmptyHint
import com.kalorientracker.app.ui.common.Expandable
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.MacroBar
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.NutrientRow
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.ToggleRow
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.PlainCard
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    onOpenWeight: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenPlanSettings: () -> Unit,
    onAdd: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDay() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                title = "Heute",
                subtitle = "${Fmt.weekday(state.date)}, ${Fmt.dayMonth(state.date)}",
                onSettings = onOpenSettings,
                actions = { if (state.streak > 0) StreakChip(state.streak) },
            )
        }
        item { CalorieHero(state.totals, state.targets) }
        item { MacroCard(state.totals, state.targets) }
        state.recalibration?.let { suggestion ->
            item {
                RecalibrationCard(
                    suggestion = suggestion,
                    onAccept = { viewModel.acceptRecalibration(suggestion) },
                    onLater = viewModel::snoozeRecalibration,
                    onDetails = onOpenPlanSettings,
                )
            }
        }
        item {
            WeightCard(
                latest = state.latestWeight?.weightKg,
                loggedToday = state.weightLoggedToday,
                targetWeight = state.profile?.targetWeightKg,
                onSave = viewModel::saveWeight,
                onOpen = onOpenWeight,
            )
        }
        val plan = state.workoutPlan
        if (plan != null && state.profile?.goal == Goal.MUSCLE_BUILD) {
            item {
                PlainCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                SectionLabel("Home-Workout")
                                Text(
                                    "${plan.frequencyPerWeek}× pro Woche · ${plan.minutesPerSession} min",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Palette.TextPrimary,
                                )
                            }
                            TextButton(onClick = onOpenWorkout) { Text("Plan", color = Palette.TextSecondary) }
                        }
                        ToggleRow(
                            title = "Heute trainiert",
                            checked = state.workoutDoneToday,
                            onCheckedChange = viewModel::setWorkoutDone,
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Mahlzeiten", Modifier.weight(1f))
                if (state.meals.isNotEmpty()) {
                    Text("${state.meals.size}", style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary)
                }
            }
        }
        if (state.meals.isEmpty() && !state.loading) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    EmptyHint("Noch nichts eingetragen.")
                    PrimaryButton("Essen hinzufügen", onAdd, icon = Icons.Outlined.Add)
                }
            }
        }
        items(state.meals, key = { it.id }) { meal ->
            MealRow(meal, onClick = { onOpenMeal(meal.id) }, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun StreakChip(days: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Palette.Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.TextPrimary))
        Spacer(Modifier.width(6.dp))
        Text(if (days == 1) "1 Tag" else "$days Tage", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
    }
}

@Composable
private fun CalorieHero(totals: Nutrients, targets: GoalTargets?) {
    val target = targets?.targetKcal?.toDouble() ?: 0.0
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CalorieRing(consumed = totals.kcal, target = target) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(totals.kcal, MaterialTheme.typography.displayLarge)
                    Text("/ ${Fmt.int(target)} kcal", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            val remaining = target - totals.kcal
            Text(
                when {
                    target <= 0 -> ""
                    remaining >= 0 -> "${Fmt.int(remaining)} kcal übrig"
                    else -> "${Fmt.int(abs(remaining))} kcal über Ziel"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
            )
        }
    }
}

@Composable
private fun MacroCard(totals: Nutrients, targets: GoalTargets?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    PlainCard(Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            MacroBar(Metric.PROTEIN.label, totals.protein, targets?.proteinG?.toDouble() ?: 0.0, Palette.Protein)
            Spacer(Modifier.height(16.dp))
            MacroBar(Metric.CARBS.label, totals.carbs, targets?.carbsG?.toDouble() ?: 0.0, Palette.Carbs)
            Spacer(Modifier.height(16.dp))
            MacroBar(Metric.FAT.label, totals.fat, targets?.fatG?.toDouble() ?: 0.0, Palette.Fat)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { haptics.perform(HapticEvent.Toggle); expanded = !expanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Weitere Nährwerte", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Palette.TextSecondary,
                )
            }
            Expandable(expanded) {
                ExtraNutrients(totals, targets)
            }
        }
    }
}

@Composable
fun ExtraNutrients(totals: Nutrients, targets: GoalTargets?) {
    NutrientRow("Ballaststoffe", "${Fmt.int(totals.fiber)} g", detail = targets?.let { "/ mind. ${it.fiberG} g" })
    NutrientRow("Zucker", "${Fmt.int(totals.sugar)} g", detail = targets?.let { "/ max. ${it.sugarMaxG} g" })
    NutrientRow("Gesättigte Fettsäuren", "${Fmt.int(totals.saturatedFat)} g", detail = targets?.let { "/ max. ${it.saturatedFatMaxG} g" })
    NutrientRow("Salz", "${Fmt.one(totals.salt)} g", detail = targets?.let { "/ max. ${Fmt.small(it.saltMaxG)} g" })
}

@Composable
private fun RecalibrationCard(
    suggestion: RecalibrationSuggestion,
    onAccept: () -> Unit,
    onLater: () -> Unit,
    onDetails: () -> Unit,
) {
    PlainCard(Modifier.fillMaxWidth()) {
        Column {
            SectionLabel("Plan-Vorschlag")
            Spacer(Modifier.height(8.dp))
            Text(
                "Dein Verbrauch weicht von der bisherigen Berechnung ab. Soll die App dein Ziel anpassen?",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Beobachtet über ${suggestion.daysAnalyzed} Tage: Ø ${Fmt.int(suggestion.averageIntakeKcal)} kcal, " +
                    "Gewicht ${Fmt.signedKg(suggestion.weeklyWeightChangeKg)} pro Woche. " +
                    "Erhaltungsbedarf eher ${Fmt.int(suggestion.observedMaintenanceKcal)} statt ${Fmt.int(suggestion.currentMaintenanceKcal)} kcal.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text("Neues Ziel: ${Fmt.int(suggestion.suggestedTargetKcal)} kcal", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Anpassen", onAccept, Modifier.weight(1f), haptic = HapticEvent.Confirm)
                SecondaryButton("Später", onLater, Modifier.weight(1f))
            }
            TextButton(onClick = onDetails) { Text("Plan selbst bearbeiten", color = Palette.TextSecondary) }
        }
    }
}

@Composable
private fun WeightCard(
    latest: Double?,
    loggedToday: Boolean,
    targetWeight: Double?,
    onSave: (String) -> Boolean,
    onOpen: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val focus = LocalFocusManager.current
    var input by remember(latest, loggedToday) { mutableStateOf(if (loggedToday && latest != null) Fmt.one(latest) else "") }
    var error by remember { mutableStateOf(false) }
    PlainCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { haptics.perform(HapticEvent.Tap); onOpen() }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Gewicht")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(latest?.let { Fmt.one(it) } ?: "–", style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
                        Text(" kg", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                        if (targetWeight != null && latest != null) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Ziel ${Fmt.one(targetWeight)} kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextTertiary,
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Verlauf", tint = Palette.TextTertiary)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    value = input,
                    onValueChange = { input = it; error = false },
                    label = if (loggedToday) "Heute eingetragen" else "Heutiges Gewicht",
                    suffix = "kg",
                    isError = error,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    text = "Speichern",
                    enabled = input.isNotBlank(),
                    haptic = HapticEvent.Confirm,
                    onClick = {
                        error = !onSave(input)
                        if (!error) focus.clearFocus()
                    },
                )
            }
        }
    }
}

@Composable
private fun MealRow(meal: Meal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHaptics.current
    val time = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { haptics.perform(HapticEvent.Tap); onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Fmt.time(time), style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary, modifier = Modifier.width(52.dp))
        Column(Modifier.weight(1f)) {
            Text(meal.category.label, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary)
            Text(meal.name, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary, maxLines = 1)
        }
        if (meal.photoPaths.isNotEmpty()) {
            Box(Modifier.padding(end = 10.dp).size(6.dp).clip(CircleShape).background(Palette.TextTertiary))
        }
        Text("${Fmt.int(meal.totals.kcal)} kcal", style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
    }
}
