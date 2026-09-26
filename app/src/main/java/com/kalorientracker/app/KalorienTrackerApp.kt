package com.kalorientracker.app

import android.app.Application
import com.kalorientracker.app.data.db.FoodSeeder
import com.kalorientracker.app.data.db.ProductCatalogImporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KalorienTrackerApp : Application() {

    @Inject lateinit var seeder: FoodSeeder
    @Inject lateinit var catalogImporter: ProductCatalogImporter

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seeder.seedIfNeeded() }
        // Runs on Dispatchers.IO and never blocks the UI; a scan that arrives before this
        // finishes still works via the online fallback in FoodRepository.lookupBarcode.
        appScope.launch { catalogImporter.importIfNeeded() }
    }
}
