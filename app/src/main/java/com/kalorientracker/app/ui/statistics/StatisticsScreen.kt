package com.kalorientracker.app.ui.statistics

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.usecase.PeriodSummary
import com.kalorientracker.app.domain.usecase.StatsRange
import com.kalorientracker.app.ui.common.AnimatedNumber
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NutrientRow
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.statistics.charts.ChartType
import com.kalorientracker.app.ui.statistics.charts.MorphingChart
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Motion
import com.kalorientracker.app.ui.theme.Palette
import com.kalorientracker.app.ui.theme.motionSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val rangeSegments = listOf(
    StatsRange.WEEK to "7T",
    StatsRange.MONTH to "1M",
    StatsRange.QUARTER to "3M",
    StatsRange.HALF_YEAR to "6M",
    StatsRange.YEAR to "1J",
    StatsRange.ALL to "Alle",
)

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
    val multiple = state.charts.size > 1

    LazyColumn(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { ScreenHeader(title = "Statistik", onSettings = onOpenSettings) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Metric.entries) { metric ->
                    SelectChip(
                        metric.label,
                        metric in selection.metrics,
                        { viewModel.toggleMetric(metric) },
                        leadingColor = if (metric in selection.metrics) null else Palette.forMetric(metric),
                    )
                }
            }
        }
        item {
            Hero(
                metric = selection.metrics.first(),
                summary = state.summary,
                targetKcal = state.targets?.targetKcal,
                targetFor = { state.targets?.targetFor(it) },
                targetWeight = state.targetWeight,
                customRange = if (selection.range == StatsRange.CUSTOM) {
                    "${Fmt.dayMonth(LocalDate.ofEpochDay(state.startDay))} – ${Fmt.dayMonth(LocalDate.ofEpochDay(state.endDay))}"
                } else {
                    null
                },
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RangeSegments(
                    selected = selection.range,
                    onSelect = { range ->
                        if (selection.range != range) haptics.perform(HapticEvent.Tick)
                        viewModel.setRange(range)
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { haptics.perform(HapticEvent.Tap); pickRange = true }) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = "Eigener Zeitraum",
                        tint = if (selection.range == StatsRange.CUSTOM) Palette.TextPrimary else Palette.TextTertiary,
                    )
                }
            }
        }
        items(state.charts, key = { it.metric.name }) { chart ->
            val isFirst = chart.metric == state.charts.first().metric
            MorphingChart(
                title = chart.metric.label,
                points = chart.points,
                type = chart.type,
                color = Palette.forMetric(chart.metric),
                target = chart.target,
                zeroBased = chart.metric != Metric.WEIGHT,
                smooth = state.smooth,
                height = if (multiple) 120.dp else 190.dp,
                showTitle = multiple,
                summary = if (multiple) chart.summary else null,
                trailing = if (isFirst) {
                    {
                        ChartTypeToggle(selection.chartType) {
                            haptics.perform(HapticEvent.Toggle)
                            viewModel.setChartType(if (selection.chartType == ChartType.BAR) ChartType.LINE else ChartType.BAR)
                        }
                    }
                } else {
                    null
                },
                format = { if (chart.metric == Metric.WEIGHT) Fmt.one(it) else Fmt.int(it) },
                modifier = Modifier.animateItem(),
            )
        }
        state.summary?.let { summary ->
            item { Details(summary, state.streak, state.targets?.targetKcal, state.targetWeight) }
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

/** One large headline number for the first selected metric, like the Today screen. */
@Composable
private fun Hero(
    metric: Metric,
    summary: PeriodSummary?,
    targetKcal: Int?,
    targetFor: (Metric) -> Double?,
    targetWeight: Double?,
    customRange: String?,
) {
    Column {
        if (metric == Metric.WEIGHT) {
            val end = summary?.weightEnd
            SectionLabel(customRange ?: "Gewicht")
            Row(verticalAlignment = Alignment.Bottom) {
                if (end != null) AnimatedNumber(end, MaterialTheme.typography.displayMedium, format = { Fmt.one(it) })
                else Text("–", style = MaterialTheme.typography.displayMedium, color = Palette.TextPrimary)
                Text(" kg", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }
            val parts = listOfNotNull(
                summary?.weightChange?.let { "${Fmt.signedKg(it)} im Zeitraum" },
                targetWeight?.let { "Ziel ${Fmt.one(it)} kg" },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Palette.TextTertiary)
        } else {
            val average = summary?.averages?.valueOf(metric) ?: 0.0
            val tracked = summary?.trackedDays ?: 0
            SectionLabel(customRange ?: "Ø ${metric.label} pro Tag")
            Row(verticalAlignment = Alignment.Bottom) {
                if (tracked > 0) AnimatedNumber(average, MaterialTheme.typography.displayMedium)
                else Text("–", style = MaterialTheme.typography.displayMedium, color = Palette.TextPrimary)
                Text(" ${metric.unit}", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }
            val target = if (metric == Metric.CALORIES) targetKcal?.toDouble() else targetFor(metric)
            val parts = listOfNotNull(
                target?.let { "Ziel ${Fmt.int(it)} ${metric.unit}" },
                summary?.let { "${it.trackedDays} von ${it.totalDays} Tagen erfasst" },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Palette.TextTertiary)
        }
    }
}

@Composable
private fun RangeSegments(selected: StatsRange, onSelect: (StatsRange) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(20.dp))
            .padding(3.dp),
    ) {
        rangeSegments.forEach { (range, label) ->
            val active = range == selected
            val bg by animateColorAsState(if (active) Palette.TextPrimary else Color.Transparent, motionSpec(Motion.snappy()), label = "segment")
            val fg by animateColorAsState(if (active) Palette.Background else Palette.TextSecondary, motionSpec(Motion.snappy()), label = "segmentText")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(17.dp))
                    .background(bg)
                    .clickable { onSelect(range) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChartTypeToggle(type: ChartType, onToggle: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, Palette.Outline, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (type == ChartType.BAR) Icons.AutoMirrored.Outlined.ShowChart else Icons.Outlined.BarChart,
            contentDescription = if (type == ChartType.BAR) "Als Linie anzeigen" else "Als Balken anzeigen",
            tint = Palette.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Everything secondary in one quiet list instead of several cards. */
@Composable
private fun Details(summary: PeriodSummary, streak: Int, targetKcal: Int?, targetWeight: Double?) {
    Column {
        HorizontalDivider(color = Palette.Outline.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        if (summary.trackedDays > 0) {
            Row(Modifier.fillMaxWidth()) {
                MiniAverage(Metric.PROTEIN, summary.averages.protein, Modifier.weight(1f))
                MiniAverage(Metric.CARBS, summary.averages.carbs, Modifier.weight(1f))
                MiniAverage(Metric.FAT, summary.averages.fat, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            if (targetKcal != null) {
                NutrientRow("Kalorienziel getroffen", "${summary.daysOnTarget} von ${summary.trackedDays} Tagen")
            }
        }
        val end = summary.weightEnd
        if (end != null && targetWeight != null) {
            val remaining = kotlin.math.abs(targetWeight - end)
            NutrientRow("Bis zum Zielgewicht", if (remaining < 0.05) "erreicht" else Fmt.kg(remaining))
        }
        NutrientRow("Serie", if (streak == 1) "1 Tag" else "$streak Tage")
    }
}

@Composable
private fun MiniAverage(metric: Metric, value: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.forMetric(metric)))
            Spacer(Modifier.width(6.dp))
            Text(metric.label, style = MaterialTheme.typography.bodySmall, color = Palette.TextTertiary, maxLines = 1)
        }
        Text("Ø ${Fmt.int(value)} g", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
    }
}
