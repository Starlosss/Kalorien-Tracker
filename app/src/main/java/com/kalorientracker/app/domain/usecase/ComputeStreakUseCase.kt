package com.kalorientracker.app.domain.usecase

/**
 * Number of consecutive tracked days. A streak stays alive through today until the day is over,
 * so it counts back from yesterday when nothing has been logged today yet.
 */
class ComputeStreakUseCase {
    operator fun invoke(trackedEpochDays: Set<Long>, todayEpochDay: Long): Int {
        var day = if (todayEpochDay in trackedEpochDays) todayEpochDay else todayEpochDay - 1
        var streak = 0
        while (day in trackedEpochDays) {
            streak++
            day--
        }
        return streak
    }
}
