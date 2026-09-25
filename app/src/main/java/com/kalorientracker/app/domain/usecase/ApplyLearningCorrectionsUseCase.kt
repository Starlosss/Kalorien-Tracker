package com.kalorientracker.app.domain.usecase

import com.kalorientracker.app.domain.model.CorrectionHint
import com.kalorientracker.app.domain.model.LearningCorrection
import kotlin.math.abs

/**
 * Condenses stored user corrections into per-food bias ratios. The median of the most recent
 * corrections is used so a single outlier does not skew future estimates.
 */
class ApplyLearningCorrectionsUseCase {

    operator fun invoke(corrections: List<LearningCorrection>): List<CorrectionHint> =
        corrections
            .filter { it.originalGrams > 0 && it.correctedGrams > 0 }
            .groupBy { it.foodKey }
            .mapNotNull { (key, items) ->
                val recent = items.sortedByDescending { it.createdAtMillis }.take(MAX_SAMPLES)
                if (recent.size < MIN_SAMPLES) return@mapNotNull null
                val ratio = median(recent.map { it.correctedGrams / it.originalGrams })
                    .coerceIn(MIN_RATIO, MAX_RATIO)
                if (abs(ratio - 1.0) < MIN_EFFECT) null else CorrectionHint(key, ratio, recent.size)
            }
            .sortedBy { it.foodKey }

    companion object {
        const val MIN_SAMPLES = 2
        const val MAX_SAMPLES = 10
        const val MIN_RATIO = 0.5
        const val MAX_RATIO = 2.0
        const val MIN_EFFECT = 0.05

        /** A correction is only worth remembering when the user changed the amount noticeably. */
        fun isMeaningful(originalGrams: Double, correctedGrams: Double): Boolean =
            originalGrams > 0 && abs(correctedGrams - originalGrams) / originalGrams >= 0.1

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
        }
    }
}
