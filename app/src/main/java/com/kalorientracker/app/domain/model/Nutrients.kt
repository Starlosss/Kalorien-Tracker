package com.kalorientracker.app.domain.model

/** Nutrient amounts. Used both as "per 100 g" reference values and as absolute totals. */
data class Nutrients(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val salt: Double = 0.0,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fiber = fiber + other.fiber,
        sugar = sugar + other.sugar,
        saturatedFat = saturatedFat + other.saturatedFat,
        salt = salt + other.salt,
    )

    operator fun times(factor: Double) = Nutrients(
        kcal = kcal * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
        fiber = fiber * factor,
        sugar = sugar * factor,
        saturatedFat = saturatedFat * factor,
        salt = salt * factor,
    )

    /** Treats this instance as per-100 g values and scales it to [grams]. */
    fun forGrams(grams: Double): Nutrients = times(grams / 100.0)

    fun valueOf(metric: Metric): Double = when (metric) {
        Metric.CALORIES -> kcal
        Metric.PROTEIN -> protein
        Metric.CARBS -> carbs
        Metric.FAT -> fat
        Metric.WEIGHT -> 0.0
    }

    companion object {
        val ZERO = Nutrients()
    }
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }
