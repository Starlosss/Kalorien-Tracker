package com.kalorientracker.app.data.repository

import com.kalorientracker.app.data.db.WorkoutDao
import com.kalorientracker.app.data.db.WorkoutExerciseEntity
import com.kalorientracker.app.data.db.WorkoutLogEntity
import com.kalorientracker.app.data.db.WorkoutPlanEntity
import com.kalorientracker.app.data.db.toWorkoutPlan
import com.kalorientracker.app.domain.model.WorkoutPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
) {
    fun observePlan(): Flow<WorkoutPlan?> =
        combine(dao.observePlan(), dao.observeExercises()) { plan, exercises ->
            plan?.let { toWorkoutPlan(it, exercises) }
        }

    suspend fun savePlan(plan: WorkoutPlan) {
        dao.replacePlan(
            WorkoutPlanEntity(
                frequencyPerWeek = plan.frequencyPerWeek,
                minutesPerSession = plan.minutesPerSession,
                difficulty = plan.difficulty.name,
                createdAt = System.currentTimeMillis(),
            ),
            plan.exercises.mapIndexed { index, e ->
                WorkoutExerciseEntity(position = index, name = e.name, sets = e.sets, reps = e.reps, durationSeconds = e.durationSeconds)
            },
        )
    }

    suspend fun removePlan() = dao.removePlan()

    fun observeCompletedDays(startDay: Long, endDay: Long): Flow<Set<Long>> =
        dao.observeLog(startDay, endDay).map { list -> list.map { it.epochDay }.toSet() }

    suspend fun setCompleted(epochDay: Long, completed: Boolean) {
        if (completed) dao.upsertLog(WorkoutLogEntity(epochDay, System.currentTimeMillis())) else dao.deleteLog(epochDay)
    }
}
