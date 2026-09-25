package com.kalorientracker.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kalorientracker.app.data.repository.MealRepository
import com.kalorientracker.app.data.repository.ProfileRepository
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.Metric
import com.kalorientracker.app.domain.usecase.BuildStatisticsUseCase
import com.kalorientracker.app.domain.usecase.ComputeStreakUseCase
import com.kalorientracker.app.domain.usecase.PeriodSummary
import com.kalorientracker.app.domain.usecase.StatsRange
import com.kalorientracker.app.ui.common.Fmt
import com.kalorientracker.app.ui.statistics.charts.ChartPoint
import com.kalorientracker.app.ui.statistics.charts.ChartType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StatsSelection(
    val metrics: List<Metric> = listOf(Metric.CALORIES),
    val chartType: ChartType = ChartType.BAR,
    val range: StatsRange = StatsRange.WEEK,
    val customStart: Long? = null,
    val customEnd: Long? = null,
)

data class MetricChart(
    val metric: Metric,
    val points: List<ChartPoint>,
    val type: ChartType,
    val target: Double?,
    val summary: String,
)

data class StatisticsUiState(
    val selection: StatsSelection = StatsSelection(),
    val startDay: Long = LocalDate.now().toEpochDay(),
    val endDay: Long = LocalDate.now().toEpochDay(),
    val charts: List<MetricChart> = emptyList(),
    val summary: PeriodSummary? = null,
    val targets: GoalTargets? = null,
    val targetWeight: Double? = null,
    val streak: Int = 0,
    val smooth: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val meals: MealRepository,
    profiles: ProfileRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val stats = BuildStatisticsUseCase()
    private val streak = ComputeStreakUseCase()

    private val selection = MutableStateFlow(StatsSelection())
    private val firstDay = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { firstDay.value = meals.firstTrackedDay() }
    }

    private val window = combine(selection, firstDay) { s, first ->
        val today = LocalDate.now().toEpochDay()
        when (s.range) {
            StatsRange.CUSTOM -> (s.customStart ?: today - 6) to (s.customEnd ?: today)
            StatsRange.ALL -> minOf(first ?: today - 6, today - 6) to today
            else -> today - (s.range.days ?: 7) + 1 to today
        }
    }

    private val daily = window.flatMapLatest { (start, end) -> meals.observeDailyTotals(start, end).map { Triple(start, end, it) } }

    private val context = combine(profiles.observeWeights(), profiles.observeTargets(), profiles.observeProfile(), meals.observeTrackedDays(), settings.settings) {
            weights, targets, profile, tracked, s ->
        Context(weights, targets, profile?.targetWeightKg, streak(tracked, LocalDate.now().toEpochDay()), s.smoothCharts)
    }

    private data class Context(
        val weights: List<com.kalorientracker.app.domain.model.WeightEntry>,
        val targets: GoalTargets?,
        val targetWeight: Double?,
        val streak: Int,
        val smooth: Boolean,
    )

    val state: StateFlow<StatisticsUiState> = combine(selection, daily, context) { s, (start, end, totals), c ->
        val bucket = stats.bucketDays(end - start + 1)
        val summary = stats.summary(start, end, totals, c.weights, c.targets)
        val charts = s.metrics.map { metric ->
            val series = stats.series(metric, start, end, totals, c.weights)
            val isWeight = metric == Metric.WEIGHT
            MetricChart(
                metric = metric,
                points = series.toChartPoints(bucket),
                type = if (isWeight) ChartType.LINE else s.chartType,
                target = if (isWeight) c.targetWeight else c.targets?.targetFor(metric),
                summary = summaryText(metric, summary),
            )
        }
        StatisticsUiState(
            selection = s,
            startDay = start,
            endDay = end,
            charts = charts,
            summary = summary,
            targets = c.targets,
            targetWeight = c.targetWeight,
            streak = c.streak,
            smooth = c.smooth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun toggleMetric(metric: Metric) = selection.update { s ->
        val next = if (metric in s.metrics) s.metrics - metric else s.metrics + metric
        s.copy(metrics = next.ifEmpty { listOf(metric) }.sortedBy { it.ordinal })
    }

    fun setChartType(type: ChartType) = selection.update { it.copy(chartType = type) }

    fun setRange(range: StatsRange) = selection.update { it.copy(range = range) }

    fun setCustomRange(start: LocalDate, end: LocalDate) = selection.update {
        it.copy(range = StatsRange.CUSTOM, customStart = start.toEpochDay(), customEnd = end.toEpochDay())
    }

    private fun summaryText(metric: Metric, summary: PeriodSummary): String = when (metric) {
        Metric.WEIGHT -> summary.weightChange?.let { Fmt.signedKg(it) } ?: ""
        Metric.CALORIES -> if (summary.trackedDays == 0) "" else "Ø ${Fmt.int(summary.averages.kcal)} kcal"
        else -> if (summary.trackedDays == 0) "" else "Ø ${Fmt.int(summary.averages.valueOf(metric))} g"
    }
}
