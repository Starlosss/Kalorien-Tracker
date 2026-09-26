package com.kalorientracker.app.domain.usecase

/**
 * Body mass index and the WHO's classification of it.
 *
 * The DGE's energy equation works from weight and age alone, so height would otherwise be data the
 * app asks for and never uses. As a BMI it stays useful: it puts the weight goal in context.
 */
object BodyMetrics {

    const val SOURCE = "WHO: Body mass index classification"

    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (heightCm < 50 || weightKg <= 0) return null
        val metres = heightCm / 100.0
        return weightKg / (metres * metres)
    }

    fun classify(bmi: Double): String = when {
        bmi < 18.5 -> "Untergewicht"
        bmi < 25.0 -> "Normalgewicht"
        bmi < 30.0 -> "Übergewicht"
        bmi < 35.0 -> "Adipositas Grad I"
        bmi < 40.0 -> "Adipositas Grad II"
        else -> "Adipositas Grad III"
    }
}
