package com.kalorientracker.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val onlineLookupEnabled: Boolean = true,
    val smoothCharts: Boolean = true,
    val showTargetWeightLine: Boolean = true,
    val recalibrationSnoozedUntilDay: Long = 0,
    val seedVersion: Int = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val animations = booleanPreferencesKey("animations")
        val haptics = booleanPreferencesKey("haptics")
        val onlineLookup = booleanPreferencesKey("online_lookup")
        val smoothCharts = booleanPreferencesKey("smooth_charts")
        val targetWeightLine = booleanPreferencesKey("target_weight_line")
        val recalibrationSnooze = longPreferencesKey("recalibration_snoozed_until")
        val seedVersion = intPreferencesKey("seed_version")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingCompleted = p[Keys.onboardingCompleted] ?: false,
            animationsEnabled = p[Keys.animations] ?: true,
            hapticsEnabled = p[Keys.haptics] ?: true,
            onlineLookupEnabled = p[Keys.onlineLookup] ?: true,
            smoothCharts = p[Keys.smoothCharts] ?: true,
            showTargetWeightLine = p[Keys.targetWeightLine] ?: true,
            recalibrationSnoozedUntilDay = p[Keys.recalibrationSnooze] ?: 0L,
            seedVersion = p[Keys.seedVersion] ?: 0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingCompleted(value: Boolean) = context.dataStore.edit { it[Keys.onboardingCompleted] = value }
    suspend fun setAnimations(value: Boolean) = context.dataStore.edit { it[Keys.animations] = value }
    suspend fun setHaptics(value: Boolean) = context.dataStore.edit { it[Keys.haptics] = value }
    suspend fun setOnlineLookup(value: Boolean) = context.dataStore.edit { it[Keys.onlineLookup] = value }
    suspend fun setSmoothCharts(value: Boolean) = context.dataStore.edit { it[Keys.smoothCharts] = value }
    suspend fun setShowTargetWeightLine(value: Boolean) = context.dataStore.edit { it[Keys.targetWeightLine] = value }
    suspend fun snoozeRecalibration(untilEpochDay: Long) = context.dataStore.edit { it[Keys.recalibrationSnooze] = untilEpochDay }
    suspend fun setSeedVersion(value: Int) = context.dataStore.edit { it[Keys.seedVersion] = value }

    suspend fun restore(settings: AppSettings) = context.dataStore.edit {
        it[Keys.onboardingCompleted] = settings.onboardingCompleted
        it[Keys.animations] = settings.animationsEnabled
        it[Keys.haptics] = settings.hapticsEnabled
        it[Keys.onlineLookup] = settings.onlineLookupEnabled
        it[Keys.smoothCharts] = settings.smoothCharts
        it[Keys.targetWeightLine] = settings.showTargetWeightLine
    }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
