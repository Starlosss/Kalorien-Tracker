package com.kalorientracker.app.ui.common

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** German number and date formatting: 1.842 kcal, 81,7 kg. */
object Fmt {
    val locale: Locale = Locale.GERMANY

    private val integer = NumberFormat.getIntegerInstance(locale)
    private fun decimals(digits: Int) = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }

    fun int(value: Double): String = integer.format(value.roundToInt())
    fun int(value: Int): String = integer.format(value)
    fun one(value: Double): String = decimals(1).format(value)

    fun kcal(value: Double) = "${int(value)} kcal"
    fun grams(value: Double) = "${int(value)} g"
    fun kg(value: Double) = "${one(value)} kg"

    /** Salt and other small amounts need one decimal. */
    fun small(value: Double): String = if (value < 10) one(value) else int(value)

    fun signedKg(value: Double): String = (if (value > 0) "+" else if (value < 0) "−" else "±") + one(kotlin.math.abs(value)) + " kg"

    /** Parses user input like "81,7" or "81.7". */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    private val dayMonth = DateTimeFormatter.ofPattern("d. MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d. MMM yyyy", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun dayMonth(date: LocalDate): String = date.format(dayMonth)
    fun dayMonthYear(date: LocalDate): String = date.format(dayMonthYear)
    fun time(value: java.time.LocalTime): String = value.format(time)
    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> "${weekday(date)}, ${dayMonth(date)}"
    }
}
