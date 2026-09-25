package com.kalorientracker.app

import android.app.Application
import com.kalorientracker.app.data.db.FoodSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KalorienTrackerApp : Application() {

    @Inject lateinit var seeder: FoodSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfNeeded() }
    }
}
