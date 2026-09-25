package com.kalorientracker.app.data.repository

import com.kalorientracker.app.data.db.GoalTargetsDao
import com.kalorientracker.app.data.db.ProfileDao
import com.kalorientracker.app.data.db.WeightDao
import com.kalorientracker.app.data.db.WeightEntryEntity
import com.kalorientracker.app.data.db.activityEntities
import com.kalorientracker.app.data.db.toDomain
import com.kalorientracker.app.data.db.toEntity
import com.kalorientracker.app.domain.model.GoalTargets
import com.kalorientracker.app.domain.model.UserProfile
import com.kalorientracker.app.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val targetsDao: GoalTargetsDao,
    private val weightDao: WeightDao,
) {
    fun observeProfile(): Flow<UserProfile?> =
        combine(profileDao.observeProfile(), profileDao.observeActivities()) { profile, activities ->
            profile?.toDomain(activities)
        }

    suspend fun getProfile(): UserProfile? = profileDao.getProfile()?.toDomain(profileDao.getActivities())

    suspend fun saveProfile(profile: UserProfile) =
        profileDao.saveProfile(profile.toEntity(), profile.activityEntities())

    fun observeTargets(): Flow<GoalTargets?> = targetsDao.observeCurrent().map { it?.toDomain() }

    suspend fun getTargets(): GoalTargets? = targetsDao.getCurrent()?.toDomain()

    suspend fun allTargets(): List<GoalTargets> = targetsDao.all().map { it.toDomain() }

    /** Adds a new version of the plan; earlier versions stay for historic comparisons. */
    suspend fun saveTargets(targets: GoalTargets) {
        targetsDao.insert(targets.toEntity(System.currentTimeMillis()))
    }

    fun observeWeights(): Flow<List<WeightEntry>> = weightDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeLatestWeight(): Flow<WeightEntry?> = weightDao.observeLatest().map { it?.toDomain() }

    /** One entry per day; saving again the same day overwrites. Keeps the profile weight current. */
    suspend fun saveWeight(epochDay: Long, weightKg: Double) {
        weightDao.upsert(WeightEntryEntity(epochDay, weightKg, System.currentTimeMillis()))
        val latest = weightDao.all().maxByOrNull { it.epochDay }
        val profile = profileDao.getProfile()
        if (profile != null && latest != null && latest.epochDay == epochDay) {
            profileDao.upsertProfile(profile.copy(weightKg = weightKg))
        }
    }

    suspend fun deleteWeight(epochDay: Long) = weightDao.delete(epochDay)
}
