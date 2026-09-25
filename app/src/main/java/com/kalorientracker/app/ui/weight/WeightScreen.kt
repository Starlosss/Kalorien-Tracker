package com.kalorientracker.app.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.repository.ProfileRepository
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.model.WeightEntry
import com.kalorientracker.app.domain.usecase.BuildStatisticsUseCase
import com.kalorientracker.app.domain.usecase.StatsRange
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.common.NumberField
import com.kalorientracker.app.ui.common.PrimaryButton
import com.kalorientracker.app.ui.common.ScreenHeader
import com.kalorientracker.app.ui.common.SectionLabel
import com.kalorientracker.app.ui.common.SelectChip
import com.kalorientracker.app.ui.statistics.charts.ChartPoint
import com.kalorientracker.app.ui.statistics.charts.ChartType
import com.kalorientracker.app.ui.statistics.charts.MorphingChart
import com.kalorientracker.app.ui.statistics.toChartPoints
import com.kalorientracker.app.ui.theme.GlassCard
import com.kalorientracker.app.ui.theme.HapticEvent
import com.kalorientracker.app.ui.theme.LocalHaptics
import com.kalorientracker.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class WeightUiState(
    val entries: List<WeightEntry> = emptyList(),
    val range: StatsRange = StatsRange.MONTH,
    val points: List<ChartPoint> = emptyList(),
    val targetWeight: Double? = null,
    val showTarget: Boolean = true,
    val smooth: Boolean = true,
    val change: Double? = null,
)

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val stats = BuildStatisticsUseCase()
    private val range = MutableStateFlow(StatsRange.MONTH)

    val state: StateFlow<WeightUiState> = combine(
        profiles.observeWeights(),
        profiles.observeProfile(),
        settings.settings,
        range,
    ) { weights, profile, s, r ->
        val today = LocalDate.now().toEpochDay()
        val first = weights.minOfOrNull { it.epochDay } ?: today
        val start = r.days?.let { today - it + 1 } ?: first
        val series = stats.series(Metric.WEIGHT, start, today, emptyList(), weights)
        val inRange = weights.filter { it.epochDay in start..today }.sortedBy { it.epochDay }
        WeightUiState(
            entries = weights.sortedByDescending { it.epochDay },
            range = r,
            points = series.toChartPoints(stats.bucketDays(today - start + 1)),
            targetWeight = profile?.targetWeightKg,
            showTarget = s.showTargetWeightLine,
            smooth = s.smoothCharts,
            change = if (inRange.size >= 2) inRange.last().weightKg - inRange.first().weightKg else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    fun setRange(r: StatsRange) {
        range.value = r
    }

    fun save(text: String): Boolean {
        val value = Fmt.parse(text) ?: return false
        if (value !in 30.0..350.0) return false
        viewModelScope.launch { profiles.saveWeight(LocalDate.now().toEpochDay(), value) }
        return true
    }

    fun delete(entry: WeightEntry) {
        viewModelScope.launch { profiles.deleteWeight(entry.epochDay) }
    }
}

val weightRanges = listOf(StatsRange.WEEK, StatsRange.MONTH, StatsRange.QUARTER, StatsRange.HALF_YEAR, StatsRange.YEAR, StatsRange.ALL)

@Composable
fun WeightScreen(onBack: () -> Unit, viewModel: WeightViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHaptics.current
    val focus = LocalFocusManager.current
    val today = LocalDate.now().toEpochDay()
    val todayEntry = state.entries.firstOrNull { it.epochDay == today }
    var input by remember(todayEntry) { mutableStateOf(todayEntry?.let { Fmt.one(it.weightKg) } ?: "") }
    var error by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(title = "Gewicht", onBack = onBack) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionLabel("Aktuell")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(state.entries.firstOrNull()?.let { Fmt.one(it.weightKg) } ?: "–", style = MaterialTheme.typography.displayLarge, color = Palette.TextPrimary)
                        Text(" kg", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    val details = listOfNotNull(
                        state.change?.let { "${Fmt.signedKg(it)} im Zeitraum" },
                        state.targetWeight?.let { "Ziel ${Fmt.one(it)} kg" },
                    )
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(input, { input = it; error = false }, "Heute", Modifier.weight(1f), suffix = "kg", isError = error)
                        Spacer(Modifier.width(10.dp))
                        PrimaryButton("Speichern", {
                            error = !viewModel.save(input)
                            if (!error) focus.clearFocus()
                        }, enabled = input.isNotBlank(), haptic = HapticEvent.Confirm)
                    }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(weightRanges) { r ->
                    SelectChip(r.label, state.range == r, {
                        if (state.range != r) haptics.perform(HapticEvent.Tick)
                        viewModel.setRange(r)
                    })
                }
            }
        }
        item {
            MorphingChart(
                title = "Verlauf",
                points = state.points,
                type = ChartType.LINE,
                color = Palette.TextPrimary,
                target = if (state.showTarget) state.targetWeight else null,
                zeroBased = false,
                smooth = state.smooth,
                height = 220.dp,
                format = { Fmt.one(it) },
            )
        }
        item { SectionLabel("Einträge", Modifier.padding(top = 8.dp)) }
        items(state.entries.take(60), key = { it.epochDay }) { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.relativeDay(entry.date), style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Text(Fmt.kg(entry.weightKg), style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
                IconButton(onClick = { haptics.perform(HapticEvent.Tap); viewModel.delete(entry) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Eintrag löschen", tint = Palette.TextTertiary)
                }
            }
        }
    }
}
