package com.kalorientracker.app.ui.statistics

import com.kalorientracker.app.domain.usecase.SeriesPoint
import com.kalorientracker.app.ui.statistics.charts.ChartPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("d.M.", Locale.GERMANY)
private val monthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.GERMANY)

fun bucketLabel(startEpochDay: Long, bucketDays: Int): String {
    val date = LocalDate.ofEpochDay(startEpochDay)
    return when {
        bucketDays >= 28 -> date.format(monthFormat)
        bucketDays > 1 -> "ab ${date.format(dayFormat)}"
        else -> date.format(dayFormat)
    }
}

fun List<SeriesPoint>.toChartPoints(bucketDays: Int): List<ChartPoint> =
    map { ChartPoint(bucketLabel(it.startEpochDay, bucketDays), it.value) }
