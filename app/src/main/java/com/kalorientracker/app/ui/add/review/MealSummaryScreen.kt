package com.kalorientracker.app.ui.add.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kalorientracker.app.domain.model.MealCategory
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.model.Nutrients
import com.kalorientracker.app.ui.add.AddFlowViewModel
import com.kalorientracker.app.ui.common.AnimatedNumber
import com.kalorientracker.app.ui.common.Expandable
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NutrientRow
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SecondaryButton
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.common.TextInput
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import java.io.File
import java.time.LocalTime

@Composable
fun MealSummaryScreen(
    viewModel: AddFlowViewModel,
    onEdit: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    var showTimePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ScreenHeader(title = "Übersicht", onBack = onEdit, modifier = Modifier.padding(horizontal = 20.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.photos, key = { it }) { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
                        )
                    }
                }
            }
            TextInput(state.suggestedName, viewModel::setMealName, "Name der Mahlzeit", Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            NutritionSummary(state.totals)
            Spacer(Modifier.height(20.dp))
            SectionLabel("Kategorie")
            Spacer(Modifier.height(8.dp))
            CategoryChips(state.category, viewModel::setCategory)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { haptics.perform(HapticEvent.Tap); showTimePicker = true }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Uhrzeit", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Text(Fmt.time(state.loggedTime), style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Bearbeiten", onEdit, Modifier.weight(1f))
            PrimaryButton(
                text = "Speichern",
                enabled = !state.saving && state.ingredients.isNotEmpty(),
                haptic = HapticEvent.Tap,
                modifier = Modifier.weight(2f),
                onClick = {
                    viewModel.save { outcome ->
                        haptics.perform(if (outcome.goalReached) HapticEvent.GoalReached else HapticEvent.Save)
                        onSaved()
                    }
                },
            )
        }
    }

    if (showTimePicker) {
        TimeDialog(
            initial = state.loggedTime,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.setLoggedTime(it)
                showTimePicker = false
            },
        )
    }
}

@Composable
fun NutritionSummary(totals: Nutrients) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedNumber(totals.kcal, MaterialTheme.typography.displayMedium)
                Text(" kcal", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            MacroValueRow(Metric.PROTEIN, totals.protein)
            MacroValueRow(Metric.CARBS, totals.carbs)
            MacroValueRow(Metric.FAT, totals.fat)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { haptics.perform(HapticEvent.Toggle); expanded = !expanded }
                    .padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Weitere Nährwerte", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = Palette.TextSecondary)
            }
            Expandable(expanded) {
                NutrientRow("Ballaststoffe", "${Fmt.one(totals.fiber)} g")
                NutrientRow("Zucker", "${Fmt.one(totals.sugar)} g")
                NutrientRow("Gesättigte Fettsäuren", "${Fmt.one(totals.saturatedFat)} g")
                NutrientRow("Salz", "${Fmt.one(totals.salt)} g")
            }
        }
    }
}

@Composable
private fun MacroValueRow(metric: Metric, grams: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.forMetric(metric)))
        Spacer(Modifier.size(10.dp))
        Text(metric.label, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        AnimatedNumber(grams, MaterialTheme.typography.titleMedium, format = { "${Fmt.int(it)} g" })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(selected: MealCategory, onSelect: (MealCategory) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealCategory.entries.forEach { category ->
            SelectChip(category.label, selected == category, { onSelect(category) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceRaised,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text("Übernehmen", color = Palette.TextPrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = Palette.TextSecondary) } },
        text = {
            TimePicker(
                state = pickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Palette.Surface,
                    selectorColor = Palette.TextPrimary,
                    timeSelectorSelectedContainerColor = Palette.TextPrimary,
                    timeSelectorSelectedContentColor = Palette.Background,
                    timeSelectorUnselectedContainerColor = Palette.Surface,
                    timeSelectorUnselectedContentColor = Palette.TextPrimary,
                ),
            )
        },
    )
}
