package com.kalorientracker.app.data.db

import android.content.Context
import com.kalorientracker.app.domain.model.FoodSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the bundled base food database on first start (and after "delete all data"). */
@Singleton
class FoodSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodDao: FoodDao,
    private val json: Json,
) {
    private val mutex = Mutex()

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (foodDao.countBySource(FoodSource.BASE_DB.name) > 0) return@withLock
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val now = System.currentTimeMillis()
            val foods = json.parseToJsonElement(text).jsonArray.map { element ->
                val o = element as JsonObject
                FoodEntity(
                    name = o.str("n"),
                    kcal = o.num("k"),
                    protein = o.num("p"),
                    carbs = o.num("c"),
                    fat = o.num("f"),
                    fiber = o.num("fi"),
                    sugar = o.num("s"),
                    saturatedFat = o.num("sf"),
                    salt = o.num("sa"),
                    servingGrams = (o["sv"] as? JsonPrimitive)?.doubleOrNull,
                    source = FoodSource.BASE_DB.name,
                    createdAt = now,
                )
            }
            foodDao.insertAll(foods)
        }
    }

    private fun JsonObject.str(key: String): String = (this[key] as JsonPrimitive).contentOrNull.orEmpty()
    private fun JsonObject.num(key: String): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    companion object {
        private const val ASSET = "base_foods.json"
    }
}
