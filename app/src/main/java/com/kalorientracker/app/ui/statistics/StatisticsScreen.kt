package com.kalorientracker.app.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.usecase.StatsRange
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NutrientRow
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.statistics.charts.ChartType
import com.kalorientracker.app.ui.statistics.charts.MorphingChart
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.PlainCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onOpenSettings: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    var pickRange by remember { mutableStateOf(false) }
    val selection = state.selection

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(title = "Statistik", onSettings = onOpenSettings) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Metric.entries) { metric ->
                    SelectChip(
                        metric.label,
                        metric in selection.metrics,
                        { viewModel.toggleMetric(metric) },
                        leadingColor = Palette.forMetric(metric),
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatsRange.entries) { range ->
                    SelectChip(
                        if (range == StatsRange.CUSTOM && selection.range == StatsRange.CUSTOM) {
                            "${Fmt.dayMonth(LocalDate.ofEpochDay(state.startDay))} – ${Fmt.dayMonth(LocalDate.ofEpochDay(state.endDay))}"
                        } else {
                            range.label
                        },
                        selection.range == range,
                        {
                            if (range == StatsRange.CUSTOM) {
                                pickRange = true
                            } else {
                                if (selection.range != range) haptics.perform(HapticEvent.Tick)
                                viewModel.setRange(range)
                            }
                        },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Balken", selection.chartType == ChartType.BAR, { viewModel.setChartType(ChartType.BAR) })
                SelectChip("Linie", selection.chartType == ChartType.LINE, { viewModel.setChartType(ChartType.LINE) })
            }
        }
        items(state.charts, key = { it.metric.name }) { chart ->
            PlainCard(Modifier.fillMaxWidth().animateItem()) {
                MorphingChart(
                    title = chart.metric.label,
                    points = chart.points,
                    type = chart.type,
                    color = Palette.forMetric(chart.metric),
                    target = chart.target,
                    zeroBased = chart.metric != Metric.WEIGHT,
                    smooth = state.smooth,
                    summary = chart.summary,
                    format = { if (chart.metric == Metric.WEIGHT) Fmt.one(it) else Fmt.int(it) },
                )
            }
        }
        state.summary?.let { summary ->
            item {
                PlainCard(Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel("Durchschnitt · ${summary.trackedDays} von ${summary.totalDays} Tagen erfasst")
                        Spacer(Modifier.height(6.dp))
                        if (summary.trackedDays == 0) {
                            Text("In diesem Zeitraum wurde noch nichts eingetragen.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextTertiary)
                        } else {
                            val t = state.targets
                            NutrientRow("Kalorien", "${Fmt.int(summary.averages.kcal)}", detail = t?.let { "/ ${Fmt.int(it.targetKcal)} kcal" } ?: "kcal")
                            NutrientRow("Protein", "${Fmt.int(summary.averages.protein)}", detail = t?.let { "/ ${it.proteinG} g" } ?: "g")
                            NutrientRow("Kohlenhydrate", "${Fmt.int(summary.averages.carbs)}", detail = t?.let { "/ ${it.carbsG} g" } ?: "g")
                            NutrientRow("Fett", "${Fmt.int(summary.averages.fat)}", detail = t?.let { "/ ${it.fatG} g" } ?: "g")
                        }
                    }
                }
            }
            item {
                PlainCard(Modifier.fillMaxWidth()) {
                    Column {
                        SectionLabel("Zielvergleich")
                        Spacer(Modifier.height(6.dp))
                        if (state.targets != null && summary.trackedDays > 0) {
                            NutrientRow(
                                "Kalorienziel getroffen (±10 %)",
                                "${summary.daysOnTarget}",
                                detail = "von ${summary.trackedDays} Tagen",
                            )
                        }
                        val change = summary.weightChange
                        if (change != null) {
                            NutrientRow("Gewicht im Zeitraum", Fmt.signedKg(change))
                        }
                        val end = summary.weightEnd
                        val target = state.targetWeight
                        if (end != null && target != null) {
                            val remaining = target - end
                            NutrientRow(
                                "Bis zum Zielgewicht",
                                if (kotlin.math.abs(remaining) < 0.05) "erreicht" else Fmt.kg(kotlin.math.abs(remaining)),
                                detail = "Ziel ${Fmt.one(target)} kg",
                            )
                        }
                        NutrientRow("Tracking-Serie", if (state.streak == 1) "1 Tag" else "${state.streak} Tage")
                    }
                }
            }
        }
    }

    if (pickRange) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = LocalDate.ofEpochDay(state.startDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = LocalDate.ofEpochDay(state.endDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            colors = DatePickerDefaults.colors(containerColor = Palette.SurfaceRaised),
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = Instant.ofEpochMilli(pickerState.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(pickerState.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        haptics.perform(HapticEvent.Tick)
                        viewModel.setCustomRange(start, end)
                        pickRange = false
                    },
                ) { Text("Übernehmen", color = Palette.TextPrimary) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Abbrechen", color = Palette.TextSecondary) } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("Zeitraum wählen", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
