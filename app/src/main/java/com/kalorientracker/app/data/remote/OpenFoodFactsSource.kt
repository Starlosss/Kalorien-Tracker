package com.kalorientracker.app.data.remote

import com.kalorientracker.app.domain.model.Food
import com.kalorientracker.app.domain.model.FoodSource
import com.kalorientracker.app.domain.model.Nutrients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/** Optional online fallback for products that are not in the local database. */
interface OnlineProductSource {
    /** @throws IOException when the device is offline or the service is unreachable. */
    suspend fun byBarcode(barcode: String): Food?

    /** @throws IOException when the device is offline or the service is unreachable. */
    suspend fun search(query: String): List<Food>
}

class OpenFoodFactsSource @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) : OnlineProductSource {

    override suspend fun byBarcode(barcode: String): Food? = withContext(Dispatchers.IO) {
        val url = "$BASE/api/v2/product/${barcode.filter { it.isDigit() }}.json".toHttpUrl().newBuilder()
            .addQueryParameter("fields", FIELDS)
            .build()
        val root = fetch(url.toString()) ?: return@withContext null
        val status = (root["status"] as? JsonPrimitive)?.contentOrNull
        if (status != "1" && status != "success") return@withContext null
        val product = root["product"] as? JsonObject ?: return@withContext null
        parseProduct(product, barcode)
    }

    override suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) {
        val url = "$BASE/cgi/search.pl".toHttpUrl().newBuilder()
            .addQueryParameter("search_terms", query)
            .addQueryParameter("search_simple", "1")
            .addQueryParameter("action", "process")
            .addQueryParameter("json", "1")
            .addQueryParameter("page_size", "20")
            .addQueryParameter("fields", "$FIELDS,code")
            .build()
        val root = fetch(url.toString()) ?: return@withContext emptyList()
        val products = root["products"]?.jsonArray ?: return@withContext emptyList()
        products.mapNotNull { element ->
            val obj = element.jsonObject
            parseProduct(obj, obj.string("code"))
        }
    }

    private fun fetch(url: String): JsonObject? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: return null
            return json.parseToJsonElement(body) as? JsonObject
        }
    }

    private fun parseProduct(product: JsonObject, barcode: String?): Food? {
        val name = product.string("product_name")?.trim().orEmpty()
        val nutriments = product["nutriments"] as? JsonObject ?: return null
        val kcal = nutriments.number("energy-kcal_100g") ?: nutriments.number("energy_100g")?.let { it / 4.184 }
        if (name.isEmpty() || kcal == null) return null
        return Food(
            name = name,
            brand = product.string("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            barcode = barcode?.takeIf { it.isNotBlank() },
            per100g = Nutrients(
                kcal = kcal,
                protein = nutriments.number("proteins_100g") ?: 0.0,
                carbs = nutriments.number("carbohydrates_100g") ?: 0.0,
                fat = nutriments.number("fat_100g") ?: 0.0,
                fiber = nutriments.number("fiber_100g") ?: 0.0,
                sugar = nutriments.number("sugars_100g") ?: 0.0,
                saturatedFat = nutriments.number("saturated-fat_100g") ?: 0.0,
                salt = nutriments.number("salt_100g") ?: 0.0,
            ),
            servingGrams = product.number("serving_quantity")?.takeIf { it > 0 },
            source = FoodSource.ONLINE_CACHED,
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Double? {
        val element: JsonElement = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
    }

    companion object {
        private const val BASE = "https://world.openfoodfacts.org"
        private const val USER_AGENT = "KalorienTracker/0.1 (Android; offline-first)"
        private const val FIELDS = "product_name,brands,nutriments,serving_quantity"
    }
}
