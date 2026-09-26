package com.kalorientracker.app.data.repository

import com.kalorientracker.app.data.db.FoodDao
import com.kalorientracker.app.data.db.MealDao
import com.kalorientracker.app.data.db.ProductCatalogImporter
import com.kalorientracker.app.data.db.toDomain
import com.kalorientracker.app.data.db.toEntity
import com.kalorientracker.app.data.remote.OnlineProductSource
import com.kalorientracker.app.data.settings.SettingsRepository
import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.FoodSource
import com.kalorientracker.app.domain.model.Nutrients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProductLookup {
    data class Found(val food: Food, val fromOnline: Boolean) : ProductLookup
    data object NotFound : ProductLookup
    data object OnlineDisabled : ProductLookup
    data object Offline : ProductLookup
}

@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val mealDao: MealDao,
    private val online: OnlineProductSource,
    private val settings: SettingsRepository,
    private val catalogImporter: ProductCatalogImporter,
) {
    /** Whether the bundled barcode catalogue has finished importing into the local database. */
    val catalogReady: Boolean get() = catalogImporter.isImported

    suspend fun search(query: String): List<Food> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return foodDao.search(q, SEARCH_LIMIT).map { it.toDomain() }
    }

    fun observeFrequent(limit: Int = 12): Flow<List<Food>> =
        foodDao.observeFrequent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun byId(id: Long): Food? = foodDao.byId(id)?.toDomain()

    /** Resolves an analyzer ingredient name to the local database. */
    suspend fun bestMatch(name: String): Food? {
        foodDao.byName(name)?.let { return it.toDomain() }
        val base = name.substringBefore('(').trim()
        if (base.length < 3) return null
        return foodDao.search(base, limit = 1).firstOrNull()?.toDomain()
    }

    /**
     * Local database first; online only when enabled, and every online hit is cached locally.
     * A scanned code is tried in every common GTIN format (e.g. a 12-digit UPC-A is also tried
     * as its 13-digit EAN-13 form), since a product may be stored under either one.
     *
     * The bundled barcode catalogue (see [ProductCatalogImporter]) may still be importing in the
     * background when this runs. That is not special-cased here on purpose: a local miss always
     * falls through to the online path below (subject to the privacy toggle), so the user never
     * sees "nicht gefunden" merely because the import has not finished yet.
     */
    suspend fun lookupBarcode(barcode: String): ProductLookup {
        val candidates = Gtin.candidates(barcode)
        for (code in candidates) {
            foodDao.byBarcode(code)?.let { return ProductLookup.Found(it.toDomain(), fromOnline = false) }
        }
        if (!settings.current().onlineLookupEnabled) return ProductLookup.OnlineDisabled
        return try {
            for (code in candidates) {
                val remote = online.byBarcode(code) ?: continue
                return ProductLookup.Found(cache(remote.copy(barcode = code)), fromOnline = true)
            }
            ProductLookup.NotFound
        } catch (e: IOException) {
            ProductLookup.Offline
        }
    }

    sealed interface OnlineSearch {
        data class Results(val foods: List<Food>) : OnlineSearch
        data object Disabled : OnlineSearch
        data object Offline : OnlineSearch
    }

    suspend fun searchOnline(query: String): OnlineSearch {
        if (!settings.current().onlineLookupEnabled) return OnlineSearch.Disabled
        return try {
            OnlineSearch.Results(online.search(query.trim()))
        } catch (e: IOException) {
            OnlineSearch.Offline
        }
    }

    /** Stores an online result so the product works offline next time. */
    suspend fun cache(food: Food): Food {
        food.barcode?.let { code -> foodDao.byBarcode(code)?.let { return it.toDomain() } }
        val entity = food.copy(id = 0, source = FoodSource.ONLINE_CACHED).toEntity(System.currentTimeMillis())
        return food.copy(id = foodDao.insert(entity), source = FoodSource.ONLINE_CACHED)
    }

    /**
     * Keeps a food the recognizer found but [bestMatch] could not resolve, so it counts as known
     * next time. Looked up the same way [bestMatch] resolves an exact name (case-insensitively,
     * through [FoodDao.byName]) to avoid storing a duplicate for a food that already exists under
     * this name; only truly unmatched names reach here, since callers only invoke this for
     * ingredients whose [bestMatch] lookup returned nothing.
     */
    suspend fun remember(name: String, per100g: Nutrients, servingGrams: Double?): Food {
        foodDao.byName(name)?.let { return it.toDomain() }
        val food = Food(name = name, per100g = per100g, servingGrams = servingGrams, source = FoodSource.AI_ESTIMATED)
        val entity = food.toEntity(System.currentTimeMillis())
        return food.copy(id = foodDao.insert(entity))
    }

    suspend fun addCustom(food: Food): Food {
        val entity = food.copy(id = 0, source = FoodSource.USER_ADDED).toEntity(System.currentTimeMillis())
        return food.copy(id = foodDao.insert(entity), source = FoodSource.USER_ADDED)
    }

    /** The amount the user logged last time for this food — a simple personal portion memory. */
    suspend fun lastGrams(foodId: Long): Double? = mealDao.lastGramsForFood(foodId)

    companion object {
        private const val SEARCH_LIMIT = 60
    }
}
